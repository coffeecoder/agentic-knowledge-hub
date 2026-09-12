from __future__ import annotations

from akh.models import ParsedSection, SourceDocument
from akh.parsers.base import DocumentParser
from akh.parsers.docx import DocxParser
from akh.parsers.pdf import PdfParser
from akh.parsers.text import TextParser


class ParserRegistry:
    def __init__(self, parsers: list[DocumentParser] | None = None) -> None:
        self._parsers = parsers or [PdfParser(), DocxParser(), TextParser()]

    def parse(self, document: SourceDocument) -> list[ParsedSection]:
        for parser in self._parsers:
            if parser.supports(document):
                return parser.parse(document)
        raise ValueError(f"Unsupported document type: {document.mime_type}")
