# Verification record: finep-extraction-factual-quality / implementation

## Run identity

- Change and cycle: `finep-extraction-factual-quality` / implementation
- OpenSpec change/tasks in scope: tasks 1.1–3.3 (11 tasks)
- Date/time (UTC): 2026-10-27
- Implementer context: primary Codex context
- Independent reviewer context/mechanism: separate collaboration context `/root/factual_quality_independent_review`, read-only review of the approved change, complete diff and test evidence.
- Final state: `ESCALATE`

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | 1.1–1.4, Track critical-field source selection coverage | Inputs carry document identity/type/version; bounded, field-aware, clause-aware selection records candidates, selected/unselected pages and stable gaps before provider calls; selected passages are not silently truncated; exact coverage and inputs are digest-bound; legacy audit inputs remain verifiable. | Selector and audit tests; offline audit verifier. |
| AC-02 | 2.1–2.2, Preserve extraction contracts | New requests use strict `finep-call-v2`, retaining v1 parsing/provider artifacts and historical verification. Provider port remains vendor-neutral. | Schema/provider contract tests; v1 compatibility tests. |
| AC-03 | 2.3–2.4, Validate composite rules and amendments | Eligibility/counterpart require complete clauses, cited evidence and complete selector coverage; unresolved differences remain `CONFLICT`; explicit replacement evidence required for supersession; no new technical state. | Positive/negative deterministic validator tests. |
| AC-04 | 3.1, Golden fixture regression | Frozen fixture supports six critical-field expectations, version/page/quote links, full counterpart and eligibility clauses, ICT=true and TRL missing; synthetic incomplete/complete single-range cases; fixture unchanged. | Deterministic backend integration/regression tests and fixture hash check. |
| AC-05 | 3.2, Audit and factual-review compatibility | Historical v1 packages and records remain valid; new v2 package contains coverage/schema/evidence and passes offline audit/factual-review validation. | Audit exporter/verifier and factual-review tests. |
| AC-06 | 3.3, API/frontend compatibility | Richer values remain readable through current DTO/frontend; no migration, endpoint, or public OpenAPI change. | API/frontend regressions; schema/migration diff inspection. |

## Plan and affected areas

- Planned vertical slice: document metadata → field/version-aware clause selection and coverage → v2 provider contract → deterministic completeness and explicit amendment checks → persistence/audit compatibility → fixture and consumer regression.
- Affected files/modules: extraction application/domain/infrastructure, ingestion-to-extraction metadata mapping, audit/factual-review compatibility, API/frontend tests if required, OpenSpec task checkboxes, verification report.
- Known risks before implementation: 24-excerpt/24,000-character ceilings may not cover every applicable version/clause; parser text may not resolve semantic completeness; v2 output can affect consumers; old digest verification must remain byte-compatible.
- Scope/architecture approval needed: none planned; any need for database migration, new technical status, or increased limits stops for escalation.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `openspec status --change finep-extraction-factual-quality` | PASS, 0/11 complete | Approved change ready for implementation. |
| `openspec validate finep-extraction-factual-quality --strict` | PASS before implementation | Planning artifacts validated. |
| `git status --short` | PASS | Only the approved planning change/report were untracked before implementation. |
| `mvn -B -f backend/pom.xml clean verify` | PASS (exit 0; 117 tests, 0 failures/errors) | Includes clean Flyway/Testcontainers PostgreSQL migrations, ingestion/audit/API regressions and current focused extraction tests. |
| `mvn -B -f backend/pom.xml -Dtest=DeterministicFieldValidatorTest,StructuredExtractionServiceTest test` | PASS (exit 0; 13 tests) | Current focused evidence, polarity, date/time/offset and explicit amendment tests. |
| `npm run lint` | PASS (exit 0) | Frontend unchanged. |
| `npm run typecheck` | PASS (exit 0) | Frontend unchanged. |
| `npm test -- --run` | PASS (exit 0; 24 tests) | Component/unit suite. |
| `npm run build` | PASS (exit 0) | Production Vite build. |
| `E2E_POSTGRES_PORT=25434 E2E_BACKEND_PORT=18082 E2E_FRONTEND_PORT=14175 E2E_S3_PORT=29004 E2E_S3_CONSOLE_PORT=29005 ./scripts/run-offline-mvp-e2e.sh` | PASS (exit 0; Playwright 1/1) | Used isolated Compose project/volumes and was run before the final conservative evidence-check revisions. Not rerun after the final revisions because the independent review exhausted the correction limit. |
| `openspec validate finep-extraction-factual-quality --strict` | PASS (exit 0) | Change validates with task 2.3 still open. |
| `git diff --check` | PASS (exit 0) | Includes task status and report edits. |
| `git status --short` / `git diff --stat` | PASS, inspected | No fixture, migration, API/OpenAPI, frontend, secret or live-execution-manifest changes. No commit/push performed. |

## Independent review

