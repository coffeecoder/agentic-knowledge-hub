CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS sources (
    source_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type TEXT NOT NULL,
    name TEXT NOT NULL,
    base_uri TEXT,
    tenant_id TEXT NOT NULL,
    checkpoint JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, source_type, name)
);

CREATE TABLE IF NOT EXISTS documents (
    document_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES sources(source_id),
    external_id TEXT NOT NULL,
    title TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    source_version TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    source_url TEXT,
    allowed_groups TEXT[] NOT NULL DEFAULT '{}',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_id, external_id, source_version)
);

CREATE TABLE IF NOT EXISTS chunks (
    chunk_id TEXT PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(document_id),
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    heading_path TEXT[] NOT NULL DEFAULT '{}',
    page_number INTEGER,
    line_start INTEGER,
    line_end INTEGER,
    token_count INTEGER NOT NULL,
    citation JSONB NOT NULL,
    embedding_model TEXT,
    embedding vector(768),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, ordinal)
);

CREATE INDEX IF NOT EXISTS chunks_embedding_hnsw
ON chunks USING hnsw (embedding vector_cosine_ops)
WHERE embedding IS NOT NULL AND is_active = TRUE;

CREATE INDEX IF NOT EXISTS chunks_content_fts
ON chunks USING gin (to_tsvector('english', content));

CREATE TABLE IF NOT EXISTS sync_runs (
    run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES sources(source_id),
    status TEXT NOT NULL,
    checkpoint_before JSONB,
    checkpoint_after JSONB,
    discovered_count INTEGER NOT NULL DEFAULT 0,
    changed_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    error JSONB,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS query_audit (
    request_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    principal_id TEXT NOT NULL,
    tenant_id TEXT NOT NULL,
    intent TEXT NOT NULL,
    filters JSONB NOT NULL DEFAULT '{}'::jsonb,
    retrieved_chunk_ids TEXT[] NOT NULL DEFAULT '{}',
    model_name TEXT,
    outcome TEXT NOT NULL,
    latency_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

