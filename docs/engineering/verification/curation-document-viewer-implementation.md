# Verification record: curation-document-viewer / implementation

## Run identity

- Change and cycle: curation-document-viewer / implementation
- OpenSpec change/tasks in scope: tasks 1.1–3.2
- Date/time (UTC): 2026-10-28
- Implementer context: primary Codex context
- Independent reviewer context/mechanism: `/root/curation_document_viewer_review`, separate read-only collaboration context
- Final state: PASS (all acceptance checks passed after resolving CURDOC-01 and VERIFY-01)

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | Requirement: Open preserved versions through the administrative session; task 1.1 | Only an authenticated manual-demo admin can read exact opportunity/document/version bytes; ownership, 50 MiB limit, digest, type and safe headers are checked; no storage key/endpoint is exposed. | Reader tests, S3 bounded-read test, PostgreSQL relationship test, anonymous endpoint denial and Playwright PDF response |
| AC-02 | Requirement: Select the source and version in field context; task 2.1 | Evidence-linked document/version is selected after searching paginated versions; unresolved/error never attaches its citation to a fallback; user can select another version and no citation is fabricated. | Component tests for evidence-linked page, page-one resolution, version switch and no-evidence state |
| AC-03 | Requirement: Preview safe document formats; task 2.2 | Preserved PDF is previewed in the review panel, page navigation only uses a valid locator, DOCX downloads as attachment, official URLs pass existing safe URL policy, panel adapts to narrow screens. | Component PDF/DOCX tests, API tests and Playwright PDF response/page plus narrow viewport |
| AC-04 | Requirement: Keep published deadlines consistent; task 1.2 | Approved human deadline appears consistently in queue, admin detail/review and public catalog; original extraction/revision history remain unchanged; absent approval falls back to current source value. | PostgreSQL/Testcontainers and Playwright deadline comparison for Finep/CNPq |
| AC-05 | AGENTS / verification | Backend, frontend, offline E2E, Compose, strict OpenSpec, diff checks and independent review pass; fixtures and documents remain unchanged. | Final command log and reviewer report |

## Plan and affected areas

