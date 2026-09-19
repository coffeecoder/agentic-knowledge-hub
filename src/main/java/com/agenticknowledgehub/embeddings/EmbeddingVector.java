package com.agenticknowledgehub.embeddings;

import java.util.Arrays;

/** Immutable, normalized vector. Never include embedding values in diagnostic output. */
public final class EmbeddingVector {
  private final double[] values;

  public EmbeddingVector(double[] input) {
    if (input == null || input.length != EmbeddingSpace.DIMENSIONS)
      throw new EmbeddingUnavailableException();
    values = input.clone();
    double norm = 0;
    for (double value : values) {
      if (!Double.isFinite(value)) throw new EmbeddingUnavailableException();
      norm = Math.hypot(norm, value);
    }
    if (!Double.isFinite(norm) || norm == 0) throw new EmbeddingUnavailableException();
    for (int i = 0; i < values.length; i++) values[i] /= norm;
  }

  public double[] values() {
    return values.clone();
  }

  /** Only pass this value as a bound SQL parameter; never log it. */
  public String sqlValue() {
    return Arrays.toString(values);
  }

  @Override
  public String toString() {
    return "EmbeddingVector[768 dimensions; values redacted]";
  }
}
