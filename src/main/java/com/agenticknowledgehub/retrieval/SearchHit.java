package com.agenticknowledgehub.retrieval;

import com.agenticknowledgehub.model.Citation;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record SearchHit(
    @JsonProperty("chunk_id") String chunkId,
    @JsonProperty("document_id") UUID documentId,
    @JsonProperty("external_id") String externalId,
    String content,
    double score,
    Citation citation) {}
