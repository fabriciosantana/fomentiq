# Spec Delta

## Purpose

Allow an authenticated curator to inspect the exact preserved document version relevant to a field while keeping the review decision and published value traceable and consistent.

## ADDED Requirements

### Requirement: Open preserved versions through the administrative session
The system SHALL return preserved document bytes only through an authenticated, opportunity-scoped administrative read and SHALL verify the requested document version belongs to that opportunity.

#### Scenario: Authorized curator reads a preserved version
- **WHEN** an authenticated administrator requests a document version belonging to the selected opportunity
- **THEN** the system returns the original bytes with safe content headers and does not disclose the storage key or storage endpoint

#### Scenario: Request is anonymous or crosses opportunity boundaries
- **WHEN** a request has no administrative session or its opportunity, document, and version identifiers do not match
- **THEN** the system denies the request or returns not found without reading unrelated content

#### Scenario: Stored bytes exceed the viewing bound or fail integrity validation
- **WHEN** reading a preserved version exceeds the configured maximum or its bytes do not match the recorded digest/type
- **THEN** the system rejects the content and does not send partial or unverified bytes

### Requirement: Select the source and version in field context
The review experience SHALL allow a curator to choose an opportunity document and one of its preserved versions, preferring a version already linked to the field evidence when available.

#### Scenario: Evidence identifies a document version
- **WHEN** the field has evidence linked to a preserved version
- **THEN** the interface searches the document-version pages until the exact linked version is found, selects that document/version, and presents the linkage without claiming the document alone proves the field

#### Scenario: Evidence version cannot be resolved
- **WHEN** the linked version is absent from all available document-version pages or a page cannot be read
- **THEN** the interface does not substitute another document/version while retaining the evidence quote, requires explicit source selection before submission, and explains the unresolved linkage

#### Scenario: Evidence is absent or the curator chooses another source
- **WHEN** no field-specific evidence exists or the curator selects another document
- **THEN** the interface states the evidence limitation, offers available documents and versions, and requires an explicit choice for the revision reference

### Requirement: Preview safe document formats without leaving the review
The review experience SHALL show supported PDF versions in an in-context panel and offer a secure download for supported document formats that cannot be previewed.

#### Scenario: Curator previews a PDF with a reliable page locator
- **WHEN** the selected version is a preserved PDF and the evidence has a valid page locator
- **THEN** the PDF is displayed in the review panel and the curator can navigate to that page

#### Scenario: Curator selects a DOCX or unsupported preview format
- **WHEN** the selected version cannot be safely previewed inline
- **THEN** the interface offers a safe download for supported DOCX content or the validated official source link, and explains the preview limitation

#### Scenario: Curator expands, collapses, or uses a small screen
- **WHEN** the curator adjusts the document panel or uses a narrow viewport
- **THEN** the panel can be collapsed and reopened, offers an expanded view, and does not prevent access to the review form

### Requirement: Keep published deadlines consistent across review surfaces
The administrative queue SHALL display the effective published deadline using the latest approved human revision when one exists, while preserving the original extraction separately.

#### Scenario: A deadline has an approved human revision
- **WHEN** queue, administrative detail, review form, and public catalog present the opportunity deadline
- **THEN** each displays the same effective human-published value while the original extracted value and revision history remain unchanged and inspectable

#### Scenario: No approved human deadline exists
- **WHEN** the opportunity has no approved human deadline revision
- **THEN** the queue continues to display the existing effective source/extraction deadline
