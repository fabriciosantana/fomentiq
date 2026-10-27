# Verification record: finep-extraction-factual-quality / FQ-R1 conservative policy

## Run identity

- Change and cycle: `finep-extraction-factual-quality` / FQ-R1 conservative policy
- OpenSpec change/tasks in scope: task 2.3, with acceptance revision explicitly authorized by the user on 2026-10-27
- Date/time (UTC): 2026-10-27
- Implementer context: `/root`
- Independent reviewer context/mechanism: separate read-only collaboration context `/root/fq_r1_strict_relations_review`
- Final state: `PASS`

## Approved specification and acceptance criteria

The product decision in this run supersedes the prior proposed deterministic-only proof for compound legal rules. It does not erase the diagnostic, strict-relations report, FQ-R1 identity, or prior attempt history. The original task 2.3 single-range automatic-validation criterion is explicitly revised below; this run does not claim that the earlier relation grammar was corrected.

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | Revised task 2.3; product decision | Non-conflicting `eligibility` and `counterpart` values never become `VALIDATED` automatically. Values and literal evidence are preserved as `EXTRACTED`. | Validator tests and PostgreSQL/Testcontainers integration assertions |
| AC-02 | Product decision; state compatibility | Missing value remains `MISSING`; explicit conflict remains `CONFLICT`; no new technical states or database changes. | Unit tests, backend clean verify, migration check |
| AC-03 | Product decision; FQ-R1 counterexamples | All prior FQ-R1 cases plus “Não se exige, para a contrapartida, declaração de ICT federal” remain unvalidated (`EXTRACTED`) and do not imply a financial exemption. | Named negative regression tests |
| AC-04 | Product decision; scalar behavior | Only existing unambiguous deterministic rules for `totalBudget` and `requiresIct` may return `VALIDATED`; ambiguous cases remain `EXTRACTED`. | Existing scalar positive and negative tests retained |
| AC-05 | Compatibility requirements | Preserve v1/v2 schema readers, audit package behavior, evidence/version links, document selection, deadline, amendments, golden fixture bytes and offline E2E. | Complete backend/frontend/Playwright/OpenSpec verification |
| AC-06 | Verified loop / AGENTS.md | Separate read-only reviewer evaluates approved revised spec, complete diff, and test evidence; no reviewer changes. | Reviewer report and worktree comparison |

## Plan and affected areas

- Planned vertical slice: revise OpenSpec task 2.3 and its design/spec acceptance; replace automatic compound-rule validation with conservative evidence-preserving `EXTRACTED` behavior; add all reported negative examples; retain scalar and selector behavior.
- Affected files/modules in this run: `DeterministicFieldValidator`, `DeterministicFieldValidatorTest`, the fixture-backed `FinepIngestionIntegrationTest` assertion, OpenSpec design/spec/tasks, and this run report. Additional uncommitted files in the worktree were present before this run and belong to preceding approved work; they were preserved and included in the independent reviewer’s complete worktree inspection.
- Known risks before implementation: consumers may have relied on automatic `VALIDATED` for composite values; report that this is an explicit product scope revision. Existing v2 clause/evidence payloads remain unchanged.
- Scope/architecture approval needed: none beyond the explicit product decision already supplied. No schema or architecture change planned.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `openspec status --change finep-extraction-factual-quality --json` | PASS | 10/11 tasks complete; task 2.3 pending |
| `openspec instructions apply --change finep-extraction-factual-quality --json` | PASS | Task 2.3 is the only pending task |
| `git status --short` | PASS | Pre-existing uncommitted changes inventoried and preserved; see conversation/worktree |
| `mvn -B -f backend/pom.xml -Dtest=DeterministicFieldValidatorTest test` | PASS, exit 0 | 16 focused validator tests passed |
| `mvn -B -f backend/pom.xml clean verify` | PASS, exit 0 | 124 tests passed; migrations 1–3 applied to clean PostgreSQL 17 Testcontainers; ingestion integration 15/15 passed |
| `npm run lint && npm run typecheck && npm test -- --run && npm run build` | PASS, exit 0 | Lint/typecheck/build passed; 24 frontend tests passed |
| `./scripts/run-offline-mvp-e2e.sh` | PASS, exit 0 | Deterministic fixture ingest succeeded; Playwright 1/1 passed; script reported exclusive resources removed |
| `git diff --check` | PASS, exit 0 | No whitespace errors |
| `openspec validate finep-extraction-factual-quality --strict` | PASS, exit 0 | Strict validation passed after moving long requirement details into scenarios |
| `openspec instructions apply --change finep-extraction-factual-quality --json` | PASS | After review, confirms 11/11 tasks complete; task 2.3 is checked under its explicitly revised criteria |

