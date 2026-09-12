from __future__ import annotations

import re

from akh.models import ParsedSection, SourceDocument, SourceType
from akh.parsers.base import DocumentParser


class TextParser(DocumentParser):
    _types = {SourceType.TEXT, SourceType.MARKDOWN, SourceType.GITHUB, SourceType.WIKI}

    def supports(self, document: SourceDocument) -> bool:
        return document.source_type in self._types or document.mime_type.startswith("text/")

    def parse(self, document: SourceDocument) -> list[ParsedSection]:
        text = document.content.decode("utf-8", errors="replace").replace("\r\n", "\n")
        lines = text.splitlines()
        sections: list[ParsedSection] = []
        headings: list[str] = []
        buffer: list[str] = []
        start = 1

        def flush(end: int) -> None:
            nonlocal buffer, start
            content = "\n".join(buffer).strip()
            if content:
                sections.append(ParsedSection(content, tuple(headings), None, start, end))
            buffer = []

        for number, line in enumerate(lines, start=1):
            match = re.match(r"^(#{1,6})\s+(.+?)\s*$", line)
            if match:
                flush(number - 1)
                level = len(match.group(1))
                headings[:] = headings[: level - 1]
                headings.append(match.group(2))
                start = number + 1
            else:
                if not buffer:
                    start = number
                buffer.append(line)
        flush(len(lines))
        return sections or [ParsedSection(text.strip(), (), None, 1, len(lines))]
