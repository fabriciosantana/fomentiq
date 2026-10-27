# Verification record: multi-source-opportunity-ingestion / v0.2-increment-a-implementation

## Run identity

- Change and cycle: `multi-source-opportunity-ingestion` / Fomentiq v0.2.0 Increment A
- OpenSpec change/tasks in scope: tasks 1.1–3.5, 5.1–6.2; tasks 4.1–4.2 explicitly deferred; 6.3 live smoke test is not authorized
- Date/time (UTC): 2026-10-27
- Implementer context: primary Codex context
- Independent reviewer context/mechanism: `/root/multisource_increment_a_review`, separate read-only context
- Final state: `PASS`

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | Tasks 1.1–1.3; D-022 | Publisher, source-native reference, opportunity and multiple responsible agencies are separate; additive Flyway migration backfills existing Finep data and source identity is publisher-scoped | PostgreSQL/Testcontainers clean and V3-upgrade migration tests pass; Finep `751/751092` retains one UUID/reference; synthetic same-ID/same-agency publishers remain separate |
| AC-02 | Tasks 2.1–2.3 | Finep and CNPq run through the same explicit connector orchestration, independent `IngestionRun` lifecycle and per-call transaction; offline tests show source attribution and no collisions | 19 `FinepIngestionIntegrationTest` tests pass, including Finep+CNPq same database, audit export, replay and synthetic cross-publisher isolation |
| AC-03 | Tasks 3.1–3.5 | Public CNPq/CAPES 30/2026 fixture is reviewed and hashed; bounded URL/download behavior is tested; deterministic extraction preserves source/document/version/run provenance | Fixture hash test; CNPq parser/URL/body subscriber tests; PostgreSQL integration asserts 5 CNPq docs, snapshots, extraction evidence, audit references, idempotent replay and a synthetic changed-call variant |
| AC-04 | Tasks 5.1–5.3 | Existing bounded API and UI support independent and combined publisher/agency filters; legacy fields remain; both sources retain document/evidence access | `OpportunityApiIntegrationTest` 7 tests; frontend 25 tests; Playwright source filter → detail → documents/evidence on both sources |
| AC-05 | Tasks 6.1–6.2 | Backend, frontend, migration, Compose, offline Playwright and OpenSpec checks pass; independent review is PASS | Backend `clean verify` 137/137; frontend checks pass; Playwright 2/2; Compose config and OpenSpec strict pass; separate-context review PASS |
| AC-06 | D-023 | Duplicate candidate detection/UI is deferred, source identities remain distinct and no automatic fusion occurs | Tasks 4.1–4.2 remain unchecked; persistence test verifies same ID and same agency under different publishers remains separate |
| AC-07 | User constraints | Finep fixture and conservative factual validation remain unchanged; no OpenAI/Finep production/CNPq restricted access, commit or push | `git diff --quiet` confirms Finep fixture unchanged; all automated integration paths use fixture providers; no external provider/source execution; no commit/push |

## Plan and affected areas

