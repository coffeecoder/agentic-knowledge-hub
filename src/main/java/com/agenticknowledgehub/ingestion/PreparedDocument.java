package com.agenticknowledgehub.ingestion;

import com.agenticknowledgehub.embeddings.*;
import com.agenticknowledgehub.model.*;
import java.util.List;

public record PreparedDocument(
    SourceScope scope,
    SourceDocument document,
    String contentHash,
    String processingVersion,
    List<KnowledgeChunk> chunks,
    EmbeddingSpace embeddingSpace,
    List<EmbeddingVector> embeddings) {
  public PreparedDocument {
    chunks = List.copyOf(chunks);
    embeddings = List.copyOf(embeddings);
  }

  public PreparedDocument(
      SourceScope scope,
      SourceDocument document,
      String contentHash,
      String processingVersion,
      List<KnowledgeChunk> chunks) {
    this(scope, document, contentHash, processingVersion, chunks, null, List.of());
  }

  public String embeddingSpaceId() {
    return embeddingSpace == null ? "none" : embeddingSpace.id();
  }

  public void requireReady() {
    if ((embeddingSpace == null && !embeddings.isEmpty())
        || (embeddingSpace != null && embeddings.size() != chunks.size())) {
      throw new EmbeddingUnavailableException();
    }
  }
}
