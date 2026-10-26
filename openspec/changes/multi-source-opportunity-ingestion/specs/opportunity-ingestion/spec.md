# Spec Delta

## ADDED Requirements

### Requirement: Discover opportunities from configured publishers
The system SHALL discover supported calls from each explicitly configured official publisher through its source connector without manual data entry.

#### Scenario: A call is discovered from a second publisher
- **WHEN** a configured publisher contains a supported call not already represented by that publisher's source identity
- **THEN** the system records the source reference and creates or links the opportunity according to verified identity rules

#### Scenario: Existing Finep call is still discovered
- **WHEN** the Finep connector finds portal entry `751092` with `databaseId=751`
- **THEN** the existing opportunity retains Finep external identity `751` and does not become a duplicate

### Requirement: Distinguish publisher, responsible agency and opportunity
The system SHALL represent the organization publishing a source record separately from the funding agency responsible for the opportunity and from the opportunity itself.

#### Scenario: Publisher and responsible agency are the same
- **WHEN** CNPq publishes a call for which CNPq is the responsible agency
- **THEN** the source publisher and responsible funding agency remain separately addressable even when they identify the same organization

#### Scenario: Publisher and responsible agency differ
- **WHEN** an official publisher hosts a call issued by another responsible funding agency
- **THEN** the source record identifies the publisher while the opportunity identifies the responsible funding agency

### Requirement: Scope external identity to its publisher
Each source-native external identifier SHALL be unique within its publisher and SHALL NOT be replaced by a title or content hash.

#### Scenario: Same external identifier appears at two publishers
- **WHEN** two publishers use the same external identifier for different calls
- **THEN** their source references remain distinct and do not collide

#### Scenario: Source identity lacks a stable identifier
- **WHEN** a connector cannot establish a stable source-native ID or canonical source reference
- **THEN** it does not silently identify the call by title or hash and records an explicit discovery failure or unresolved candidate

### Requirement: Preserve source-specific artifacts and versions
The system SHALL preserve each publisher's source snapshots and document versions independently while associating them with the appropriate opportunity.

#### Scenario: One opportunity is referenced by multiple publishers
- **WHEN** an authoritative cross-reference or reviewed mapping confirms that two publisher records describe the same opportunity
- **THEN** both source references and their independent snapshot/document histories remain available on that opportunity

#### Scenario: A publisher's document changes
- **WHEN** content changes for a known source document
- **THEN** a new version is recorded for that source document and its prior versions remain accessible

### Requirement: Keep uncertain cross-publisher duplicates separate
The system SHALL identify possible duplicate opportunities conservatively and SHALL NOT automatically merge records on uncertain evidence.

#### Scenario: A deterministic duplicate candidate is found
- **WHEN** records satisfy the documented exact candidate rule, such as an official cross-reference or exact normalized title with matching deadline and year
- **THEN** the records are surfaced as possible duplicates with the match basis while retaining separate opportunity identities

#### Scenario: Similar records do not meet the candidate rule
- **WHEN** titles or extracted content are merely similar without an authoritative cross-reference or exact candidate match
- **THEN** records remain separate and the system does not infer identity from similarity or a shared hash

### Requirement: Preserve audited ingestion transactions across publishers
Each publisher run SHALL remain auditable, and a failure processing one call SHALL NOT leave that call's relational writes partially committed or erase previously committed calls.

#### Scenario: A source run succeeds or partially downloads documents
- **WHEN** a connector run completes with successful calls or recoverable document failures
- **THEN** its `IngestionRun` records the source identity, timestamps, status and detected/changed counts, and committed call work is consistent

#### Scenario: A call transaction fails
- **WHEN** relational persistence for one discovered call fails
- **THEN** that call's relational unit rolls back before the run is finalized as failed, while earlier committed calls and the independent run audit remain intact

### Requirement: Filter the unified catalog by publisher and agency
The read-only catalog SHALL allow filtering by source publisher separately from responsible funding agency while preserving existing search, status, deadline, instrument, region and theme filters.

#### Scenario: User filters by source and responsible agency
- **WHEN** a user supplies publisher and/or responsible-agency filters
- **THEN** the paginated catalog returns only opportunities satisfying the selected filters and retains deterministic ordering

#### Scenario: Existing catalog filters remain in use
- **WHEN** a user searches or applies existing status, deadline, instrument, region or theme filters
- **THEN** existing filter behavior remains available with multi-source records

### Requirement: Integrate CNPq as the second source
The system SHALL ingest one explicitly selected CNPq public call using a stable call number/year identity and only its publicly accessible official page and documents.

#### Scenario: CNPq public call and documents are available
- **WHEN** the selected CNPq page and linked public documents are supplied by the connector
- **THEN** the source reference, public source snapshots, documents, versions and extracted evidence are associated with the responsible agency and opportunity

#### Scenario: CNPq public contract changes or requires restricted access
- **WHEN** discovery or document access requires an unapproved login, token, or host, or the expected public structure changes
- **THEN** the connector fails explicitly without bypassing access controls or weakening URL policy

### Requirement: Keep connector regression tests offline
Automated tests for every configured publisher SHALL use local deterministic fixtures and SHALL NOT require Finep, CNPq or OpenAI network access.

#### Scenario: Two source fixtures share one database and API
- **WHEN** Finep and CNPq fixtures are ingested through the real persistence adapters in PostgreSQL integration tests
- **THEN** both sources pass through deterministic extraction and real persistence, and are queryable through the unified read API with distinct source references, correct agencies, evidence provenance and no identity collision

#### Scenario: A connector fixture changes
- **WHEN** identical and changed source fixtures are replayed
- **THEN** idempotency, source-specific new versions, run attribution, evidence provenance and history are verified without external requests
