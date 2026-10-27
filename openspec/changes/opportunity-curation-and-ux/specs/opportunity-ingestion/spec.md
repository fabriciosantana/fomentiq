# Spec Delta

## MODIFIED Requirements

### Requirement: Browse opportunities
The system SHALL provide a web catalog and detail view for ingested opportunities, presenting an approved human revision as the effective value when one exists while preserving the original extracted value and its provenance.

#### Scenario: User opens catalog
- **WHEN** a user opens the opportunity catalog
- **THEN** the system displays ingested calls with title, agency, status and deadline when available, using the approved human revision for a published field when one exists

#### Scenario: User opens detail
- **WHEN** a user opens an opportunity detail page
- **THEN** the system displays structured fields, validation states, documents, versions and evidence links, and distinguishes an approved human value from its unchanged original extraction
