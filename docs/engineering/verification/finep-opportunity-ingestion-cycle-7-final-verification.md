# Verification record: finep-opportunity-ingestion / cycle 7 final integrated verification

## Run identity

- Change and cycle: `finep-opportunity-ingestion`, final integrated verification (tasks 7.3, 7.6, 7.7, 7.8, 7.9)
- OpenSpec change/tasks in scope: tasks 7.3, 7.6, 7.7, 7.8 and 7.9 only
- Date/time (UTC): 2026-10-26
- Implementer context: primary Codex context
- Independent reviewer context/mechanism: separate read-only collaboration context; initial and final reviews completed
- Final state: `PASS`

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | Task 7.6; OpenSpec “Record provenance for critical facts” | Persisting a critical candidate without usable evidence never produces `VALIDATED`; the regression runs against PostgreSQL/Testcontainers and checks the persisted state. | PostgreSQL integration test with a deterministic candidate whose citation is absent or unusable |
| AC-02 | Task 7.9; PRD RF-12; OpenSpec “Attribute structured fields to their producing run” | Every field, including `MISSING`, has UTC `createdAt`, a valid `ingestionRunId`, `callVersionId`, and method. Identical reprocessing creates no duplicate field and preserves original IDs/timestamps/run/evidence attribution while the new run is audited. Changed reprocessing preserves the prior field/evidence history and attributes new records to the new run/version. | PostgreSQL/Testcontainers assertions for FK coverage, timestamps, prior/new record histories and repeated attribution |
| AC-03 | Task 7.3; OpenSpec browse/evidence requirements | With fixture-only input, deterministic extraction, real PostgreSQL persistence, S3-compatible artifact storage, Spring API and React UI, Playwright covers catalog → detail, fixture-backed `EXTRACTED` and `MISSING` states, and opening a persisted evidence quote/public-source link. No live Finep/OpenAI request is required. | Reproducible command/script, sanitized backend/frontend logs, Playwright results and persisted counts |
| AC-04 | Task 7.8; OpenSpec named fixture and environment-configured provider | Document offline setup separately from real Finep/OpenAI execution, including environment prerequisites, known limits, cost-reporting method, and explicit non-execution of paid/live calls in this run. Never record credential values. | New demo guide and this report; verify documented commands/options |
| AC-05 | Task 7.7; approved change/spec and user instruction | Strict OpenSpec validation and final behavior-vs-spec verification pass; only tasks fully proven by the checks are marked complete. | `openspec validate finep-opportunity-ingestion --strict`, refreshed task status and independent review |
| AC-06 | User constraints / AGENTS.md / verified loop | Keep changes within tasks 7.3/7.6/7.7/7.8/7.9; keep golden fixture, production API, schema and architecture unchanged; no paid calls, commit/push or E2E work beyond the specified flow. | Complete diff review, fixture hash comparison, status and command evidence |

## Plan and affected areas

