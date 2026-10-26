# Verification record: finep-opportunity-ingestion / F-03 transaction boundaries

## Run identity

- Change and cycle: `finep-opportunity-ingestion`, stabilization of finding F-03
- OpenSpec change/tasks in scope: existing ingestion run, artifact preservation, field attribution, version/idempotency requirements; integration evidence for tasks 7.4 and 7.5. Those checkboxes are updated only after the full test and independent review.
- Date/time (UTC): 2026-10-26
- Implementer context: primary Codex context
- Independent reviewer context/mechanism: separate `/root/f03_transaction_review` collaboration context, read-only
- Final state: `PASS`

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | User-approved F-03 architecture; OpenSpec “Record ingestion execution” | `IngestionRun` start and finish commit independently of the business transaction; after a recoverable SQL failure rolls back the business transaction, a durable `FAILED` run with finish time and safe error summary remains. | Testcontainers trigger failure and repository assertions after rollback |
| AC-02 | User-approved F-03 architecture; OpenSpec source preservation/versioning | Each discovered call is processed in one explicit transaction. SQL/persistence errors leave that transaction immediately and are not followed by more SQL in the aborted transaction. | Transactional per-call Spring bean; controlled SQL trigger test verifies rollback before lifecycle finish |
| AC-03 | OpenSpec ingestion status and user retry requirement | Recoverable document failure is `PARTIAL`; extraction failure is `FAILED` with an explicit extraction execution record; both can be retried safely. | Integration tests for document failure, extraction failure and retries |
| AC-04 | OpenSpec idempotency/versioning; tasks 7.4 and 7.5 | Identical fixture replay creates no duplicate versions; synthetic changed fixture creates one new call version and keeps the prior version. | `FinepIngestionIntegrationTest.repeatedFixtureIsIdempotentAndChangedSyntheticCallPreservesPriorVersion` |
| AC-05 | PRD RF-12; OpenSpec run attribution | `createdAt`, `ingestionRunId`, `callVersionId` and evidence relationships remain valid after successful and repeated runs; failed business units leave no relational artifacts. | Integration assertions query field/run FK, evidence/document/call-version joins, and absence of rows following SQL rollback |
| AC-06 | User-approved architectural decision | No new status, schema, public contract, fixture, service infrastructure, queue, or automatic S3 cleanup; document the approved transaction boundary and PostgreSQL/S3-compatible consistency limit. | Diff review of Flyway/OpenAPI/fixture status; DECISIONS.md and ARCHITECTURE.md |

## Plan and affected areas

- Planned vertical slice: add an independent run-lifecycle component (`REQUIRES_NEW` start/finish); move one call's business persistence into a separate `REQUIRES_NEW` Spring bean; keep the existing orchestration outside transactions; catch per-document operational failures as `PARTIAL`, but let SQL failures escape the per-call proxy before finishing the run; preserve explicit extraction failure audit while marking the run `FAILED`.
- Affected files/modules: ingestion application and JPA persistence boundaries; PostgreSQL/Testcontainers ingestion integration tests; `DECISIONS.md`; ingestion and storage transaction section in `ARCHITECTURE.md`; this report. Existing unrelated F-01/F-02 and task 6.6 worktree changes will be preserved.
- Known risks: PostgreSQL and S3-compatible object storage do not share a transaction. Objects successfully uploaded before SQL rollback may remain orphaned. Content-addressed keys allow exact content to be reused on retry; automatic deletion is explicitly out of scope.
- Scope/architecture approval needed: approved by the user in the current request. Stop if new execution states, schema changes, or changed consistency rules appear necessary.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `openspec status --change finep-opportunity-ingestion --json` | pass | Change uses `spec-driven`; 37/44 tasks complete at start. Tasks 7.3–7.9 remain pending except 7.1/7.2 already complete. |
| `openspec instructions apply --change finep-opportunity-ingestion --json` | pass | Read full proposal, spec, design and task list; approved requirements include audit status, source preservation and run attribution. |
| `git status --short` | pass | Existing F-01/F-02 and task 6.6 worktree changes identified; they are not part of F-03 implementation except the previously added test hunks remain preserved. |
| `mvn -B -f backend/pom.xml -Dtest=FinepIngestionIntegrationTest test` (first attempt) | fail, corrected | Checkstyle found an unused import; after removal, Spring found ambiguous lifecycle constructor selection. The production constructor was marked `@Autowired`. |
| `mvn -B -f backend/pom.xml -Dtest=FinepIngestionIntegrationTest test` (second attempt) | fail, corrected | Rollback/retry test showed call-scoped storage paths could not reuse an orphan after the call row rolled back. Storage keys were changed to `content/{sha256}.{extension}` and fixture assertions now verify those keys against bytes. |
| `mvn -B -f backend/pom.xml clean verify` (first full run) | fail, corrected | One legacy assertion still selected keys under `/documents/`; updated to assert the new content-addressed PDF keys and exact SHA-256. All other tests passed. |
| `mvn -B -f backend/pom.xml clean verify` (final) | pass | Clean build; Testcontainers with PostgreSQL 17; Flyway applied all three migrations to empty schemas; 31 tests, 0 failures/errors; jar packaged. |
| `git diff --check` | pass | No whitespace errors. |
| `docker compose config -q` | pass | Local Compose configuration is valid. |
| `openspec validate finep-opportunity-ingestion --strict` | pass | Change is valid. |

