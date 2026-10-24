-- Who submitted the document (token's preferred_username). NULL for documents ingested before
-- authentication existed, or by internal calls.
ALTER TABLE document_registry ADD COLUMN ingested_by VARCHAR(255);
