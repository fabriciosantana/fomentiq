# Verification record: manual-opportunity-localdate-serialization

## Run identity

- Change and cycle: `manual-opportunity-localdate-serialization` / bugfix
- OpenSpec change/tasks in scope: tasks 1.1–1.3
- Date/time (UTC): 2026-10-29
- Implementer context: `/root`
- Independent reviewer context/mechanism: `/root/curation_increment_b_review` (separate collaboration context, read-only)
- Final state: `PASS`

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | OpenSpec task 1.1; save opportunity with deadline | Create opportunity with `LocalDate`, persist to PostgreSQL, reload same value, and update deadline successfully | Testcontainers regression `persistsIsoDeadlineAndReloadsUpdatesNullAndCustomSections` |
| AC-02 | OpenSpec task 1.1; JSONB representation | Persist deadline as JSON string in ISO-8601 format, not an array/timestamp | `jsonb_typeof(editable_content->'deadline') = 'string'` and raw JSON contains quoted ISO value |
| AC-03 | OpenSpec task 1.1; nullable and custom content | Null deadline remains JSON null; ordered custom sections/fields round-trip unchanged alongside the date | Testcontainers regression assertions |
| AC-04 | OpenSpec task 1.2; Jackson compatibility | Use Jackson 2 `JavaTimeModule` on a local mapper copy, with string date output; verify the same persisted-content path deserializes | Adapter change and PostgreSQL integration regression |
| AC-05 | User authorization; scope preservation | No schema, metamodel, contract, fixture, frontend behavior, or external-call changes | Diff review; no migration or fixture changes |

## Plan and affected areas

- Planned vertical slice: reproduce `LocalDate` failure in the PostgreSQL persistence test, configure only the JDBC adapter's Jackson 2 mapper copy, then run backend, frontend/E2E and OpenSpec checks.
- Affected files/modules: `JdbcManualOpportunityStore`, its PostgreSQL/Testcontainers regression test, new OpenSpec change, and verification report. Frontend E2E test adds a date save/reload assertion; no frontend application code changes.
- Known risks before implementation: Jackson 2 and Jackson 3 coexist in the dependency graph; changing the global mapper could alter unrelated serialization. Mitigation: configure only a copied Jackson 2 mapper in the affected adapter.
- Scope/architecture approval needed: none; no schema or metamodel change.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `openspec validate manual-opportunity-localdate-serialization --strict` | PASS after correcting delta operation to `ADDED` | Initial validation warned that `MODIFIED` had no main spec target; the operation was changed to `ADDED` before implementation. |
| `mvn -B -f backend/pom.xml -Dtest=ManualEditorialPersistenceIntegrationTest#persistsIsoDeadlineAndReloadsUpdatesNullAndCustomSections test` (before mapper fix) | Expected failure | Reproduced the reported `InvalidDataAccessApiUsageException` / Jackson `InvalidDefinitionException` for `Content.deadline`. |
| Same targeted Maven test (after mapper fix) | PASS, exit 0; 1 test | Testcontainers PostgreSQL verified create/reload, update, null, ISO string JSONB type, and section/field round-trip. |
| `mvn -B -f backend/pom.xml clean verify` | PASS, exit 0; 182 tests | Full backend clean verify and Testcontainers suite passed. |
| `npm run lint && npm run typecheck && npm test -- --run && npm run build` (from `frontend/`) | PASS, exit 0 | Lint, typecheck, 50 component tests and production build passed. |
| `bash scripts/run-offline-mvp-e2e.sh` | PASS, exit 0; 4/4 Playwright tests | Isolated offline journey saved an opportunity deadline, reloaded the editor from PostgreSQL and verified the ISO date; all legacy Finep/CNPq E2E journeys also passed. Temporary E2E resources were cleaned by the script. |
| `openspec validate manual-opportunity-localdate-serialization --strict` | PASS, exit 0 | New change validates strictly. |
| `openspec validate --all --strict` | PASS, exit 0; 14/14 items | All current changes/specs validated. |
| `git diff --check` | PASS, exit 0 | No whitespace errors in tracked diff. |
| `docker compose config -q` | PASS, exit 0 | Compose configuration remains valid. |
| `mvn -B -f backend/pom.xml dependency:tree -Dincludes=com.fasterxml.jackson.core:jackson-databind,com.fasterxml.jackson.datatype:jackson-datatype-jsr310,tools.jackson.core:jackson-databind` | PASS, exit 0 | Dependency tree confirms Jackson 2.21.5 `com.fasterxml` for the adapter and Jackson 3.1.5 `tools.jackson` elsewhere (Flyway); the adapter type is Jackson 2. |

## Independent review

- Evidence supplied: OpenSpec criteria/design, full diff (including pre-existing uncommitted editorial work, identified separately), dependency-tree result, reproduced failing test, passing full Maven/frontend/E2E checks, and the Testcontainers regression.
- Reviewer had a separate context: yes.
- Reviewer was instructed not to modify implementation, tests, specs, or this report: yes.
- Worktree was checked before and after review; reviewer made no changes: yes.
- Spec adherence, implementation quality, regressions, and serialization compatibility reviewed: yes.
- Outcome: `PASS`, no findings. The reviewer independently confirmed the Jackson 2/3 dependency distinction, mapper-copy configuration, PostgreSQL regression coverage, E2E persistence check, and no schema/API/fixture/global mapper changes.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| F-LD-01 | High; AC-01–AC-04 | New PostgreSQL regression failed before fix with the exact unsupported Java time type exception. | Attempt 1: copy injected Jackson 2 mapper, register `JavaTimeModule`, disable timestamp date output. No global mapper change. | Targeted Testcontainers regression PASS; independent review pending. |

## Test integrity

- Tests added/changed: `ManualEditorialPersistenceIntegrationTest` now exercises non-null deadline create/reload/update, null, JSONB type/string representation and custom section/field round-trip; Playwright authoring journey saves a deadline, reloads the editor, and verifies it persisted.
- Demonstrated test defect, if any: none. The first run was intentionally performed before the fix and reproduced the user-reported failure.
- How approved acceptance criteria and equivalent or stronger coverage were preserved: the failure was fixed in production code, while the regression asserts database JSON type and the value after re-reading through the application adapter.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: global Jackson configuration remains unchanged by design; other consumers continue using their existing mapper configuration. No other affected `LocalDate` serialization path was identified in manual-opportunity persistence.
- Decisions made: local Jackson 2 mapper copy per the OpenSpec design; no schema migration or API contract change.
- OpenSpec tasks marked complete and evidence: 1.1–1.3; reproduced failure then passing Testcontainers regression, Maven clean verify, frontend checks, offline Playwright, OpenSpec strict, Compose config, diff check, and independent review.
- Final verification after fixes: all commands in the table passed after the implementation fix; the targeted pre-fix run reproduced the original exception.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `PASS`; all acceptance criteria and regression checks passed, with no independent-review findings.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.

No external calls, commit, or push were performed.
