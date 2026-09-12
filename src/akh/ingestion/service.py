from __future__ import annotations

from dataclasses import dataclass
import hashlib

from akh.chunking import SemanticChunker
from akh.models import KnowledgeChunk, SourceDocument
from akh.parsers import ParserRegistry


@dataclass(frozen=True)
class IngestionResult:
    status: str
    content_hash: str
    chunks: tuple[KnowledgeChunk, ...]


class IngestionService:
    def __init__(
        self, parsers: ParserRegistry | None = None, chunker: SemanticChunker | None = None
    ) -> None:
        self.parsers = parsers or ParserRegistry()
        self.chunker = chunker or SemanticChunker()

    def ingest(
        self, document: SourceDocument, previous_hash: str | None = None
    ) -> IngestionResult:
        content_hash = hashlib.sha256(document.content).hexdigest()
        if previous_hash == content_hash:
            return IngestionResult("UNCHANGED", content_hash, ())
        sections = self.parsers.parse(document)
        chunks = tuple(self.chunker.chunk(document, sections))
        if not chunks:
            return IngestionResult("EMPTY", content_hash, ())
        return IngestionResult("CHANGED", content_hash, chunks)
