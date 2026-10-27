# Tasks

## 1. Field-aware selection and coverage

- [x] 1.1 Carry existing document ID/type/version metadata into extraction inputs; verify original notices, amendments, FAQs, annexes and results are classified without schema/database changes.
- [x] 1.2 Implement field-specific page candidate profiles and version-diverse bounded selection; verify low-keyword pages are retained when uniquely relevant and all document/excerpt/character/token ceilings remain enforced.
- [x] 1.3 Select clause-aware passage windows with page/section locators; verify no relevant condition/exception is silently prefix-truncated and overlong passages produce field coverage gaps.
- [x] 1.4 Record candidate/selected/unselected field-page-version coverage and stable gap reasons before provider invocation; verify exact input and coverage are immutable, digest-bound, and legacy inputs still verify offline.

## 2. Versioned structured rules and deterministic validation

- [x] 2.1 Define `finep-call-v2` while retaining the v1 contract for historical records; verify strict schema requires clause groups and per-clause evidence references for eligibility and counterpart.
- [x] 2.2 Adapt provider-independent parsing and the infrastructure provider to v2 without embedding vendor types in domain/application ports; verify deterministic adapter parsing and no external calls.
- [x] 2.3 Apply the revised conservative product policy for eligibility/counterpart: preserve non-conflicting structured values and evidence as `EXTRACTED`, preserve absent values as `MISSING` and unresolved divergence as `CONFLICT`, and never automatically `VALIDATED` a compound rule. Verify paperwork waivers are not treated as financial counterpart exemptions; retain only existing unambiguous deterministic validation for `totalBudget` and `requiresIct`. This explicitly supersedes the original single-range automatic-validation criterion; it does not claim the FQ-R1 relation grammar was fixed.
- [x] 2.4 Resolve version differences only with explicit source replacement references, preserving earlier claims/evidence; verify amendment order alone never resolves a conflict.

## 3. Fixture regression and audit compatibility

- [x] 3.1 Add offline golden-fixture regressions for deadline `2025-06-06T17:00:00-03:00`, budget `BRL 500000000.00` with `UP_TO`, every counterpart range/exemption, all eligibility criteria/exclusions, `requiresIct=true`, TRL `null`/`MISSING`, and field/document/page/quote bindings; also test that even a synthetically complete single-range counterpart remains `EXTRACTED` under the revised product policy. Verify fixture files/hashes remain unchanged and these historical values are test expectations, not production constants.
- [x] 3.2 Verify existing v1 audit packages and factual-review records remain valid, and deterministic v2 runs export coverage/schema/evidence that passes offline audit and factual-review verification.
- [x] 3.3 Verify API DTO and existing frontend rendering remain compatible with richer structured values, while migrations, endpoints and public OpenAPI stay unchanged; run backend and frontend regression suites plus strict OpenSpec validation.
