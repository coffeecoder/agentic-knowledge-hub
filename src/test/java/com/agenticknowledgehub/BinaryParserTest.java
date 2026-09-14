package com.agenticknowledgehub;

import static org.junit.jupiter.api.Assertions.*;

import com.agenticknowledgehub.model.SourceType;
import com.agenticknowledgehub.parsers.*;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class BinaryParserTest {
  @Test
  void pdfRetainsPageNumbersAndSkipsEmptyPages() throws Exception {
    byte[] bytes;
    try (var pdf = new PDDocument();
        var output = new ByteArrayOutputStream()) {
      pdf.addPage(new PDPage());
      var page = new PDPage();
      pdf.addPage(page);
      try (var content = new PDPageContentStream(pdf, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(50, 700);
        content.showText("Synthetic evidence");
        content.endText();
      }
      pdf.save(output);
      bytes = output.toByteArray();
    }
    var sections =
        new PdfParser().parse(TestDocuments.document(SourceType.PDF, "application/pdf", bytes));
    assertEquals(1, sections.size());
    assertEquals(2, sections.getFirst().pageNumber());
    assertEquals("Synthetic evidence", sections.getFirst().text());
  }

  @Test
  void docxRetainsHeadingHierarchy() throws Exception {
    byte[] bytes;
    try (var word = new XWPFDocument();
        var output = new ByteArrayOutputStream()) {
      var heading = word.createParagraph();
      heading.setStyle("Heading1");
      heading.createRun().setText("Architecture");
      word.createParagraph().createRun().setText("Cloud Run hosts the API.");
      var subheading = word.createParagraph();
      subheading.setStyle("Heading2");
      subheading.createRun().setText("Security");
      word.createParagraph().createRun().setText("Deny by default.");
      word.write(output);
      bytes = output.toByteArray();
    }
    var sections =
        new DocxParser()
            .parse(
                TestDocuments.document(
                    SourceType.DOCX,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    bytes));
    assertEquals(2, sections.size());
    assertEquals(List.of("Architecture"), sections.getFirst().headingPath());
    assertEquals(List.of("Architecture", "Security"), sections.get(1).headingPath());
    assertEquals("Deny by default.", sections.get(1).text());
  }

  @Test
  void malformedDocumentsFailWithoutEchoingContent() {
    byte[] secret = "synthetic-sensitive-payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var pdfError =
        assertThrows(
            DocumentParsingException.class,
            () ->
                new PdfParser()
                    .parse(TestDocuments.document(SourceType.PDF, "application/pdf", secret)));
    var docxError =
        assertThrows(
            DocumentParsingException.class,
            () ->
                new DocxParser()
                    .parse(TestDocuments.document(SourceType.DOCX, "application/docx", secret)));
    assertFalse(pdfError.getMessage().contains("synthetic-sensitive"));
    assertFalse(docxError.getMessage().contains("synthetic-sensitive"));
    assertThrows(
        DocumentParsingException.class,
        () ->
            new ParserRegistry(List.of(new TextParser()))
                .parse(
                    TestDocuments.document(
                        SourceType.SHAREPOINT, "application/octet-stream", secret)));
  }
}
