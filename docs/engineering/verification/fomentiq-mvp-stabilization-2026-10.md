# Verification record: Fomentiq MVP stabilization / 2026-10

## Run identity

- Change and cycle: Fomentiq MVP stabilization, audit findings F-01–F-03
- OpenSpec change/tasks in scope: `finep-opportunity-ingestion`; existing requirements for public read API, ingestion status and source preservation. Task 7.1 was checked after the complete backend verification suite passed; no other checkbox was changed by this stabilization run.
- Date/time (UTC): 2026-10-26
- Implementer context: primary Codex context
- Independent reviewer context/mechanism: separate collaboration reviewer context `/root/stabilization_review`, two review rounds; reviewer made no edits
- Final state: `ESCALATE`

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | User audit finding F-01; read-only API / source-link requirements | Public API URLs remove sensitive query parameter variants including `key` and `access_key`; local/private destinations are suppressed consistently with frontend validation. | Sanitizer unit and API integration regression tests |
| AC-02 | User audit finding F-02; opportunity list/detail read behavior | Invalid persisted date candidates cannot cause list, detail, or deadline-filter requests to fail; invalid dates are treated as unavailable. | PostgreSQL/Testcontainers regression test |
| AC-03 | User audit finding F-03; OpenSpec “Record ingestion execution” and “Preserve source artifacts” | Storage, extraction, and persistence failure behavior is reproduced and assessed for run status durability, transaction outcome, preserved artifacts, and recovery by retry. Only compatible, evidenced corrections are applied. | Ingestion/Testcontainers regression tests and transaction-path review |
| AC-04 | User instruction | README staleness is reviewed and a documentation correction proposal is presented without unrelated functional scope. | README content/file inventory review |
| AC-05 | User instruction / repository workflow | Backend, frontend, diff, and strict OpenSpec checks applicable to changes are run; independent reviewer inspects diff and evidence without editing. | Exact command results and separate-context review |

## Plan and affected areas

- Planned vertical slice: reproduce the three audit findings, apply only confirmed in-scope bug fixes, add regression tests, record residual transaction/storage limitations, review README freshness, then obtain independent review.
- Affected files/modules: API URL sanitization and API tests; JDBC opportunity read adapter and PostgreSQL integration test; ingestion service and integration test only if a correction is demonstrated to be safe; this report.
- Pre-existing worktree changes at run start belong to the previously authorized task 6.6 (`frontend/src/OpportunityDetailPage.*`, `frontend/src/App.test.tsx`, OpenSpec task 6.6, and its report); these will be preserved and excluded from this stabilization diff/reviewer scope.
- Known risks before implementation: object storage cannot participate in PostgreSQL transactions; changing transaction propagation may be an architectural decision and must not be made silently.
- Scope/architecture approval needed: none for narrow sanitizer/date handling; stop for human direction if F-03 requires a material transaction-boundary redesign.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `openspec status --change finep-opportunity-ingestion --json` | pass | Before this run's 7.1 completion, 36/44 tasks were complete; final progress is 37/44. Task 6.6 was already complete in the pre-existing worktree; 7.3–7.9 remain incomplete. |
| `openspec instructions apply --change finep-opportunity-ingestion --json` | pass | Confirmed current task progress and task descriptions; no task checkbox changed for this stabilization. |
| Regression test before F-01/F-02 fixes: `mvn -B -f backend/pom.xml -Dtest=PublicUrlSanitizerTest,OpportunityApiIntegrationTest test` | expected failure | Reproduced `key`/`access_key` in public detail and document URLs; private URL was not suppressed; `2026-99-99` made list request return HTTP 500. |
| Temporary SQL failure reproduction: `mvn -B -f backend/pom.xml -Dtest=FinepIngestionIntegrationTest#temporaryReproductionDatabaseWriteFailureRollsBackRunButLeavesAlreadyWrittenObject test` | pass (reproduction) | Controlled PostgreSQL trigger caused an aborted transaction; `finishRun` then hit SQLSTATE 25P02; relational run/call rolled back while one content-addressed object remained. Temporary reproduction test was removed so the suite does not codify the failure as desired behavior. |
| Focused backend checks: `mvn -B -f backend/pom.xml -Dtest=PublicUrlSanitizerTest,OpportunityApiIntegrationTest,FinepIngestionIntegrationTest test` | pass | 15 tests passed after corrections, including public API URL, invalid date, extraction/storage failure and retry behavior. |
| `mvn -B -f backend/pom.xml verify` | pass | Full backend suite: 30 tests passed, migrations applied to clean Testcontainers PostgreSQL 17, package built. |
| `mvn -B -f backend/pom.xml clean verify` | pass | Final clean build and suite: 30 tests passed; migrations applied from empty Testcontainers PostgreSQL 17 database; package built. Confirms the temporary SQL reproduction test is absent from the suite. |
| `npm run lint && npm run typecheck && npm test -- --run && npm run build` (`frontend/`) | pass | Final run: lint and typecheck passed; 24 frontend tests passed, including octal/hexadecimal local URL cases; production build completed. Existing task 6.6 work remains in the worktree. |
| `docker compose config -q` | pass | Compose configuration valid. |
| `git diff --check` | pass | No whitespace errors; final rerun after review pending. |
| `openspec validate finep-opportunity-ingestion --strict` | pass | Change validates; no tasks were changed by stabilization. |

## Independent review