## Independent review

- Evidence supplied: revised OpenSpec artifacts, acceptance matrix, complete diff, exact test/build/check results.
- Reviewer had a separate context: yes, `/root/fq_r1_strict_relations_review`.
- Reviewer was instructed not to modify implementation, tests, specs, or this report: yes.
- Worktree was checked before and after review; reviewer made no changes: yes.
- Spec adherence, implementation quality, regressions, and security reviewed: yes.
- Result: `PASS`, no blocking findings. Reviewer confirmed task 2.3's revised policy, states/evidence behavior, FQ-R1 negative cases, retained scalar tests, no migration/golden fixture changes, and consistency with verification results.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| FQ-R1 | High; AC-01/AC-03 | Prior independent review reproduced a false positive where “Não se exige, para a contrapartida, declaração de ICT federal” was treated as a financial counterpart exemption; earlier strict-relations report remains `ESCALATE`. | Historical attempts remain recorded in prior reports. This run applies an explicitly approved product-policy change; it does not reset or relabel prior attempts. Composite automatic validation is now deferred rather than claiming the grammar was fixed. | PASS under revised policy; historical grammar finding remains documented |

## Test integrity

- Tests added/changed: complete-looking single-range and other compound-rule tests now expect `EXTRACTED`; added exact regression for “Não se exige, para a contrapartida, declaração de ICT federal”; retained wrong-category counterpart, wrong budget qualifier, wrong ICT actor, incomplete citation/coverage, MISSING/CONFLICT tests; strengthened PostgreSQL fixture assertion so both compound fields must be `EXTRACTED` or `CONFLICT` and existing evidence/page/hash assertions remain.
- Demonstrated test defect, if any: none. Prior positive compound expectations were changed solely because the user explicitly revised the product acceptance policy. They are now negative policy regressions; no unrelated coverage was removed.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: unchanged scalar positive/negative tests, selection/page/version coverage, deadline, budget, amendment, audit package, evidence binding and E2E checks all passed. Added direct false-positive reproductions and a real Testcontainers persistence assertion.
- Any test weakening/removal: none planned; scalar, selection, fixture, version, audit, integration and E2E coverage must remain.

## Risks, decisions, and final verification

- Residual risks/debts: compound eligibility/counterpart values are not technically auto-validated; human factual review remains necessary. This intentionally creates false negatives rather than unsupported legal conclusions.
- Decisions made (link ADR/OpenSpec artifact): explicit product decision in this run; revised OpenSpec design/spec/task 2.3 to follow.
- OpenSpec tasks marked complete and evidence: task 2.3 checked under the revised, independently reviewed product policy; all other checkboxes were preserved. `openspec instructions apply` reports 11/11 complete.
- Final verification after fixes: backend clean verify, frontend lint/typecheck/tests/build, isolated Playwright offline E2E, OpenSpec strict and diff check all passed.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `PASS` for implementation of the revised conservative policy: all required suites and strict OpenSpec pass, and independent review found no blocking issue. The original relation grammar is not claimed as fixed; compound legal rules remain `EXTRACTED` for factual review. FQ-R1's prior attempt history and prior `ESCALATE` report remain unchanged.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.

Do not include secrets, tokens, credentials, or sensitive provider payloads in this record.
