package com.agenticknowledgehub.retrieval;

import com.agenticknowledgehub.embeddings.*;
import com.agenticknowledgehub.security.CallerContext;
import java.util.List;

public interface VectorSearchRepository {
  List<SearchHit> search(
      CallerContext caller, EmbeddingVector query, EmbeddingSpace space, int limit);
}