- Evidence supplied: approved spec/tasks, acceptance matrix, complete diff, test results and regressions.
- Reviewer had a separate context and was instructed not to modify implementation, tests, specs, or this report.
- The reviewer made no changes.
- Review result: `RETRY` after three correction attempts against the same finding; the loop terminal state is `ESCALATE` because the maximum attempts were exhausted.
- The review covered spec adherence, evidence-to-claim integrity, conditional rules, migrations/API/frontend scope, fixture integrity, and regressions.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| FQ-R1 | High; AC-03 / task 2.3: source text must support every cited composite/scalar claim and must not combine unrelated clauses into a validated rule. | Independent reviewer reproduced unsupported exception (`Banco X`), explicit inclusion/exclusion polarity contradiction, unrelated negation in a long quote, and numeric/qualifier/ICT signals that can occur in separate sentences. | Attempt 1: added clause/value anchor, numeric boundary, scalar value+qualifier/date+time evidence checks and negative unsupported-claim regression. Attempt 2: added local inclusion/exclusion polarity and explicit timezone-offset matching. Attempt 3: scoped polarity to the sentence containing claim anchors and added the unrelated-negation regression. | Still open: ranges check both numeric endpoints against the entire citation quote after finding any anchored sentence; budget and ICT checks can also combine signals from separate sentences. Reviewer explicitly treated this as the same finding, not a new ID. Stop after attempt 3 and escalate; no further edits. |
| FQ-02 | Medium; AC-01/task 1.3: exact text and locator integrity. | Initial audit package verifier rejected evidence quotes that were assembled from trimmed clauses and did not match exact source offsets. | Attempt 1: select exact original text spans with original start/end offsets; regression compares the selected excerpt to the source substring. | Resolved; focused selector and full backend tests pass. |
| FQ-03 | Medium; AC-04/task 3.1: fixture evidence must bind to the correct version. | Duplicate text on different historical PDFs allowed a fixture adapter to choose the wrong document version. | Attempt 1: prioritize exact fixture artifact filename and retain quote fallback only when no expected artifact path exists; integration asserts expected hash/page binding. | Resolved; ingestion integration suite passes. |
| FQ-04 | Medium; AC-02/AC-05: v2 result and audit package consistency. | V2 response serialization omitted required nullable schema properties; selected-page numeric node types caused coverage-summary mismatch in offline verification. | Attempt 1: serialize all required nullable properties and canonicalize page number nodes to integer representation; added legacy-input verifier regression. | Resolved; audit/factual review tests and full backend verify pass. |
| FQ-05 | Medium; AC-03/task 2.3: a structured composite must not validate when model clauses and JSON groups disagree. | Initial validator did not require one-to-one correspondence between structured ranges/inclusions/exclusions and cited clauses. | Attempt 1: added one-to-one range and set correspondence, complete conditions/exclusions and explicit exception/condition mapping; added positive complete single-band and negative partial-band tests. | Structural mismatch checks pass, but covered by unresolved FQ-R1 when citations can combine unrelated text. |
| FQ-06 | Medium; AC-06/task 3.3: preserve existing catalog/detail E2E behavior. | Offline Playwright initially failed because program evidence on the main notice first page was no longer selected. | Attempt 1: add one supplementary first-page notice passage after reserving critical field/version excerpts; reran the isolated E2E successfully. | Resolved for the run recorded above; final stricter evidence changes still need E2E rerun after human direction resolves FQ-R1. |

## Test integrity

- Tests added/changed: selector coverage/gap/locator tests; frozen-fixture critical-field/version/evidence regression; deterministic composite completeness, unsupported citation, inclusion/exclusion polarity and timezone/qualifier tests; provider schema/parser and v1 audit compatibility tests; ingestion idempotency and full audit package integration tests. Existing assertions were retained or strengthened.
- Demonstrated test defect: no acceptance test was removed or weakened. The amendment regression fixture text and its exact citation quote were expanded together to include the now-required explicit time and offset, preserving its approved amendment-resolution intent.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: all six fixture values/evidence bindings remain tested; composite partial rules stay `EXTRACTED`; the new false-positive regressions require values and polarity to be present in cited text.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: FQ-R1 remains unresolved. A citation may still combine separated clauses for counterpart range endpoints, budget qualifiers, or ICT requirement signals. Do not rely on `VALIDATED` for factual homologation until this is fixed and independently reviewed. The selected-token ceiling uses a conservative character heuristic, not the provider's exact tokenizer. Final E2E was not rerun after the last evidence-validator changes.
- Decisions made (link ADR/OpenSpec artifact) or `none`: pending; no new durable architectural decision planned.
- OpenSpec tasks marked complete and evidence: 1.1–1.4, 2.1–2.2, 2.4, and 3.1–3.3 checked (10 tasks); task 2.3 remains unchecked because FQ-R1 is unresolved.
- Final verification after fixes: backend clean verify 117/117, focused validator/service tests 13/13, frontend lint/typecheck/tests/build all pass, OpenSpec strict and diff check pass. The previously successful isolated Playwright run predates final validator changes.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `ESCALATE`. Independent review kept the same high-severity FQ-R1 open after three correction attempts. `AGENTS.md`/verified-implementation-loop prohibit more attempts against this finding in this run; human direction is required before another run.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.
