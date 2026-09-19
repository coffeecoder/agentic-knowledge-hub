package com.agenticknowledgehub.ingestion;

import com.agenticknowledgehub.model.SourceScope;
import com.agenticknowledgehub.model.SourceType;
import java.util.UUID;

/** Write operations run on the writer's transaction-bound connection. */
public interface DocumentRepository {
  boolean isUnchanged(PreparedDocument prepared);

  UUID lockSource(SourceScope scope, SourceType type);

  boolean isUnchanged(UUID sourceId, PreparedDocument prepared);

  UUID saveDocument(UUID sourceId, PreparedDocument prepared);

  void replaceChunks(UUID documentId, PreparedDocument prepared);
}
