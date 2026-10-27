# Verification record: finep-extraction-factual-quality / FQ-R1 strict relations

## Run identity

- Change and cycle: `finep-extraction-factual-quality` / FQ-R1 strict relations
- OpenSpec change/tasks in scope: task 2.3 only; no other task is authorized for completion by this run.
- Date/time (UTC): 2026-10-27
- Implementer context: primary Codex context
- Independent reviewer context/mechanism: `/root/fq_r1_strict_relations_review`, separate read-only collaboration context; first review returned RETRY, follow-up review pending
- Final state: `ESCALATE`
- Finding identity: same high-severity finding `FQ-R1`; previous three correction attempts remain recorded in the 2026-10-26 implementation report and the 2026-10-27 diagnostic. This authorization does not delete, reset, split or relabel those attempts.

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | Human architectural decision; task 2.3 | No schema v2, v1 reader, database, audit package or technical-state change. | Diff inspection and compatibility suites. |
| AC-02 | Human decision; task 2.3 | `VALIDATED` requires a recognized syntactic relation binding all claim parts; co-occurrence in quote/page/clause/segment is insufficient. | Exact positive/negative grammar tests. |
| AC-03 | Human decision; task 2.3 | Counterpart category, condition and both percentage limits are unambiguously bound by one recognized rule expression; ambiguous/multiple categories or cross-clause conditions remain `EXTRACTED`. | Original category-swap counterexample and positive/negative tests. |
| AC-04 | Human decision; task 2.3 | Global budget amount and qualifier belong to the same recognized monetary expression; other amounts/qualifiers cannot be combined. | Original qualifier counterexample and positive/negative tests. |
| AC-05 | Human decision; task 2.3 | ICT requirement explicitly predicates that eligible proposer/submitter/executor must be an ICT; incidental ICT document duties and optional applicant lists do not validate. | Original actor counterexample and positive/negative tests. |
| AC-06 | Human decision; task 2.3 | Unsupported syntax, ambiguous tables, references, clause boundaries or insufficient literal citation fail closed to `EXTRACTED`. | Negative tests for ambiguity and incomplete literal evidence. |
| AC-07 | Existing OpenSpec + user constraints | Preserve fixture files/hashes, v1 package verification, audit compatibility, frontend/API, migrations, and all prior task completion marks; task 2.3 stays unchecked pending full proof/review. | Fixture checksums, regression suites, OpenSpec status. |
| AC-08 | Process | Backend clean verify, applicable frontend checks, offline E2E, strict OpenSpec, diff check, separate-context independent review; no external Finep/OpenAI calls and no commit/push. | Command table and reviewer outcome. |

## Plan and affected areas

- Planned vertical slice: inspect the FQ-R1 diagnostic and prior three attempts → formalize D8 supported grammars in OpenSpec → add regressions for relation binding and fail-closed behavior → tighten only `DeterministicFieldValidator` and strictly necessary helpers → run required regression stack → obtain independent review → correct only in-scope findings within the existing FQ-R1 attempt history.
- Affected files/modules: `openspec/changes/finep-extraction-factual-quality/design.md`, `backend/src/main/java/com/fomentiq/extraction/application/DeterministicFieldValidator.java`, `backend/src/test/java/com/fomentiq/extraction/DeterministicFieldValidatorTest.java`, and this new run report. Preserve every other pre-existing worktree change.
- Known risks before implementation: conservative parser may leave fixture claims `EXTRACTED` where existing expected status assumes broad semantic support; legal drafting outside the explicitly accepted grammar will intentionally fail closed. If task 2.3 or immutable fixture assertions require incompatible behavior, stop/escalate rather than weaken tests or expand the contract.
- Scope/architecture approval needed: no; the deterministic-only decision is expressly approved. Any need to expand schema, state model, migrations or broader scope remains a stop/escalate condition.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `openspec status --change finep-extraction-factual-quality --json` | PASS, exit 0 | Schema `spec-driven`; 10/11 tasks complete, task 2.3 remaining. |
| `openspec instructions apply --change finep-extraction-factual-quality --json` | PASS, exit 0 | Context files read; task 2.3 identified as the sole pending task. |
| `openspec validate finep-extraction-factual-quality --strict` (before code edits) | PASS, exit 0 | D8 design decision added and validated before implementation. |
| `git diff --check` (before code edits) | PASS, exit 0 | No whitespace errors at plan checkpoint. |
| `mvn -B -f backend/pom.xml -Dtest=DeterministicFieldValidatorTest test` | PASS, exit 0 | Initial scoped regression run: 12 tests passed. |
| `mvn -B -f backend/pom.xml clean verify` | FAIL, exit non-zero | First clean run reported missing generated classfiles during test compilation; an immediate clean retry compiled normally. No source change was made to address this transient failure. |
| `mvn -B -f backend/pom.xml clean verify` | FAIL, exit non-zero | 120 tests ran; two positive budget grammar tests exposed terminal punctuation captured as part of the amount. Corrected the money token boundary and reran focused tests. |
| `mvn -B -f backend/pom.xml -Dtest=DeterministicFieldValidatorTest test` | PASS, exit 0 | 12 tests passed after the amount-boundary fix. |
| `mvn -B -f backend/pom.xml clean verify` | PASS, exit 0 | 120 tests passed; migrations 1–3 applied in clean PostgreSQL 17 Testcontainers databases; package rebuilt. |
| `./scripts/run-offline-mvp-e2e.sh` | PASS, exit 0 | Offline fixture ingestion succeeded; Playwright 1/1 passed; isolated containers, network and volumes removed. |
| Frontend `npm run lint && npm run typecheck && npm test -- --run && npm run build` | PASS, exit 0 | 24 component tests passed; lint/typecheck/build passed. |
| `mvn -B -f backend/pom.xml -Dtest=DeterministicFieldValidatorTest test` | PASS, exit 0 | 13 focused tests passed, including literal-but-incomplete citation regression. |
| `mvn -B -f backend/pom.xml clean verify` | PASS, exit 0 | After follow-up review corrections: 122 backend tests passed; Flyway clean migration and package build passed. |
| `./scripts/run-offline-mvp-e2e.sh` | PASS, exit 0 | Fixture ingestion succeeded; Playwright 1/1 passed; isolated resources removed. |
| Frontend `npm run lint && npm run typecheck && npm test -- --run && npm run build` | PASS, exit 0 | Final: 24 component tests passed; lint/typecheck/build passed. |
| `openspec validate finep-extraction-factual-quality --strict` | PASS, exit 0 | Strict change validation after implementation. |
| `git diff --check` | PASS, exit 0 | Root worktree whitespace check after implementation. |
| `git diff --exit-code -- backend/src/test/resources/fixtures/finep/proinfra-2024-expansao-751` | PASS, exit 0 | Golden fixture unchanged. |

