package com.agenticknowledgehub.model;

import java.util.List;

public record ParsedSection(
    String text, List<String> headingPath, Integer pageNumber, Integer lineStart, Integer lineEnd) {
  public ParsedSection {
    headingPath = List.copyOf(headingPath);
  }
}
