CREATE TABLE source_publisher (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    official_url VARCHAR(2048) NOT NULL
);

INSERT INTO source_publisher (id, code, name, official_url) VALUES
    (gen_random_uuid(), 'FINEP', 'Financiadora de Estudos e Projetos', 'https://www.finep.gov.br'),
    (gen_random_uuid(), 'CNPQ', 'Conselho Nacional de Desenvolvimento Científico e Tecnológico', 'https://www.gov.br/cnpq');

INSERT INTO funding_agency (id, name, acronym, official_url)
SELECT gen_random_uuid(), 'Conselho Nacional de Desenvolvimento Científico e Tecnológico', 'CNPq',
       'https://www.gov.br/cnpq'
WHERE NOT EXISTS (SELECT 1 FROM funding_agency WHERE acronym = 'CNPq');

INSERT INTO funding_agency (id, name, acronym, official_url)
SELECT gen_random_uuid(), 'Coordenação de Aperfeiçoamento de Pessoal de Nível Superior', 'CAPES',
       'https://www.gov.br/capes'
WHERE NOT EXISTS (SELECT 1 FROM funding_agency WHERE acronym = 'CAPES');

CREATE TABLE call_source_reference (
    id UUID PRIMARY KEY,
    call_id UUID NOT NULL REFERENCES call_for_proposals(id),
    publisher_id UUID NOT NULL REFERENCES source_publisher(id),
    external_id VARCHAR(200) NOT NULL,
    portal_entry_id VARCHAR(200),
    canonical_url VARCHAR(2048) NOT NULL,
    CONSTRAINT uk_source_reference_publisher_external UNIQUE (publisher_id, external_id),
    CONSTRAINT uk_source_reference_call_publisher UNIQUE (call_id, publisher_id),
    CONSTRAINT uk_source_reference_id_call UNIQUE (id, call_id)
);
CREATE INDEX ix_source_reference_call ON call_source_reference (call_id, publisher_id);

INSERT INTO call_source_reference (id, call_id, publisher_id, external_id, portal_entry_id, canonical_url)
SELECT gen_random_uuid(), c.id, sp.id, c.external_id, c.portal_entry_id, c.official_url
FROM call_for_proposals c
JOIN funding_agency a ON a.id = c.agency_id
JOIN source_publisher sp ON sp.code = 'FINEP' AND a.acronym = 'FINEP';

CREATE TABLE call_funding_agency (
    call_id UUID NOT NULL REFERENCES call_for_proposals(id),
    agency_id UUID NOT NULL REFERENCES funding_agency(id),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (call_id, agency_id)
);
CREATE UNIQUE INDEX uk_call_funding_agency_primary
    ON call_funding_agency (call_id) WHERE is_primary;

INSERT INTO call_funding_agency (call_id, agency_id, is_primary)
SELECT id, agency_id, TRUE FROM call_for_proposals;

-- Legacy agency/external-id uniqueness incorrectly couples source identities to a funding agency.
-- Publisher-scoped uniqueness is enforced by call_source_reference instead.
ALTER TABLE call_for_proposals DROP CONSTRAINT uk_call_agency_external_id;

ALTER TABLE source_snapshot ADD COLUMN source_reference_id UUID;
UPDATE source_snapshot s
SET source_reference_id = r.id
FROM call_source_reference r
JOIN source_publisher p ON p.id = r.publisher_id AND p.code = 'FINEP'
WHERE r.call_id = s.call_id;
ALTER TABLE source_snapshot ALTER COLUMN source_reference_id SET NOT NULL;
ALTER TABLE source_snapshot
    ADD CONSTRAINT fk_source_snapshot_reference_call
    FOREIGN KEY (source_reference_id, call_id) REFERENCES call_source_reference (id, call_id);
CREATE INDEX ix_source_snapshot_reference_captured
    ON source_snapshot (source_reference_id, captured_at DESC);

ALTER TABLE call_document ADD COLUMN source_reference_id UUID;
UPDATE call_document d
SET source_reference_id = r.id
FROM call_source_reference r
JOIN source_publisher p ON p.id = r.publisher_id AND p.code = 'FINEP'
WHERE r.call_id = d.call_id;
ALTER TABLE call_document ALTER COLUMN source_reference_id SET NOT NULL;
ALTER TABLE call_document DROP CONSTRAINT uk_call_document_call_external;
ALTER TABLE call_document
    ADD CONSTRAINT fk_call_document_reference_call
    FOREIGN KEY (source_reference_id, call_id) REFERENCES call_source_reference (id, call_id);
ALTER TABLE call_document
    ADD CONSTRAINT uk_call_document_reference_external UNIQUE (source_reference_id, external_id);
CREATE INDEX ix_call_document_source_reference ON call_document (source_reference_id);