- Evidence supplied: approved source requirements, this acceptance matrix, complete stabilization diff, and final test/build/check results.
- Reviewer had a separate context: yes (`/root/stabilization_review`)
- Reviewer was instructed not to modify implementation, tests, specifications, or this report: yes
- Worktree was checked before and after review; reviewer made no changes: yes; the reviewer inspected stabilization hunks plus frontend guard/tests, excluding the unrelated pre-existing task 6.6 UI changes
- Spec adherence, implementation quality, regressions, and security reviewed: yes; F-01 was returned RETRY once for legacy IPv4 bypass then re-reviewed PASS; F-02 PASS; F-03 ESCALATE remains open

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| F-01 | Confirmed; high impact / medium likelihood; public URL credential disclosure and local/private navigation. AC-01 | Regression failed before fix: API exposed `key`/`access_key` and accepted private canonical hosts. Reviewer found `0177.0.0.1` could pass backend validation while a browser treats it as loopback. | Attempt 1: filter normalized key aliases and reject private/reserved IP literals/internal suffixes. Attempt 2: reject noncanonical IPv4 spellings (octal/hex/integer and leading-zero components); add unit, API and component regressions. | Review round 1: RETRY; round 2: PASS. Low residual risk: backend strips sensitive query parameters but frontend rejects raw URLs containing them; no leak in the API→UI flow, but policy logic is duplicated. Neither guard resolves public DNS names that point to private addresses. |
| F-02 | Confirmed; medium impact / low likelihood; invalid stored deadline makes public list/detail/filter request 500. AC-02 | Regression failed before fix for persisted `2026-99-99T00:00:00Z`; PostgreSQL raised date out-of-range on `::date`. | Attempt 1: guard casts with PostgreSQL `pg_input_is_valid` in list, detail and deadline filters; malformed values now map to no date. | PASS; API integration regression passes against PostgreSQL 17. |
| F-03 | Partially confirmed; medium impact / low likelihood; database persistence failure defeats run audit and can orphan stored object. AC-03 | Controlled trigger on `source_snapshot` produced SQLSTATE 25P02 when `finishRun` ran in the aborted `@Transactional` transaction; transaction rolled back `IngestionRun` and relational changes but left one content-addressed object. Storage and provider failures instead persist FAILED/PARTIAL; artifacts are preserved and same-fixture retry succeeds without duplicate call/version. | Attempt 0: no transaction-boundary change made. A separate transaction for run lifecycle, or another isolation mechanism, is required to durably record terminal status after SQL transaction abort; this is a material architectural decision. | ESCALATE; independent reviewer agrees; human approval needed for remediation design |

## Test integrity

- Tests added/changed: `PublicUrlSanitizerTest`, `OpportunityApiIntegrationTest`, `FinepIngestionIntegrationTest`, and two URL cases in `frontend/src/App.test.tsx` alongside pre-existing task 6.6 edits.
- Demonstrated test defect, if any: first post-fix deadline-filter assertion incorrectly expected an empty catalog, despite a second unrelated opportunity having a valid database deadline. It was narrowed to assert that the invalid-deadline opportunity is absent; this preserves the acceptance criterion.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: before/after API integration tests exercise URL projection and invalid-date behavior; component tests cover frontend rejection of legacy local URLs; ingestion integration tests cover persisted failure status, artifacts and successful idempotent retry. Temporary SQL-trigger test was used only for reproduction and removed rather than retaining assertions that bless lost audit state.
- Any test weakening/removal: no

## README review

`README.md` still describes itself as a bootstrap pack and tells contributors to install/initialize OpenSpec even though this repository already has an OpenSpec root and active change. Its repo-local skill list omits the verified implementation loop and official OpenSpec skills, and the “first objective” reads as a future goal although most of that vertical slice is already implemented. The referenced reading-order files exist, so that list itself is not stale.

Proposed documentation-only correction (not applied in this run): retitle README as the current project guide; replace initialization instructions with use of the existing OpenSpec CLI/root; list or link the current skill catalog; describe the current MVP state and point to `MVP_SCOPE.md`, `openspec status`, and local run/test instructions for current onboarding.

## Risks, decisions, and final verification

- Residual risks/debts: F-03 SQL persistence failure can leave an orphaned content-addressed object and no durable terminal `IngestionRun`. Failure of PostgreSQL itself cannot guarantee audit persistence. URL screening is syntactic and blocks private literals/known local suffixes; it does not resolve public DNS names to detect private DNS answers. OFFSET pagination debt remains documented elsewhere.
- Decisions made (link ADR/OpenSpec artifact) or `none`: none incorporated. A transaction-isolated run lifecycle is proposed only and awaits human approval.
- OpenSpec tasks marked complete and evidence: 7.1 only; `mvn -B -f backend/pom.xml clean verify` passed all 30 unit/integration tests; all other task statuses were preserved.
- Final verification after fixes: clean Maven verify (30 tests), frontend lint/typecheck/tests (24)/build, Compose config, diff check and strict OpenSpec validation pass. Independent review: F-01/F-02 PASS, F-03 ESCALATE.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `ESCALATE` until a human approves or explicitly accepts the F-03 transaction-audit limitation; do not silently change transaction architecture.
- Human approval still required before commit/push: yes
- Human approval still required before scope/architectural change: yes

Do not include secrets, tokens, credentials, or sensitive provider payloads in this record.
