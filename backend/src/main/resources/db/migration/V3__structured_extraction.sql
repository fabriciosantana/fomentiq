CREATE TABLE extraction_execution (
    id UUID PRIMARY KEY,
    ingestion_run_id UUID NOT NULL REFERENCES ingestion_run(id),
    call_version_id UUID NOT NULL REFERENCES call_version(id),
    provider VARCHAR(80) NOT NULL,
    model VARCHAR(160) NOT NULL,
    schema_version VARCHAR(80) NOT NULL,
    input_digest VARCHAR(64) NOT NULL,
    response_body TEXT NOT NULL,
    input_tokens BIGINT CHECK (input_tokens IS NULL OR input_tokens >= 0),
    output_tokens BIGINT CHECK (output_tokens IS NULL OR output_tokens >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCEEDED', 'FAILED', 'REFUSED', 'INCOMPLETE')),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_extraction_execution_run_input UNIQUE (ingestion_run_id, call_version_id, schema_version, input_digest)
);
CREATE INDEX ix_extraction_execution_call_version ON extraction_execution (call_version_id, created_at DESC);
