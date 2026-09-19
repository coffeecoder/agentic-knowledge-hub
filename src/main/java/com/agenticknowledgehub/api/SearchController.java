package com.agenticknowledgehub.api;

import com.agenticknowledgehub.retrieval.*;
import com.agenticknowledgehub.security.CallerContextProvider;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class SearchController {
  private final CallerContextProvider callers;
  private final RetrievalService retrieval;

  public SearchController(CallerContextProvider callers, RetrievalService retrieval) {
    this.callers = callers;
    this.retrieval = retrieval;
  }

  public record SearchResponse(List<SearchHit> results) {}

  @PostMapping("/v1/search")
  public SearchResponse search(@RequestBody SearchRequest request) {
    return new SearchResponse(
        retrieval.search(
            callers.currentCaller(), request.query(), request.limit(), request.mode()));
  }

  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public org.springframework.http.ResponseEntity<java.util.Map<String, String>> invalidJson() {
    return org.springframework.http.ResponseEntity.unprocessableContent()
        .body(java.util.Map.of("detail", "Invalid search request"));
  }
}
