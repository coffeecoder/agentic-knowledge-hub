-- Existing rows and unclassified legacy vectors are retained; semantic search only uses
-- explicitly compatible spaces. Re-ingestion populates vectors for existing documents.
ALTER TABLE documents ADD COLUMN embedding_space TEXT NOT NULL DEFAULT 'none';
ALTER TABLE documents ADD COLUMN embedding_configuration JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE documents ADD COLUMN chunk_count INTEGER;
UPDATE documents d SET chunk_count = (SELECT count(*) FROM chunks c WHERE c.document_id = d.document_id);
ALTER TABLE documents ALTER COLUMN chunk_count SET NOT NULL;
ALTER TABLE documents ADD CONSTRAINT documents_chunk_count_nonnegative CHECK (chunk_count >= 0);
ALTER TABLE chunks ADD COLUMN embedding_space TEXT;
ALTER TABLE chunks ADD COLUMN embedding_dimensions INTEGER;
ALTER TABLE chunks ADD CONSTRAINT chunks_managed_embedding_complete CHECK (
    embedding_space IS NULL OR (
        embedding IS NOT NULL AND embedding_model IS NOT NULL
        AND embedding_dimensions IS NOT NULL AND embedding_dimensions = 768
    )
);
