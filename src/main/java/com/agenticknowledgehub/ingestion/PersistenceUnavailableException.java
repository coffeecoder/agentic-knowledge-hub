package com.agenticknowledgehub.ingestion;

/** Intentionally omits the JDBC cause, which may contain SQL parameters or document content. */
public final class PersistenceUnavailableException extends RuntimeException {
  public PersistenceUnavailableException() {
    super("Document persistence unavailable");
  }
}
