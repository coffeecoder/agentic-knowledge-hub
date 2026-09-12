from __future__ import annotations

import hashlib
import re

from akh.models import Citation, KnowledgeChunk, ParsedSection, SourceDocument


class SemanticChunker:
    def __init__(self, max_words: int = 550, overlap_words: int = 90) -> None:
        if max_words < 20:
            raise ValueError("max_words must be at least 20")
        if overlap_words < 0 or overlap_words >= max_words:
            raise ValueError("overlap_words must be non-negative and smaller than max_words")
        self.max_words = max_words
        self.overlap_words = overlap_words

    def chunk(
        self, document: SourceDocument, sections: list[ParsedSection]
    ) -> list[KnowledgeChunk]:
        chunks: list[KnowledgeChunk] = []
        ordinal = 0
        for section in sections:
            words = re.findall(r"\S+", section.text)
            if not words:
                continue
            step = self.max_words - self.overlap_words
            for start in range(0, len(words), step):
                part = words[start : start + self.max_words]
                if not part:
                    break
                content = " ".join(part)
                digest = hashlib.sha256(content.encode("utf-8")).hexdigest()
                stable_key = (
                    f"{document.external_id}:{document.source_version}:{ordinal}:{digest[:16]}"
                )
                chunk_id = hashlib.sha256(stable_key.encode("utf-8")).hexdigest()
                citation = Citation(
                    source_type=document.source_type,
                    title=document.title,
                    source_url=document.source_url,
                    source_version=document.source_version,
                    heading_path=section.heading_path,
                    page_number=section.page_number,
                    line_start=section.line_start,
                    line_end=section.line_end,
                )
                chunks.append(
                    KnowledgeChunk(
                        chunk_id,
                        document.external_id,
                        ordinal,
                        content,
                        digest,
                        len(part),
                        citation,
                        document.allowed_groups,
                    )
                )
                ordinal += 1
                if start + self.max_words >= len(words):
                    break
        return chunks
