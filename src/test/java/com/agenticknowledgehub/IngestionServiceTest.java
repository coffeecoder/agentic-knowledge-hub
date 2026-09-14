package com.agenticknowledgehub;

import static com.agenticknowledgehub.ingestion.IngestionResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.ingestion.*;
import com.agenticknowledgehub.parsers.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class IngestionServiceTest {
  @Test
  void preparesEvidenceAndProcessingVersionBeforeCallingWriter() {
    var captured = new AtomicReference<PreparedDocument>();
    var writer =
        new TransactionalDocumentWriter(null) {
          @Override
          public IngestionResult write(PreparedDocument prepared) {
            captured.set(prepared);
            return new IngestionResult(CHANGED, prepared.contentHash(), prepared.chunks());
          }
        };
    var service = service(writer);
    assertEquals(CHANGED, service.ingest(TestDocuments.text("useful evidence")).status());
    assertEquals("parsers-v1/chunker-v2:20:3", captured.get().processingVersion());
    assertEquals(TestDocuments.SCOPE, captured.get().scope());
    assertEquals(1, captured.get().chunks().size());
  }

  @Test
  void whitespaceDoesNotCallWriter() {
    var writer =
        new TransactionalDocumentWriter(null) {
          @Override
          public IngestionResult write(PreparedDocument prepared) {
            fail("Empty must not write");
            return null;
          }
        };
    assertEquals(EMPTY, service(writer).ingest(TestDocuments.text(" \n\t")).status());
  }

  @Test
  void databaseFailureDoesNotLeakJdbcDetails() {
    var writer =
        new TransactionalDocumentWriter(null) {
          @Override
          public IngestionResult write(PreparedDocument prepared) {
            throw new DataAccessResourceFailureException("synthetic-sensitive SQL parameters");
          }
        };
    var error =
        assertThrows(
            PersistenceUnavailableException.class,
            () -> service(writer).ingest(TestDocuments.text("evidence")));
    assertNull(error.getCause());
    assertFalse(error.getMessage().contains("sensitive"));
  }

  private IngestionService service(TransactionalDocumentWriter writer) {
    return new IngestionService(
        new ParserRegistry(List.of(new TextParser())),
        new SemanticChunker(20, 3),
        writer,
        TestDocuments.SCOPE);
  }
}
