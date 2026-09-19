package com.agenticknowledgehub.embeddings;

/** Explicit keyword-only mode; it must never silently fall back from another provider. */
public final class DisabledEmbeddingProvider implements EmbeddingProvider {
  @Override
  public boolean enabled() {
    return false;
  }

  @Override
  public EmbeddingSpace space() {
    return new EmbeddingSpace("none", "none", "v1");
  }

  @Override
  public EmbeddingVector embed(String text, Task task) {
    throw new EmbeddingUnavailableException();
  }
}
