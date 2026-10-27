-- Human curation history is separate from machine extraction and is never backfilled into ExtractedField.
ALTER TABLE call_document
    ADD CONSTRAINT uk_call_document_id_call UNIQUE (id, call_id);

ALTER TABLE call_document_version
    ADD CONSTRAINT uk_call_document_version_id_document UNIQUE (id, document_id);

CREATE TABLE curated_field_revision (
    id UUID PRIMARY KEY,
    call_id UUID NOT NULL REFERENCES call_for_proposals(id),
    field_name VARCHAR(100) NOT NULL CHECK (field_name IN (
        'program', 'instrumentType', 'title', 'summary', 'status', 'publicationDate', 'openingDate', 'deadline',
        'totalBudget', 'projectFunding', 'eligibility', 'regions', 'trl', 'requiresIct', 'counterpart',
        'technologyThemes', 'eligibleExpenses', 'evaluationCriteria', 'requiredDocuments', 'officialLinks'
    )),
    value_kind VARCHAR(30) NOT NULL CHECK (value_kind IN ('VALUE', 'CONFIRMED_ABSENCE')),
    proposed_value JSONB,
    previous_published_value JSONB,
    previous_revision_id UUID,
    justification TEXT NOT NULL CHECK (length(trim(justification)) > 0),
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    page INTEGER CHECK (page IS NULL OR page > 0),
    section VARCHAR(500),
    quote_fragment TEXT,
    locator_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    decided_by VARCHAR(200),
    decided_at TIMESTAMPTZ,
    decision_reason TEXT,
    row_version BIGINT NOT NULL DEFAULT 0 CHECK (row_version >= 0),
    CONSTRAINT ck_curated_value_kind CHECK (
        (value_kind = 'VALUE' AND proposed_value IS NOT NULL AND proposed_value <> 'null'::jsonb)
        OR (value_kind = 'CONFIRMED_ABSENCE' AND proposed_value IS NULL)
    ),
    CONSTRAINT ck_curated_value_quote CHECK (
        value_kind = 'CONFIRMED_ABSENCE' OR (quote_fragment IS NOT NULL AND length(trim(quote_fragment)) > 0)
    ),
    CONSTRAINT ck_curated_decision_state CHECK (
        (status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL AND decision_reason IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND decided_by IS NOT NULL AND decided_at IS NOT NULL
            AND decision_reason IS NOT NULL AND length(trim(decision_reason)) > 0)
    ),
    CONSTRAINT uk_curated_revision_id_call_field UNIQUE (id, call_id, field_name),
    CONSTRAINT fk_curated_revision_document_call
        FOREIGN KEY (document_id, call_id) REFERENCES call_document (id, call_id),
    CONSTRAINT fk_curated_revision_document_version
        FOREIGN KEY (document_version_id, document_id) REFERENCES call_document_version (id, document_id),
    CONSTRAINT fk_curated_revision_previous
        FOREIGN KEY (previous_revision_id, call_id, field_name)
        REFERENCES curated_field_revision (id, call_id, field_name)
);

CREATE INDEX ix_curated_revision_queue ON curated_field_revision (status, created_at, call_id);
CREATE INDEX ix_curated_revision_field_history
    ON curated_field_revision (call_id, field_name, created_at, id);
CREATE INDEX ix_curated_revision_approved_projection
    ON curated_field_revision (call_id, field_name, decided_at DESC, id DESC)
    WHERE status = 'APPROVED';

CREATE FUNCTION prevent_curated_revision_rewrite() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Terminal curated revisions are immutable';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.call_id IS DISTINCT FROM OLD.call_id
       OR NEW.field_name IS DISTINCT FROM OLD.field_name
       OR NEW.value_kind IS DISTINCT FROM OLD.value_kind
       OR NEW.proposed_value IS DISTINCT FROM OLD.proposed_value
       OR NEW.previous_published_value IS DISTINCT FROM OLD.previous_published_value
       OR NEW.previous_revision_id IS DISTINCT FROM OLD.previous_revision_id
       OR NEW.justification IS DISTINCT FROM OLD.justification
       OR NEW.document_id IS DISTINCT FROM OLD.document_id
       OR NEW.document_version_id IS DISTINCT FROM OLD.document_version_id
       OR NEW.page IS DISTINCT FROM OLD.page
       OR NEW.section IS DISTINCT FROM OLD.section
       OR NEW.quote_fragment IS DISTINCT FROM OLD.quote_fragment
       OR NEW.locator_json IS DISTINCT FROM OLD.locator_json
       OR NEW.created_by IS DISTINCT FROM OLD.created_by
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.status NOT IN ('APPROVED', 'REJECTED')
       OR NEW.decided_by IS NULL
       OR NEW.decided_at IS NULL
       OR NEW.decision_reason IS NULL
       OR length(trim(NEW.decision_reason)) = 0
       OR NEW.row_version <> OLD.row_version + 1 THEN
        RAISE EXCEPTION 'Only a single pending-to-terminal decision is allowed';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_curated_revision_immutable
BEFORE UPDATE ON curated_field_revision
FOR EACH ROW EXECUTE FUNCTION prevent_curated_revision_rewrite();
