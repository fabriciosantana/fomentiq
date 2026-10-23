CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE funding_agency (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    acronym VARCHAR(40) NOT NULL,
    official_url VARCHAR(2048),
    CONSTRAINT uk_funding_agency_acronym UNIQUE (acronym)
);

CREATE TABLE funding_program (
    id UUID PRIMARY KEY,
    agency_id UUID NOT NULL REFERENCES funding_agency(id),
    name VARCHAR(300) NOT NULL,
    description TEXT,
    instrument_types TEXT[] NOT NULL DEFAULT '{}',
    CONSTRAINT uk_funding_program_id_agency UNIQUE (id, agency_id)
);

CREATE TABLE call_for_proposals (
    id UUID PRIMARY KEY,
    program_id UUID REFERENCES funding_program(id),
    agency_id UUID NOT NULL REFERENCES funding_agency(id),
    external_id VARCHAR(160) NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary TEXT,
    instrument_type VARCHAR(100),
    status VARCHAR(30) NOT NULL CHECK (status IN ('OPEN', 'UPCOMING', 'CLOSED', 'SUSPENDED', 'UNKNOWN')),
    publication_date DATE,
    opening_date DATE,
    deadline DATE,
    official_url VARCHAR(2048),
    detected_at TIMESTAMPTZ NOT NULL,
    last_checked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_call_agency_external_id UNIQUE (agency_id, external_id),
    CONSTRAINT fk_call_program_agency FOREIGN KEY (program_id, agency_id)
        REFERENCES funding_program (id, agency_id)
);

CREATE TABLE call_version (
    id UUID PRIMARY KEY,
    call_id UUID NOT NULL REFERENCES call_for_proposals(id),
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    content_hash VARCHAR(64) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    supersedes_version_id UUID REFERENCES call_version(id),
    CONSTRAINT uk_call_version_number UNIQUE (call_id, version_number),
    CONSTRAINT uk_call_version_hash UNIQUE (call_id, content_hash)
);

CREATE TABLE call_document (
    id UUID PRIMARY KEY,
    call_id UUID NOT NULL REFERENCES call_for_proposals(id),
    document_type VARCHAR(30) NOT NULL CHECK (document_type IN ('NOTICE', 'ANNEX', 'FAQ', 'AMENDMENT', 'RESULT_PRELIMINARY', 'RESULT_FINAL', 'OTHER')),
    title VARCHAR(1000) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    CONSTRAINT uk_call_document_call_source UNIQUE (call_id, source_url)
);

CREATE TABLE call_document_version (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES call_document(id),
    content_hash VARCHAR(64) NOT NULL,
    storage_key VARCHAR(1024) NOT NULL,
    media_type VARCHAR(200) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    downloaded_at TIMESTAMPTZ NOT NULL,
    extracted_text_key VARCHAR(1024),
    CONSTRAINT uk_document_version_hash UNIQUE (document_id, content_hash)
);

CREATE TABLE ingestion_run (
    id UUID PRIMARY KEY,
    source_type VARCHAR(80) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    detected_count INTEGER NOT NULL DEFAULT 0 CHECK (detected_count >= 0),
    changed_count INTEGER NOT NULL DEFAULT 0 CHECK (changed_count >= 0),
    error_summary TEXT,
    CHECK (finished_at IS NULL OR finished_at >= started_at)
);

CREATE TABLE extracted_field (
    id UUID PRIMARY KEY,
    call_version_id UUID NOT NULL REFERENCES call_version(id),
    field_name VARCHAR(100) NOT NULL,
    normalized_value TEXT,
    raw_value TEXT,
    validation_status VARCHAR(20) NOT NULL CHECK (validation_status IN ('EXTRACTED', 'VALIDATED', 'CONFLICT', 'MISSING')),
    extraction_method VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    ingestion_run_id UUID NOT NULL REFERENCES ingestion_run(id),
    confidence NUMERIC(5, 4) CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CHECK (validation_status <> 'MISSING' OR (normalized_value IS NULL AND raw_value IS NULL))
);
CREATE INDEX ix_extracted_field_call_version_field_created
    ON extracted_field (call_version_id, field_name, created_at DESC, id DESC);

CREATE TABLE evidence (
    id UUID PRIMARY KEY,
    extracted_field_id UUID NOT NULL REFERENCES extracted_field(id),
    document_version_id UUID REFERENCES call_document_version(id),
    source_url VARCHAR(2048) NOT NULL,
    page INTEGER CHECK (page IS NULL OR page > 0),
    section VARCHAR(500),
    quote_fragment TEXT,
    locator_json JSONB NOT NULL
);
CREATE INDEX ix_evidence_extracted_field ON evidence (extracted_field_id);
