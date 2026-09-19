package com.agenticknowledgehub.embeddings;

public final class EmbeddingUnavailableException extends RuntimeException {
  public EmbeddingUnavailableException() {
    super("Embedding service unavailable");
  }
}
