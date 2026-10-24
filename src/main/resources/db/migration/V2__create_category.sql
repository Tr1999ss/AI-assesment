-- Category registry. The AI classifier reads name + description, so descriptions should say
-- what belongs in a category (and what doesn't), not just restate the name.
CREATE TABLE category (
    name        VARCHAR(64)  PRIMARY KEY,
    description TEXT         NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_category_name CHECK (name ~ '^[a-z0-9][a-z0-9_-]*$'),
    CONSTRAINT ck_category_description CHECK (length(trim(description)) > 0)
);

-- Placeholder seed; replace with real categories in a later migration.
-- 'general' is the fallback used when classification is not confident (ingestion.category.fallback).
INSERT INTO category (name, description) VALUES
    ('hr',          'People matters: hiring, onboarding, leave, benefits, compensation, performance reviews, workplace conduct policies.'),
    ('finance',     'Money matters: budgets, invoices, expenses and reimbursement, pricing, revenue, accounting, tax, financial reports.'),
    ('legal',       'Contracts, terms of service, privacy and data protection, compliance, regulatory obligations, licensing, disputes.'),
    ('engineering', 'Software and systems: architecture, APIs, code, infrastructure, deployment, runbooks, technical specifications.'),
    ('general',     'Anything that does not clearly fit another category: company announcements, miscellaneous notes, mixed-topic content.');
