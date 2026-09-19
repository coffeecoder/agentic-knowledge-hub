package com.agenticknowledgehub.api;

public record SearchRequest(String query, Integer limit, String mode) {
  public SearchRequest {
    mode = mode == null ? "keyword" : mode;
    limit = limit == null ? 5 : limit;
  }
}
