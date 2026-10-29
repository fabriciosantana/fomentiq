# Verification record: manual editorial consolidation / 2026-10-29

## Run identity

- Change and cycle: `manual-opportunity-editorial-prototype`, `manual-opportunity-editorial`, and `manual-opportunity-localdate-serialization` / final consolidation
- OpenSpec changes/tasks in scope: prototype tasks 1.1–4.1; implementation tasks 1.1–4.3; LocalDate tasks 1.1–1.3
- Date/time (UTC): 2026-10-29
- Implementer context: `/root`
- Independent reviewer context/mechanism: `/root/curation_increment_b_review`, separate read-only collaboration context
- Final state: `RETRY` pending push, remote CI, and final Git-state confirmation

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | `manual-opportunity-editorial-prototype` tasks 1.1–4.1 | Keep the approved, isolated prototype, its OpenSpec artifacts, and its verification report together; prototype remains separate from production application. | Prototype smoke is recorded in the prototype report; complete file inventory contains only the three authorized changes and this run report; staged review found no credential or temporary artifact. |
| AC-02 | `manual-opportunity-editorial` tasks 1.1–4.3 | Persist manual editorial content additively; preserve historical calls; secure administration; expose only published manual opportunities; retain the prototype-aligned authoring/public flow. | Final `mvn -B -f backend/pom.xml clean verify`: PASS, 183 tests, including clean Flyway V1–V6 and V5→V6 upgrade; frontend lint/typecheck/tests/build: PASS, 50 tests. |
| AC-03 | `manual-opportunity-localdate-serialization` tasks 1.1–1.3 | Serialize `LocalDate` as ISO-8601 JSON string and round-trip create, update, null and nested editorial content through PostgreSQL without changing schema or contracts. | Included Testcontainers regression passed in Maven clean verify; exact test and persisted JSONB assertions are documented in the change report. |
| AC-04 | User consolidation authorization | Preserve all approved changes and user-entered demo data; do not investigate or edit the manually corrected Finep deadline; do not alter original fixtures, `v0.1.0`, or active manual demo resources. | Fixture tree has no diff; Finep 26/26 and CNPq 7/7 manifest entries match size/SHA-256. Manual Compose PostgreSQL and object-storage services remained healthy after backend verification. |
| AC-05 | User consolidation authorization | Separate approved scopes into logical commits without dropping hunks; if dependencies make a separate commit incoherent, explain the smallest consistent composition; verify each staged diff and scan for secrets/temporary artifacts. | Prototype is independent. Editorial runtime includes LocalDate serializer fix and the public-snapshot query fix. Both staged packages passed `git diff --cached --check`, scope/secret review, and independent review. |
| AC-06 | User consolidation authorization | Complete offline E2E, strict OpenSpec, Compose, whitespace, push and remote CI checks; finish with clean synchronized `main`. | Offline E2E passed 4/4 on rerun; OpenSpec strict 14/14, `docker compose config -q`, and `git diff --check` passed. Push/CI and final Git state remain pending. |

## Plan and affected areas

- Planned vertical slice: commit the approved standalone prototype separately, then commit the complete manual editorial product flow with the LocalDate persistence fix and its regression. Three commits are not used because the new editorial store and non-null deadline editor are one runtime feature; splitting the serializer fix after the feature commit would leave an intermediate product commit unable to save an allowed deadline.
- Affected files/modules: `prototypes/manual-opportunity-editorial/`; `openspec/changes/manual-opportunity-editorial-prototype/`; `openspec/changes/manual-opportunity-editorial/`; `openspec/changes/manual-opportunity-localdate-serialization/`; `backend` editorial/security/API/migration/tests; `frontend` editorial routes/components/tests and offline Playwright; approved demo documentation and verification records.
- Known risks: preserve an atomic feature history while incorporating a newly identified public-snapshot search/order leak; avoid affecting the running manual demo database/storage.
- Scope/architecture approval needed: none. The user explicitly authorized these commits and push after all gates pass.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `mvn -B -f backend/pom.xml clean verify` | PASS, exit 0; 183 tests | Final run after MEO-CONS-01 correction; Testcontainers PostgreSQL; Flyway clean migrations V1–V6 and V5→V6 upgrade tests passed. |
| `mvn -B -f backend/pom.xml -Dtest=ManualEditorialPersistenceIntegrationTest#publicCatalogSearchAndOrderUsePublishedSnapshotAfterDraftEdit test` | PASS, exit 0; 1 test | PostgreSQL regression confirms public search/order are bound to published snapshot while administrative search sees editable draft. An initial compile attempt used the wrong DTO accessor `items()`; correction to `content()` is recorded as MEO-CONS-02. |
| `npm run lint && npm run typecheck && npm test -- --run && npm run build` (`frontend/`) | PASS, exit 0; 50 tests | Lint, typecheck, four test files, and production build passed. |
| `docker compose config -q` | PASS, exit 0 | Compose syntax/configuration valid. |
| `openspec validate --all --strict` | PASS, exit 0; 14/14 | All current OpenSpec items passed strict validation. |
| `git diff --check` | PASS, exit 0 | Tracked unstaged diff had no whitespace errors. |
| Manifest SHA-256/size verification for Finep and CNPq fixtures | PASS; 26/26 Finep and 7/7 CNPq artifacts | Verified against their existing manifests. Neither fixture tree is changed. |
| `bash scripts/run-offline-mvp-e2e.sh` (after MEO-CONS-01 fix) | First run RETRY (one Playwright assertion timed out on manual editor heading after reload); immediate second run PASS, exit 0; 4/4 | Failure was not reproducible on unchanged rerun; no test was weakened or removed. Coverage includes manual draft save/reload with ISO deadline, preview/publish/edit/unpublish, legacy Finep/CNPq catalog and evidence, and curation journeys. The script uses a unique Compose identity, checks reserved ports, refuses resource reuse, and cleans only its generated project/temp files. Existing manual demo PostgreSQL and storage remained healthy. |
| `git diff --cached --check` and complete staged-diff review | PASS for both commit packages | Prototype package: 12 files; editorial package: 35 files. Both reviewed independently and contained only authorized files. No `gitleaks` binary was installed; targeted credential-pattern scan found no matches. |
| GitHub Actions backend/frontend/OpenSpec | Pending | Only after push; no result is presumed. |

