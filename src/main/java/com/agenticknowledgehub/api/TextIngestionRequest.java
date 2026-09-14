package com.agenticknowledgehub.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TextIngestionRequest(
    @JsonProperty("external_id") @NotNull String externalId,
    @NotNull String title,
    @NotNull String text,
    @JsonProperty("source_version") String sourceVersion,
    @JsonProperty("source_url") String sourceUrl,
    @JsonProperty("allowed_groups") List<@NotNull String> allowedGroups) {
  public TextIngestionRequest {
    sourceVersion = sourceVersion == null ? "1" : sourceVersion;
    allowedGroups = allowedGroups == null ? List.of() : List.copyOf(allowedGroups);
  }
}
