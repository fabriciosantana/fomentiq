# Spec Delta

## Purpose

Enable a designated demonstration administrator to correct and publish opportunity facts without changing or obscuring the original machine extraction or its documentary provenance.

## ADDED Requirements

### Requirement: Gate administrative authentication to the isolated demonstration environment
Administrative authentication and HTTP operations SHALL be enabled only by an explicit isolated demonstration configuration and SHALL be denied by default in every other environment.

#### Scenario: Demo administrator signs in
- **WHEN** the isolated demonstration profile has a configured administrator username and secret and the user submits matching credentials
- **THEN** the application establishes an authenticated server session and exposes the authenticated identity to administrative audit actions

#### Scenario: Demo administrator configuration is incomplete
- **WHEN** the isolated demonstration profile lacks a required administrator credential or security setting
- **THEN** the application refuses to enable administrative access

#### Scenario: Administrative request outside isolated demonstration
- **WHEN** an unauthenticated or non-demo request accesses an administrative operation
- **THEN** the request is denied and no administrative data is disclosed or changed

#### Scenario: Session and CSRF protections
- **WHEN** an authenticated browser performs an administrative mutation
- **THEN** the request requires a valid CSRF token and uses a session cookie with HttpOnly, SameSite, and Secure when served over HTTPS

### Requirement: Preserve public catalog reads
The public catalog and opportunity read operations SHALL remain available without an administrative login.

#### Scenario: Anonymous user browses catalog
- **WHEN** an anonymous user lists or opens an opportunity
- **THEN** the existing read-only catalog information remains accessible

### Requirement: Create an auditable human field revision
An authenticated administrator SHALL be able to create a typed pending revision for a supported opportunity field with a justification and verifiable reference to a document belonging to that opportunity.

#### Scenario: Revision has valid value and documentary reference
- **WHEN** an administrator submits a supported field value, non-empty justification, a literal quote, and reference to an opportunity document version with its available locator
- **THEN** the system stores a pending revision attributed to the authenticated administrator without modifying extracted fields or their evidence

#### Scenario: Confirmed absence has a documentary review reference without fabricated quotation
- **WHEN** an administrator confirms a field is absent after full document review and provides a non-empty justification and an opportunity document-version locator, without a literal quote
- **THEN** the system stores an explicit pending absence revision with no fabricated quote and does not change technical extraction state

#### Scenario: Revision input is invalid
- **WHEN** a revision contains an unsupported field, invalid field-specific value, blank justification, or document reference belonging to another opportunity
- **THEN** the system rejects it without creating or publishing a revision

#### Scenario: Administrator inspects evidence
- **WHEN** an administrator reviews an opportunity
- **THEN** the interface exposes its existing document versions and evidence with the owning field and opportunity references

#### Scenario: Evidence locator has no literal quote
- **WHEN** an evidence association has no preserved literal quote
- **THEN** public and administrative detail views label the locator as unverified and do not present it as proof that the field value is supported

### Requirement: Preserve immutable revision history
The system SHALL preserve every human revision and decision with its proposed value, prior published value, justification, documentary reference, authenticated author, timestamps, and state.

#### Scenario: Revision is rejected
- **WHEN** an administrator rejects a pending revision with a decision reason
- **THEN** the rejection and its author/time remain in history and the public value is unchanged

#### Scenario: Revision history is viewed
- **WHEN** an administrator opens revision history for an opportunity field
- **THEN** the system displays original extracted records and makes every human revision reachable in bounded chronological pages without overwriting prior decisions or silently truncating history

#### Scenario: Revision history is paginated
- **WHEN** an opportunity field has more revisions than fit in one bounded response
- **THEN** the administrator can navigate all pages in chronological order, and invalid page or size parameters are rejected

### Requirement: Enforce revision state transitions and conflict-safe publication
A human revision SHALL transition only from PENDING to APPROVED or REJECTED, and publication SHALL atomically reject stale concurrent approvals.

#### Scenario: Pending revision is approved
- **WHEN** the current pending revision is approved against the published baseline it recorded
- **THEN** the approval and effective published value commit atomically with decision author and timestamp

#### Scenario: Revision baseline changed concurrently
- **WHEN** an administrator approves a pending revision whose recorded published baseline is no longer current
- **THEN** the system rejects the stale decision and preserves all revisions and the current published value

#### Scenario: Terminal revision is changed
- **WHEN** an administrator attempts to approve or reject an already approved or rejected revision
- **THEN** the system rejects the transition and preserves the terminal decision

### Requirement: Publish human values separately from machine extraction
Public catalog and detail reads SHALL prefer the latest approved human revision for a field while retaining access to the original extracted value, technical state, and evidence.

#### Scenario: Approved revision is published
- **WHEN** an opportunity has an approved human revision for a field
- **THEN** the public read model presents that human value as the effective value and identifies it as human-reviewed while the original extraction remains available as historical provenance

#### Scenario: Revision remains pending or is rejected
- **WHEN** the latest revision is pending or rejected and no newer revision is approved
- **THEN** the public read model continues to present the current published value without treating the human proposal as approved

#### Scenario: Human approval does not alter extraction state
- **WHEN** an administrator approves a human revision
- **THEN** the original extracted field value, evidence, validation state, and ingestion attribution remain unchanged

### Requirement: Record administrator identity for all curation actions
Every creation and decision action SHALL record the identity derived from the authenticated session and SHALL ignore any client-supplied actor identity.

#### Scenario: Client attempts actor spoofing
- **WHEN** an authenticated client includes a different actor identity in a create or decision request
- **THEN** the server records the session identity and does not accept the supplied identity

### Requirement: Provide a minimal administrative review interface
The demonstration interface SHALL let the administrator find opportunities needing review, inspect evidence, create revisions, approve or reject them, and view revision history.

#### Scenario: Admin completes review workflow
- **WHEN** the administrator signs in, selects an opportunity, supplies a corrected value, justification and document reference, then approves it
- **THEN** the interface confirms the published value and displays its revision history beside the original extraction

#### Scenario: Admin interface handles request states
- **WHEN** administrative data is loading, unavailable, empty, invalid, or rejected as stale
- **THEN** the interface presents an accessible state and does not imply that an unpublished value is public

### Requirement: Render structured opportunity values legibly
The opportunity detail interface SHALL render supported nested values in readable localized presentation and SHALL keep raw technical JSON out of the primary content view.

#### Scenario: Structured field is displayed
- **WHEN** a structured eligibility, counterpart, budget, or other supported value is present
- **THEN** the interface renders its labels, values, conditions, and arrays as accessible readable content without showing raw JSON in the primary view

#### Scenario: Field is absent or not identified
- **WHEN** extraction did not identify a field and no approved human revision states that it is absent from the source
- **THEN** the interface describes it as not identified by extraction rather than asserting that the source document contains no such requirement

#### Scenario: Technical identifiers are requested
- **WHEN** a user needs record identifiers or technical audit JSON
- **THEN** those details are available only in a clearly labeled expandable technical section