## Independent review

- Evidence supplied: all three approved OpenSpec changes, complete worktree diff, commit partition plan, exact local verification results, fixture checksums, and active manual demo Compose status.
- Reviewer had a separate context: yes, `/root/curation_increment_b_review`.
- Reviewer was instructed not to modify implementation, tests, specs, or this report: yes.
- Worktree was checked before and after review; reviewer made no changes: yes.
- Spec adherence, implementation quality, regressions, security, secret/artifact scope, and commit separation reviewed: yes.
- Outcome: `PASS` after resolving MEO-CONS-01, MEO-CONS-03; no implementation findings remain. Reviewer confirmed the two-commit grouping avoids an intermediate editorial build unable to persist non-null deadlines.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| MEO-CONS-01 | High; editorial task 2.2 and public snapshot confidentiality/consistency | Independent review demonstrated that public list search/order used editable relational columns while returned public details used the published snapshot. An unpublished draft title could affect public search and ordering. | Attempt 1: use `published_content` title/institution JSON for public search and published title for public ordering; retain editable columns for admin; add PostgreSQL regression for published title search, draft title exclusion, admin draft search, and snapshot ordering. | PASS; targeted PostgreSQL regression and full Maven clean verify pass; independent re-review confirmed correction and test coverage. |
| MEO-CONS-02 | Low; regression test compilation | First targeted regression compile used `items()` but the page DTO exposes `content()`. | Attempt 1: changed test assertions to use the declared `content()` accessor. | Targeted PostgreSQL test and full Maven clean verify PASS. |
| E2E-CONS-01 | Medium; offline end-to-end verification | First E2E run after the SQL fix timed out waiting for the reloaded manual opportunity heading; the other three journeys passed. | Retry 1: reran the unchanged isolated E2E script. All four journeys passed; no expectation was weakened. | Passed on retry; likely transient under concurrent build/test load, retained as residual observation. |
| MEO-CONS-03 | Low; verification report consistency | Independent re-review found a stale `Running` row duplicating the completed PASS entry for the targeted PostgreSQL regression. | Attempt 1: removed the stale row; retained the single PASS record. | PASS; independent report-only verification confirmed the duplicate is gone and the result table matches evidence. |

## Test integrity

- Tests added/changed: existing approved test suites, including the LocalDate Testcontainers regression, manual editorial Playwright journey, and the new published-snapshot list search/order regression.
- Demonstrated test defect, if any: the new regression initially used a nonexistent page accessor (`items()`); corrected to `content()` without changing the asserted behavior. `MEO-CONS-01` was an implementation leak found by independent review. Regression asserts the approved published-only behavior.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: no tests or expectations have been modified in this consolidation.
- Any test weakening/removal: no. The first compile attempt of the new regression failed only because of its incorrect accessor and was corrected; acceptance assertions were retained. The E2E timeout passed on an unchanged isolated rerun.

## Risks, decisions, and final verification

- Residual risks/debts: demonstration administration continues to be isolated to the manual demo profile; production administration requires a production identity provider. CI remains unverified until the commits are pushed.
- Decisions made: use two coherent commits—(1) prototype; (2) complete editorial implementation, LocalDate correction, both relevant OpenSpec changes and reports. The approved three-commit split is adjusted because removing the LocalDate serializer from the editorial commit would leave supported deadline entry broken in that intermediate commit. This follows the user's instruction that three commits are preferred but not required when separation harms consistency.
- OpenSpec tasks marked complete and evidence: prototype 1.1–4.1; editorial 1.1–4.3; LocalDate 1.1–1.3. Existing task marks were not changed in this consolidation.
- Final verification after fixes: targeted regression and full backend/frontend/OpenSpec/Compose checks passed; E2E passed on retry and independent re-review passed. Push, remote CI, and final Git state remain pending.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `RETRY` until all pending gates pass.
- Human approval still required before commit/push: no; explicitly authorized conditionally by the user.
- Human approval still required before scope/architectural change: yes.

The manually entered Finep submission deadline discrepancy was explicitly resolved by the user as a data-entry correction. It was not investigated or changed in this consolidation.
