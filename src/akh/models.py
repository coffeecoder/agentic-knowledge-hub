from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any


class SourceType(StrEnum):
    GITHUB = "GITHUB"
    WIKI = "WIKI"
    SHAREPOINT = "SHAREPOINT"
    PDF = "PDF"
    DOCX = "DOCX"
    MARKDOWN = "MARKDOWN"
    TEXT = "TEXT"


@dataclass(frozen=True)
class SourceDocument:
    external_id: str
    title: str
    source_type: SourceType
    source_version: str
    source_url: str | None
    content: bytes
    mime_type: str
    allowed_groups: tuple[str, ...] = ()
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class ParsedSection:
    text: str
    heading_path: tuple[str, ...] = ()
    page_number: int | None = None
    line_start: int | None = None
    line_end: int | None = None


@dataclass(frozen=True)
class Citation:
    source_type: SourceType
    title: str
    source_url: str | None
    source_version: str
    heading_path: tuple[str, ...] = ()
    page_number: int | None = None
    line_start: int | None = None
    line_end: int | None = None


@dataclass(frozen=True)
class KnowledgeChunk:
    chunk_id: str
    document_external_id: str
    ordinal: int
    content: str
    content_hash: str
    word_count: int
    citation: Citation
    allowed_groups: tuple[str, ...] = ()
