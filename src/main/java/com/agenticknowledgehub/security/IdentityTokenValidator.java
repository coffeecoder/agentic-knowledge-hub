package com.agenticknowledgehub.security;

import java.time.Instant;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

/** Mandatory Identity Platform claims, in addition to Spring's issuer/time validators. */
public final class IdentityTokenValidator implements OAuth2TokenValidator<Jwt> {
  private final String project;

  public IdentityTokenValidator(String project) {
    this.project = project;
  }

  public static OAuth2TokenValidator<Jwt> forProject(String project) {
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer("https://securetoken.google.com/" + project),
        new IdentityTokenValidator(project));
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    Instant now = Instant.now();
    Object authTime = jwt.getClaims().get("auth_time");
    boolean valid =
        jwt.getAudience() != null
            && jwt.getAudience().contains(project)
            && jwt.getSubject() != null
            && !jwt.getSubject().isBlank()
            && jwt.getSubject().length() <= 128
            && jwt.getExpiresAt() != null
            && jwt.getIssuedAt() != null
            && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
            && !jwt.getIssuedAt().isAfter(now.plusSeconds(60))
            && authTime instanceof Number value
            && value.longValue() >= 0
            && value.longValue() <= now.plusSeconds(60).getEpochSecond()
            && value.longValue() <= jwt.getIssuedAt().getEpochSecond()
            && "RS256".equals(jwt.getHeaders().get("alg"))
            && jwt.getHeaders().get("kid") instanceof String kid
            && !kid.isBlank();
    return valid
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "Invalid identity token", null));
  }
}
