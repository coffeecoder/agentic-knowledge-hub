package com.agenticknowledgehub.retrieval;

public final class SearchUnavailableException extends RuntimeException {
  public SearchUnavailableException() {
    super("Search unavailable");
  }
}
