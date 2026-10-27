# Verification record: finep-extraction-factual-quality / planning

## Run identity

- Change and cycle: `finep-extraction-factual-quality` / planning
- OpenSpec change/tasks in scope: planning artifacts only; implementation tasks 1.1–3.3 remain unchecked
- Date/time (UTC): 2026-10-26
- Implementer context: primary Codex context
- Independent reviewer context/mechanism: separate read-only context, no file edits
- Final state: `PASS`

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | User scope; delta `Track critical-field source selection coverage`; tasks 1.1–1.2 | Specify measurable selection coverage for each critical field/document/version, including lower-scoring relevant pages, original notice and amendments; limits remain effective and omissions have stable reasons. | Spec scenarios for fitting/over-budget coverage; design D1; tasks 1.1–1.2. |
| AC-02 | User scope; delta `Preserve complete evidence passages`; task 1.3 | Specify clause-aware windows, locators and explicit incomplete coverage instead of silently truncating conditions/exceptions. | Spec scenarios for passage within/over limits; design D2; task 1.3. |
| AC-03 | User scope; delta `Validate eligibility and counterpart`; tasks 2.1–2.3 | Specify completeness of positive/negative eligibility criteria, conditions, ranges, exemptions, exceptions and evidence; partial structures cannot be `VALIDATED`, while a legitimately complete single-range counterpart is not rejected by count alone. | Spec scenarios for incomplete eligibility/counterpart, complete single-range counterpart and incomplete source coverage; design D3/D5; tasks 2.1–2.3. |
| AC-04 | User scope; delta `Preserve claims across source versions`; task 2.4 | Retain original/amendment claims and resolve only explicit source replacement; recency alone leaves unresolved differences as `CONFLICT`. | Spec scenarios for explicit amendment and unresolved versions; design D6; task 2.4. |
| AC-05 | User scope; delta `Distinguish extraction gaps` and `Preserve extraction and review package compatibility`; tasks 1.4, 2.1, 3.2 | Retain existing field states, no migration, distinguish selection gaps from absence, and maintain verification of existing v1 packages plus new coverage-aware packages. | Spec scenarios; design D3/D4; tasks 1.4 and 3.2. |
| AC-06 | User regression expectations; task 3.1 | Plan deterministic fixture tests for deadline `2025-06-06T17:00:00-03:00`, budget BRL 500,000,000 with `UP_TO`, all counterpart clauses, eligibility criteria/exclusions, ICT, absent TRL and provenance without turning historical values into global rules or editing the fixture. | Design D7; task 3.1. |
| AC-07 | User constraints; MVP_SCOPE.md and ARCHITECTURE.md | Plan only; no source calls, paid model calls, migrations, implementation changes, new states, public API expansion, fixture edits, or commit/push. | Worktree/status, task boxes all unchecked, scope review, no implementation tests run. |

## Plan and affected areas

- Planned vertical slice: OpenSpec proposal/spec delta/design/tasks for field-aware bounded extraction, composite rule validation, version handling and offline regression; this run produces no implementation.
- Affected files/modules: `openspec/changes/finep-extraction-factual-quality/.openspec.yaml`, `proposal.md`, `design.md`, `tasks.md`, `specs/opportunity-ingestion/spec.md`, and this verification report only.
- Existing implementation inspected: `DocumentExcerptSelector` limits 6 documents, 24 excerpts, 1,400 characters/excerpt and 24,000 total characters; global term score and page-prefix slicing. `DocumentInput` currently carries only version ID/title/URL/bytes, while source document category already exists upstream. `DeterministicFieldValidator` accepts any non-empty JSON object for eligibility/counterpart. `StructuredExtractionService` verifies quotes only against selected excerpts and captures exact inputs before provider invocation. `AuditPackageExporter` includes exact provider inputs/schema/results; verifier recomputes a canonical input digest and factual review binds decisions to verified package contents.
- Known risks: character/token and document ceilings can make complete page coverage impossible; the design records a gap rather than bypassing limits. Full page selection and cited clauses cannot prove the LLM semantically enumerated every legal provision; automated `VALIDATED` is explicitly not human/legal homologation. Existing package digest compatibility needs conditional old/new canonicalization.
- Proposed decisions (not yet implemented): retain limits and technical states; use a versioned `finep-call-v2` contract while preserving v1; attach coverage to the provider-input audit artifact with a digest-aware backwards-compatible verifier; no DB migration or outer audit-package format bump. Any evidence that these constraints cannot be met is an implementation stop/escalation.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `git status --short --branch && git rev-parse HEAD && git rev-parse origin/main` | PASS | Clean `main`; both revisions equal baseline `f381cb4b8034128c3089f3e84706dea20eb1e6cb`. |
| `openspec list --json` | PASS | Confirmed change name did not already exist. |
| `openspec list --specs --json` | PASS | Confirmed `opportunity-ingestion` is the existing capability to modify; no duplicate capability introduced. |
| `openspec show opportunity-ingestion --type spec` | PASS | Read the full existing requirements and scenarios before drafting the delta. |
| `openspec status --change finep-extraction-factual-quality` | PASS | All 4 planning artifacts complete. |
| `openspec validate finep-extraction-factual-quality --strict` | PASS | New change valid. |
| `openspec validate --all --strict` | PASS | 6/6 changes/specs valid. |
| `openspec instructions apply --change finep-extraction-factual-quality --json` | PASS | 0/11 implementation tasks complete; all 11 remain pending for later apply authorization. |
| `git diff --check` | PASS | No whitespace errors. |
| New planning-file whitespace scan | PASS | All six generated/planning files checked, including `.openspec.yaml`. |
| Maven/frontend tests and build | Not run | Planning only; no implementation or behavior changed. |
| Finep/OpenAI network activity | Not performed | No external calls or paid requests. |

