package com.agenticknowledgehub.ingestion;

import com.agenticknowledgehub.model.KnowledgeChunk;
import java.util.List;

public record IngestionResult(Status status, String contentHash, List<KnowledgeChunk> chunks) {
  public enum Status {
    CHANGED,
    UNCHANGED,
    EMPTY
  }

  public IngestionResult {
    chunks = List.copyOf(chunks);
  }
}
