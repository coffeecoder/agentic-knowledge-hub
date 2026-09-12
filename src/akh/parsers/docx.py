from __future__ import annotations

from io import BytesIO

from akh.models import ParsedSection, SourceDocument, SourceType
from akh.parsers.base import DocumentParser


class DocxParser(DocumentParser):
    def supports(self, document: SourceDocument) -> bool:
        return document.source_type == SourceType.DOCX or "wordprocessingml" in document.mime_type

    def parse(self, document: SourceDocument) -> list[ParsedSection]:
        try:
            from docx import Document
        except ImportError as exc:
            raise RuntimeError("Install the python-docx dependency to parse DOCX files") from exc
        word = Document(BytesIO(document.content))
        headings: list[str] = []
        sections: list[ParsedSection] = []
        buffer: list[str] = []

        def flush() -> None:
            nonlocal buffer
            content = "\n".join(buffer).strip()
            if content:
                sections.append(ParsedSection(content, tuple(headings)))
            buffer = []

        for paragraph in word.paragraphs:
            text = paragraph.text.strip()
            if not text:
                continue
            style = paragraph.style.name if paragraph.style else ""
            if style.startswith("Heading"):
                flush()
                try:
                    level = int(style.split()[-1])
                except ValueError:
                    level = 1
                headings[:] = headings[: level - 1]
                headings.append(text)
            else:
                buffer.append(text)
        flush()
        return sections
