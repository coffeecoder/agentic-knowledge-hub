package com.agenticknowledgehub.ingestion;

import com.agenticknowledgehub.model.*;
import java.util.List;

public record PreparedDocument(
    SourceScope scope,
    SourceDocument document,
    String contentHash,
    String processingVersion,
    List<KnowledgeChunk> chunks) {
  public PreparedDocument {
    chunks = List.copyOf(chunks);
  }
}
