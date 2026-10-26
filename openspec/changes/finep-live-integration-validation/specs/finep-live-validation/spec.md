# Spec Delta

## Purpose

Define a bounded, isolated and auditable procedure for validating the existing Finep and structured-extraction integrations against one specifically approved live opportunity, without treating offline evidence as proof of online availability.

## ADDED Requirements

### Requirement: Validate a versioned manifest before side effects
The validation runner SHALL load a versioned typed manifest and matching human approval before database migration, external request or application data write. The manifest SHALL identify exactly one Finep call by both source IDs and its official canonical URL, plus its allowed source hosts.

#### Scenario: Manifest includes all execution bounds
- **WHEN** the internal runner receives the exact call identity, approval reference, source hosts, document and response byte bounds, timeouts, provider/model/schema/token/attempt limits, spend estimate/ceiling and disposable resource/cleanup identities
- **THEN** it loads the manifest before Spring data initialization and applies each bound at its corresponding execution boundary

#### Scenario: Manifest is missing, invalid or exceeds supported bounds
- **WHEN** a required field is absent, inconsistent, unsupported or exceeds a built-in safety maximum
- **THEN** the runner exits nonzero before database migration, Finep/OpenAI access or application data writes and emits only a sanitized error

### Requirement: Verify isolation before Spring data initialization
The internal runner SHALL verify the effective JDBC target and identity, S3-compatible endpoint and bucket, Compose project and actual mounted resources before Spring initializes Flyway/JPA or the connector. It SHALL reject shared, operational, ambiguous or unrecognized targets and may remove only the exact Compose project/resources named in the validated manifest after review.

#### Scenario: Isolated disposable targets are verified
- **WHEN** the effective application configuration maps to the expected local PostgreSQL and S3-compatible services and their Compose labels, ports and volume identities match the approved manifest
- **THEN** the application context may initialize and the run may be prepared

#### Scenario: Isolation validation fails
- **WHEN** a configured target, credential identity, Compose project, mount, database identity or storage bucket is absent or mismatched, any command-line, Spring/Fomentiq or JVM system-property override can supersede a manifest-bound target/configuration value, or the S3 endpoint contains userinfo, query or fragment components
- **THEN** the process exits before Flyway, external Finep/OpenAI access or writes to an unapproved target

### Requirement: Apply bounded document and response streaming
The Finep validation path SHALL enforce approved document-count, per-document-byte, aggregate-byte, discovery/metadata response-byte and timeout limits while receiving responses. It SHALL reject an over-limit response and SHALL NOT truncate it and process the prefix as a complete source artifact.

#### Scenario: Approved response and document set are within limits
- **WHEN** a Finep response completes under the approved response and operation limits and the document catalog does not exceed its count ceiling
- **THEN** the complete bytes are preserved and aggregate consumption is charged before the next document starts

#### Scenario: Stream, document count, aggregate bytes or time exceed a limit
- **WHEN** a body exceeds a byte ceiling, the document catalog exceeds its maximum, a read/operation/global deadline expires, or the next document cannot fit the remaining aggregate budget
- **THEN** the response is cancelled/rejected, no partial bytes are accepted as a complete artifact, remaining requests stop, and the `IngestionRun` is finalized auditably when the isolated database is available

### Requirement: Run live validation only through an explicit internal entry point
Live Finep validation SHALL be started only by an explicit private command with a validated manifest and a separately supplied approval reference matching the manifest. It SHALL NOT run on normal startup or be exposed through an HTTP endpoint.

#### Scenario: Explicit approved command is invoked
- **WHEN** an operator invokes the internal runner with the matching manifest and approval reference
- **THEN** it performs exactly the manifest-authorized call, emits a sanitized report and exits with a status code corresponding to `PASS`, `FAIL` or `ESCALATE`

#### Scenario: Normal startup or unauthorized command
- **WHEN** the application starts normally, the runner flag is absent, or the approval reference is missing/mismatched
- **THEN** no live ingestion or provider request is initiated

### Requirement: Bound provider attempts and request parameters
Each OpenAI request SHALL enforce its model, schema, input/output token and timeout bounds. Every attempt, including retries, SHALL count against the manifest ceiling. An experimental live validation SHALL be limited to one total attempt with automatic retries disabled.

#### Scenario: Attempt and token limits are enforceable
- **WHEN** the provider request is prepared under the approved maximum-attempt and token bounds
- **THEN** automatic retry configuration is reduced to the manifest allowance and no request starts after the attempt ceiling is reached

#### Scenario: Attempt or request parameters exceed the manifest
- **WHEN** an OpenAI request would exceed the approved model, schema, token, timeout or attempt limit
- **THEN** the request is rejected before it is sent

### Requirement: Monitor an approved experimental budget
Before any external request, a conservative cost estimate SHALL be calculated from the manifest token ceilings and dated official pricing. An authorized experimental run MAY proceed without a provider-enforced financial cap only when the exact estimated budget and its non-guaranteed nature are separately approved. The runner SHALL record provider-reported usage and estimated cost, and stop/escalate if usage is missing or the estimated budget is exceeded.

#### Scenario: Estimate exceeds approved experimental budget
- **WHEN** the conservative estimate from approved token ceilings and official pricing exceeds the approved budget
- **THEN** the runner records the estimate, exits as `ESCALATE`, and sends no external request

#### Scenario: Explicitly approved monitored budget without a hard cap
- **WHEN** the separate approval accepts a non-guaranteed experimental budget, the conservative estimate fits, and the run is bounded to one provider attempt with automatic retries disabled
- **THEN** the runner may perform the explicitly authorized request, records provider-reported usage and estimated cost, and does not describe the budget as a guaranteed billing cap

