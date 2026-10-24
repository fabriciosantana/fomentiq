# Verification record: finep-opportunity-ingestion / Cycle 5 task 6.4

## Run identity

- Change and cycle: `finep-opportunity-ingestion`, Cycle 5, task 6.4
- OpenSpec change/tasks: `openspec/changes/finep-opportunity-ingestion`, task 6.4 only
- Date/time (UTC): 2026-10-24
- Implementer context: primary Codex context
- Independent reviewer context/mechanism: separate collaboration reviewer `/root/cycle5_task64_review`
- Final state: `PASS`

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | Task 6.4; Browse opportunities / User opens detail | Each field's evidence can be expanded/collapsed independently; evidence presentation remains distinct from field validation state. | Component test, reviewer checks semantics/accessibility |
| AC-02 | Existing `Evidence` DTO and user request | Show literal quote when supplied, evidence ID, associated field record/name, document-version ID, and available locator; don't invent absent data. | Component tests for quote and locator/absent values |
| AC-03 | OpenSpec / Sources conflict | Show all evidence nested under the conflicted field without selecting any evidence as authoritative. | Conflict component test |
| AC-04 | `Evidence.sourceUrl`; user request | Only valid HTTP(S) URLs without embedded credentials are openable; use a new tab with `noopener`, `noreferrer`, and no referrer. Missing/invalid URL is shown unavailable. | Component tests for safe/invalid/missing URLs; review |
| AC-05 | OpenAPI `GET /api/opportunities/{callId}`; `OpportunityDetail` embeds fields/evidence | Reuse the existing detail response. Loading/error/404 follow the detail request; an empty evidence collection has its own explicit empty state. | Component tests; API/diff review |
| AC-06 | User request | Accessible disclosure controls and responsive evidence content; tests cover navigation, loading/error, no evidence, quote, locator, conflict, and links. | Lint/typecheck/component tests/build/review |

## Plan and affected areas

- Planned vertical slice: replace compact evidence references with a field-scoped evidence disclosure using the nested evidence in the existing detail DTO; show quoted text, evidence/field/document-version identifiers, safe locator data, and safe public source links.
- Affected files/modules: `frontend/src/OpportunityDetailPage.tsx`, `frontend/src/OpportunityDetailPage.css`, `frontend/src/App.test.tsx`, this report, and OpenSpec task 6.4 checkbox only after review.
- Known risks before implementation: OpenAPI does not provide a human document title in the `Evidence` DTO, only `documentVersionId`; source links must not accept unsafe schemes; conflicts may have multiple pieces of evidence.
- Scope/architecture approval needed: none. Reuse nested evidence already returned by the detail endpoint to preserve its field association; no additional endpoint call, backend/API change, document lookup, storage access, or timeline.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `git status --short --untracked-files=all` | pass | Clean before implementation. |
| `git log -1 --oneline` / `git branch -vv` | pass | Task 6.3 commit `0f81e07` is on `origin/main`. |
| `openspec status --change finep-opportunity-ingestion --json` | pass | Planning complete. |
| `openspec instructions apply --change finep-opportunity-ingestion --json` | pass | 33/44 tasks complete; 6.4 pending; 6.5 and 6.6 pending. |
| `openspec validate finep-opportunity-ingestion --strict` | pass | Change valid before implementation. |
| `npm run lint` (`frontend/`) | pass, exit 0 | oxlint, final run after findings fixed. |
| `npm run typecheck` (`frontend/`) | pass, exit 0 | Final run after correcting test helper DOM element typing. |
| `npm test -- --run` (`frontend/`) | pass, exit 0 | 1 test file, 14 tests passed after fixes. |
| `npm run build` (`frontend/`) | pass, exit 0 | TypeScript + Vite production build after fixes. |
| `git diff --check` | pass, exit 0 | Final run after report and task-status updates. |
| `openspec validate finep-opportunity-ingestion --strict` | pass, exit 0 | Final run after task-status update; change valid. |
| `openspec instructions apply --change finep-opportunity-ingestion --json` | pass | 34/44 tasks complete; 6.4 complete; 6.5 remains pending. |
| Independent review | pass | Separate context confirmed F-01/F-02 resolved, no scope/API/backend change, valid Finep HTTPS URL still accepted, worktree unchanged. |

## Independent review

- Evidence supplied: approved OpenSpec task/spec, acceptance matrix, complete diff, and exact test/build/check results.
- Reviewer had a separate context: yes, `/root/cycle5_task64_review`.
- Reviewer was instructed not to modify implementation, tests, specs, or this report: yes.
- Worktree was checked before and after review; reviewer made no changes: yes; status remained unchanged.
- Spec adherence, implementation quality, regression, link security, and semantic presentation reviewed: yes.
- Independent outcome: PASS after one correction attempt for each finding; reviewer confirmed safe links, field-version-evidence association, literal quote rendering, locator allowlist, conflict behavior, accessibility, and scope.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| F-01 | Medium; AC-04 source URL security | `?key=...` was not rejected by the initial URL parameter filter. | Attempt 1: reject generic `key` and normalized credential aliases; add regression fixture/assertion. | PASS on independent re-review; no link emitted. |
| F-02 | Medium; AC-04 public host validation | Some reserved IPv4, single-label/internal DNS names (including `.internal`) remained linkable. | Attempt 1: reject special/reserved IPv4 blocks, non-global/documentation/6to4 IPv6, single-label hosts, and internal/reserved suffixes; add 192.0.0.1 and `service.internal` regressions. | PASS on independent re-review; public Finep HTTPS URL remains linked safely. |
| T-01 | Low; AC-02/03/04 test specificity | Initial component tests queried repeated evidence/document identifiers and a page-global link while the test fixture intentionally contained several evidence records. | Attempt 1 scoped assertions to the relevant fields; one global link assertion remained too broad. Attempt 2 scoped the remaining assertion. | All 14 tests pass. |
| T-02 | Low; test compilation | TypeScript reported `Element` was not assignable to `HTMLElement` in scoped Testing Library assertions. | Attempt 1: typed `closest` results as `HTMLElement`; typecheck/build and tests pass. | All 14 tests pass. |

## Test integrity

- Tests added/changed: `frontend/src/App.test.tsx`; adds literal quote/locator/field/document-version display, safe-link behavior, conflicting evidence presentation, empty evidence, and refusal of JavaScript, local-IP, and credential-query URLs. Existing catalog navigation and detail error/loading tests remain.
- Demonstrated test defect, if any: overly broad selectors caused false failures; generic DOM typing in test helpers caused compilation errors. Fixes are tracked as F-01 and F-02.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: selectors were scoped to individual field cards and typed correctly; no acceptance assertions were removed or weakened.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: the API exposes a document-version UUID but no human-readable document title in `Evidence`; the viewer uses the supplied version ID. Browser E2E remains task 7.3, and the document/version timeline remains task 6.5.
- Decisions made (link ADR/OpenSpec artifact) or `none`: none.
- OpenSpec tasks marked complete and evidence: task 6.4 only; component tests and independent review pass. Task 6.5 remains unchecked.
- Final verification after fixes: `npm run lint`, `npm run typecheck`, `npm test -- --run` (14 passed), `npm run build`, `git diff --check`, `openspec validate finep-opportunity-ingestion --strict`, and apply-instructions status (34/44, 6.4 complete, 6.5 pending) all pass.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `PASS`; all task acceptance criteria have passing evidence and independent review found no unresolved issue.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.
- Commit/push and task 6.5 gates remain pending; neither is authorized by this run.
