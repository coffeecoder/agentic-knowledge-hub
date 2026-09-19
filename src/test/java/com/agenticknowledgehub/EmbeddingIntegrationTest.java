package com.agenticknowledgehub;

import static com.agenticknowledgehub.ingestion.IngestionResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.embeddings.*;
import com.agenticknowledgehub.ingestion.*;
import com.agenticknowledgehub.model.*;
import com.agenticknowledgehub.parsers.*;
import com.agenticknowledgehub.retrieval.VectorSearchRepository;
import com.agenticknowledgehub.security.CallerContext;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
class EmbeddingIntegrationTest extends PostgresTestSupport {
  @Autowired TransactionalDocumentWriter writer;
  @Autowired VectorSearchRepository search;
  @Autowired JdbcTemplate jdbc;

  static class CountingProvider implements EmbeddingProvider {
    final AtomicInteger calls = new AtomicInteger();
    final String revision;
    int failAt = -1;
    CyclicBarrier barrier;

    CountingProvider(String revision) {
      this.revision = revision;
    }

    @Override
    public EmbeddingSpace space() {
      return new EmbeddingSpace("test", "axis", revision);
    }

    @Override
    public EmbeddingVector embed(String text, Task task) {
      assertFalse(
          TransactionSynchronizationManager.isActualTransactionActive(),
          "Model call must not hold a DB transaction");
      if (calls.incrementAndGet() == failAt) throw new EmbeddingUnavailableException();
      if (barrier != null) {
        try {
          barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
          throw new AssertionError(exception);
        }
      }
      return vector(text.contains("far") ? 1 : 0);
    }
  }

  @Test
  void backfillsExistingDocumentsThenSkipsCallsAndRebuildsChangedContentOrSpace() {
    var scope = scope();
    var original = doc("doc", "original evidence", List.of("support"));
    service(scope, new DisabledEmbeddingProvider()).ingest(original);
    var provider = new CountingProvider("v1");
    var service = service(scope, provider);
    assertEquals(CHANGED, service.ingest(original).status());
    assertEquals(1, provider.calls.get());
    assertEquals(UNCHANGED, service(scope, provider).ingest(original).status());
    assertEquals(1, provider.calls.get());
    assertEquals(CHANGED, service.ingest(doc("doc", "new evidence", List.of("support"))).status());
    assertEquals(2, provider.calls.get());
    var revised = new CountingProvider("v2");
    assertEquals(
        CHANGED,
        service(scope, revised).ingest(doc("doc", "new evidence", List.of("support"))).status());
    assertTrue(search.search(caller(scope), vector(0), provider.space(), 5).isEmpty());
    var hits = search.search(caller(scope), vector(0), revised.space(), 5);
    assertEquals(1, hits.size());
    assertEquals("new evidence", hits.getFirst().content());
    assertEquals("Guide", hits.getFirst().citation().title());
  }

  @Test
  void missingChunksAndVectorsAreRepairedDespiteSameChecksum() {
    var scope = scope();
    var provider = new CountingProvider("v1");
    var original = doc("doc", "some evidence", List.of("support"));
    var service = service(scope, provider);
    var result = service.ingest(original);
    String chunk = result.chunks().getFirst().chunkId();
    jdbc.update(
        "UPDATE chunks SET embedding = NULL, embedding_space = NULL WHERE chunk_id = ?", chunk);
    assertEquals(CHANGED, service.ingest(original).status());
    jdbc.update("DELETE FROM chunks WHERE chunk_id = ?", chunk);
    assertEquals(CHANGED, service.ingest(original).status());
    assertEquals(3, provider.calls.get());
    assertEquals(UNCHANGED, service.ingest(original).status());
  }

  @Test
  void providerFailureAndChunkLimitLeaveOriginalDocumentIntact() {
    var scope = scope();
    var provider = new CountingProvider("v1");
    var original = doc("doc", "old evidence", List.of("support"));
    var service = service(scope, provider);
    service.ingest(original);
    provider.failAt = 3; // first new chunk succeeds, second fails; nothing is committed.
    assertThrows(
        EmbeddingUnavailableException.class,
        () -> service.ingest(doc("doc", words(45), List.of("support"))));
    assertEquals(
        "old evidence",
        search.search(caller(scope), vector(0), provider.space(), 5).getFirst().content());
    assertEquals(UNCHANGED, service.ingest(original).status());
    var bounded =
        new IngestionService(
            new ParserRegistry(List.of(new TextParser())),
            new SemanticChunker(20, 5),
            writer,
            scope,
            provider,
            1);
    int before = provider.calls.get();
    assertThrows(
        EmbeddingInputException.class,
        () -> bounded.ingest(doc("doc", words(45), List.of("support"))));
    assertEquals(before, provider.calls.get());
  }

