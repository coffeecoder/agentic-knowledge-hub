package com.agenticknowledgehub.model;

import java.util.List;

public record Citation(
    SourceType sourceType,
    String title,
    String sourceUrl,
    String sourceVersion,
    List<String> headingPath,
    Integer pageNumber,
    Integer lineStart,
    Integer lineEnd) {
  public Citation {
    headingPath = List.copyOf(headingPath);
  }
}
