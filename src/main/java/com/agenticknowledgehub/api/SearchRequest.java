package com.agenticknowledgehub.api;

public record SearchRequest(String query, Integer limit) {
  public SearchRequest {
    limit = limit == null ? 5 : limit;
  }
}
