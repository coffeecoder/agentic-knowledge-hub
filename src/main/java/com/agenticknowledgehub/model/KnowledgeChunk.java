package com.agenticknowledgehub.model;

import java.util.List;

public record KnowledgeChunk(
    String chunkId,
    String documentExternalId,
    int ordinal,
    String content,
    String contentHash,
    int wordCount,
    Citation citation,
    List<String> allowedGroups) {
  public KnowledgeChunk {
    allowedGroups = List.copyOf(allowedGroups);
  }
}
