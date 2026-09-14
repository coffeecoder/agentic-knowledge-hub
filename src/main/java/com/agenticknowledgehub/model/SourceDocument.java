package com.agenticknowledgehub.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SourceDocument(
    String externalId,
    String title,
    SourceType sourceType,
    String sourceVersion,
    String sourceUrl,
    byte[] content,
    String mimeType,
    List<String> allowedGroups,
    Map<String, Object> metadata) {
  public SourceDocument {
    Objects.requireNonNull(externalId);
    Objects.requireNonNull(title);
    Objects.requireNonNull(sourceType);
    Objects.requireNonNull(sourceVersion);
    Objects.requireNonNull(mimeType);
    content = content.clone();
    allowedGroups = List.copyOf(allowedGroups);
    metadata = Map.copyOf(metadata);
  }

  @Override
  public byte[] content() {
    return content.clone();
  }
}
