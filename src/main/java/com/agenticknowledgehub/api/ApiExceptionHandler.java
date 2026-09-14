package com.agenticknowledgehub.api;

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
}
