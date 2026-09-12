from __future__ import annotations

from akh.models import ParsedSection, SourceDocument, SourceType
from akh.parsers.base import DocumentParser


class PdfParser(DocumentParser):
    def supports(self, document: SourceDocument) -> bool:
        return document.source_type == SourceType.PDF or document.mime_type == "application/pdf"

    def parse(self, document: SourceDocument) -> list[ParsedSection]:
        try:
            import fitz
        except ImportError as exc:
            raise RuntimeError("Install the pymupdf dependency to parse PDF files") from exc
        pdf = fitz.open(stream=document.content, filetype="pdf")
        return [
            ParsedSection(page.get_text("text").strip(), (), page.number + 1)
            for page in pdf
            if page.get_text("text").strip()
        ]
