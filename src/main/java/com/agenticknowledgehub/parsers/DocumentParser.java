package com.agenticknowledgehub.parsers;

import com.agenticknowledgehub.model.ParsedSection;
import com.agenticknowledgehub.model.SourceDocument;
import java.util.List;

public interface DocumentParser {
  boolean supports(SourceDocument document);

  List<ParsedSection> parse(SourceDocument document);
}
