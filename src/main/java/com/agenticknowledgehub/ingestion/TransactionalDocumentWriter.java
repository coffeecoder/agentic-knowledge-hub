package com.agenticknowledgehub.ingestion;

import static com.agenticknowledgehub.ingestion.IngestionResult.Status.*;

import java.util.List;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Non-final so Spring can create a class-based transaction proxy. */
public class TransactionalDocumentWriter {
  private final DocumentRepository repository;

  public TransactionalDocumentWriter(DocumentRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, timeout = 5)
  public boolean isUnchanged(PreparedDocument prepared) {
    return repository.isUnchanged(prepared);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
  public IngestionResult write(PreparedDocument prepared) {
    prepared.requireReady();
    var sourceId = repository.lockSource(prepared.scope(), prepared.document().sourceType());
    if (repository.isUnchanged(sourceId, prepared)) {
      return new IngestionResult(UNCHANGED, prepared.contentHash(), List.of());
    }
    var documentId = repository.saveDocument(sourceId, prepared);
    repository.replaceChunks(documentId, prepared);
    return new IngestionResult(CHANGED, prepared.contentHash(), prepared.chunks());
  }
}
