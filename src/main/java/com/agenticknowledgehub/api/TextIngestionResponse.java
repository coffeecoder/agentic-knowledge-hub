package com.agenticknowledgehub.api;

import com.agenticknowledgehub.ingestion.IngestionResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TextIngestionResponse(
    IngestionResult.Status status,
    @JsonProperty("content_hash") String contentHash,
    @JsonProperty("chunk_count") int chunkCount,
    @JsonProperty("chunk_ids") List<String> chunkIds) {}
