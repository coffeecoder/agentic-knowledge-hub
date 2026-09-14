package com.agenticknowledgehub.model;

/**
 * Trusted application context, never inferred from retrieved text or an untrusted tenant header.
 */
public record SourceScope(String tenantId, String sourceName) {
  public SourceScope {
    if (tenantId == null || tenantId.isBlank() || sourceName == null || sourceName.isBlank()) {
      throw new IllegalArgumentException("Source scope must be configured");
    }
  }
}
