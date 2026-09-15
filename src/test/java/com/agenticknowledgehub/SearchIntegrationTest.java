package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.ingestion.*;
import com.agenticknowledgehub.model.*;
import com.agenticknowledgehub.parsers.*;
import com.agenticknowledgehub.retrieval.*;
import com.agenticknowledgehub.security.CallerContext;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SearchIntegrationTest extends PostgresTestSupport {
  @Autowired TransactionalDocumentWriter writer;
  @Autowired RetrievalService retrieval;
  @Autowired JdbcTemplate jdbc;

  @Test
  void filtersUnauthorizedEvidenceBeforeLimitAndPreservesCitations() {
    String tenant = UUID.randomUUID().toString();
    var allowed = ingest(tenant, "allowed", "Cloud Run deployment guide", List.of("support"));
    ingest(tenant, "restricted", "deployment deployment deployment deployment", List.of("admin"));
    ingest(tenant, "unassigned", "deployment deployment", List.of());
    ingest(tenant + "-other", "other-tenant", "deployment deployment", List.of("support"));
    var hits = retrieval.search(caller(tenant), "deployment", 1);
    assertEquals(1, hits.size());
    assertEquals(allowed.chunks().getFirst().chunkId(), hits.getFirst().chunkId());
    assertEquals(allowed.chunks().getFirst().citation(), hits.getFirst().citation());
    assertEquals("Cloud Run deployment guide", hits.getFirst().content());
    assertTrue(hits.getFirst().score() > 0);
    assertTrue(
        retrieval
            .search(new CallerContext("learner", tenant, List.of()), "deployment", 5)
            .isEmpty());
  }

  @Test
  void excludesDeletedDocumentsInactiveChunksAndInactiveSources() {
    String tenant = UUID.randomUUID().toString();
    var deleted = ingest(tenant, "deleted", "deployment guide", List.of("support"));
    var inactive = ingest(tenant, "inactive", "deployment guide", List.of("support"));
    ingest(tenant, "disabled-source", "deployment guide", List.of("support"));
    jdbc.update(
        "UPDATE documents SET is_deleted = true WHERE document_id = (SELECT document_id FROM chunks WHERE chunk_id = ?)",
        deleted.chunks().getFirst().chunkId());
    jdbc.update(
        "UPDATE chunks SET is_active = false WHERE chunk_id = ?",
        inactive.chunks().getFirst().chunkId());
    jdbc.update(
        "UPDATE sources SET status = 'DISABLED' WHERE tenant_id = ? AND name = 'disabled-source'",
        tenant);
    assertTrue(retrieval.search(caller(tenant), "deployment", 20).isEmpty());
  }

  @Test
  void ranksMatchesAndBreaksTiesDeterministically() {
    String tenant = UUID.randomUUID().toString();
    ingest(tenant, "one", "Cloud Run deployment guide", List.of("support"));
    ingest(tenant, "two", "Cloud Run deployment guide", List.of("support"));
    ingest(tenant, "three", "deployment deployment deployment deployment", List.of("support"));
    var hits = retrieval.search(caller(tenant), "deployment", 20);
    assertEquals(3, hits.size());
    assertEquals("three", hits.getFirst().externalId());
    assertTrue(hits.get(0).score() > hits.get(1).score());
    assertTrue(hits.get(1).chunkId().compareTo(hits.get(2).chunkId()) < 0);
    assertEquals(hits, retrieval.search(caller(tenant), "deployment", 20));
    assertTrue(retrieval.search(caller(tenant), "unfindablexyz", 5).isEmpty());
    assertTrue(retrieval.search(caller(tenant), "the and", 5).isEmpty());
    assertEquals(2, retrieval.search(caller(tenant), "\"Cloud Run\"", 20).size());
  }

  @Test
  void malformedStoredCitationFailsWithoutExposingStoredText() {
    String tenant = UUID.randomUUID().toString();
    var result = ingest(tenant, "corrupt", "deployment", List.of("support"));
    jdbc.update(
        "UPDATE chunks SET citation = ?::jsonb WHERE chunk_id = ?",
        "{\"sourceType\":\"synthetic-sensitive-value\"}",
        result.chunks().getFirst().chunkId());
    var failure =
        assertThrows(
            SearchUnavailableException.class,
            () -> retrieval.search(caller(tenant), "deployment", 5));
    assertNull(failure.getCause());
  }

  private CallerContext caller(String tenant) {
    return new CallerContext("learner", tenant, List.of("support"));
  }

  private IngestionResult ingest(String tenant, String id, String text, List<String> groups) {
    var service =
        new IngestionService(
            new ParserRegistry(List.of(new TextParser())),
            new SemanticChunker(20, 5),
            writer,
            new SourceScope(tenant, id));
    return service.ingest(
        new SourceDocument(
            id,
            "Guide",
            SourceType.TEXT,
            "1",
            "https://example.test/guide",
            text.getBytes(StandardCharsets.UTF_8),
            "text/plain",
            groups,
            Map.of()));
  }
}