## Independent review

- Evidence supplied: approved F-03 decision, OpenSpec context, acceptance matrix, complete F-03 diff and final test/build evidence.
- Reviewer had a separate context: yes, `/root/f03_transaction_review`.
- Reviewer was instructed not to modify implementation, tests, specifications, or this report: yes.
- Worktree was checked before and after review; reviewer made no changes: yes.
- Spec adherence, transaction behavior, consistency, retry, provenance and residual S3 risk reviewed: PASS; no actionable findings.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| F-03 | Confirmed; high impact / low likelihood; SQL failure previously removed the run audit and could leave an unreferenced S3 object. AC-01–AC-06 | Prior controlled Testcontainers reproduction: source-snapshot trigger aborted the encompassing transaction, `finishRun` then hit SQLSTATE 25P02, relational data/run rolled back and the already-uploaded object remained. | Attempt 1: split orchestration, run lifecycle, and per-call transaction; controlled test then exposed Spring constructor selection, fixed with `@Autowired`. Attempt 2: make storage keys globally content-addressed so retries reuse objects even when the relational call ID rolled back; update the legacy key assertion accordingly. | Independent review PASS; final clean verify passes. |

## Test integrity

- Tests added/changed: remove test-class-wide transaction so committed audit state can be observed; truncate the database explicitly between integration tests; add PostgreSQL trigger failure/rollback and retry test; assert no inconsistent relational rows, stored-object reuse, every field's `createdAt`/run FK, valid evidence/document/call-version relations, extraction failure audit/retry, and unchanged attribution on identical reprocessing.
- Demonstrated test defect, if any: none identified
- How approved acceptance criteria and equivalent/stronger coverage were preserved: assertions were strengthened around independently committed run status, full call-unit rollback, content-addressed storage retry, evidence/document/call-version relationships, and extraction attribution. Existing same-fixture and synthetic-change assertions remain enabled.
- Any test weakening/removal: no

## Risks, decisions, and final verification

- Residual risks/debts: S3-compatible writes cannot be rolled back together with PostgreSQL; an unreferenced content-addressed object can remain if no retry reuses it. No orphan cleanup is authorized. Since the unit is per call, earlier calls may already be committed when a later call makes the overall run `FAILED`; the run metrics/status must be read with that boundary in mind. If PostgreSQL is unavailable when the run starts or finishes, that database outage prevents audit persistence.
- Decisions made: user-approved transaction split recorded as [D-019 in DECISIONS.md](../../../DECISIONS.md); transaction and S3 boundary documented in [ARCHITECTURE.md](../../../ARCHITECTURE.md).
- OpenSpec tasks marked complete and evidence: 7.4 and 7.5 only; integration tests prove identical replay has no duplicate version and synthetic change creates a new version while retaining the prior one. No E2E task was started.
- Final verification after fixes: `mvn -B -f backend/pom.xml clean verify` pass (31 tests); `git diff --check` pass; `docker compose config -q` pass; `openspec validate finep-opportunity-ingestion --strict` pass. No frontend code changed in F-03, so frontend checks were not applicable.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `PASS`; all approved criteria have passing evidence and independent review found no actionable issues.
- Human approval still required before commit/push: yes
- Human approval still required before scope/architectural change: no for the user-approved split; yes for anything beyond it

Do not include secrets, tokens, credentials, or sensitive provider payloads in this record.