- Planned vertical slice: strengthen fixture ingestion integration assertions for evidence and run attribution; add a test-only offline Spring application/profile using the frozen `FixtureSourceConnector` and `FixtureStructuredExtractionProvider`, real Flyway/PostgreSQL and the configured S3-compatible store; add Playwright configuration/test for the existing catalog-detail-evidence journey; add a reproducible offline demo script/guide and separately document real Finep/OpenAI prerequisites and cost reporting without invoking them.
- Affected files/modules: backend `src/test` integration/configuration only; frontend Playwright config/spec/package manifest and lockfile; `scripts/` offline demo runner if needed; `docs/engineering/` guide and this report; `tasks.md` only for criteria fully verified at the end.
- Known risks before implementation: Playwright and browser binaries are not present in the current workspace; external package/browser download may be unavailable. Compose credentials are development-only defaults and will not be copied into tracked artifacts. RustFS and PostgreSQL must be available for the full demo. The offline E2E uses the same public API/UI and persistence adapters but deliberately injects fixture connector/provider only in test source.
- Scope/architecture approval needed: none for test-only wiring and documentation. Stop and escalate if the demo requires production runtime changes, schema/API changes, fixture modification, a new execution state, or a material architecture change.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `openspec status --change finep-opportunity-ingestion --json` | pass | `spec-driven`; 39/44 tasks complete; exactly 7.3, 7.6–7.9 remain. |
| `openspec instructions apply --change finep-opportunity-ingestion --json` | pass | Read proposal, spec, design and tasks; only the five approved pending tasks are in scope. |
| `openspec validate finep-opportunity-ingestion --strict` | pass at start | Current change artifacts validate before implementation. |
| Context/skills and repository status reads | pass | AGENTS, project/PRD/MVP/domain/architecture, verified loop, code verification and OpenSpec apply instructions read; worktree clean at start. |
| `mvn -B -f backend/pom.xml -Dtest=FinepIngestionIntegrationTest test` | pass | PostgreSQL 17 Testcontainers; 8 tests, 0 failures/errors/skips. Includes no-critical-VALIDATED-without-evidence and all-field run/version/method/timestamp and history/idempotency regressions. |
| `npm run lint && npm run typecheck && npm test -- --run && npm run build` | pass | Oxlint and TypeScript succeeded; Vitest 1 file / 24 tests passed; Vite production build succeeded. `npm ci` audit reported 0 vulnerabilities. |
| `./scripts/run-offline-mvp-e2e.sh` | pass (final isolated run) | Unique ephemeral Compose project, clean PostgreSQL 17 migrations 1–3, RustFS health, real API and newly launched Vite/backend. Fixture ingestion `SUCCEEDED`, detected 1, changed 19 (one call snapshot + 18 document versions); Playwright Chromium 1/1 passed in 9.6s. The script removed its containers, network, named volumes, backend process, classpath/log and Playwright output directory at exit. Persisted counts and repeat idempotency were separately verified in the earlier PostgreSQL run and Testcontainers suite. A deliberate startup port collision returned nonzero and left no E2E container, network, volume, or capture log. |
| `docker compose config -q && git diff --check && openspec validate finep-opportunity-ingestion --strict` | pass | Compose schema/configuration, whitespace check and strict OpenSpec validation succeeded. |
| `mvn -B -f backend/pom.xml clean verify` | pass | Full clean build, 32 tests, 0 failures/errors/skips; Testcontainers migrations started from empty databases and passed. |
| Final frontend command set | pass | Final Oxlint/typecheck/Vitest (24/24)/build run passed. |

### Sanitized execution excerpts

```text
Flyway: Successfully applied 3 migrations to schema "public", now at version v3
OFFLINE_FIXTURE_INGEST status=SUCCEEDED detected=1 changed=19 runId=[redacted]
Playwright: 1 passed (9.6s), chromium
PostgreSQL counts: calls=1, call_versions=1, documents=18, document_versions=18,
  extracted_fields=21, evidence=1, ingestion_runs=3
Flyway history: versions 1, 2, 3 all successful
Cleanup: ephemeral Compose project, network, containers, volumes and temp files removed
Failure-path check: intentional port collision exited 1; no E2E resources remained
Finep/OpenAI live requests: not performed; cost: none
```

No Finep/OpenAI credentials, source payloads, or authorization material are recorded. OpenSpec exposes strict validation but no separate `verify` CLI operation in this repository version; the verify step was performed against the acceptance matrix and approved scenarios, then independently reviewed.

## Independent review

