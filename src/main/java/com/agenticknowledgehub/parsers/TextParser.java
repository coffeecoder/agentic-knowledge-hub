package com.agenticknowledgehub.parsers;

import com.agenticknowledgehub.model.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public final class TextParser implements DocumentParser {
  private static final Set<SourceType> TYPES =
      EnumSet.of(SourceType.TEXT, SourceType.MARKDOWN, SourceType.GITHUB, SourceType.WIKI);
  private static final Pattern HEADING =
      Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$", Pattern.UNICODE_CHARACTER_CLASS);

  @Override
  public boolean supports(SourceDocument document) {
    return TYPES.contains(document.sourceType()) || document.mimeType().startsWith("text/");
  }

  @Override
  public List<ParsedSection> parse(SourceDocument document) {
    String text = new String(document.content(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    String[] lines = text.split("\r\n|[\n\r\u000B\f\u001C-\u001E\u0085\u2028\u2029]", -1);
    // Python splitlines keeps interior/trailing blank lines but omits the final terminator.
    if (lines.length > 0 && lines[lines.length - 1].isEmpty()) {
      lines = Arrays.copyOf(lines, lines.length - 1);
    }
    List<ParsedSection> sections = new ArrayList<>();
    List<String> headings = new ArrayList<>();
    List<String> buffer = new ArrayList<>();
    int start = 1;
    for (int i = 0; i < lines.length; i++) {
      var match = HEADING.matcher(lines[i]);
      if (match.matches()) {
        flush(sections, buffer, headings, start, i);
        int level = match.group(1).length();
        while (headings.size() > level - 1) {
          headings.removeLast();
        }
        headings.add(match.group(2));
        start = i + 2;
      } else {
        if (buffer.isEmpty()) {
          start = i + 1;
        }
        buffer.add(lines[i]);
      }
    }
    flush(sections, buffer, headings, start, lines.length);
    if (sections.isEmpty()) {
      sections.add(
          new ParsedSection(text.strip(), List.of(), null, 1, text.isEmpty() ? 0 : lines.length));
    }
    return List.copyOf(sections);
  }

  private static void flush(
      List<ParsedSection> sections,
      List<String> buffer,
      List<String> headings,
      int start,
      int end) {
    String content = String.join("\n", buffer).strip();
    if (!content.isEmpty()) {
      sections.add(new ParsedSection(content, headings, null, start, end));
    }
    buffer.clear();
  }
}
