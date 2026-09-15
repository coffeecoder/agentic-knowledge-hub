package com.agenticknowledgehub.security;

import java.util.List;

public record CallerContext(String principalId, String tenantId, List<String> groups) {
  public CallerContext {
    if (principalId == null || principalId.isBlank() || tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("Caller identity must be configured");
    }
    groups =
        groups.stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().sorted().toList();
  }
}
