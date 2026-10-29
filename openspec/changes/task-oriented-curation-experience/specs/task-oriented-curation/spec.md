# Spec Delta

## Purpose

Provide a focused Portuguese review workflow that helps administrators assess opportunity information against its documentary source while preserving the existing curation and publication controls.

## ADDED Requirements

### Requirement: Present a concise review queue
The administrative experience SHALL present opportunities requiring attention with search, source and agency context, opportunity status, deadline when available, review status, and a clear action to continue review.

#### Scenario: Reviewer opens the queue
- **WHEN** an authenticated reviewer opens the administrative home
- **THEN** the interface shows paginated opportunities, API-provided opportunity status and deadline, and only counts returned by the review queue, with an explicit empty, loading, or error state

#### Scenario: Reviewer searches loaded queue pages
- **WHEN** a reviewer searches by opportunity title or institution/agency
- **THEN** matching loaded queue entries are shown without claiming the search covers pages not yet loaded

#### Scenario: A human-reviewed field no longer needs attention
- **WHEN** a field has an approved human revision
- **THEN** the administrative queue excludes that field from the attention count while preserving its technical extraction state

### Requirement: Summarize an opportunity by review task
The administrative experience SHALL summarize an opportunity in task-oriented sections and make pending information and the next review action clear without exposing raw JSON or internal identifiers in the primary view.

#### Scenario: Reviewer opens an opportunity overview
- **WHEN** a reviewer selects a Finep or CNPq opportunity
- **THEN** the interface shows its available title, responsible agencies, status, deadline, budget, grouped review fields, and review states using user-facing Portuguese labels

#### Scenario: Reviewer selects a section or field
- **WHEN** a reviewer starts or continues a section review
- **THEN** the interface opens one field at a time and preserves the distinction between machine extraction, human publication, and pending review

### Requirement: Submit value confirmation or correction as a pending review
The experience SHALL submit confirmation and correction through the existing revision API and SHALL NOT publish a value when it is saved.

#### Scenario: Reviewer confirms the current value
- **WHEN** the reviewer confirms the displayed value with a justification and documentary reference
- **THEN** the client creates a pending `VALUE` revision with the unchanged value and leaves the public projection unchanged until approval

#### Scenario: Reviewer proposes a correction
- **WHEN** the reviewer submits a corrected supported value with justification and documentary reference
- **THEN** the client creates a pending `VALUE` revision and leaves publication to the existing approval transition

### Requirement: Record absence only after documentary review
The experience SHALL distinguish an unlocated value from a value absent in source documents and SHALL use the existing confirmed-absence revision semantics.

#### Scenario: Reviewer records confirmed absence
- **WHEN** the reviewer confirms that all applicable documents and versions were reviewed and submits a justification with a valid opportunity document reference
- **THEN** the client creates a pending `CONFIRMED_ABSENCE` revision and does not publish it automatically

#### Scenario: Evidence is insufficient
- **WHEN** no field-specific evidence or literal quote is available for confirming or correcting a value
- **THEN** the interface clearly indicates the limitation and does not invent a citation or imply that a document alone proves the field

### Requirement: Show documentary evidence in field context
The experience SHALL display available field evidence and document/version references beside the reviewed information and SHALL preserve safe access to public source URLs where supplied.

#### Scenario: Reviewer examines an evidenced field
- **WHEN** evidence is available for the selected field
- **THEN** its literal quote, document title, version, page, section, and safe source link are shown when those values are available

#### Scenario: Reviewer opens history on demand
- **WHEN** a reviewer requests revision history
- **THEN** the existing paginated history is displayed separately from the primary review form

### Requirement: Approve or reject through existing decisions
The experience SHALL use the existing authenticated decision operations for approval and rejection and SHALL preserve original extraction data and audit history.

#### Scenario: Reviewer approves a pending revision
- **WHEN** the reviewer approves a pending revision with a decision reason
- **THEN** the existing backend transition publishes the human value and the interface can show it in the public catalog while retaining the machine extraction

#### Scenario: Reviewer rejects a pending revision
- **WHEN** the reviewer rejects a pending revision with a decision reason
- **THEN** the public value remains unchanged and the rejection remains visible in revision history

### Requirement: Keep the administrative workflow accessible and resilient
The experience SHALL use accessible responsive controls and provide clear states for loading, empty results, API failures, missing opportunities, and expired sessions.

#### Scenario: Reviewer uses a small viewport or loses the session
- **WHEN** the interface is used on a narrow screen or an API request reports an expired session
- **THEN** controls remain operable and the reviewer receives a clear recovery path without exposing technical payloads
