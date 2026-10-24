-- Optional BM25 index for retrieval.keyword.engine=paradedb-bm25.
-- Only applied with the 'paradedb' profile, which adds this location to spring.flyway.locations,
-- and only works on a database with the pg_search extension (paradedb/paradedb image).
-- Repeatable migration: runs after all versioned migrations whenever this file changes,
-- so enabling it later never conflicts with version ordering.

CREATE EXTENSION IF NOT EXISTS pg_search;

CREATE INDEX IF NOT EXISTS vector_store_bm25_idx
    ON vector_store USING bm25 (id, content)
    WITH (key_field = 'id');
