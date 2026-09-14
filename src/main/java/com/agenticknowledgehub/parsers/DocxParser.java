package com.agenticknowledgehub.parsers;

import com.agenticknowledgehub.model.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

public final class DocxParser implements DocumentParser {
  private static final Pattern HEADING =
      Pattern.compile("^Heading\\s*(\\d+).*$", Pattern.CASE_INSENSITIVE);

  @Override
  public boolean supports(SourceDocument document) {
    return document.sourceType() == SourceType.DOCX
        || document.mimeType().contains("wordprocessingml");
  }

  @Override
  public List<ParsedSection> parse(SourceDocument document) {
    try (var word = new XWPFDocument(new ByteArrayInputStream(document.content()))) {
      List<ParsedSection> sections = new ArrayList<>();
      List<String> headings = new ArrayList<>();
      List<String> buffer = new ArrayList<>();
      for (var paragraph : word.getParagraphs()) {
        String text = paragraph.getText().strip();
        if (text.isEmpty()) {
          continue;
        }
        String styleName = paragraph.getStyle();
        var style =
            word.getStyles() == null || styleName == null
                ? null
                : word.getStyles().getStyle(styleName);
        if (style != null && style.getName() != null) {
          styleName = style.getName();
        }
        var match = HEADING.matcher(styleName == null ? "" : styleName);
        if (match.matches()) {
          flush(sections, buffer, headings);
          int level = Integer.parseInt(match.group(1));
          while (!headings.isEmpty() && headings.size() > Math.max(0, level - 1)) {
            headings.removeLast();
          }
          headings.add(text);
        } else {
          buffer.add(text);
        }
      }
      flush(sections, buffer, headings);
      return List.copyOf(sections);
    } catch (IOException | RuntimeException exception) {
      throw new DocumentParsingException("Unable to parse DOCX document");
    }
  }

  private static void flush(
      List<ParsedSection> sections, List<String> buffer, List<String> headings) {
    String content = String.join("\n", buffer).strip();
    if (!content.isEmpty()) {
      sections.add(new ParsedSection(content, headings, null, null, null));
    }
    buffer.clear();
  }
}
