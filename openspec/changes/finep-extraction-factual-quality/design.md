# Design

## Context

See `proposal.md` for motivation and `specs/opportunity-ingestion/spec.md` for behavior. Today `DocumentExcerptSelector` chooses at most six documents, ranks up to 24 pages by a small keyword list, takes the first 1,400 characters of each page and caps the aggregate at 24,000 characters. It receives only version ID, title, URL and bytes, although the ingestion source already has a document category. `DeterministicFieldValidator` considers any non-empty JSON object sufficient for `eligibility` or `counterpart`; `StructuredExtractionService` validates citations only against selected excerpts. Input excerpts are captured before provider invocation and exported by `AuditPackageExporter`. The audit-package v1 verifier accepts the current extraction schema and verifies the exact excerpt digest; factual review binds field/evidence state but does not change it.

## Goals / Non-Goals

**Goals:**

- Select relevant evidence by critical field and document version within existing operational ceilings, with explicit coverage gaps.
- Preserve source-version claims and exact passage locators for offline audit.
- Reject technically incomplete composite values and keep the existing four validation states.
- Keep existing audit packages verifiable and make new packages reviewable by the factual-review tool.

**Non-Goals:**

- Live Finep or OpenAI calls, a new source connector, changes to the frozen fixture, database migrations, new field/run states, public API endpoints, or human factual-review decisions.
- General legal interpretation or automatic proof that the model returned every semantic clause in a fully scanned source. `VALIDATED` remains a deterministic structural/provenance result, not legal homologation.

## Decisions

### D1 — Field-aware page selection with bounded coverage accounting

Scan pages from source documents classified by existing document type; reserve source capacity for the original notice and applicable amendments before ranking other material. Use six field-specific profiles combining lexical variants, section/item anchors and document type, then rank within each field/version. A diversity allocator gives each critical field and applicable version a chance before spending remaining page/character capacity on higher scores. Include adjacent page/paragraph context when a clause crosses a boundary. Do not include result documents as requirement evidence. Preserve current hard ceilings (6 documents, 24 excerpts, 1,400 characters per excerpt, 24,000 total characters) unless separate evidence and authorization justify changing them. When a required version or candidate passage cannot fit, record a stable reason (`DOCUMENT_LIMIT`, `EXCERPT_LIMIT`, `CHARACTER_LIMIT`, `TOKEN_LIMIT`, `PASSAGE_TOO_LONG`, or `SOURCE_UNSCANNED`) and an incomplete field/version coverage entry; never silently omit it.

**Alternative:** send all 18 PDFs or all pages in one request. Rejected because it breaks cost/input limits and loses field-level auditability. **Alternative:** retain global keyword ranking and raise ceilings. Rejected because it still lets high-scoring pages displace low-scoring unique facts and silently truncates passages.

### D2 — Passage windows rather than prefix truncation

Select paragraph/clause windows around candidate anchors, preserving clause boundaries and adjacent conditions. Locators retain document version, PDF page and text offsets/section where available. If a relevant clause exceeds its remaining character allowance, do not cut its prefix and pass it as complete; record a coverage gap for the affected field. An excerpt may still be used for unrelated fields only when its boundaries and field attribution remain clear.

**Alternative:** take a substring from the page start. Rejected because the current 1,400-character prefix can omit the operative condition or exception while retaining the heading/value.

### D3 — Versioned provider contract with clause-level provenance

Introduce `finep-call-v2` for new extraction requests; keep v1 schema artifacts and package verification readable for historical runs. Preserve existing value keys but represent eligibility criteria/exclusions and counterpart conditions/ranges/exemptions as explicit clause entries linked to citation IDs. Require evidence references on each clause; retain separate claims per document version. The selector must not label a page as an amendment based solely on its title if structured `CallDocumentType` metadata is available; titles are a fallback classification only.

**Alternative:** change `finep-call-v1` in place. Rejected because stored runs and audit packages record the schema definition/version and must remain reproducible. **Alternative:** use only a single field-level citation list. Rejected because it cannot prove which condition/range the citation supports.

### D4 — Coverage is audit metadata, not a new technical state

Store the selection coverage matrix in the exact extraction-input artifact before provider invocation. It records source versions/types scanned, candidate and selected pages per critical field, whether a passage was complete, omitted candidates and stable gap reasons. A null model value is `NO_VALUE_RETURNED`; it does not establish documentary absence. If selection is incomplete, retain the existing field state for compatibility but block `VALIDATED` for affected fields and preserve the gap for human review. Factual absence remains a separate human outcome.

Extend the canonical input digest with a versioned coverage section for new runs. Update offline verification so legacy inputs without this section use the existing digest calculation, while new inputs must verify the coverage-aware digest and references. Keep audit package outer format v1 unless verification demonstrates that the additive input record cannot be represented safely; any need to bump package format or migrate database schema is a stop/escalate condition, not an implementation-time silent change. The factual-review validator must accept old and new complete packages and continue to bind review to the same package ZIP/manifest checksums.