## Independent review

- Evidence supplied: OpenSpec task/design, acceptance matrix, relevant diff, and then-current backend/frontend/E2E/OpenSpec results.
- Reviewer had a separate context: yes, `/root/fq_r1_strict_relations_review`.
- Reviewer was instructed not to modify implementation, tests, specs, or this report: yes.
- Worktree was checked before and after review; reviewer made no changes: yes for both reviews.
- Spec adherence, implementation quality, regressions, and security reviewed: yes. First review returned `RETRY`; follow-up confirmed those findings resolved but reproduced a new false positive for the same stable FQ-R1, so final status is `ESCALATE`.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| FQ-R1 | High; AC-02–AC-06 / task 2.3 | Prior reports preserve three historical false-positive reproductions. First independent review in this run reproduced: counterpart exemption co-occurrence; scalar `validate`/empty quote validation without text; and mismatch between documented and recognized ICT relation grammar. The follow-up review confirmed those fixes but reproduced another false positive: claim `exempt=["ICT federal"]` with quote `Municipal rule: 0.1% to 4% for up to 50,000 residents; Não se exige, para a contrapartida, declaração de ICT federal.` The source waives a declaration; the validator accepts it as an exemption from counterpart. | The earlier three attempts remain documented and consumed. This post-ESCALATE run is separately identified as explicitly authorized by the human architecture decision: run correction pass 1 tightened relation grammars; independent RETRY findings were addressed in correction pass 2 by relation-checking counterpart exemptions, preventing scalar VALIDATED without source text, and aligning ICT grammar/tests. The follow-up review shows FQ-R1 still unresolved. Per the declared stop condition and bounded retry process, no further correction is attempted; no task/contract/state is split or renamed. | First review RETRY; follow-up review RETRY. Final `ESCALATE`; implementation must not be considered complete. |
| FQ-R2 | Medium; AC-05 / implementation correctness | Focused tests exposed a malformed static regex during initialization after the ICT grammar update. | Attempt 1: corrected grouping, added `propostas?` to match the documented Portuguese positive construction, and reran the focused suite. | PASS in focused tests; included in pending independent re-review. |
| FQ-R3 | Low; AC-05 / supported grammar | After regex syntax correction, the positive `Somente ICTs podem apresentar propostas` case initially remained `EXTRACTED` because only English `proposals?` was recognized. | Attempt 1: expanded the same approved syntax alternative to include Portuguese `propostas?`, without widening claim semantics; 14 focused tests passed. | PASS in focused tests; included in pending independent re-review. |

## Test integrity

- Tests added/changed: strict scalar source text, budget scope/qualifier binding, requiresIct subject/predicate including Portuguese/English positive forms, incidental documentation duty, counterpart category/condition/range association, explicit exemption relation, insufficient literal citation and ambiguous multi-category/cross-sentence cases.
- Demonstrated test defect, if any: none identified so far.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: tests assert every unrecognized or incomplete relationship remains `EXTRACTED`; positive tests cover only constructions explicitly listed in D8. No fixture assertion, schema, technical state or v1 reader was weakened.
- Any test weakening/removal: none planned; prohibited.

## Risks, decisions, and final verification

- Residual risks/debts: blocking FQ-R1 false positive remains for counterpart exemptions: a statement waiving a declaration can be interpreted as waiving counterpart. Do not rely on `VALIDATED` for counterpart until this predicate is tied specifically to the claimed exemption. Other conservative gaps remain: unrecognized legal drafting, tables, references and formats stay for human factual review; automation must not imply complete legal interpretation.
- Decisions made (link ADR/OpenSpec artifact) or `none`: approved deterministic-only relation validation, documented as D8 in `openspec/changes/finep-extraction-factual-quality/design.md`; no v2 contract change.
- OpenSpec tasks marked complete and evidence: none; task 2.3 remains unchecked because FQ-R1 is unresolved.
- Final verification after fixes: backend `clean verify` 122 tests PASS; frontend lint/typecheck/24 tests/build PASS; offline Playwright 1/1 PASS; strict OpenSpec PASS; `git diff --check` PASS; fixture diff check PASS. Independent follow-up returned `RETRY` with the exact residual false positive recorded above.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `ESCALATE`; the follow-up independent reviewer reproduced a false `VALIDATED` under the approved syntactic-relation requirement. Stop further implementation under this run, preserve the failure evidence, and request a new human decision/authorization before continuing.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.

No Finep/OpenAI calls will be made. No commit or push is authorized in this run.
