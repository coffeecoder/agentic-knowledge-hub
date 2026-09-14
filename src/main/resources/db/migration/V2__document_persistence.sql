-- Never choose a winning historical version implicitly.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM documents GROUP BY source_id, external_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Multiple versions exist for a logical document; reconcile before migration';
    END IF;
END $$;

ALTER TABLE documents DROP CONSTRAINT documents_source_id_external_id_source_version_key;
ALTER TABLE documents ADD CONSTRAINT documents_source_external_key UNIQUE (source_id, external_id);
ALTER TABLE documents ADD COLUMN processing_version TEXT NOT NULL DEFAULT 'legacy';
ALTER TABLE documents ALTER COLUMN processing_version DROP DEFAULT;
ALTER TABLE chunks ADD COLUMN word_count INTEGER CHECK (word_count >= 0);
-- Existing counts cannot safely be reinterpreted as words; leave unknown historical values null.
ALTER TABLE chunks ALTER COLUMN token_count DROP NOT NULL;
