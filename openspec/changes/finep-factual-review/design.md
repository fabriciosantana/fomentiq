# Design

## Context

See `proposal.md` - Why. The `finep-audit-package` change defines the immutable evidence boundary and offline package verifier. This capability consumes that package and produces a separate factual decision artifact.

## Goals / Non-Goals

**Goals:**
- Record evidence-based decisions for the six named critical fields.
- Make completeness and uncertainty explicit, especially for conditional rules and retifications.
- Keep human outcomes independent of `ExtractedField.validationStatus`.

**Non-Goals:**
- Change extraction, validation, persistence or public API behavior.
- Provide a web workflow, reviewer authentication, automatic approval or legal interpretation.
- Reconstruct missing artifacts from the first live run.

## Decisions

### D1 — Use a detached, versioned decision document

Create an append-only JSON decision record keyed to the verified audit package ID and root SHA-256. It contains one row per critical field and is stored alongside the package under the same private access and retention policy; it does not mutate the package or database. A later correction creates a superseding decision record with an explicit relation, preserving prior decisions.

Alternative: update extracted-field rows or package contents. Rejected because factual review is a distinct human judgment and changing either would erase the technical/audit boundary.

### D2 — Require outcome-specific completeness

Every row includes the extracted value snapshot, documentary value (nullable only with an explicit absence/uncertainty outcome), source document version/hash, page, exact quotation, locator, conditions/exceptions, outcome, rationale, reviewer and UTC timestamp. A local JSON Schema plus semantic validator rejects incomplete combinations. `APROVADO` requires all applicable branches; `AUSÊNCIA CONFIRMADA` requires a verified complete package, explicit reviewed-document set and search scope. For `CONFLICT`, preserve all candidate claims and references.

Alternative: allow free-form notes with no machine-checkable status. Rejected because that cannot consistently distinguish absence, insufficient evidence and approval.

### D3 — Keep completeness factual, not self-attested

The review artifact references the audit package root checksum and validator result. The offline validator verifies both integrity/completeness and decision-specific required evidence, but does not decide factual correctness or identify a reviewer beyond the supplied recorded identity. Review outcome remains the human's responsibility.

### D4 — Sequence after the audit package contract

Implementation is blocked until `finep-audit-package` has an implemented, stable package format and its offline verifier accepts the package. OpenSpec does not express cross-change runtime dependencies, so this dependency is stated here and in tasks. No review of the historical live run may be called complete unless its package actually contains the captured artifacts; fixture or reconstructed material remains explicitly labeled and cannot substitute.

## Risks / Trade-offs

- [Reviewers may treat partial evidence as complete] → outcome-specific validation and explicit branch coverage, particularly for counterpart and eligibility.
- [A detached record could be separated from its evidence] → bind it to package ID/root checksum and apply the package's private access/retention policy.
- [Review identity is supplied in the record] → restrict storage to named reviewers and retain access logs; authentication is outside this change.
- [Human decisions can still be wrong] → preserve literal citations and rationale for later independent audit; the validator checks traceability, not truth.

## Migration Plan

No schema or application migration is planned. First implement and stabilize the audit package format and verifier, then implement the detached review schema and offline validator. Existing technical field states and prior runs remain unchanged. Historical review is possible only to the extent that preserved package evidence supports it.
