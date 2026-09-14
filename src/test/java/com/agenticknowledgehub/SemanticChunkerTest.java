package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.model.*;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SemanticChunkerTest {
  @Test
  void preservesPythonGoldenChunkIdsOverlapAndCitations() {
    String words = String.join(" ", IntStream.range(0, 45).mapToObj(i -> "word" + i).toList());
    var section = new ParsedSection(words, List.of("Recovery"), null, 10, 20);
    var chunker = new SemanticChunker(20, 5);
    var document = TestDocuments.text("unused");
    var chunks = chunker.chunk(document, List.of(section));
    assertEquals(
        List.of(
            "03e5d4055cb1e9b48d04dc1fc3c6fab7ce23cc0f8e61f4fa1fea2e8737266135",
            "0864ed43a7171d959dd9e5f5cb3697e8089318810afe04129377495ae2c776f7",
            "7d4887c33bff3d4de39d7ad9c9601f4304cff0f8dd6c39b0d08ab8c790579c79"),
        chunks.stream().map(KnowledgeChunk::chunkId).toList());
    assertEquals(chunks, chunker.chunk(document, List.of(section)));
    assertEquals(List.of(20, 20, 15), chunks.stream().map(KnowledgeChunk::wordCount).toList());
    assertTrue(chunks.get(1).content().startsWith("word15 word16"));
    var citation = chunks.getFirst().citation();
    assertEquals(List.of("Recovery"), citation.headingPath());
    assertEquals(10, citation.lineStart());
    assertEquals(20, citation.lineEnd());
    assertEquals("v1", citation.sourceVersion());
    assertEquals("https://example.test/runbook", citation.sourceUrl());
    assertEquals(List.of("support"), chunks.getFirst().allowedGroups());
  }

  @Test
  void rejectsInvalidWindowConfiguration() {
    assertThrows(IllegalArgumentException.class, () -> new SemanticChunker(19, 0));
    assertThrows(IllegalArgumentException.class, () -> new SemanticChunker(20, 20));
    assertThrows(IllegalArgumentException.class, () -> new SemanticChunker(20, -1));
  }

  @Test
  void unicodeWhitespaceSeparatesWords() {
    var chunks =
        new SemanticChunker(20, 0)
            .chunk(
                TestDocuments.text("unused"),
                List.of(new ParsedSection("Cloud\u00a0Run\u2003hosts", List.of(), 2, null, null)));
    assertEquals("Cloud Run hosts", chunks.getFirst().content());
    assertEquals(2, chunks.getFirst().citation().pageNumber());
  }
}