- Vertical slice: freeze one official public CNPq fixture; add publisher/reference/agency migration and persistence; generalize connector selection; implement the narrow CNPq adapter; expose API/OpenAPI and UI filters; demonstrate both sources offline with Playwright.
- Affected areas: `backend/src/main/java/com/fomentiq/{funding,ingestion,search,api,audit}`, Flyway migrations, backend tests and fixtures, `frontend/src`, `frontend/e2e`, OpenAPI, `openspec/changes/multi-source-opportunity-ingestion`, `DECISIONS.md`, demo instructions, and this report.
- Known risks: CNPq public page shape may change; the pilot is ICT-led and not company-only; CNPq is primary only for compatibility and CAPES is associated as evidenced in the notice; storage writes and SQL transactions are not distributed; object-store orphans remain a manual cleanup risk.
- Approved architectural scope: D-022/D-023 record the approved identity model and deferred duplicate candidates. The V4 migration removes obsolete `(agency_id, external_id)` uniqueness because the approved native identity is `(publisher_id, external_id)`; it preserves all rows, call UUIDs, legacy `agency_id`, and API fields.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `mvn -B -f backend/pom.xml -Dtest=CnpqFixtureSourceConnectorTest,CnpqSourceConnectorFixtureTest,CnpqUrlPolicyTest,CnpqLimitedBodySubscriberTest test` | PASS, exit 0 | 7 tests passed; fixture/parser/URL/size subscriber; no external source requests |
| `mvn -B -f backend/pom.xml clean verify -DforkCount=0` | PASS, exit 0 | 137 tests passed; Checkstyle 0 violations; Flyway V1–V4 clean migration and Testcontainers V3→V4 upgrade; JAR packaged. `-DforkCount=0` avoids this environment terminating the long Surefire child with SIGTERM. |
| `npm run lint && npm run typecheck && npm test -- --run && npm run build` (from `frontend/`) | PASS, exit 0 | Oxlint/typecheck pass; 25 component tests pass; Vite production build pass |
| `E2E_POSTGRES_PORT=55432 E2E_S3_PORT=59000 E2E_S3_CONSOLE_PORT=59001 E2E_BACKEND_PORT=58080 E2E_FRONTEND_PORT=54173 ./scripts/run-offline-mvp-e2e.sh` | PASS, exit 0 | Both Playwright tests pass; Finep and CNPq each ingested successfully into isolated PostgreSQL with deterministic provider; script reports cleanup of its backend, unique Compose project/network/volumes, temp files and Playwright output |
| Earlier `./scripts/run-offline-mvp-e2e.sh` attempts with default/alternate ports | RETRY, corrected | First test revision assumed documents were initially expanded and used stale title/section selection. Opened the existing history section in Playwright and verified the rendered document list; final run passes. The first retry was refused because prior run left only TIME_WAIT sockets on the default ports; no listener/process remained and no service was stopped. |
| `docker compose config -q` | PASS, exit 0 | Compose model resolves |
| `openspec validate multi-source-opportunity-ingestion --strict` | PASS, exit 0 | Change valid |
| `openspec validate --all --strict` | PASS, exit 0 | 6 items passed, 0 failed |
| `git diff --check` | PASS, exit 0 | No whitespace errors |
| `git diff --quiet -- backend/src/test/resources/fixtures/finep/proinfra-2024-expansao-751` | PASS, exit 0 | Golden Finep fixture unchanged |
| Read-only high-confidence credential-pattern scan over changed and untracked paths | PASS | No key/JWT/Bearer/AWS-secret pattern found; binary fixture artifacts were skipped |
| `docker ps -a --filter label=com.docker.compose.project=fomentiq-cycle7-e2e-85945-32529` and matching `docker volume ls` filter | PASS, exit 0 | No E2E containers or volumes remain after successful script cleanup |
| `sha256sum backend/src/test/resources/fixtures/finep/proinfra-2024-expansao-751/*` | RETRY, command shape only | Printed regular-file hashes but returned 1 because the fixture directory also contains `pdf/` and `synthetic/`; no file was modified. Corrected verification is the clean `git diff --quiet` above. |

## Independent review

