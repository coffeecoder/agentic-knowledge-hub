package com.agenticknowledgehub.security;

public final class SearchAccessDeniedException extends RuntimeException {
  public SearchAccessDeniedException() {
    super("Search identity unavailable");
  }
}
