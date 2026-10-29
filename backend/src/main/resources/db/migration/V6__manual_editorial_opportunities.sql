CREATE TABLE manual_opportunity (
    id UUID PRIMARY KEY,
    title VARCHAR(240) NOT NULL,
    institution VARCHAR(240) NOT NULL,
    summary VARCHAR(2000) NOT NULL DEFAULT '',
    deadline DATE,
    status VARCHAR(40) NOT NULL CHECK (length(trim(status)) > 0),
    official_url VARCHAR(2000),
    editorial_state VARCHAR(20) NOT NULL CHECK (editorial_state IN ('DRAFT', 'PUBLISHED')),
    editable_content JSONB NOT NULL,
    published_content JSONB,
    created_by VARCHAR(200) NOT NULL,
    updated_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0 CHECK (row_version >= 0),
    CONSTRAINT ck_manual_published_snapshot CHECK (
        (editorial_state = 'PUBLISHED' AND published_content IS NOT NULL)
        OR (editorial_state = 'DRAFT' AND published_content IS NULL)
    )
);

CREATE INDEX ix_manual_opportunity_public ON manual_opportunity (updated_at DESC, id)
    WHERE editorial_state = 'PUBLISHED';
CREATE INDEX ix_manual_opportunity_admin ON manual_opportunity (updated_at DESC, id);

CREATE TABLE manual_opportunity_audit_event (
    id UUID PRIMARY KEY,
    opportunity_id UUID NOT NULL REFERENCES manual_opportunity(id),
    action VARCHAR(20) NOT NULL CHECK (action IN ('CREATED', 'SAVED', 'PUBLISHED', 'UNPUBLISHED')),
    aggregate_version BIGINT NOT NULL CHECK (aggregate_version >= 0),
    actor VARCHAR(200) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_manual_opportunity_audit ON manual_opportunity_audit_event
    (opportunity_id, occurred_at, id);

CREATE FUNCTION prevent_manual_editorial_audit_rewrite() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Manual editorial audit events are append-only';
END;
$$;

CREATE TRIGGER trg_manual_editorial_audit_immutable
BEFORE UPDATE OR DELETE ON manual_opportunity_audit_event
FOR EACH ROW EXECUTE FUNCTION prevent_manual_editorial_audit_rewrite();
