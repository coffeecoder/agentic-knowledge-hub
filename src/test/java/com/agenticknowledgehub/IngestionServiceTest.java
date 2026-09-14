package com.agenticknowledgehub;

import static com.agenticknowledgehub.ingestion.IngestionResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.ingestion.IngestionService;
import com.agenticknowledgehub.parsers.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngestionServiceTest {
  private final IngestionService service =
      new IngestionService(
          new ParserRegistry(List.of(new TextParser())), new SemanticChunker(20, 3));

  @Test
  void comparesCallerSuppliedChecksumButDoesNotPretendToPersist() {
    var document = TestDocuments.text("some useful knowledge");
    var first = service.ingest(document);
    assertEquals(CHANGED, first.status());
    var second = service.ingest(document, first.contentHash());
    assertEquals(UNCHANGED, second.status());
    assertTrue(second.chunks().isEmpty());
    assertEquals(CHANGED, service.ingest(document).status());
  }

  @Test
  void changedContentProducesDifferentHashAndChunks() {
    var first = service.ingest(TestDocuments.text("Original evidence"));
    var changed = service.ingest(TestDocuments.text("Updated evidence"), first.contentHash());
    assertEquals(CHANGED, changed.status());
    assertNotEquals(first.contentHash(), changed.contentHash());
    assertNotEquals(first.chunks().getFirst().chunkId(), changed.chunks().getFirst().chunkId());
  }

  @Test
  void whitespaceIsEmpty() {
    var result = service.ingest(TestDocuments.text(" \n\t"));
    assertEquals(EMPTY, result.status());
    assertTrue(result.chunks().isEmpty());
  }
}
