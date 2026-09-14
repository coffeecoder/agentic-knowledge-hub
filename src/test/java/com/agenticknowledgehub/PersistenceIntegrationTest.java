package com.agenticknowledgehub;

import static com.agenticknowledgehub.ingestion.IngestionResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.ingestion.*;
import com.agenticknowledgehub.model.*;
import com.agenticknowledgehub.parsers.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class PersistenceIntegrationTest extends PostgresTestSupport {
  @Autowired TransactionalDocumentWriter writer;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;

  @Test
  void firstRepeatAndChangedContentPersistAcrossServiceInstances() {
    var scope = scope();
    var original =
        doc("# Recovery\n" + words(45), "v1", List.of("support"), Map.of("owner", "platform"));
    var first = service(scope).ingest(original);
    assertEquals(CHANGED, first.status());
    assertEquals(3, first.chunks().size());
    var id = documentId(scope);
    assertEquals(3, countChunks(id));
    var row = jdbc.queryForMap("SELECT * FROM chunks WHERE document_id = ? AND ordinal = 0", id);
    assertEquals(20, row.get("word_count"));
    assertNull(row.get("token_count"));
    assertNull(row.get("embedding"));
    var citation = json.readValue(row.get("citation").toString(), Citation.class);
    assertEquals(first.chunks().getFirst().citation(), citation);
    assertEquals(List.of("Recovery"), citation.headingPath());
    assertEquals(2, citation.lineStart());
    var before =
        jdbc.queryForMap(
            "SELECT content_hash, updated_at FROM documents WHERE document_id = ?", id);
    assertEquals(UNCHANGED, service(scope).ingest(original).status());
    assertEquals(
        before,
        jdbc.queryForMap(
            "SELECT content_hash, updated_at FROM documents WHERE document_id = ?", id));
    var changed =
        service(scope).ingest(doc("Replacement evidence", "v1", List.of("support"), Map.of()));
    assertEquals(CHANGED, changed.status());
    assertEquals(id, documentId(scope));
    assertEquals(1, countChunks(id));
    for (var old : first.chunks()) {
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT count(*) FROM chunks WHERE chunk_id = ?", Integer.class, old.chunkId()));
    }
    assertEquals(
        UNCHANGED,
        service(scope)
            .ingest(doc("Replacement evidence", "v1", List.of("support"), Map.of()))
            .status());
  }

  @Test
  void metadataPermissionsAndProcessingChangesRefreshEvidence() {
    var scope = scope();
    var first = doc("evidence", "v1", List.of("a", "b"), Map.of("x", 1));
    service(scope).ingest(first);
    assertEquals(
        UNCHANGED,
        service(scope)
            .ingest(doc("evidence", "v1", List.of("b", "a", "a"), Map.of("x", 1)))
            .status());
    var changed =
        new SourceDocument(
            first.externalId(),
            "New title",
            first.sourceType(),
            "v2",
            "https://example.test/new",
            first.content(),
            first.mimeType(),
            List.of("admin"),
            Map.of("x", 2));
    var result = service(scope).ingest(changed);
    assertEquals(CHANGED, result.status());
    var row =
        jdbc.queryForMap(
            "SELECT source_version, title, source_url, to_jsonb(allowed_groups)::text AS groups, metadata::text AS metadata FROM documents WHERE document_id = ?",
            documentId(scope));
    assertEquals("v2", row.get("source_version"));
    assertEquals("New title", row.get("title"));
    assertEquals("https://example.test/new", row.get("source_url"));
    assertEquals("[\"admin\"]", row.get("groups"));
    assertEquals(2, json.readTree(row.get("metadata").toString()).get("x").asInt());
    var revised =
        new IngestionService(
            new ParserRegistry(List.of(new TextParser())),
            new SemanticChunker(20, 5, "revision-2"),
            writer,
            scope);
    var rebuilt = revised.ingest(changed);
    assertEquals(CHANGED, rebuilt.status());
    assertNotEquals(result.chunks().getFirst().chunkId(), rebuilt.chunks().getFirst().chunkId());
    assertEquals(UNCHANGED, revised.ingest(changed).status());
  }

  @Test
  void emptyInputPreservesPreviousEvidence() {
    var scope = scope();
    var original = doc("evidence", "v1", List.of(), Map.of());
    service(scope).ingest(original);
    var id = documentId(scope);
    assertEquals(EMPTY, service(scope).ingest(doc(" \n", "v1", List.of(), Map.of())).status());
    assertEquals(1, countChunks(id));
    assertEquals(UNCHANGED, service(scope).ingest(original).status());
  }

  @Test
  void tenantAndSourceScopesDoNotCollide() {
    var first = scope();
    var otherTenant = new SourceScope(first.tenantId() + "-other", first.sourceName());
    var otherSource = new SourceScope(first.tenantId(), first.sourceName() + "-other");
    var document = doc("evidence", "v1", List.of(), Map.of());
    Set<String> ids = new HashSet<>();
    for (var scope : List.of(first, otherTenant, otherSource)) {
      ids.add(service(scope).ingest(document).chunks().getFirst().chunkId());
      assertEquals(1, countChunks(documentId(scope)));
    }
    assertEquals(3, ids.size());
  }

  @Test
  void simultaneousFirstIngestionsCommitOnlyOnce() throws Exception {
    assertTrue(AopUtils.isAopProxy(writer), "Transaction annotation must be intercepted");
    var scope = scope();
    var document = doc(words(45), "v1", List.of(), Map.of());
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<IngestionResult.Status> call =
          () -> {
            start.await();
            return service(scope).ingest(document).status();
          };
      var a = executor.submit(call);
      var b = executor.submit(call);
      start.countDown();
      assertEquals(
          Set.of(CHANGED, UNCHANGED),
          Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
    }
    assertEquals(3, countChunks(documentId(scope)));
  }

  @Test
  void failedSecondChunkInsertRollsBackDocumentAndAllChunkWrites() {
    var scope = scope();
    var original = doc("Original evidence", "v1", List.of("support"), Map.of());
    var first = service(scope).ingest(original);
    var id = documentId(scope);
    String name = "reject_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute(
        "CREATE FUNCTION "
            + name
            + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.document_id = '"
            + id
            + "'::uuid AND NEW.ordinal = 1 THEN RAISE EXCEPTION 'Synthetic write failure'; END IF; RETURN NEW; END $$");
    jdbc.execute(
        "CREATE TRIGGER "
            + name
            + " BEFORE INSERT ON chunks FOR EACH ROW EXECUTE FUNCTION "
            + name
            + "()");
    try {
      assertThrows(
          PersistenceUnavailableException.class,
          () -> service(scope).ingest(doc(words(45), "v2", List.of("admin"), Map.of())));
      assertEquals(
          first.contentHash(),
          jdbc.queryForObject(
              "SELECT content_hash FROM documents WHERE document_id = ?", String.class, id));
      assertEquals(
          "v1",
          jdbc.queryForObject(
              "SELECT source_version FROM documents WHERE document_id = ?", String.class, id));
      assertEquals(1, countChunks(id));
      assertEquals(
          first.chunks().getFirst().chunkId(),
          jdbc.queryForObject(
              "SELECT chunk_id FROM chunks WHERE document_id = ?", String.class, id));
    } finally {
      jdbc.execute("DROP TRIGGER " + name + " ON chunks");
      jdbc.execute("DROP FUNCTION " + name + "()");
    }
    assertEquals(UNCHANGED, service(scope).ingest(original).status());
  }

  private SourceScope scope() {
    return new SourceScope("test-" + UUID.randomUUID(), "manual");
  }

  private IngestionService service(SourceScope scope) {
    return new IngestionService(
        new ParserRegistry(List.of(new TextParser())), new SemanticChunker(20, 5), writer, scope);
  }

  private SourceDocument doc(
      String text, String version, List<String> groups, Map<String, Object> metadata) {
    return new SourceDocument(
        "same-external-id",
        "Runbook",
        SourceType.TEXT,
        version,
        "https://example.test/runbook",
        text.getBytes(StandardCharsets.UTF_8),
        "text/plain",
        groups,
        metadata);
  }

  private UUID documentId(SourceScope scope) {
    return jdbc.queryForObject(
        "SELECT d.document_id FROM documents d JOIN sources s USING(source_id) WHERE s.tenant_id = ? AND s.name = ?",
        UUID.class,
        scope.tenantId(),
        scope.sourceName());
  }

  private int countChunks(UUID id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM chunks WHERE document_id = ?", Integer.class, id);
  }

  private String words(int count) {
    return String.join(" ", IntStream.range(0, count).mapToObj(i -> "word" + i).toList());
  }
}
