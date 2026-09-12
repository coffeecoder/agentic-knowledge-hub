from __future__ import annotations

from dataclasses import dataclass
import re

from akh.models import KnowledgeChunk


@dataclass(frozen=True)
class CitationRecord:
    citation_id: str
    chunk: KnowledgeChunk


class CitationManifest:
    def __init__(self, chunks: list[KnowledgeChunk]) -> None:
        self.records = tuple(CitationRecord(f"C{i}", chunk) for i, chunk in enumerate(chunks, 1))
        self._by_id = {record.citation_id: record for record in self.records}

    def validate_answer(self, answer: str) -> tuple[bool, tuple[str, ...]]:
        referenced = tuple(dict.fromkeys(re.findall(r"\[(C\d+)\]", answer)))
        unknown = tuple(cid for cid in referenced if cid not in self._by_id)
        return not unknown, unknown

    def prompt_context(self) -> str:
        return "\n\n".join(f"[{r.citation_id}] {r.chunk.content}" for r in self.records)