- Evidence supplied: approved OpenSpec requirements/tasks, acceptance matrix, complete diff, exact commands/results and sanitized logs.
- Reviewer had a separate context: yes, independent collaboration context.
- Reviewer was instructed not to modify implementation, tests, specs, or this report: yes; reviewer confirmed no files changed.
- Initial review found F7-R01: Playwright could reuse a pre-existing local Vite server outside CI, weakening proof that the fresh tested app/backend were used. Corrected `reuseExistingServer` to `false`; post-fix rerun passed.
- Focused review confirmed F7-R01 resolved. A later pre-commit review found F7-R02 concerning Playwright failure artifacts. The runner now directs artifacts to a unique temporary path and removes it in cleanup; final independent re-review confirmed closure and found no further issues. Reviewer confirmed no files were changed.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| F7-01 | Low; AC-03 | Vitest initially discovered the Playwright `.spec.ts` and failed during collection; component tests passed. | Attempt 1: exclude `e2e/**` from Vitest. Re-run: 24/24 component tests pass and E2E runs separately. | PASS. |
| F7-02 | Low; AC-03 | Playwright initially expected `VALIDATED`; PostgreSQL/API output showed `EXTRACTED`, TRL `MISSING`, and one evidence row for `program`. | Attempt 1: align assertions with fixture states and open the `program` quote/source link. Re-run: Chromium 1/1 passes. Fixture/spec unchanged. | PASS. |
| F7-03 | Low; AC-03 | Spring Boot Maven `run` failed to load test-only launcher class with `ClassNotFoundException`. | Attempt 1: use explicit test runtime classpath. Re-run: fixture ingestion/API/browser flow passes. | PASS. |
| F7-04 | Low; AC-03 | Chromium could not start because `libnspr4.so` and other browser libraries were missing. | Attempt 1: install documented Playwright Chromium dependencies. Re-run: Chromium 1/1 passes; environment-only change. | PASS. |
| F7-R01 | Medium; AC-03/AC-04 | Independent reviewer found server reuse could point tests at a stale Vite process/API target. | Attempt 1: set `reuseExistingServer: false`; post-fix rerun passed Chromium 1/1 against a fresh Vite process. Independent review confirmed closure. | PASS. |
| F7-R02 | Medium; AC-03/AC-04 | Pre-commit audit found the earlier E2E runner retained its fixed Compose project/volumes; independent review then found Playwright's default output could retain traces after failure. | Attempt 1: generated per-run Compose project, backend health guard, scoped project teardown and temp log/classpath cleanup. Attempt 2: route Playwright output to a unique `/tmp` directory and delete only that path in the same trap. Isolated E2E passed; deliberate port-collision failure exited 1 and left no E2E containers/network/volumes; final success left no Compose resources, backend process, log/classpath or Playwright directory. | PASS; independent final review confirmed closure. |

## Test integrity

- Tests added/changed: PostgreSQL regression for a critical candidate stripped of evidence not becoming `VALIDATED`; checks for required run/version/method/createdAt references and preservation of original field/evidence records through unchanged and changed fixture ingestions; Playwright catalog/detail/validation/evidence flow.
- Demonstrated test defect: initial E2E assertion expected a `VALIDATED` fixture field, which does not exist; corrected to the actual frozen fixture states based on persisted PostgreSQL rows.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: no tests removed or weakened; Vitest only excludes the separate Playwright directory; E2E now asserts the fixture's real states and evidence.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: live Finep/OpenAI run is intentionally not performed and may have cost/rate/portal variability; offline fixture remains deterministic but cannot prove current portal availability. The script uses local-only Compose placeholders and ephemeral named volumes that it removes on exit. E2E covers Chromium and a single user path, not a browser/mobile matrix.
- Decisions made (link ADR/OpenSpec artifact) or `none`: no architecture/schema/API decisions are approved or planned.
- OpenSpec tasks marked complete and evidence: 7.3 Chromium catalog/detail/evidence 1/1 after review fix; 7.6 PostgreSQL/Testcontainers critical-evidence regression; 7.7 final strict validation and behavior/spec review; 7.8 offline/live demo guide; 7.9 field provenance/idempotency/history integration checks.
- Final verification after fixes: backend clean verify (32 tests); frontend lint/typecheck/Vitest 24/24/build; Playwright Chromium 1/1; Compose config; OpenSpec strict validation and diff check pass.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): PASS. Independent review closed F7-R01 and F7-R02; required checks pass. No live/paid calls, production API/schema/fixture changes, commit or push.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.

Do not include secrets, tokens, credentials, or sensitive provider payloads in this record.
