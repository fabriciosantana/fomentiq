ALTER TABLE call_for_proposals ADD COLUMN portal_entry_id VARCHAR(160);

ALTER TABLE call_version DROP CONSTRAINT uk_call_version_hash;

ALTER TABLE call_document DROP CONSTRAINT uk_call_document_call_source;
ALTER TABLE call_document ADD COLUMN external_id VARCHAR(200);
UPDATE call_document SET external_id = 'legacy:' || id::text;
ALTER TABLE call_document ALTER COLUMN external_id SET NOT NULL;
ALTER TABLE call_document ADD CONSTRAINT uk_call_document_call_external UNIQUE (call_id, external_id);

ALTER TABLE call_document_version DROP CONSTRAINT uk_document_version_hash;

CREATE TABLE source_snapshot (
    id UUID PRIMARY KEY,
    call_id UUID NOT NULL REFERENCES call_for_proposals(id),
    ingestion_run_id UUID NOT NULL REFERENCES ingestion_run(id),
    artifact_type VARCHAR(30) NOT NULL CHECK (artifact_type IN ('DISCOVERY', 'CALL', 'DOCUMENT_LIST')),
    source_url VARCHAR(2048) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    storage_key VARCHAR(1024) NOT NULL,
    media_type VARCHAR(200) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_source_snapshot_call_captured ON source_snapshot (call_id, captured_at DESC);
CREATE INDEX ix_source_snapshot_run ON source_snapshot (ingestion_run_id);
