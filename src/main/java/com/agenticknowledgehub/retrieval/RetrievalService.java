package com.agenticknowledgehub.retrieval;

import com.agenticknowledgehub.security.CallerContext;
import java.util.List;
import org.springframework.dao.DataAccessException;

public final class RetrievalService {
  private final ChunkSearchRepository repository;

  public RetrievalService(ChunkSearchRepository repository) {
    this.repository = repository;
  }

  public List<SearchHit> search(CallerContext caller, String query, int limit) {
    if (query == null
        || query.isBlank()
        || query.length() > 500
        || query.indexOf(0) >= 0
        || limit < 1
        || limit > 20) {
      throw new InvalidSearchException();
    }
    if (caller == null) {
      throw new com.agenticknowledgehub.security.SearchAccessDeniedException();
    }
    if (caller.groups().isEmpty()) {
      return List.of();
    }
    try {
      return repository.search(caller, query.strip(), limit);
    } catch (DataAccessException exception) {
      throw new SearchUnavailableException();
    }
  }
}
