package com.agenticknowledgehub.api;

import com.agenticknowledgehub.ingestion.IngestionService;
import com.agenticknowledgehub.model.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class DocumentController {
  private final IngestionService ingestion;

  public DocumentController(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  @GetMapping("/health/live")
  public Map<String, String> liveness() {
    return Map.of("status", "UP", "version", "0.1.0");
  }

  @PostMapping("/v1/documents/text")
  public TextIngestionResponse ingestText(@Valid @RequestBody TextIngestionRequest request) {
    var document =
        new SourceDocument(
            request.externalId(),
            request.title(),
            SourceType.TEXT,
            request.sourceVersion(),
            request.sourceUrl(),
            request.text().getBytes(StandardCharsets.UTF_8),
            "text/plain",
            request.allowedGroups(),
            Map.of());
    var result = ingestion.ingest(document);
    return new TextIngestionResponse(
        result.status(),
        result.contentHash(),
        result.chunks().size(),
        result.chunks().stream().map(KnowledgeChunk::chunkId).toList());
  }
}
