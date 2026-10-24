-- Final ingestion outcome, written when a document moves from PENDING to INGESTED.
ALTER TABLE document_registry
    ADD COLUMN primary_category VARCHAR(64),
    ADD COLUMN chunk_count      INTEGER,
    ADD CONSTRAINT ck_document_registry_chunk_count CHECK (chunk_count IS NULL OR chunk_count > 0),
    ADD CONSTRAINT ck_document_registry_ingested_complete
        CHECK (status <> 'INGESTED' OR (primary_category IS NOT NULL AND chunk_count IS NOT NULL));

-- The stale-claim sweeper looks up old PENDING rows.
CREATE INDEX ix_document_registry_pending_created_at
    ON document_registry (created_at)
    WHERE status = 'PENDING';