  @org.junit.jupiter.api.RepeatedTest(10)
  void concurrentDuplicatesRecheckUnderLockAfterExternalCalls() throws Exception {
    var scope = scope();
    var provider = new CountingProvider("v1");
    provider.barrier = new CyclicBarrier(2);
    var original = doc("doc", "same evidence", List.of("support"));
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> service(scope, provider).ingest(original).status());
      var second = executor.submit(() -> service(scope, provider).ingest(original).status());
      assertEquals(
          Set.of(CHANGED, UNCHANGED),
          Set.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
    }
    assertEquals(2, provider.calls.get());
    assertEquals(1, search.search(caller(scope), vector(0), provider.space(), 5).size());
  }

  @Test
  void exactRankingFiltersTenantAclLifecycleAndIncompatibleSpacesBeforeLimit() {
    var scope = scope();
    var provider = new CountingProvider("v1");
    var service = service(scope, provider);
    service.ingest(doc("denied", "perfect evidence", List.of("admin")));
    service.ingest(doc("empty-acl", "perfect evidence", List.of()));
    service(new SourceScope(scope.tenantId() + "-other", "manual"), provider)
        .ingest(doc("tenant-denied", "perfect evidence", List.of("support")));
    service(scope, new CountingProvider("v2"))
        .ingest(doc("space-denied", "perfect evidence", List.of("support")));
    service.ingest(doc("allowed", "far evidence", List.of("support")));
    var hits = search.search(caller(scope), vector(0), provider.space(), 1);
    assertEquals(1, hits.size());
    assertEquals("allowed", hits.getFirst().externalId());
    assertEquals(0, hits.getFirst().score(), 1e-6);
    UUID id = hits.getFirst().documentId();
    jdbc.update("UPDATE documents SET is_deleted = true WHERE document_id = ?", id);
    assertTrue(search.search(caller(scope), vector(0), provider.space(), 5).isEmpty());
    jdbc.update("UPDATE documents SET is_deleted = false WHERE document_id = ?", id);
    jdbc.update("UPDATE chunks SET is_active = false WHERE document_id = ?", id);
    assertTrue(search.search(caller(scope), vector(0), provider.space(), 5).isEmpty());
    jdbc.update("UPDATE chunks SET is_active = true WHERE document_id = ?", id);
    jdbc.update("UPDATE sources SET status = 'INACTIVE' WHERE tenant_id = ?", scope.tenantId());
    assertTrue(search.search(caller(scope), vector(0), provider.space(), 5).isEmpty());
  }

  @Test
  void databaseFailureRollsBackVectorsWithDocumentAndCitations() {
    var scope = scope();
    var provider = new CountingProvider("v1");
    var original = doc("doc", "far old evidence", List.of("support"));
    var first = service(scope, provider).ingest(original);
    String chunkId = first.chunks().getFirst().chunkId();
    UUID id =
        jdbc.queryForObject(
            "SELECT document_id FROM chunks WHERE chunk_id = ?", UUID.class, chunkId);
    String before =
        jdbc.queryForObject(
            "SELECT embedding::text FROM chunks WHERE chunk_id = ?", String.class, chunkId);
    String trigger = "embedding_fail_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute(
        "CREATE FUNCTION "
            + trigger
            + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.document_id = '"
            + id
            + "'::uuid AND NEW.ordinal = 1 THEN RAISE EXCEPTION 'Synthetic failure'; END IF; RETURN NEW; END $$");
    jdbc.execute(
        "CREATE TRIGGER "
            + trigger
            + " BEFORE INSERT ON chunks FOR EACH ROW EXECUTE FUNCTION "
            + trigger
            + "()");
    try {
      assertThrows(
          PersistenceUnavailableException.class,
          () -> service(scope, provider).ingest(doc("doc", words(45), List.of("admin"))));
      assertEquals(
          before,
          jdbc.queryForObject(
              "SELECT embedding::text FROM chunks WHERE chunk_id = ?", String.class, chunkId));
      assertEquals(
          "far old evidence",
          search.search(caller(scope), vector(1), provider.space(), 5).getFirst().content());
      assertEquals(UNCHANGED, service(scope, provider).ingest(original).status());
    } finally {
      jdbc.execute("DROP TRIGGER " + trigger + " ON chunks");
      jdbc.execute("DROP FUNCTION " + trigger + "()");
    }
  }

  private IngestionService service(SourceScope scope, EmbeddingProvider provider) {
    return new IngestionService(
        new ParserRegistry(List.of(new TextParser())),
        new SemanticChunker(20, 5),
        writer,
        scope,
        provider,
        16);
  }

  private SourceScope scope() {
    return new SourceScope("embedding-" + UUID.randomUUID(), "manual");
  }

  private CallerContext caller(SourceScope scope) {
    return new CallerContext("test", scope.tenantId(), List.of("support"));
  }

  private SourceDocument doc(String id, String text, List<String> groups) {
    return new SourceDocument(
        id,
        "Guide",
        SourceType.TEXT,
        "1",
        null,
        text.getBytes(StandardCharsets.UTF_8),
        "text/plain",
        groups,
        Map.of());
  }

  private static EmbeddingVector vector(int axis) {
    double[] values = new double[768];
    values[axis] = 1;
    return new EmbeddingVector(values);
  }

  private String words(int count) {
    return String.join(" ", Collections.nCopies(count, "evidence"));
  }
}
