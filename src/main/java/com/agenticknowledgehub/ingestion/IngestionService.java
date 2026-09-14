package com.agenticknowledgehub.ingestion;

import static com.agenticknowledgehub.ingestion.IngestionResult.Status.EMPTY;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.model.*;
import com.agenticknowledgehub.parsers.ParserRegistry;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

public final class IngestionService {
  private final ParserRegistry parsers;
  private final SemanticChunker chunker;
  private final TransactionalDocumentWriter writer;
  private final SourceScope scope;

  public IngestionService(
      ParserRegistry parsers,
      SemanticChunker chunker,
      TransactionalDocumentWriter writer,
      SourceScope scope) {
    this.parsers = parsers;
    this.chunker = chunker;
    this.writer = writer;
    this.scope = scope;
  }

  public IngestionResult ingest(SourceDocument document) {
    String hash = ContentHash.sha256(document.content());
    // Preparation stays outside the transaction; concurrent writers recheck durable state under
    // lock.
    var chunks = chunker.chunk(scope, document, parsers.parse(document));
    if (chunks.isEmpty()) {
      return new IngestionResult(EMPTY, hash, List.of());
    }
    try {
      return writer.write(
          new PreparedDocument(scope, document, hash, chunker.processingVersion(), chunks));
    } catch (DataAccessException | TransactionException exception) {
      // Includes errors from proxy commit, not just SQL statements in the method body.
      throw new PersistenceUnavailableException();
    }
  }
}
