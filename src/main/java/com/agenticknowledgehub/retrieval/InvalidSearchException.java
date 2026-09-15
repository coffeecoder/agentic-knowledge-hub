package com.agenticknowledgehub.retrieval;

public final class InvalidSearchException extends RuntimeException {
  public InvalidSearchException() {
    super("Invalid search request");
  }
}
