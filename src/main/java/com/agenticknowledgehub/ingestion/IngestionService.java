package com.agenticknowledgehub.ingestion;

import static com.agenticknowledgehub.ingestion.IngestionResult.Status.EMPTY;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.embeddings.*;
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
  private final EmbeddingProvider embeddings;
  private final int maxEmbeddingChunks;

  public IngestionService(
      ParserRegistry parsers,
      SemanticChunker chunker,
      TransactionalDocumentWriter writer,
      SourceScope scope) {
    this(parsers, chunker, writer, scope, new DisabledEmbeddingProvider(), 16);
  }

  public IngestionService(
      ParserRegistry parsers,
      SemanticChunker chunker,
      TransactionalDocumentWriter writer,
      SourceScope scope,
      EmbeddingProvider embeddings,
      int maxEmbeddingChunks) {
    if (maxEmbeddingChunks < 1 || maxEmbeddingChunks > 64)
      throw new IllegalArgumentException("Embedding chunk limit must be 1-64");
    this.embeddings = embeddings;
    this.maxEmbeddingChunks = maxEmbeddingChunks;
    this.parsers = parsers;
    this.chunker = chunker;
    this.writer = writer;
    this.scope = scope;
  }

  public IngestionResult ingest(SourceDocument document) {
    return ingest(scope, document);
  }

  public IngestionResult ingest(SourceScope scope, SourceDocument document) {
    String hash = ContentHash.sha256(document.content());
    // Preparation stays outside the transaction; concurrent writers recheck durable state under
    // lock.
    var chunks = chunker.chunk(scope, document, parsers.parse(document));
    if (chunks.isEmpty()) {
      return new IngestionResult(EMPTY, hash, List.of());
    }
    try {
      var prepared =
          new PreparedDocument(
              scope,
              document,
              hash,
              chunker.processingVersion(),
              chunks,
              embeddings.enabled() ? embeddings.space() : null,
              List.of());
      if (embeddings.enabled()) {
        if (chunks.size() > maxEmbeddingChunks) throw new EmbeddingInputException();
        // An unchanged read is valid at this snapshot. Changed writes recheck after acquiring a
        // lock.
        if (writer.isUnchanged(prepared)) {
          return new IngestionResult(IngestionResult.Status.UNCHANGED, hash, List.of());
        }
        chunks.forEach(chunk -> EmbeddingProvider.validateInput(chunk.content()));
        var vectors =
            chunks.stream()
                .map(chunk -> embeddings.embed(chunk.content(), EmbeddingProvider.Task.DOCUMENT))
                .toList();
        prepared =
            new PreparedDocument(
                scope,
                document,
                hash,
                chunker.processingVersion(),
                chunks,
                embeddings.space(),
                vectors);
      }
      return writer.write(prepared);
    } catch (DataAccessException | TransactionException exception) {
      // Includes errors from proxy commit, not just SQL statements in the method body.
      throw new PersistenceUnavailableException();
    }
  }
}
