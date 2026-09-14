package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.model.*;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SemanticChunkerTest {
  @Test
  void preservesScopedGoldenChunkIdsOverlapAndCitations() {
    String words = String.join(" ", IntStream.range(0, 45).mapToObj(i -> "word" + i).toList());
    var section = new ParsedSection(words, List.of("Recovery"), null, 10, 20);
    var chunker = new SemanticChunker(20, 5);
    var document = TestDocuments.text("unused");
    var chunks = chunker.chunk(TestDocuments.SCOPE, document, List.of(section));
    assertEquals(
        List.of(
            "e20ee8194203ed00d124738b5a808baa25cea695218086c58c38746bcbdcacf9",
            "95abf2724aa5283a7e38f500463515932e7e868211248314b33850160dcdf0aa",
            "96addb8ce3163a9f0685a6b4763fa3ce4ef5d9bbd031520e83923f15eda1e086"),
        chunks.stream().map(KnowledgeChunk::chunkId).toList());
    assertEquals(chunks, chunker.chunk(TestDocuments.SCOPE, document, List.of(section)));
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
                TestDocuments.SCOPE,
                TestDocuments.text("unused"),
                List.of(new ParsedSection("Cloud\u00a0Run\u2003hosts", List.of(), 2, null, null)));
    assertEquals("Cloud Run hosts", chunks.getFirst().content());
    assertEquals(2, chunks.getFirst().citation().pageNumber());
  }
}
