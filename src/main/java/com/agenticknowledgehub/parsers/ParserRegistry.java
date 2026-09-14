package com.agenticknowledgehub.parsers;

import com.agenticknowledgehub.model.ParsedSection;
import com.agenticknowledgehub.model.SourceDocument;
import java.util.List;

public final class ParserRegistry {
  private final List<DocumentParser> parsers;

  public ParserRegistry(List<DocumentParser> parsers) {
    this.parsers = List.copyOf(parsers);
  }

  public List<ParsedSection> parse(SourceDocument document) {
    return parsers.stream()
        .filter(parser -> parser.supports(document))
        .findFirst()
        .orElseThrow(() -> new DocumentParsingException("Unsupported document type"))
        .parse(document);
  }
}
