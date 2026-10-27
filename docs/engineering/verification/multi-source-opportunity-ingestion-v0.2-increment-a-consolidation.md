# Verification record: multi-source-opportunity-ingestion / v0.2 increment A consolidation

## Run identity

- Change and cycle: `multi-source-opportunity-ingestion`, increment A consolidation
- OpenSpec tasks in scope: 1.1–1.3, 2.1–2.3, 3.1–3.5, 5.1–5.3, 6.1–6.2
- Date/time (UTC): 2026-10-27
- Implementer context: primary Codex context
- Independent reviewer context/mechanism: separate-context follow-up to `multisource_increment_a_review`; first review returned RETRY with finding `MS-C-01`, corrected within Increment A scope; final re-review PASS.
- Final state: `PASS`

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | User consolidation authorization | Staged paths contain only Increment A changes and reports; Finep golden fixture and v0.1.0 tag remain unchanged. | Reviewed staged path inventory; `git diff --quiet -- backend/src/test/resources/fixtures/finep/proinfra-2024-expansao-751`; tag resolves to original commit. |
| AC-02 | User pre-commit gate | Selective `git diff --cached --check` excludes only immutable source snapshots identified by the full check; every other staged path passes. The staged diff is reviewed before commit. | Selective check passed after excluding exactly `call.html`, `discovery.html`, and `notice.pdf`; all other staged paths passed. |
| AC-03 | User verification gate | Backend, frontend, Compose, OpenSpec and isolated offline Playwright checks pass. | Maven clean verify: 138 tests pass; frontend lint/typecheck/tests/build pass; Compose config and strict OpenSpec pass; offline Playwright 2/2 pass and isolated resources were removed. |

## Plan and affected areas

- Planned vertical slice: verify, stage, inspect, commit and push the approved offline multifonte catalog increment.
- Affected areas: additive source identity/persistence, CNPq connector and frozen fixture, API/frontend filters, offline E2E, OpenSpec and verification reports.
- Known risks before implementation: none newly identified for consolidation; public connector remains untested against the live portal.
- Scope/architecture approval needed: none. The path-only check exception is expressly approved by the user and does not change global Git whitespace behavior.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `mvn -B -f backend/pom.xml clean verify -DforkCount=0` | PASS, exit 0 | 137 tests passed; clean V1–V4 and V3→V4 Testcontainers migrations; jar packaged. |
| `npm run lint && npm run typecheck && npm test -- --run && npm run build` (`frontend/`) | PASS, exit 0 | 25 component tests; production build completed. |
| `docker compose config -q` | PASS, exit 0 | Local Compose configuration valid. |
| `openspec validate --all --strict` | PASS, exit 0 | 6 items passed, 0 failed. |
| `E2E_POSTGRES_PORT=55432 E2E_S3_PORT=59000 E2E_S3_CONSOLE_PORT=59001 E2E_BACKEND_PORT=58080 E2E_FRONTEND_PORT=54173 ./scripts/run-offline-mvp-e2e.sh` | PASS, exit 0 | Finep and CNPq fixture ingestion succeeded; Playwright 2/2. Isolated containers, network and volumes were removed. |
| `mvn -B -f backend/pom.xml -Dtest=CnpqSourceConnectorFixtureTest,FinepIngestionIntegrationTest test` | PASS, exit 0 | 22 tests passed, including a controlled login-HTML response for a PDF and safe `PARTIAL` run diagnosis. |
| `mvn -B -f backend/pom.xml clean verify -DforkCount=0` | PASS, exit 0 | 138 tests passed after the bounded connector/audit fix; Checkstyle 0 violations; clean V1–V4 and V3→V4 Testcontainers migrations; JAR packaged. |
| `E2E_POSTGRES_PORT=55432 E2E_S3_PORT=59000 E2E_S3_CONSOLE_PORT=59001 E2E_BACKEND_PORT=58080 E2E_FRONTEND_PORT=54173 ./scripts/run-offline-mvp-e2e.sh` (after correction) | PASS, exit 0 | Both source flows passed Playwright 2/2; isolated services and volumes removed. |
| `git diff --cached --check` | Reports whitespace in 3 exact paths | `backend/src/test/resources/fixtures/cnpq/chamada-30-2026/{call.html,discovery.html,notice.pdf}`. These are public source snapshots; their bytes remain unchanged. |
| `git diff --cached --check -- . ':(exclude)backend/src/test/resources/fixtures/cnpq/chamada-30-2026/call.html' ':(exclude)backend/src/test/resources/fixtures/cnpq/chamada-30-2026/discovery.html' ':(exclude)backend/src/test/resources/fixtures/cnpq/chamada-30-2026/notice.pdf'` | PASS, exit 0 | Excluded only the three exact paths reported by the full check. All other staged code, tests, configuration, docs and migrations passed. |
| Inline manifest verifier for CNPq fixture | PASS, 7/7 | File sizes and SHA-256 values match `manifest.json`; HTML uses `storedSha256`/`storedBytes`, other artifacts use `sha256`/`bytes`. |
| `git diff --quiet -- backend/src/test/resources/fixtures/finep/proinfra-2024-expansao-751` | PASS, exit 0 | Finep golden fixture unchanged. |

