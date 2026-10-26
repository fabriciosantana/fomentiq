# Spec Delta

## Purpose

Provide a portable, integrity-checkable record of one completed Finep ingestion and extraction so reviewers can audit its provenance offline after disposable services are removed.

## ADDED Requirements

### Requirement: Export a completed run before environment cleanup
The system SHALL export an audit package for a terminal `IngestionRun` while its database and object artifacts remain available, before the isolated environment is removed.

#### Scenario: Run and artifacts are available
- **WHEN** an authorized internal export selects a terminal run and its source/document artifacts
- **THEN** the exporter reads them without triggering Finep/OpenAI and writes a package bound to that run and code revision

#### Scenario: Environment cleanup is requested before export verification
- **WHEN** required export or integrity checks have not passed
- **THEN** cleanup SHALL be blocked or the package SHALL be explicitly incomplete; the system SHALL NOT report a complete audit package

### Requirement: Preserve exact extraction inputs and persisted outputs
The system SHALL include the exact selected excerpts supplied to the provider, effective schema/provider parameters, structured provider result, persisted field values/states, evidence locators, document/call versions, and `IngestionRun` attribution.

#### Scenario: Provider excerpts are durably captured before invocation
- **WHEN** extraction is about to invoke the provider
- **THEN** the canonical exact excerpt manifest has already been durably stored and its checksum/reference bound to that extraction execution before any request is sent

#### Scenario: Exact-input capture fails
- **WHEN** the excerpt artifact cannot be stored or its reference cannot be recorded before provider invocation
- **THEN** the provider is not invoked and the run/package cannot claim that exact provider input was preserved

#### Scenario: Extraction request is exported
- **WHEN** a package is built for a run with a provider execution
- **THEN** it records exact excerpt text, segment IDs, document version IDs, pages/locators, schema version and effective non-secret parameters without regenerating excerpts

#### Scenario: Run has no extraction or field history
- **WHEN** expected extraction outputs or field-state history are absent
- **THEN** the package records the missing items and fails the completeness check rather than inferring them

### Requirement: Include source artifacts and version integrity
The package SHALL contain or explicitly identify every permitted snapshot and original document artifact required to resolve its evidence, with source identity, version metadata, media type, byte length and SHA-256 checksum.

#### Scenario: Required source artifacts are present
- **WHEN** a reviewer resolves an evidence locator in the package
- **THEN** the referenced document version is identifiable by content hash and the quoted passage can be checked against its preserved source artifact

#### Scenario: Artifact cannot be included or verified
- **WHEN** a required source artifact is unavailable, disallowed, corrupt or has a mismatched hash
- **THEN** the package lists the gap and is not classified complete

### Requirement: Sanitize and protect audit packages
The system SHALL exclude credentials, OAuth/API tokens, authorization headers, storage credentials, internal storage keys and sensitive URL parameter values from exported package content.

#### Scenario: Package passes sanitization
- **WHEN** allowlisted package content is written to its approved destination
- **THEN** secret-bearing fields are absent or safely redacted with the source artifact hash and redaction action documented, and the destination is private and access-controlled

#### Scenario: Sensitive material cannot be safely sanitized
- **WHEN** a required artifact cannot be exported without exposing a credential or sensitive content
- **THEN** export fails closed or marks the package incomplete and reports the excluded artifact without copying its secret value

### Requirement: Inventory files and fail closed on package gaps
Every package SHALL have a versioned manifest containing its run identity, source classification, required-file inventory, byte lengths, SHA-256 checksums and explicit completeness result.

#### Scenario: Complete inventory verifies
- **WHEN** an offline verifier checks a package with all required entries
- **THEN** it validates every file checksum and all declared document, excerpt, locator, field, evidence and run references

#### Scenario: A file or reference is missing or invalid
- **WHEN** an inventory checksum, source reference, quote, locator or required relationship does not validate
- **THEN** verification fails with a sanitized list of missing or invalid items and the package cannot be labeled complete

### Requirement: Verify packages without live services
The system SHALL provide a verification procedure that checks a package using only its files and local verification code, without PostgreSQL, S3-compatible storage, Finep or OpenAI access.

#### Scenario: Package is verified in a clean offline environment
- **WHEN** the verifier is given a package directory or archive on a clean machine with no external credentials
- **THEN** it validates schema, inventory, checksums, field-to-run attribution, document/version references, evidence locators, exact quotes and package completeness without making network requests

### Requirement: Distinguish live evidence, fixtures and later reconstructions
The package SHALL identify each artifact as captured in the live run, copied from a frozen fixture, or generated by a later offline analysis, and SHALL NOT represent a reconstruction as evidence captured during that run.

#### Scenario: Historical live artifact is missing
- **WHEN** a later package is prepared for a run whose original request excerpt or output was not retained
- **THEN** the package records that artifact as unavailable and remains incomplete; fixture replay or inferred data SHALL NOT fill the live-evidence slot

#### Scenario: Fixture is included as a comparator
- **WHEN** fixture data is included for comparison
- **THEN** it is separately labeled with its fixture identity and checksums and is not attributed to the live `IngestionRun`

### Requirement: Store packages under an approved private retention policy
The system SHALL write packages only to a configured private directory outside Git and disposable run resources, restrict read access to the technical responsible and authorized reviewers, and retain packages for five years without automatic deletion.

#### Scenario: Approved private destination and policy are configured
- **WHEN** export begins
- **THEN** the package is stored outside the disposable run resources and public source-control artifacts, and access, directory identity, creation date and five-year retention metadata are recorded without embedding credentials

#### Scenario: Destination or retention policy is missing
- **WHEN** no approved private destination, access policy or retention duration is configured
- **THEN** export SHALL refuse to declare the package archived or complete and SHALL retain it only in a controlled temporary location pending owner configuration

#### Scenario: Retention window expires
- **WHEN** a package reaches its five-year retention date
- **THEN** it remains stored until an explicit owner-authorized deletion is recorded; no automatic deletion occurs
