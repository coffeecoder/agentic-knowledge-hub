package com.agenticknowledgehub.security;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

/** Application permissions are issued by an administrator, never taken from request fields. */
public record IdentityClaims(
    String tenant,
    List<String> groups,
    List<String> permissions,
    List<String> writeSources,
    List<String> assignableGroups) {
  public IdentityClaims {
    groups = List.copyOf(groups);
    permissions = List.copyOf(permissions);
    writeSources = List.copyOf(writeSources);
    assignableGroups = List.copyOf(assignableGroups);
  }

  public static IdentityClaims from(Jwt jwt) {
    Object raw = jwt.getClaims().get("akh");
    if (!(raw instanceof Map<?, ?> claims)
        || !(claims.get("tenant") instanceof String tenant)
        || tenant.isBlank()) {
      throw new AccessDeniedException("Application access denied");
    }
    return new IdentityClaims(
        tenant,
        strings(claims, "groups"),
        strings(claims, "permissions"),
        strings(claims, "write_sources"),
        strings(claims, "assignable_groups"));
  }

  private static List<String> strings(Map<?, ?> claims, String key) {
    Object raw = claims.get(key);
    if (raw == null) return List.of();
    if (!(raw instanceof List<?> values)
        || values.stream()
            .anyMatch(
                item ->
                    !(item instanceof String value)
                        || value.isBlank()
                        || !value.equals(value.strip()))) {
      throw new AccessDeniedException("Application access denied");
    }
    return values.stream().map(String.class::cast).distinct().sorted().toList();
  }
}
