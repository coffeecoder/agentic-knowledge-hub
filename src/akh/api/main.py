from __future__ import annotations

from fastapi import FastAPI
from pydantic import BaseModel, Field

from akh import __version__
from akh.chunking import SemanticChunker
from akh.ingestion import IngestionService
from akh.models import SourceDocument, SourceType

app = FastAPI(title="Agentic Knowledge Hub", version=__version__)
ingestion = IngestionService(chunker=SemanticChunker())


class TextIngestionRequest(BaseModel):
    external_id: str
    title: str
    text: str
    source_version: str = "1"
    source_url: str | None = None
    allowed_groups: list[str] = Field(default_factory=list)


@app.get("/health/live")
def liveness() -> dict[str, str]:
    return {"status": "UP", "version": __version__}


@app.post("/v1/documents/text")
def ingest_text(request: TextIngestionRequest) -> dict[str, object]:
    document = SourceDocument(
        external_id=request.external_id,
        title=request.title,
        source_type=SourceType.TEXT,
        source_version=request.source_version,
        source_url=request.source_url,
        content=request.text.encode("utf-8"),
        mime_type="text/plain",
        allowed_groups=tuple(request.allowed_groups),
    )
    result = ingestion.ingest(document)
    return {
        "status": result.status,
        "content_hash": result.content_hash,
        "chunk_count": len(result.chunks),
        "chunk_ids": [chunk.chunk_id for chunk in result.chunks],
    }