## Independent review

- Evidence supplied: implementation OpenSpec, staged path inventory and diff, full-check diagnostic path list, CNPq fixture manifest and hash results, selective whitespace check, test results, and correction diff.
- Reviewer had a separate context: yes.
- Reviewer was instructed not to modify implementation, tests, specs, or this report: yes.
- Worktree was checked before and after review; reviewer made no changes: yes; the reviewer confirmed no staging changes, unstaged changes or untracked files.
- Spec adherence, implementation quality, regressions, security, and the exact-path fixture exception: final separate-context re-review PASS; reviewer confirmed `MS-C-01` resolved and found no new findings.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| C-01 | Path-specific immutable-fixture whitespace exception, AC-02 | Full check reports trailing whitespace in exactly three captured CNPq artifacts; the seven-artifact fixture matches its manifest and all remaining staged paths pass the selective check. | Attempt 1: applied only the user-approved path exclusions; no artifact bytes or global Git whitespace policy changed. | PASS; reviewer independently confirmed the exact paths and selective check. |
| MS-C-01 | Medium; OpenSpec tasks 3.2–3.3 — unsupported/login document response | Independent reviewer found no test for a PDF URL returning login HTML and no useful run-level document failure diagnosis. | Attempt 1: CNPq connector now rejects unexpected media types with stable `UNSUPPORTED_CONTENT_TYPE`; failed documents add only sanitized source-document ID and failure code to the terminal `PARTIAL` run summary. Added controlled HTTP response regression plus Testcontainers audit assertion. | PASS; separate-context re-review confirmed behavior, regression and sanitized run attribution. |
| MS-C-02 | Low; verification test compilation | Initial assertion used a multi-argument matcher unsupported by this AssertJ version. | Attempt 1: replaced it with exact safe-message assertion, which excludes the mock login body and source URL. | PASS; targeted and full Maven verification pass. |

## Test integrity

- Tests added/changed: a controlled CNPq connector test for login HTML served at an expected PDF URL; existing Testcontainers partial-run test now asserts the sanitized `UNSUPPORTED_CONTENT_TYPE` audit diagnosis.
- Demonstrated test defect, if any: not applicable.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: implementation tests were rerun unchanged.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: live CNPq behavior remains unverified; deferred duplicate-candidate tasks remain open. The full check will continue to report source-preserved whitespace in the three named files. This report records the exact-path-only procedure for future immutable-fixture commits; code and all other paths remain under normal whitespace checks.
- Excluded snapshots and verified SHA-256: `call.html` `354c7972cf8e909e76052fa8d84ea6e3e88491ce524baae1fae1708177cc246c`; `discovery.html` `ab20ae108d7d892f2c58d8a16d409f80452921b2314dfa7751ce583aa9434fa9`; `notice.pdf` `f95afaf25669d08101223fecd2c189ac775c4ec9101a861fbacdc596444d3592`.
- Repeatable procedure for frozen evidence: run the unfiltered check and capture only its reported paths; verify every captured artifact's manifest hash and size; rerun `git diff --cached --check` excluding only those exact paths; do not exclude directories, code, tests, docs, migrations, or configuration. Any newly reported path requires separate hash/provenance verification and review.
- Decisions made: none.
- OpenSpec tasks marked complete and evidence: 1.1–1.3, 2.1–2.3, 3.1–3.5, 5.1–5.3, 6.1–6.2, as recorded in `tasks.md`; 4.1–4.2 and 6.3 remain open.
- Final verification after fixes: targeted tests and full Maven verify (138 tests) passed; offline E2E 2/2; frontend, Compose, OpenSpec, selective whitespace check and manifest integrity passed. Independent re-review PASS.
- Final state rationale: `PASS`; all gates passed using the expressly approved three-path snapshot exception. Conditional commit/push authorization is granted; CI is pending push.
- Human approval still required before commit/push: no; this consolidation is expressly authorized subject to the passing gates.
- Human approval still required before scope/architectural change: yes.
