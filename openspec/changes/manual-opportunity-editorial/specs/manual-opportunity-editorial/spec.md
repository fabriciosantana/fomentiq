## ADDED Requirements

### Requirement: Create and edit manual opportunities
The system SHALL allow an authorized administrator in the isolated demonstration environment to create and edit manual opportunities with a title, responsible institution, summary, optional deadline, status, and validated official URL.

#### Scenario: Administrator creates a draft
- **WHEN** an authenticated administrator starts a new opportunity, including with incomplete main details
- **THEN** the system stores a manual opportunity in `DRAFT` state and records the authenticated actor and timestamps

#### Scenario: Incomplete draft cannot be published
- **WHEN** an administrator attempts to publish without a title, responsible institution, situation, or official URL
- **THEN** the system rejects publication and leaves the draft private

#### Scenario: Invalid official URL is submitted
- **WHEN** an administrator submits a URL with an unsupported scheme, user information, or invalid syntax
- **THEN** the system rejects the change with a field-level validation error

#### Scenario: Concurrent editor submits stale data
- **WHEN** an administrator saves using a stale aggregate version
- **THEN** the system rejects the update as a conflict and preserves the newer content

### Requirement: Author ordered custom sections and typed fields
The system SHALL support ordered sections and ordered fields of the approved types: short text, long text, date, currency, link, and list. Values SHALL be validated according to their type and SHALL be rendered as text or safe links, never as arbitrary HTML.

#### Scenario: Administrator adds and reorders content
- **WHEN** an administrator saves an opportunity with sections and typed fields in a chosen order
- **THEN** that order and each supported value are persisted and returned unchanged

#### Scenario: Field value does not match its type
- **WHEN** a submitted field value is invalid for its declared type
- **THEN** the backend rejects the aggregate update without partially changing its content

#### Scenario: Editorial content contains markup
- **WHEN** a field contains HTML or script-like text
- **THEN** public and preview pages display it as escaped text and do not execute it

### Requirement: Keep editorial administration protected and isolated
The system SHALL require the existing authenticated administrator session, role authorization, and CSRF protection for all manual editing and publication operations. These operations SHALL be disabled by default outside the isolated demonstration environment.

#### Scenario: Anonymous user attempts an administrative operation
- **WHEN** a request without an authenticated administrator session accesses a manual administration route
- **THEN** the system denies the request without changing data

#### Scenario: Administrative routes are requested outside the demo profile
- **WHEN** the application runs without the explicitly enabled isolated demonstration configuration
- **THEN** manual administration routes are unavailable or denied by default

### Requirement: Save and preview without publishing
The system SHALL distinguish editable draft content from published content. Saving an edit SHALL NOT publish it. An administrator SHALL be able to preview the current editable content before publication.

#### Scenario: Draft is saved
- **WHEN** an administrator saves a draft
- **THEN** the draft is available to authorized administration and preview, but not to public listing or detail routes

#### Scenario: Published content is edited but not republished
- **WHEN** an administrator saves changes to a published opportunity without explicitly publishing the new version
- **THEN** public readers continue to see the last published snapshot while administration and preview show the saved edits

### Requirement: Publish and withdraw editorial content
The system SHALL provide explicit, authorized, auditable transitions to publish current content and withdraw it from publication. Publication SHALL atomically expose a consistent content snapshot; withdrawn or draft opportunities SHALL not be publicly accessible.

#### Scenario: Administrator publishes current content
- **WHEN** an authorized administrator explicitly publishes a valid opportunity version
- **THEN** the system records actor and time and makes that exact content snapshot available publicly

#### Scenario: Administrator withdraws publication
- **WHEN** an authorized administrator withdraws a published opportunity
- **THEN** public listing no longer includes it and direct public detail access returns not found

### Requirement: Present a published-only editorial catalog
The public root catalog SHALL list only published manual opportunities, with reader-oriented title, institution, summary, status, and deadline when present. Public detail SHALL render sections and typed fields in saved order, with localized dates and currency. Historical extracted opportunities SHALL remain available on an explicitly legacy route.

#### Scenario: Public reader browses catalog
- **WHEN** a visitor opens the primary catalog
- **THEN** the visitor sees published manual opportunities and no drafts or technical extraction metadata

#### Scenario: Public reader requests a draft by identifier
- **WHEN** a visitor requests a manual opportunity that is a draft or has been withdrawn
- **THEN** the public API returns not found without revealing the draft content

#### Scenario: Public detail renders custom content
- **WHEN** a visitor opens a published opportunity
- **THEN** the page renders its ordered sections and typed fields in an accessible editorial layout without raw JSON, internal identifiers, or extraction states

### Requirement: Preserve existing source-ingested opportunity records
Manual authoring SHALL use additive persistence and SHALL NOT mutate or delete existing ingested calls, extraction fields, evidence, document versions, audit packages, or human curation history.

#### Scenario: Manual schema is migrated on an existing database
- **WHEN** the additive migration is applied to a database containing existing Finep or CNPq records
- **THEN** all existing identities and records remain unchanged and readable through legacy routes

#### Scenario: Manual schema is migrated on a clean database
- **WHEN** Flyway applies all migrations to a clean supported PostgreSQL database
- **THEN** manual opportunity tables and constraints are created successfully
