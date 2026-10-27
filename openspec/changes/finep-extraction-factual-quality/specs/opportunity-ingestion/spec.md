# Spec Delta

## ADDED Requirements

### Requirement: Track critical-field source selection coverage
Before a structured extraction request, the system SHALL record candidate and selected page coverage for each critical field, source document and document version, including applicable source types and any selection gaps.

#### Scenario: Relevant source pages fit within configured limits
- **WHEN** candidate pages for critical fields are found in an original notice and one or more amendments and fit the configured document, excerpt, character and token limits
- **THEN** the provider input covers the relevant field/page candidates across those source versions and the audit record identifies their document versions and pages

#### Scenario: Relevant evidence cannot fit within configured limits
- **WHEN** document, excerpt, character or token limits prevent a candidate page or applicable source version from being selected
- **THEN** the system records the affected field/version and a stable gap reason, marks coverage incomplete, and does not treat a null provider result as documentary absence or validation success

### Requirement: Preserve complete evidence passages within extraction bounds
The system SHALL select bounded source passages without silently truncating a relevant condition, exception or clause boundary; any passage that cannot be preserved within configured limits SHALL be reported as incomplete coverage.

#### Scenario: A relevant clause fits within limits
- **WHEN** a candidate passage contains a field value with its surrounding condition or exception and fits the remaining limits
- **THEN** the exact selected passage and a resolvable page locator are preserved in the provider input audit artifact

#### Scenario: A relevant clause exceeds the excerpt budget
- **WHEN** preserving the complete relevant passage would exceed a per-excerpt or aggregate limit
- **THEN** the system records a truncation/coverage gap and does not use the shortened passage to validate the affected critical field

### Requirement: Preserve claims across source versions and explicit amendments
The system SHALL retain evidence and candidate claims by document version and SHALL resolve a changed critical value only when an explicit source amendment identifies the replaced rule; document recency alone SHALL NOT resolve a difference.

#### Scenario: An amendment explicitly replaces an earlier clause
- **WHEN** a preserved amendment explicitly identifies the earlier clause it changes and supplies the replacement text
- **THEN** the resulting candidate records the replacement and retains the earlier claim and its evidence as history

#### Scenario: Versions differ without explicit supersession evidence
- **WHEN** applicable source versions contain different critical values and no explicit replacement relation can be verified
- **THEN** the field remains `CONFLICT` and evidence from each version remains linked

### Requirement: Preserve compound legal rules for factual review without automatic validation
The system SHALL preserve extracted `eligibility` and `counterpart` values and evidence, but SHALL NOT automatically mark them `VALIDATED`. Non-conflicting values with evidence SHALL remain `EXTRACTED`; absent values SHALL remain `MISSING`; unresolved source differences SHALL remain `CONFLICT`. Citations or source coverage alone SHALL NOT prove semantic completeness.

#### Scenario: Eligibility is extracted with literal evidence
- **WHEN** a provider returns an eligibility value and citations, whether the represented criteria appear structurally complete or incomplete
- **THEN** the value and evidence are preserved and the technical state remains `EXTRACTED` for factual review

#### Scenario: Counterpart ranges and exemptions are extracted
- **WHEN** a provider returns counterpart ranges or exemptions with citations
- **THEN** the value and evidence are preserved and the technical state remains `EXTRACTED`; no range is asserted to be the complete legal rule

#### Scenario: Paperwork is waived but financial counterpart is not addressed
- **WHEN** a source says “Não se exige, para a contrapartida, declaração de ICT federal”
- **THEN** the text is not interpreted as a financial counterpart exemption and the counterpart remains `EXTRACTED`

#### Scenario: Scalar values use existing deterministic checks
- **WHEN** `totalBudget` or `requiresIct` has an ambiguous or unrecognized relation to its literal evidence
- **THEN** the value remains `EXTRACTED`; existing unambiguous deterministic checks remain available

#### Scenario: A compound rule appears structurally complete
- **WHEN** clause groups and citations appear complete and source coverage is complete
- **THEN** the eligibility or counterpart remains `EXTRACTED` because automatic proof of legal completeness and semantic relations is outside the revised acceptance scope


### Requirement: Distinguish extraction gaps from documentary absence without new field states
The system SHALL retain the existing technical states and record selection/extraction coverage separately; a missing provider value or unselected candidate SHALL NOT by itself establish factual absence.

#### Scenario: Provider returns null after complete page selection
- **WHEN** the provider returns no value for a critical field after the recorded applicable source scope was selected
- **THEN** the technical field may remain `MISSING`, while the audit record reports no extracted value and does not claim human-confirmed documentary absence

#### Scenario: Provider returns null after an incomplete selection
- **WHEN** the provider returns no value while a relevant source page/version was not selected or a passage was truncated
- **THEN** the existing technical state is preserved compatibly, the coverage gap is recorded, and factual absence cannot be inferred

### Requirement: Preserve extraction and review package compatibility
Extraction coverage metadata and schema evolution SHALL remain readable by existing offline audit-package verification and factual-review workflows without changing existing technical states or requiring a database migration.

#### Scenario: Existing audit package is verified
- **WHEN** an unchanged package produced under the current extraction schema is checked
- **THEN** its format, checksums, evidence links and factual-review compatibility remain valid

#### Scenario: New extraction is exported for offline review
- **WHEN** a run using the revised extraction contract is exported
- **THEN** the package preserves its schema version, coverage report, evidence/version relationships and six-field review input and verifies offline
