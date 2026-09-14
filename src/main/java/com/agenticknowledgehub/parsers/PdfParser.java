package com.agenticknowledgehub.parsers;

import com.agenticknowledgehub.model.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

public final class PdfParser implements DocumentParser {
  @Override
  public boolean supports(SourceDocument document) {
    return document.sourceType() == SourceType.PDF || document.mimeType().equals("application/pdf");
  }

  @Override
  public List<ParsedSection> parse(SourceDocument document) {
    try (var pdf = Loader.loadPDF(document.content())) {
      var stripper = new PDFTextStripper();
      List<ParsedSection> sections = new ArrayList<>();
      for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        String text = stripper.getText(pdf).strip();
        if (!text.isEmpty()) {
          sections.add(new ParsedSection(text, List.of(), page, null, null));
        }
      }
      return List.copyOf(sections);
    } catch (IOException exception) {
      throw new DocumentParsingException("Unable to parse PDF document");
    }
  }
}
