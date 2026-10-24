-- Flyway owns the vector store table from here on (spring.ai.vectorstore.pgvector.initialize-schema=false).
-- Matches the schema Spring AI's PgVectorStore expects, so the vector store keeps working unchanged.
-- Idempotent: on existing databases the table and HNSW index already exist; on fresh ones they are created.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
    id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(${embedding_dimensions})
);

CREATE INDEX IF NOT EXISTS spring_ai_vector_index
    ON vector_store USING hnsw (embedding vector_cosine_ops);

-- Keyword retrieval: section title weighted above body text. The 'english' text search configuration
-- (stemming, stop words) must match PostgresFullTextEngine.TEXT_SEARCH_CONFIG.
ALTER TABLE vector_store
    ADD COLUMN IF NOT EXISTS content_tsv tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(metadata ->> 'title', '')), 'A') ||
        setweight(to_tsvector('english', coalesce(content, '')), 'B')
    ) STORED;

CREATE INDEX IF NOT EXISTS vector_store_content_tsv_idx
    ON vector_store USING gin (content_tsv);
