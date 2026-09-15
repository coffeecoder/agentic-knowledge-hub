package com.agenticknowledgehub.api;

import com.agenticknowledgehub.ingestion.PersistenceUnavailableException;
import com.agenticknowledgehub.parsers.DocumentParsingException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    DocumentParsingException.class
  })
  public ResponseEntity<Map<String, String>> invalidDocument(Exception exception) {
    // Do not echo binding errors: rejected values can contain confidential document text.
    return ResponseEntity.unprocessableContent().body(Map.of("detail", "Invalid document request"));
  }

  @ExceptionHandler(PersistenceUnavailableException.class)
  public ResponseEntity<Map<String, String>> persistenceUnavailable() {
    return ResponseEntity.status(503).body(Map.of("detail", "Document persistence unavailable"));
  }

  @ExceptionHandler(com.agenticknowledgehub.security.SearchAccessDeniedException.class)
  public ResponseEntity<Map<String, String>> searchDenied() {
    return ResponseEntity.status(403).body(Map.of("detail", "Search identity unavailable"));
  }

  @ExceptionHandler(com.agenticknowledgehub.retrieval.InvalidSearchException.class)
  public ResponseEntity<Map<String, String>> invalidSearch() {
    return ResponseEntity.unprocessableContent().body(Map.of("detail", "Invalid search request"));
  }

  @ExceptionHandler(com.agenticknowledgehub.retrieval.SearchUnavailableException.class)
  public ResponseEntity<Map<String, String>> searchUnavailable() {
    return ResponseEntity.status(503).body(Map.of("detail", "Search unavailable"));
  }
}
