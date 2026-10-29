## ADDED Requirements

### Requirement: Persist and reload editorial dates
The system SHALL persist a non-null manual-opportunity deadline inside editorial content as an ISO-8601 `LocalDate` string and SHALL reload that value without loss. A nullable deadline SHALL remain null. This SHALL NOT change the editorial metamodel, database schema, or public JSON contract.

#### Scenario: Save and reload opportunity with a deadline
- **WHEN** an administrator creates or updates a manual opportunity with a valid deadline
- **THEN** the database JSON content stores that deadline as an ISO-8601 string and reloading returns the same `LocalDate`

#### Scenario: Save opportunity without a deadline
- **WHEN** an administrator saves a manual opportunity whose deadline is null
- **THEN** the JSON content stores null and reloading returns a null deadline

#### Scenario: Save content with ordered custom fields and a deadline
- **WHEN** an administrator saves a deadline together with ordered sections and typed fields
- **THEN** the deadline and custom content both round-trip unchanged
