package com.agenticknowledgehub.security;

import com.agenticknowledgehub.ingestion.*;
import com.agenticknowledgehub.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthorizedIngestionService {
  private final IngestionService ingestion;
  private final String source;

  public AuthorizedIngestionService(
      IngestionService ingestion, @Value("${akh.source.name}") String source) {
    this.ingestion = ingestion;
    this.source = source;
  }

  public IngestionResult ingest(SourceDocument document) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
      throw new AccessDeniedException("Application access denied");
    }
    var claims = IdentityClaims.from(token.getToken());
    // A publisher has source-wide replacement rights, with bounded ACL assignment.
    if (!claims.permissions().contains("knowledge:write")
        || !claims.writeSources().contains(source)
        || !claims.assignableGroups().containsAll(document.allowedGroups())) {
      throw new AccessDeniedException("Application access denied");
    }
    return ingestion.ingest(new SourceScope(claims.tenant(), source), document);
  }
}
