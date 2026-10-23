# Spec Delta

## Purpose

Provide the first end-to-end capability for discovering, preserving, versioning, extracting and presenting a Finep funding opportunity with verifiable source provenance.

## ADDED Requirements

### Requirement: Detect configured Finep opportunities
The system SHALL discover supported Finep calls from configured official source entry points without requiring manual data entry.

#### Scenario: New call is discovered
- **WHEN** a configured Finep source contains a supported call not present in the catalog
- **THEN** the system creates a call record and records the detection timestamp

#### Scenario: Known call is discovered again
- **WHEN** a configured Finep source contains a call already present with unchanged source content
- **THEN** the system does not create a duplicate call or call version

### Requirement: Preserve source artifacts
The system SHALL preserve the source snapshot and downloaded call documents used for interpretation.

#### Scenario: Source page is fetched
- **WHEN** the connector successfully fetches a call source page
- **THEN** the system stores a content-addressed snapshot before structured extraction

#### Scenario: Linked document is discovered
- **WHEN** an official call page links a supported document
- **THEN** the system downloads and stores the document with source URL, media type, hash and timestamp

### Requirement: Version changed content
The system SHALL create immutable versions when previously stored source or document content changes.

#### Scenario: Document content changes
- **WHEN** a known document URL returns content with a new hash
- **THEN** the system creates a new document version and preserves the previous version

#### Scenario: Same document is reprocessed
- **WHEN** downloaded content has the same hash as the latest stored version
- **THEN** no duplicate document version is created

### Requirement: Extract structured funding data
The system SHALL extract the configured MVP funding fields into typed structured values when the source contains sufficient information.

#### Scenario: Deadline is present
- **WHEN** an official source states a submission deadline
- **THEN** the system stores a normalized date-time candidate and its raw source value

#### Scenario: Field is absent
- **WHEN** a configured field cannot be found in available source artifacts
- **THEN** the field state is recorded as `MISSING` rather than invented

### Requirement: Record provenance for critical facts
The system SHALL associate critical extracted facts with evidence that lets a reviewer locate the supporting official source.

#### Scenario: Critical field has evidence
- **WHEN** a deadline, budget, eligibility rule, counterpart requirement, TRL range or ICT requirement is extracted
- **THEN** the system stores an evidence locator referencing the source URL or preserved document version and available page/section context

#### Scenario: Critical field lacks evidence
- **WHEN** a critical extracted value has no usable evidence locator
- **THEN** the system MUST NOT mark that value as `VALIDATED`

### Requirement: Expose validation state
The system SHALL expose `EXTRACTED`, `VALIDATED`, `CONFLICT` or `MISSING` state for critical structured fields.

#### Scenario: Deterministic checks pass
- **WHEN** an extracted critical value has evidence and passes configured deterministic validation
- **THEN** it may be marked `VALIDATED`

#### Scenario: Sources conflict
- **WHEN** available official artifacts support different current values for the same critical field and recency/authority cannot deterministically resolve them
- **THEN** the field is marked `CONFLICT` and both evidences remain accessible

### Requirement: Browse opportunities
The system SHALL provide a web catalog and detail view for ingested opportunities.

#### Scenario: User opens catalog
- **WHEN** a user opens the opportunity catalog
- **THEN** the system displays ingested calls with title, agency, status and deadline when available

#### Scenario: User opens detail
- **WHEN** a user opens an opportunity detail page
- **THEN** the system displays structured fields, validation states, documents, versions and evidence links

### Requirement: Search and filter opportunities
The system SHALL support text search and configured MVP filters.

#### Scenario: Filter by status and deadline
- **WHEN** a user applies status and deadline filters
- **THEN** the catalog returns only calls satisfying those filters

### Requirement: Record ingestion execution
The system SHALL record operational status for each ingestion run.

#### Scenario: Ingestion completes
- **WHEN** a scheduled or manual ingestion run ends
- **THEN** the system records start/end timestamps, status and detected/changed counts

#### Scenario: Ingestion fails
- **WHEN** an unrecoverable connector or processing error occurs
- **THEN** the run is marked failed with an error summary while previously valid catalog data remains available