## Independent review

- Evidence supplied: user scope, current main spec, proposal/design/tasks delta, code-inspection findings, acceptance matrix and OpenSpec validation output.
- Reviewer has a separate context: yes.
- Reviewer was instructed not to modify implementation, tests, specs, or this report: yes.
- Worktree was checked after final review; reviewer made no changes.
- Initial review result: `RETRY`; one medium finding F-01. Correction attempt 1 was independently re-reviewed and passed; the final review passed without pending findings.
- Spec adherence, criteria measurability, compatibility, scope, implementation risks and test plan reviewed.

## Findings and correction attempts

Use stable IDs for underlying problems. Count no more than three correction attempts per finding across the run; rewording or splitting the same issue does not reset its count.

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| F-01 | Medium; AC-03 / `Validate eligibility and counterpart` | Initial spec said a counterpart with only one range cannot be `VALIDATED`, which would reject a fully evidenced source whose complete rule legitimately contains one range. | Attempt 1: make the gate depend on complete source-scope coverage, evidence for conditions/exceptions and evidence-backed review that no other range/exemption applies; add a planned positive single-range scenario and synthetic regression. | PASS; independent re-review confirmed attempt 1. |
| F-02 | Low; AC-06 / task 3.1 | Initial fixture test task referred only to “final deadline” and “global budget”, without writing their historical expected values in the acceptance checklist. | Attempt 1: state the fixture-specific ISO deadline, BRL budget amount/qualifier, ICT boolean and TRL null/MISSING directly in task 3.1; specify they are test expectations, not production constants. | PASS; included in final independent review. |
| F-03 | Low; AC-07 / report inventory | Final report inventory omitted the `.openspec.yaml` file generated by the approved OpenSpec scaffold. | Attempt 1: add `.openspec.yaml` explicitly to the affected-files inventory; verify it remains the only generated metadata file in the change. | PASS; independent confirmation received. |

## Test integrity

- Tests added/changed: none; implementation is not authorized in this planning run.
- Demonstrated test defect, if any: none examined or claimed.
- How approved acceptance criteria will be preserved in future tests: use current golden PDFs/JSON as read-only inputs; expected call facts remain fixture-specific assertions. Add synthetic tests for low-score unique pages, page boundaries, per-field/source coverage gaps, empty groups with evidence, duplicate/omitted clauses, cross-version conflicts, explicit amendment references, token/character limits and legacy package digest compatibility.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: semantic completeness of an LLM extraction cannot be proven by structural checks alone; the factual review remains the human authority. No external Finep/OpenAI operation has been revalidated as part of this planning activity.
- Decisions made (link ADR/OpenSpec artifact) or `none`: proposed only in `design.md`; no durable architecture decision has been incorporated outside the proposed change.
- OpenSpec tasks marked complete and evidence: none; all implementation tasks are unchecked.
- Final verification after fixes: `openspec validate finep-extraction-factual-quality --strict` (PASS); `openspec validate --all --strict` (PASS, 6/6); `git diff --check` and new-file whitespace scan (PASS); OpenSpec apply instructions confirm 0/11 implementation tasks complete; independent review (PASS, no pending findings).
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `PASS`; planning artifacts satisfy the authorized scope and independent review has no pending findings. Implementation remains unauthorized and all 11 implementation tasks remain unchecked.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.

No secrets, tokens, credentials, live payloads or reconstructed live evidence belong in this record.
