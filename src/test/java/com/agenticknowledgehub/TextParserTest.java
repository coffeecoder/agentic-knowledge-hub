package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.parsers.TextParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextParserTest {
  @Test
  void preservesHeadingHierarchyAndLineAnchors() {
    var sections =
        new TextParser()
            .parse(
                TestDocuments.text(
                    "# Architecture\r\nCloud Run hosts the API.\r\n\r\n## Security\r\nDeny by default."));
    assertEquals(2, sections.size());
    assertEquals(List.of("Architecture"), sections.getFirst().headingPath());
    assertEquals("Cloud Run hosts the API.", sections.getFirst().text());
    assertEquals(2, sections.getFirst().lineStart());
    assertEquals(3, sections.getFirst().lineEnd());
    assertEquals(List.of("Architecture", "Security"), sections.get(1).headingPath());
    assertEquals(5, sections.get(1).lineStart());
  }

  @Test
  void keepsNewlinesWithinASectionAndReplacesSiblingHeadings() {
    var sections = new TextParser().parse(TestDocuments.text("# One\na\nb\n# Two\nc\n"));
    assertEquals("a\nb", sections.getFirst().text());
    assertEquals(List.of("Two"), sections.get(1).headingPath());
    assertEquals(5, sections.get(1).lineEnd());
  }

  @Test
  void preservesTrailingBlankLinesInSourceAnchor() {
    var sections = new TextParser().parse(TestDocuments.text("evidence\n\n\n"));
    assertEquals("evidence", sections.getFirst().text());
    assertEquals(3, sections.getFirst().lineEnd());
  }
}
