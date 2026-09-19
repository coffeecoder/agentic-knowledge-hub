package com.agenticknowledgehub.embeddings;

public interface EmbeddingProvider {
  enum Task {
    DOCUMENT,
    QUERY
  }

  EmbeddingSpace space();

  EmbeddingVector embed(String text, Task task);

  default boolean enabled() {
    return true;
  }

  static void validateInput(String text) {
    if (text == null || text.isBlank() || text.length() > 32000 || text.indexOf(0) >= 0) {
      throw new EmbeddingInputException();
    }
  }
}
