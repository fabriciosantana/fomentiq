# Verification record: opportunity-curation-ux-refinements / UX correction

## Run identity

- Change and cycle: `opportunity-curation-ux-refinements` / bounded UX implementation.
- OpenSpec change/tasks in scope: tasks 1.1–4.2.
- Date/time (UTC): 2026-10-28.
- Implementer context: primary Codex context in `/workspaces/fomentiq`.
- Independent reviewer context/mechanism: separate read-only context `/root/curation_increment_b_review`.
- Final state: `PASS`.
- Baseline: clean `main` at `b888a0026a8b59aac14517d99b8d3af84b85d6fc`, synchronized with `origin/main` before implementation.

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | 1.1–1.2 | Shared pt-BR labels and display formatting; original property names and submitted values unchanged. | `fieldPresentation.ts`; unit tests for labels, points and Brasília date-time round-trip; 34 frontend tests pass. |
| AC-02 | 2.1 | Concise field list and one selected editor; current/original value, status, origin, revision, criticality, and human-review progress visible. | Admin component test; E2E admin flow; independent review PASS. |
| AC-03 | 2.2 | Responsible agencies use the existing relation, support multiple agencies, and are read-only; no free-text `agency` editor. | Component test with CNPq/CAPES; `agency` excluded from editable allowlist; no backend/API changes. |
| AC-04 | 3.1–3.2 | Typed controls for existing scalar/list/structured fields preserve untouched branches, nulls, conditions and exceptions. | Component tests cover eligibility, counterpart ranges and exceptions, nulls, and confirmed absence. |
| AC-05 | 3.3 | Document version remains coupled to selected document; changing document clears stale version; statuses and controls are localized. | Component test verifies reset; Playwright admin flow passed. |
| AC-06 | 4.1–4.2 | Frontend, backend, isolated offline E2E, Compose and strict OpenSpec checks pass; reviewer independently checks complete diff. | Results recorded below; reviewer confirmed PASS and no additional findings. |

## Plan and affected areas

- Planned vertical slice: localized display, focused admin review, typed existing-field editors, and updated tests for the existing admin/public flow.
- Affected files: `frontend/src/fieldPresentation.ts`, `frontend/src/fieldPresentation.test.ts`, `frontend/src/AdminCurationPage.tsx`, `frontend/src/AdminCurationPage.css`, `frontend/src/OpportunityDetailPage.tsx`, `frontend/src/App.test.tsx`, `frontend/e2e/offline-opportunity-flow.spec.ts`, OpenSpec artifacts, and this report.
- No backend, database, API/OpenAPI, extraction schema, fixture, or audit-package changes.
- Known risks: rendering and editing complex nested payloads without dropping branches; keeping date-time display/edit in Brasília time; distinguishing agency relationship from extracted agency text.
- Scope/architecture approval needed: none.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `npm run lint` (frontend) | PASS, exit 0 | No lint findings. |
| `npm run typecheck` (frontend) | PASS, exit 0 | TypeScript project build passed. |
| `npm test -- --run` (frontend) | PASS, exit 0; 34 tests | Includes new public criterion-weight and date-time presentation regressions. |
| `npm run build` (frontend) | PASS, exit 0 | Vite production bundle built. |
| `mvn -B -f backend/pom.xml clean verify` | PASS, exit 0; 162 tests | Includes Testcontainers/PostgreSQL and migrations; no backend files changed. |
| `bash scripts/run-offline-mvp-e2e.sh` | PASS, exit 0; Playwright 3/3 | Isolated Compose project; Finep and CNPq fixture ingestion plus admin publish/reject flow; script reported its test resources removed on exit. No external Finep/CNPq/OpenAI calls. |
| `docker compose config -q` | PASS, exit 0 | Compose configuration valid. |
| `openspec validate opportunity-curation-ux-refinements --strict` | PASS, exit 0 | Change valid. |
| `openspec validate --all --strict` | PASS, exit 0; 8/8 artifacts | All current changes/specs valid. |
| `git diff --check` | PASS, exit 0 | No whitespace errors. |

The first E2E attempt used selectors for the previous all-fields-expanded UI and timed out. A second attempt exposed an ambiguous accessible-name locator for the document selector. The Playwright test was updated to interact with the selected-field editor and target the two selects within the document-reference group. The final isolated E2E run passed 3/3.

## Independent review

- Evidence supplied: OpenSpec proposal/design/spec/tasks, implementation diff, frontend tests/build, backend verify and E2E results.
- Reviewer had a separate context: yes (`/root/curation_increment_b_review`).
- Reviewer was instructed not to modify implementation, tests, specs, or this report: yes.
- Worktree was checked before and after review; reviewer made no changes: yes; no reviewer-authored changes are present in the final status.
- Spec adherence, implementation quality, regressions, and security reviewed: yes.
- Review outcome: `PASS` after correction attempt 1 for finding `UXR-REV-01`.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt and correction | Re-review outcome |
|---|---|---|---|---|
| UXR-REV-01 | Medium; AC-01 / task 1.2 | Public detail rendered `evaluationCriteria[].weight = 5` as `500%`, while the admin editor labels it as points. | Attempt 1: introduced shared `formatPoints`, used it in public detail and added regression asserting “5 pontos” and absence of “500%”. | Resolved; reviewer PASS, no new findings. |

## Test integrity

- Tests added/changed: `fieldPresentation.test.ts`, `App.test.tsx`, and `offline-opportunity-flow.spec.ts`.
- Demonstrated test defect: existing Playwright selectors referenced the old UI structure and English enum statuses after the approved UI redesign/localization.
- How approved criteria and equivalent/stronger coverage were preserved: selectors now exercise the actual selected-field editor, localized revision status, typed Brasília deadline input, document/version binding, publication and rejection. Tests were not weakened or removed.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: date-time input uses the `America/Sao_Paulo` timezone rules available in the runtime; historical Brazil daylight-saving transitions are resolved by `Intl`. The public schema has no additional localized metadata for unknown future field names, so newly introduced fields require adding an explicit friendly-label mapping.
- Decisions made: agency editing remains read-only because the existing structured responsible-agency relation is authoritative; no model/API change was introduced.
- OpenSpec tasks marked complete: 1.1–4.2, with evidence above.
- Final verification after fixes: frontend lint/typecheck/tests/build, backend clean verify, isolated Playwright E2E, Compose config, all strict OpenSpec validation, and diff whitespace validation passed.
- Final state rationale: `PASS`; all scoped criteria are covered and the only independent finding was corrected and re-reviewed.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.
