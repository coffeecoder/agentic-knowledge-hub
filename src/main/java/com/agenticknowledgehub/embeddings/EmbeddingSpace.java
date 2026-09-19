package com.agenticknowledgehub.embeddings;

import com.agenticknowledgehub.model.ContentHash;

/** Identifies a compatible vector space; dimensions alone do not establish compatibility. */
public record EmbeddingSpace(String provider, String model, String revision) {
  public static final int DIMENSIONS = 768;

  public EmbeddingSpace {
    if (provider == null
        || provider.isBlank()
        || model == null
        || model.isBlank()
        || revision == null
        || revision.isBlank()) {
      throw new IllegalArgumentException("Embedding provider, model and revision are required");
    }
  }

  public String id() {
    return ContentHash.sha256(frame(provider) + frame(model) + frame(revision) + ":768:l2-v1");
  }

  private static String frame(String value) {
    return value.length() + ":" + value;
  }
}
