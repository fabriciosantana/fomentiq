# Spec Delta

## Purpose

Define a bounded, isolated and auditable procedure for validating the existing Finep and structured-extraction integrations against one specifically approved live opportunity, without treating offline evidence as proof of online availability.

## ADDED Requirements

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

#### Scenario: Provider usage or cost cannot be established
- **WHEN** usage is unavailable or the cost cannot be bounded against the approval
- **THEN** it is reported as unavailable and the result is `ESCALATE`; usage is not inferred from payload size

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