- Evidence supplied: approved OpenSpec proposal/design/spec/tasks, complete unstaged diff and untracked-file inventory, this report, and exact final verification results.
- Reviewer had a separate context: yes, `/root/multisource_increment_a_review`.
- Reviewer was instructed not to modify implementation, tests, specifications, fixture or verification records: yes; reviewer confirmed no modifications.
- Worktree checked before/after review: yes; reviewer made no changes.
- Review scope: spec adherence; migration compatibility and publisher-scoped identity; transaction/audit/evidence links; CNPq URL/download bounds and fixture provenance; API filters/OpenAPI; frontend accessibility/behavior; regression and security risks.
- Result: `PASS`; no blocking findings. Reviewer confirmed publisher-scoped identity/migration, source and agency separation, provenance and audit links, fixture hashes and scope, API filters, and that deferred duplicate tasks and unauthorized live smoke remain incomplete.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| MS-01 | High; AC-01/AC-06 — cross-publisher identity | Initial persistence fallback queried `(agency, externalId)` when no publisher reference existed; V4 legacy unique constraint also rejected same ID and agency across publishers | Attempt 1 removed fallback and added Testcontainers regression; attempt 2 removed the obsolete legacy uniqueness constraint in V4 and JPA metadata, documented the rationale, and extended migration test to two publishers with same agency/ID | PASS; independent reviewer confirmed publisher-scoped uniqueness and no merge |
| MS-02 | Medium; AC-02 — connector wiring | Spring test context had two primary `SourceConnector` beans after adding CNPq | Attempt 1 marked Finep primary but collided with test fixture primary; attempt 2 removed Finep primary and marked CNPq `@Fallback`, preserving fixture replacement and Finep default | PASS; full Spring integration contexts pass |
| MS-03 | Low; AC-01 — upgrade test binding | PostgreSQL JDBC could not infer `java.time.Instant` parameter type in V3 upgrade fixture | Attempt 1 replaced test-only timestamp parameters with `java.sql.Timestamp`; migration upgrade now passes | PASS; V3→V4 Testcontainers upgrade passes |
| MS-04 | Low; AC-07 — live-manifest test portability | Historical-manifest test assumed a single Maven working directory | Attempt 1 made the repository-relative test path resolve from root or backend module; loader/policy regression passes | PASS; backend suite passes from repository root |
| MS-05 | Low; AC-04/AC-05 — Playwright interaction | CNPq history/document section is collapsed by default; initial E2E queried it without opening, and its first selector used stale copy | Attempt 1 aligned the selector to current rendered section; attempt 2 opened the existing accessible section and asserted a rendered document title; E2E 2/2 passes | PASS; independent reviewer confirmed behavior matches existing UI |

No finding was split or reworded to reset an attempt counter. None of the fixes weaken approved acceptance criteria.

## Test integrity

- Tests added/changed: CNPq fixture hashes and source parsing; CNPq URL/redirect and bounded subscriber tests; Postgres same-database, API filter, publisher collision, replay, synthetic changed-call version, document/evidence provenance and audit export; V3 upgrade/backfill; frontend compositional filter; Playwright CNPq flow.
- Demonstrated test defects: Playwright originally did not open the existing collapsed history section; the upgrade test passed `Instant` to PostgreSQL JDBC without a supported SQL type; historical manifest test path assumed one working directory.
- Acceptance criteria remain intact: tests now model actual UI interaction and supported JDBC semantics; the changed CNPq variant is explicitly synthetic (`RECONSTRUCTED`) and does not alter the frozen fixture.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: the official CNPq page may change; DOCX originals are stored/versioned but current deterministic text extraction consumes PDF only; SQL rollback cannot roll back S3-compatible writes, so failed runs may leave orphan objects for manual cleanup; candidate duplicate detection/UI remains intentionally deferred.
- Decisions: publisher `CNPQ`, source-native ID `30/2026`; CNPq is primary agency and CAPES additional for the selected joint call; preserve Finep `751/751092`; no automatic merge; source-specific identity is the only domain key; remove agency/external-id uniqueness because it does not represent source identity.
- OpenSpec tasks marked complete: 1.1–1.3, 2.1–2.3, 3.1–3.5, 5.1–5.3, and 6.1–6.2. Tasks 4.1–4.2 remain explicitly deferred and unchecked; 6.3 remains unauthorized and unchecked.
- Final verification after fixes: backend, frontend, Playwright, migration, Compose and strict OpenSpec all pass as recorded above.
- Final state rationale: `PASS`; all offline acceptance criteria in this incremental scope are covered by passing checks and independent review. Live connector smoke is not part of this PASS and remains unauthorized.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.

No OpenAI calls, Finep production ingestion, CNPq live connector run, restricted portal access, commit or push were performed.