- Planned vertical slice: secure admin content read through existing S3-compatible storage abstraction; integrate PDF/document selection beside the field form; correct queue deadline projection using existing approval precedence.
- Affected files/modules: documents application/infrastructure; storage application/S3 adapter; admin curation controller/OpenAPI; curation JDBC queue; frontend admin page/styles/API/tests; offline Playwright; OpenSpec artifacts.
- Initial findings: existing admin queue reads raw call_for_proposals.deadline; current read API detail/public projection applies latest approved human revision. In the manual DB CNPq 30/2026 has raw date 2026-12-11 and approved human value 2026-10-21T12:32:00Z. No history is to be rewritten.
- Existing uncommitted work: preserve all changes already present, including prior task-oriented curation, reports, and prototypes. HEAD at start: b888a0026a8b59aac14517d99b8d3af84b85d6fc; branch main.
- Scope/architecture approval needed: none beyond explicit user authorization; new content route is admin-only in the existing isolated demo profile and adds no schema or migration.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| git status --short, git rev-parse --short HEAD, git branch --show-current | PASS | Existing changes inventoried and preserved; HEAD b888a00, branch main. |
| openspec status --change task-oriented-curation-experience | PASS | Existing implementation change has all artifacts and prior task groups complete. |
| openspec status --change opportunity-curation-and-ux | PASS | Existing curation change remains unarchived with artifacts intact. |
| Read-only psql query for CNPq deadline/revision | PASS | Source deadline 2026-12-11; approved review value 2026-10-21T12:32:00Z; no writes performed. |
| openspec status --change curation-document-viewer --json; openspec instructions apply --change curation-document-viewer --json | PASS | Spec-driven; 0/6 tasks initially; all approved planning artifacts loaded. |
| openspec validate curation-document-viewer --strict | PASS | Valid after initial authoring and after reviewer finding clarification. |
| mvn -B -f backend/pom.xml -Dtest=PreservedDocumentContentReaderTest,S3CompatibleArtifactStoreTest,OpportunityApiIntegrationTest test | PASS | Reader/storage/API Testcontainers regressions passed; clean test DB applied Flyway V1–V5. |
| mvn -B -f backend/pom.xml clean verify | PASS | 173 tests, 0 failures/errors; migrations applied to clean PostgreSQL/Testcontainers schemas. |
| cd frontend && npm run lint && npm run typecheck && npm test -- --run | PASS | Oxlint, TypeScript and 48/48 component tests pass, including an evidence version on page 1. |
| cd frontend && npm run build | PASS, rerun final | TypeScript and Vite production build passed before the paginated-version correction. |
| docker compose config -q | PASS | Compose config valid. |
| git diff --check | PASS, rerun final | No whitespace errors at last run. |
| ./scripts/run-offline-mvp-e2e.sh | RETRY | Re-run against the current revision failed all three Playwright specs. Backend started and logged successful isolated Finep/CNPq fixture persistence. Chromium showed a blank page / `Target crashed` before public API requests; a diagnostic trace confirmed Vite/React module responses but no API traffic or page error. The script cleaned its generated Compose resources. |
| `E2E_POSTGRES_PORT=25433 E2E_S3_PORT=29010 E2E_S3_CONSOLE_PORT=29011 E2E_BACKEND_PORT=18081 E2E_FRONTEND_PORT=14174 ./scripts/run-offline-mvp-e2e.sh` | RETRY | Same three browser failures with distinct ports; the first two checks timed out before app content/API requests, admin flow timed out during navigation. Backend fixture ingestion succeeded. |
| `E2E_POSTGRES_PORT=25434 E2E_S3_PORT=29012 E2E_S3_CONSOLE_PORT=29013 E2E_BACKEND_PORT=18082 E2E_FRONTEND_PORT=14175 ./scripts/run-offline-mvp-e2e.sh` | RETRY | Diagnostic run retained trace temporarily; blank page, Chromium target crash, no API requests/page errors. Diagnostic trace and logs were removed after inspection. A temporary failure-retention change to the script was reverted; `git diff -- scripts/run-offline-mvp-e2e.sh` is empty. |
| `DEBUG=pw:browser* E2E_POSTGRES_PORT=25435 E2E_S3_PORT=29014 E2E_S3_CONSOLE_PORT=29015 E2E_BACKEND_PORT=18083 E2E_FRONTEND_PORT=14176 ./scripts/run-offline-mvp-e2e.sh` | RETRY | Backend persisted both fixtures. Browser output showed TLS/WebSocket handshake failures (`ERR_CERT_AUTHORITY_INVALID` / net error -202) and delayed HMR connection; catalog cards/admin queue remained unavailable in assertions. Runner cleaned its isolated resources. |
| `E2E_POSTGRES_PORT=25436 E2E_S3_PORT=29016 E2E_S3_CONSOLE_PORT=29017 E2E_BACKEND_PORT=18084 E2E_FRONTEND_PORT=14177 ./scripts/run-offline-mvp-e2e.sh` | PASS | After configuring Playwright Chromium to ignore the self-signed certificate only for HTTPS test runs, all 3 Playwright specs passed (catalog Finep, catalog CNPq, admin workflow). Finep and CNPq fixture ingests succeeded; isolated backend/Compose volumes were removed by the runner. |
| `cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build` | PASS | Oxlint/typecheck passed, 48/48 component tests passed, production build succeeded after the Playwright config fix. |
| `git diff --check && docker compose config -q && openspec validate curation-document-viewer --strict` | PASS | Clean diff, valid Compose configuration, and strict OpenSpec validation. |

## Independent review

