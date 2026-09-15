package com.agenticknowledgehub.retrieval;

import com.agenticknowledgehub.security.CallerContext;
import java.util.List;

public interface ChunkSearchRepository {
  List<SearchHit> search(CallerContext caller, String query, int limit);
}