### D5 — Conservative technical status for compound legal rules (revised by product decision 2026-10-27)

The v2 contract continues to preserve field-specific clause groups for eligibility positives/exclusions and counterpart ranges, conditions and exemptions. These groups, source coverage, and citations remain extraction/audit data; they do not prove semantic completeness or legal relationships. For `eligibility` and `counterpart`, the system SHALL NOT assign automatic `VALIDATED` to compound rules. When a non-conflicting structured value is present, retain it and its evidence as `EXTRACTED`. Preserve `MISSING` when no value was extracted and `CONFLICT` when unresolved source values conflict. Do not introduce technical states or migrations.

This is an explicit scope revision to the original task 2.3 criterion that allowed a fully evidenced single counterpart range to become automatically `VALIDATED`. The system does not claim to have fixed the earlier relation grammar or demonstrated legal completeness. Human factual review remains separate and is required for a factual approval.

For `counterpart`, a waiver concerning a declaration, certificate, or other document (including “Não se exige, para a contrapartida, declaração de ICT federal”) SHALL NOT be interpreted as a waiver of the financial counterpart obligation. Such text may remain a cited extracted claim, but does not establish a financial exemption. `totalBudget` and `requiresIct` may still receive `VALIDATED` only through their existing narrow deterministic checks; ambiguity or an unrecognized construction remains `EXTRACTED`.

### D6 — Explicit amendment handling without a “newest wins” rule

Carry document ID, type and version metadata into extraction input. Parse an amendment as superseding a value only when the captured wording explicitly identifies the item/statement changed and includes the replacement. Preserve both old and replacement claims. Where the link is unclear, values remain `CONFLICT`; do not infer precedence from title, download date or version order.

**Alternative:** use latest document date/version as precedence. Rejected because it can silently erase a still-applicable exception or a non-amended clause.

### D7 — Fixture-only regression, no fixture mutation

Use the frozen PDFs, `expected.json`, hashes and provider-response fixture as read-only sources. Add test-only expectations/assertions for final deadline 2025-06-06 17:00 -03:00, budget qualifier `UP_TO` and BRL 500,000,000, all counterpart branches/exemptions, all eligibility criteria/exclusions, ICT requirement, no TRL value, and evidence version/page/quote bindings. Do not turn those historical values into production constants. Verify current and new audit-package/factual-review paths using existing v1 fixtures and an exported v2-contract package generated in memory or by deterministic test tooling.

### D8 — Scalar deterministic validation retained; compound rule relations deferred

Retain the existing narrow deterministic validation for `totalBudget` and `requiresIct`. Values are `VALIDATED` only when the existing field-specific expression is unambiguous and matched to preserved literal evidence; ambiguity remains `EXTRACTED`. Do not broaden those grammars in this scope.

For `eligibility` and `counterpart`, do not infer completeness or semantic relationships from a model clause, citation membership, matching words, co-location in a quote/page/segment, or document coverage. Keep non-conflicting values and their citations in `EXTRACTED` for downstream human review. Preserve the v2 clause/evidence contract as captured data and keep v1 readers, database shape, and audit-package format unchanged. This intentionally revises D8's prior proposed compound relation grammars: FQ-R1 remains historically recorded as a false-positive finding, and the earlier grammar is not claimed as fixed.

## Risks / Trade-offs

- [Field synonyms or headings vary across notices] → use conservative multilingual term/section profiles and maintain explicit no-candidate versus unselected coverage; test on the frozen fixture and synthetic boundary cases.
- [Mandatory version/field diversity uses budget on low-yield pages] → reserve only required source/version candidates, then allocate remaining capacity by score; report unavoidable gaps rather than exceed limits.
- [Compound legal rules cannot be safely auto-validated] → keep eligible/counterpart facts as `EXTRACTED` with provenance and require factual review; do not infer financial exemptions from waived paperwork.
- [Amendment precedence is ambiguous] → leave conflict unresolved; do not choose the newest source.
- [Audit compatibility regresses] → verify legacy packages unchanged and new coverage-aware package digests offline; retain outer package v1 and database shape unless tests prove that impossible, then stop for approval.
- [Richer values affect consumers] → preserve existing top-level field names, validation states and evidence DTOs; add compatibility tests for read API serialization and frontend rendering, with no endpoint/OpenAPI change in the planned scope.

## Migration Plan

No database migration is proposed. New runs use the v2 extraction schema and coverage-aware input artifact; old run records, schema definitions and audit-package v1 archives remain read-only and verifiable. Rollback disables v2 extraction and returns to the previous application artifact; it does not rewrite prior runs or package bytes. If a DB change or audit-package outer-format migration proves necessary, stop and request separate approval.
