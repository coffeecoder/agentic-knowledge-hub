package com.agenticknowledgehub.embeddings;

import com.agenticknowledgehub.model.ContentHash;
import java.util.Locale;

/** Offline pipeline demo only: hashed word features, not learned semantic embeddings. */
public final class LocalHashEmbeddingProvider implements EmbeddingProvider {
  @Override
  public EmbeddingSpace space() {
    return new EmbeddingSpace("local-hash", "hashed-words", "v1");
  }

  @Override
  public EmbeddingVector embed(String text, Task task) {
    EmbeddingProvider.validateInput(text);
    double[] values = new double[EmbeddingSpace.DIMENSIONS];
    for (String word : text.toLowerCase(Locale.ROOT).split("\\s+")) {
      String hash = ContentHash.sha256(word);
      int bucket = (int) (Long.parseLong(hash.substring(0, 8), 16) % values.length);
      values[bucket] += 1;
    }
    return new EmbeddingVector(values);
  }
}