#### Scenario: Usage is unavailable or the estimated budget is exceeded
- **WHEN** the provider returns no usage metadata or reported usage estimates a cost above the approved budget
- **THEN** the run is recorded as `ESCALATE` and no further provider request is made

### Requirement: Gate live validation with explicit authorization
The live validation SHALL contact external providers only after a separate human approval defines the target call, document and byte limits, configured model, total provider request-attempt limit, and maximum spend.

#### Scenario: Complete validation manifest is approved
- **WHEN** the target, limits, model, request-attempt ceiling and spend ceiling are approved
- **THEN** the operator may start the isolated run and must enforce every approved limit

#### Scenario: Approval or limits are missing
- **WHEN** any required approval or limit is absent or a limit is reached
- **THEN** the system stops before the next external request and reports `ESCALATE` without exceeding the approved bound

### Requirement: Isolate live validation data
Live source snapshots, downloaded documents and relational records SHALL be written only to a dedicated disposable validation database and S3-compatible namespace separated from development or operational data.

#### Scenario: Isolation is verified before execution
- **WHEN** the validation environment is prepared
- **THEN** its database and object-storage namespace are verified as dedicated before connector or provider access begins

#### Scenario: Isolation cannot be demonstrated
- **WHEN** configuration could write to a shared or operational database or object-storage namespace
- **THEN** no live request or write occurs and the result is `ESCALATE`

### Requirement: Validate the bounded Finep source flow
The approved run SHALL discover only the selected Finep opportunity, preserve its source snapshots before extraction, and download only its approved documents through the configured Finep URL policy.

#### Scenario: Approved call and documents are retrieved
- **WHEN** the public Finep source provides the approved call and its bounded document set
- **THEN** snapshots and document bytes are preserved in the isolated namespace with their source identifiers, URLs, media types, timestamps and hashes

#### Scenario: Finep identity or URL policy does not match
- **WHEN** the selected identity is ambiguous, a redirect violates the approved host policy, or a document exceeds its approved limit
- **THEN** that access is rejected, credentials are not exposed, and the run is reported as `FAIL` or `ESCALATE` according to whether the issue is safely understood

### Requirement: Bound real structured extraction
The live run SHALL use the configured `StructuredExtractionProvider` and approved model/schema limits, with every network attempt including automatic retries counted against the approved request ceiling.

#### Scenario: Provider returns structured output
- **WHEN** the approved request succeeds
- **THEN** the application validates its schema and evidence against only the bounded excerpts supplied to the provider

#### Scenario: Provider fails or refuses
- **WHEN** the provider times out, rate-limits, refuses, returns incomplete output or violates the schema
- **THEN** the failure is recorded explicitly, no deterministic fixture values substitute for it, and no additional request occurs beyond the approved ceiling

### Requirement: Review critical facts against preserved evidence
Every critical field considered in the live validation SHALL be evaluated against the preserved official source passage and its document/version locator; model output alone SHALL NOT establish factual correctness.

#### Scenario: Critical value is supported and deterministic checks pass
- **WHEN** a reviewer confirms a value against its exact preserved evidence and deterministic validation succeeds
- **THEN** the report records the field, source/version, evidence locator and review outcome

#### Scenario: Evidence is missing, conflicting or insufficient
- **WHEN** a critical value cannot be verified or official sources conflict without a deterministic resolution
- **THEN** it remains `MISSING`, `EXTRACTED` or `CONFLICT` as appropriate and is not reported as a confirmed fact

### Requirement: Verify idempotency and version preservation without unnecessary paid calls
The validation SHALL distinguish live integration evidence from deterministic persistence regression and SHALL verify identical-input idempotency and changed-input version preservation without repeating a paid provider call unless that call is separately approved and budgeted.

#### Scenario: Identical preserved input is replayed offline
- **WHEN** the same approved source bytes are replayed in the isolated deterministic regression path
- **THEN** no duplicate call or document version is created and prior versions and audit attribution remain unchanged

#### Scenario: Changed input is replayed offline
- **WHEN** a documented synthetic source variant is replayed in the deterministic regression path
- **THEN** a new version is created and prior versions/evidence remain accessible

### Requirement: Record sanitized run measurements
The validation record SHALL include the run ID, target identity, UTC start/end and duration, status, document/field counts, model and schema versions, reported input/output tokens when available, failures, and a cost estimate with pricing source/date and arithmetic.

#### Scenario: Provider usage is available
- **WHEN** the provider reports token usage
- **THEN** the report records those counts and estimates cost using the official pricing applicable to the run without recording credentials or sensitive payloads

#### Scenario: Provider usage is unavailable
- **WHEN** provider usage metadata is unavailable
- **THEN** the report states usage and cost are unknown, the result is `ESCALATE`, and usage is not inferred from payload size

### Requirement: Classify validation outcomes objectively
The final validation SHALL produce exactly one of `PASS`, `FAIL` or `ESCALATE` based on isolation, approved limits, source identity, preservation, evidence review, deterministic regressions and operational measurements.

#### Scenario: All acceptance evidence passes
- **WHEN** isolation, limits, source/document preservation, evidence review, deterministic idempotency/version regressions and cost accounting all pass with no unresolved critical finding
- **THEN** the result is `PASS`

#### Scenario: A bounded technical or factual check fails
- **WHEN** a reproducible connector/provider/persistence defect or unsupported critical fact is found within the approved and isolated run
- **THEN** the result is `FAIL`, with evidence and impact recorded; the validation does not imply permission to fix or rerun externally

#### Scenario: Safety, scope or evidence is uncertain
- **WHEN** isolation, authorization, budget, identity, URL safety, evidence sufficiency or a required architectural decision cannot be established
- **THEN** the result is `ESCALATE` and dependent external activity stops
