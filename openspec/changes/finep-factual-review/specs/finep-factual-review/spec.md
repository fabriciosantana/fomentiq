# Spec Delta

## Purpose

This capability records human factual decisions about critical Finep fields using preserved documentary evidence. It keeps factual adjudication separate from technical extraction and validation states.

## ADDED Requirements

### Requirement: Provide a complete matrix for the six critical fields
Each review matrix MUST contain exactly one current decision row for each of `deadline`, `totalBudget`, `eligibility`, `counterpart`, `trl` and `requiresIct`; a field without a factual conclusion still receives an explicit uncertainty or absence outcome.

#### Scenario: All critical fields are represented
- **WHEN** a review matrix is submitted as complete
- **THEN** it contains one row for each of the six required fields and no field is omitted because extraction returned `MISSING`

#### Scenario: A critical field row is omitted
- **WHEN** any of the six required fields has no decision row
- **THEN** the matrix is incomplete and cannot be classified as complete

### Requirement: Review every critical field with traceable provenance
The system MUST record a separate human review decision for each critical field, including extracted value, confirmed documentary value, source document version, page, literal citation, locator, applicable conditions and exceptions, outcome, rationale, reviewer identity and UTC review date.

#### Scenario: Complete field review
- **WHEN** a reviewer records a decision for a critical field
- **THEN** the record contains the extracted value and all required documentary, decision and reviewer provenance

#### Scenario: Decision lacks locator
- **WHEN** a review decision claims a documentary value but omits its source locator
- **THEN** the decision record is incomplete and cannot be classified as complete

### Requirement: Keep factual outcomes separate from technical extraction states
Factual outcomes MUST be one of `APROVADO`, `REPROVADO`, `INCONCLUSIVO` or `AUSÊNCIA CONFIRMADA` and MUST NOT overwrite or imply the technical states `EXTRACTED`, `VALIDATED`, `CONFLICT` or `MISSING`.

#### Scenario: Missing technical field is reviewed
- **WHEN** a critical field has technical state `MISSING`
- **THEN** its factual review outcome is stored separately and does not change the technical state

#### Scenario: Review is approved
- **WHEN** a reviewer records `APROVADO`
- **THEN** the record represents human factual adjudication and does not promote the extracted field to technical `VALIDATED`

### Requirement: Approve only complete rules and values
A reviewer MUST use `APROVADO` only when the confirmed documentary value and every applicable condition, exception and branch needed to interpret the field are supported by cited evidence.

#### Scenario: Only one counterpart band is supported
- **WHEN** a counterpart rule has multiple conditional bands or exceptions and evidence supports only one band
- **THEN** the complete rule cannot receive `APROVADO`

#### Scenario: Eligibility includes exclusions
- **WHEN** eligibility evidence contains both positive criteria and exclusions
- **THEN** approval requires citations covering the applicable criteria and exclusions

### Requirement: Preserve uncertainty and competing source claims
The review record MUST preserve conflicting or insufficiently supported documentary claims and MUST NOT select a claim solely because its document version is newest.

#### Scenario: Retifications conflict
- **WHEN** applicable source versions contain unresolved conflicting values
- **THEN** the reviewer records the competing claims and uses `INCONCLUSIVO` unless documentary evidence resolves them

#### Scenario: Insufficient evidence
- **WHEN** preserved evidence does not establish the full value or rule
- **THEN** the outcome is `INCONCLUSIVO` and the gap is stated in the rationale

### Requirement: Confirm absence only after a scoped document review
`AUSÊNCIA CONFIRMADA` MUST be used only when the reviewer records the applicable document set and search scope and confirms that the field is absent from that complete set; model omission or missing captured evidence alone is insufficient.

#### Scenario: Field absent from complete applicable set
- **WHEN** the reviewer searches the complete applicable call and retification documents and finds no statement for the field
- **THEN** the record may use `AUSÊNCIA CONFIRMADA` and identifies the reviewed document versions and search scope

#### Scenario: Package has missing source artifacts
- **WHEN** a source document required to establish the review scope is absent from the package
- **THEN** `AUSÊNCIA CONFIRMADA` is rejected as unsupported

### Requirement: Bind review decisions to an immutable verified package
Each factual review record MUST identify the audit package ID and root checksum it reviewed, and MUST be rejected when the package verifier reports incomplete or invalid evidence.

#### Scenario: Review complete package
- **WHEN** the review record is created from a package that passes offline integrity and completeness verification
- **THEN** it records the package identifier and root checksum

#### Scenario: Review incomplete package
- **WHEN** the package has missing required files, invalid checksums or unresolved references
- **THEN** no review record may be classified as complete

### Requirement: Preserve superseded factual decisions
Corrections to a factual review MUST create a new decision record that identifies the decision it supersedes and MUST preserve the prior decision and its package binding.

#### Scenario: Reviewer corrects a prior decision
- **WHEN** a reviewer submits a corrected outcome or rationale for a field
- **THEN** the new record references the prior decision, and both decisions remain auditable without changing the source package
