-- One row per unique document content. content_hash is the duplicate-detection key;
-- the UNIQUE constraint is the real guard against concurrent identical uploads.
CREATE TABLE document_registry (
    id           UUID         PRIMARY KEY,
    content_hash CHAR(64)     NOT NULL,
    filename     TEXT         NOT NULL,
    format       VARCHAR(16)  NOT NULL,
    channel      VARCHAR(16)  NOT NULL,
    categories   TEXT[]       NOT NULL DEFAULT '{}',
    status       VARCHAR(16)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_document_registry_content_hash UNIQUE (content_hash),
    CONSTRAINT ck_document_registry_content_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_document_registry_status CHECK (status IN ('PENDING', 'INGESTED'))
);