- Evidence supplied: approved change artifacts, acceptance matrix, implementation diff and test evidence.
- Reviewer had a separate context: yes, `/root/curation_document_viewer_review`.
- Reviewer was instructed not to modify implementation, tests, specs, or this report: yes; reviewer confirmed no edits.
- Worktree was checked before and after review; reviewer made no changes: confirmed.
- First review result: RETRY due CURDOC-01; correction was implemented as attempt 1.
- Read-only re-review confirmed CURDOC-01 resolved and reported no new implementation issue, but asked for E2E evidence on the current revision (VERIFY-01, low).
- After the local HTTPS Playwright certificate fix and passing E2E run, the independent reviewer returned PASS. It confirmed `--ignore-certificate-errors` is only set for HTTPS Playwright sessions, production TLS and cookie behavior remain unchanged, all three E2E specs passed, and no files were edited by the reviewer.

## Findings and correction attempts

Use stable IDs for underlying problems. Count no more than three correction attempts per finding across the run; rewording or splitting a finding does not reset its count.

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| TEST-01 | Low; tasks 2.1/2.2 | Existing UI tests queried document selectors while the disclosure was closed. | Updated tests to open “Selecionar outro documento” and retained document/version binding assertions. | Attempt 1: fixed stale UI test interactions. |
| INTEGRITY-01 | Medium; tasks 2.1/3.1 | E2E expected a quote to remain after selecting another version. | Keep the evidence-linked version for that revision; component test confirms alternate selection clears mismatched citation/locator. | Attempts 1–2: updated Finep/CNPq E2E setup to retain provenance. |
| CURDOC-01 | Medium; AC-02 / task 2.1 | Independent reviewer found that evidence version lookup only searched page zero (20 versions); an older version could be missed and its quote remain beside a fallback source. | Search later pages until the exact version is found. If missing or lookup errors, keep source unresolved and require explicit source selection; do not attach the quote to a fallback. Added page-one regression and clarified OpenSpec. | Attempt 1 implemented; independent re-review confirmed resolved. |
| VERIFY-01 | Low; task 3.2 | Chromium rejected Vite's self-signed HTTPS HMR WebSocket certificate (`ERR_CERT_AUTHORITY_INVALID` / net error -202), leaving the test page without expected content. | Attempt 1: pass `--ignore-certificate-errors` only to Chromium when Playwright uses HTTPS. Re-ran isolated full E2E, all 3 specs passed; HTTPS and Secure-cookie assertions remain enabled. | Independent re-review PASS; finding resolved. |

## Test integrity

- Tests added/changed: `PreservedDocumentContentReaderTest`, `S3CompatibleArtifactStoreTest`, `OpportunityApiIntegrationTest`, `frontend/src/App.test.tsx`, and `frontend/e2e/offline-opportunity-flow.spec.ts`.
- Demonstrated test defects: earlier UI tests assumed selectors were permanently visible; the E2E setup expected a quote to survive a version change. Updates align tests with progressive disclosure and provenance rather than relaxing acceptance criteria.
- Acceptance coverage preserved: alternate document selection is tested without inheriting evidence, and a page-one evidence version must resolve before its citation can be used.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: DOCX is download-only; inline PDF depends on browser-native PDF support. Resolving evidence in very deep document-version history may require sequential page requests. The manual-demo CNPq deadline revision is demonstration data and remains untouched.
- Decisions made (link ADR/OpenSpec artifact) or none: document bytes are served only through the existing demo admin session; see openspec/changes/curation-document-viewer/design.md.
- OpenSpec tasks marked complete and evidence: 1.1, 1.2, 2.1, 2.2, 3.1 and 3.2 are complete after the final isolated E2E and independent review passed.
- Final verification after fixes: backend clean verify PASS (173 tests); frontend lint/typecheck/tests/build PASS (48 component tests); isolated Playwright PASS (3/3); Compose config, strict OpenSpec and diff check PASS.
- Final state rationale (PASS, RETRY, or ESCALATE): PASS. All acceptance criteria and required verification now have passing evidence; no tests were weakened. Commit/push and further changes remain unauthorized.
- Human approval still required before commit/push: yes
- Human approval still required before scope/architectural change: yes

Do not include secrets, tokens, credentials, or sensitive provider payloads in this record.
