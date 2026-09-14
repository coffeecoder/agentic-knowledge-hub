package com.agenticknowledgehub.ingestion;

import static com.agenticknowledgehub.ingestion.IngestionResult.Status.*;

import com.agenticknowledgehub.chunking.SemanticChunker;
import com.agenticknowledgehub.model.ContentHash;
import com.agenticknowledgehub.model.SourceDocument;
import com.agenticknowledgehub.parsers.ParserRegistry;
import java.util.List;

public final class IngestionService {
  private final ParserRegistry parsers;
  private final SemanticChunker chunker;

  public IngestionService(ParserRegistry parsers, SemanticChunker chunker) {
    this.parsers = parsers;
    this.chunker = chunker;
  }

  public IngestionResult ingest(SourceDocument document) {
    return ingest(document, null);
  }

  /** Compatibility baseline only: persisted checksum lookup belongs to the next milestone. */
  public IngestionResult ingest(SourceDocument document, String previousHash) {
    String hash = ContentHash.sha256(document.content());
    if (hash.equals(previousHash)) {
      return new IngestionResult(UNCHANGED, hash, List.of());
    }
    var chunks = chunker.chunk(document, parsers.parse(document));
    return new IngestionResult(chunks.isEmpty() ? EMPTY : CHANGED, hash, chunks);
  }
}
