package com.agenticknowledgehub.retrieval;

import com.agenticknowledgehub.embeddings.*;
import com.agenticknowledgehub.security.CallerContext;
import java.util.List;
import org.springframework.dao.DataAccessException;

public final class RetrievalService {
  private final ChunkSearchRepository repository;
  private final VectorSearchRepository vectors;
  private final EmbeddingProvider embeddings;

  public RetrievalService(ChunkSearchRepository repository) {
    this(
        repository,
        (caller, query, space, limit) -> {
          throw new EmbeddingUnavailableException();
        },
        new DisabledEmbeddingProvider());
  }

  public RetrievalService(
      ChunkSearchRepository repository,
      VectorSearchRepository vectors,
      EmbeddingProvider embeddings) {
    this.repository = repository;
    this.vectors = vectors;
    this.embeddings = embeddings;
  }

  public List<SearchHit> search(CallerContext caller, String query, int limit) {
    return search(caller, query, limit, "keyword");
  }

  public List<SearchHit> search(CallerContext caller, String query, int limit, String mode) {
    if (!("keyword".equals(mode) || "semantic".equals(mode))) throw new InvalidSearchException();
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
      if (mode.equals("semantic")) {
        if (!embeddings.enabled()) throw new EmbeddingUnavailableException();
        var vector = embeddings.embed(query.strip(), EmbeddingProvider.Task.QUERY);
        return vectors.search(caller, vector, embeddings.space(), limit);
      }
      return repository.search(caller, query.strip(), limit);
    } catch (DataAccessException exception) {
      throw new SearchUnavailableException();
    }
  }
}
