package com.agenticknowledgehub.embeddings;

public final class EmbeddingInputException extends RuntimeException {
  public EmbeddingInputException() {
    super("Embedding input exceeds limits or was rejected");
  }
}
