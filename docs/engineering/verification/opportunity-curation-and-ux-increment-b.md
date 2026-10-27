# Verification record: opportunity-curation-and-ux / increment-b

> Run record for the approved Increment B implementation and verification.

## Run identity

- Change and cycle: opportunity-curation-and-ux / increment-b
- OpenSpec change/tasks in scope: opportunity-curation-and-ux (tasks 1.1–6.3)
- Date/time (UTC): 2026-10-27
- Implementer context: /root
- Independent reviewer context/mechanism: separate read-only reviewer `/root/curation_increment_b_review`
- Final state: PASS after independent review and final verification

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | administrative-curation / gate auth | Admin endpoints exist only in explicit isolated demo configuration; non-demo and unauthenticated access denied; credentials are environment supplied and hashed by Spring Security | Security context/config tests + negative integration tests |
| AC-02 | administrative-curation / CSRF and audit | Session mutation requires CSRF; session cookie HttpOnly/SameSite/Secure for HTTPS; actor is authenticated principal, not request data | Security integration tests and audit assertions |
| AC-03 | administrative-curation / typed revision | Supported typed field, justification, and a document reference owned by the opportunity create a pending revision; invalid/cross-call references rejected | Validator and Testcontainers tests |
| AC-04 | administrative-curation / immutable history | Original ExtractedField/Evidence remains unchanged; review history stores values, author, times, reason, source reference and decisions | PostgreSQL/Testcontainers before/after assertions |
| AC-05 | administrative-curation / transitions | Pending can become approved/rejected once; approval is atomic and stale/concurrent approval is rejected; rejection does not publish | Concurrent and state-transition integration tests |
| AC-06 | administrative-curation + opportunity-ingestion / public effective value | Existing public reads expose latest approved value while identifying human publication separately from unchanged technical extraction; pending/rejected revisions do not publish | API integration tests, DTO/OpenAPI checks |
| AC-07 | administrative-curation / interface | Admin can log in, choose Finep/CNPq call, inspect source, submit correction, approve/reject, and inspect history; public catalog reflects approved value | Playwright E2E with isolated offline fixtures |
| AC-08 | administrative-curation / readable presentation | Nested values render accessibly in Portuguese; main detail view has no raw JSON; technical IDs/JSON remain expandable; MISSING means extraction did not identify it unless human review confirms absence | Component tests and Playwright accessibility assertions |
| AC-09 | request / evidence mismatch diagnosis | Determine whether mismatch arises in extraction persistence/read API/presentation; do not assert semantic support from co-occurrence; hide/qualify unsafe association without rewriting historical evidence | Reproducible fixture-backed test and recorded root cause |
| AC-10 | project constraints | Finep and CNPq remain available; source/agencies consistent; original fixtures/extractions/audit package contracts unchanged; no external requests | Full offline E2E, fixture hash checks, package/factual-review regressions |

## Plan and affected areas

- Planned vertical slice: add demo-only session security and curated revision persistence/API, project approved values through existing public DTOs, add administrative React flow and readable field formatting, then exercise the workflow against the isolated Finep+CNPq offline stack.
- Affected files/modules: OpenSpec change; backend security, curation domain/application/persistence/API, Flyway, public read adapter/DTO; frontend API/router/catalog/detail/admin screens; demo guide; backend/frontend/Playwright tests; this report.
- Known risks before implementation: field-specific validation and safe effective-value projection; session/CSRF across Codespace forwarding; stale approval races; preserving extraction states/provenance; semantic evidence mismatch may originate in extraction and may not be safely repairable in this scope.
- Scope/architecture approval needed: none; single-admin manual-demo session auth approved by user.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `openspec validate opportunity-curation-and-ux --strict` | PASS | Change validates strictly after implementation |
| `mvn -B -f backend/pom.xml clean verify` | PASS, exit 0 | Final run: 162 tests; 0 failures/errors/skips. Includes PostgreSQL/Testcontainers clean and upgrade migration tests, security/API/curation tests, 101-revision pagination, unsupported source-evidence omission, and audit package/factual review compatibility regressions. |
| `cd frontend && npm run lint` | PASS | oxlint completed successfully |
| `cd frontend && npm run typecheck` | PASS | TypeScript build check completed successfully |
| `cd frontend && npm test -- --run` | PASS | 1 test file, 28 tests passed |
| `cd frontend && npm run build` | PASS | TypeScript build and Vite production build completed |
| `bash scripts/run-offline-mvp-e2e.sh` | PASS | Final run: 3 Playwright tests passed against real backend and isolated PostgreSQL/S3-compatible Compose resources, seeded only by offline deterministic Finep/CNPq fixture connectors. Project-specific containers/network/volumes and temporary TLS files were removed by the runner. No Finep, CNPq, or OpenAI access. Finep regions detail reports no field evidence instead of showing the unrelated budget locator. |
| `docker compose config -q` | PASS | Base Compose configuration is valid |
| `git diff --check` | PASS | No whitespace errors |
| OpenAPI/controller and migration checks in backend suite | PASS | API integration tests passed; clean V1–V5 and V3→V4→V5 upgrade paths passed |

## Independent review

- Evidence supplied: approved proposal/design/spec/tasks, full worktree diff, command results above, and this report.
- Reviewer context: separate read-only context `/root/curation_increment_b_review`.
- Reviewer instructed not to modify implementation, tests, specs, or this report.
- First review outcome: RETRY, no files modified by reviewer.
- First review findings: CUR-REV-01 (high, citation fallback obscured unverified evidence) and CUR-HIST-01 (medium, silent 100-revision cutoff).
- Corrections applied: deterministic fixture provider emits only literal quotes present in the excerpt; unquoted citation is omitted to satisfy the strict schema, and extraction persistence continues to reject missing quotes. Offline E2E asserts that the regions field reports no evidence; PostgreSQL regression ensures the budget-clause locator is not persisted as region evidence. Replaced hidden 100-row cutoff with bounded chronological pages, total count and UI navigation; OpenAPI and OpenSpec updated.
- Second review outcome: no blockers for CUR-REV-01/CUR-HIST-01; reviewer noted that E2E/demo guide incorrectly described the confirmed-absence workflow as a cited correction. CUR-DOC-01 (low) corrected the test title and demo guide to state that the flow publishes TRL absence with a document/version locator and justification, without inventing a quote, and rejects an unsupported deadline value.
- Second independent review: PASS; reviewer also checked all 18 task marks and CUR-DOC-01 wording in the final worktree.
- Worktree comparison after final review and task marking: PASS, reviewer made no changes.
- Spec adherence, implementation quality, regressions, and security outcome: PASS.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| CUR-API-01 | High; AC-06 | Initial API integration exposed a detail query that did not project curated document reference columns required by the DTO. | Attempt 1: selected the revision's document/version/source/page/section/quote columns and added API projection assertions. | API integration suite, full backend suite and independent review PASS. |
| CUR-API-02 | High; AC-06/08 | Initial API detail read exposed a nullable extracted value path that was wrapped in `Optional` and could fail when no normalized value existed. | Attempt 1: used nullable-safe mapping; added/retained coverage for absent fields. | API integration suite, full backend suite and independent review PASS. |
| CUR-E2E-01 | High; AC-07 | First isolated Playwright attempt found the self-signed HTTPS certificate was not ignored by the Playwright web-server readiness probe. | Attempt 1: enabled `ignoreHTTPSErrors` for the webServer probe and browser context; no weakening of HTTPS cookie assertions. | Final E2E and independent review PASS. |
| CUR-E2E-02 | Medium; AC-07 | A repeat run treated a port in TIME_WAIT from this runner's own prior execution as an active service. | Attempt 1: availability probe enables `SO_REUSEADDR`, still refuses any port that cannot be bound. | Final E2E and independent review PASS; isolated runner uses unique project/resources. |
| CUR-E2E-03 | High; AC-02/07 | Login/logout invalidated the CSRF token cached by the client, causing the next authenticated mutation to be rejected. | Attempt 1: invalidate/refetch CSRF and session queries on auth transitions; CSRF enforcement remains enabled. | Final E2E and independent review PASS. |
| CUR-API-03 | High; AC-03/06 | Spring Boot 4 HTTP converters use Jackson 3 while DTOs initially carried Jackson 2 `JsonNode`, causing incompatible body conversion; absent locator was also serialized as a JSON null node rather than Java null. | Attempt 1: public DTO boundary accepts JSON-compatible objects and explicitly converts with the existing Jackson 2 mapper; absent locator remains null. Contract shape unchanged. | Full backend, E2E and independent review PASS. |
| CUR-E2E-04 | Medium; AC-07 | E2E actor-spoof interception initially retained the old Content-Length after replacing the request body, and later queried an audit element using a too-narrow paragraph locator. | Attempt 1: recompute byte Content-Length and assert authenticated actor in the revision card. The test still submits a forged actor and verifies it is absent. | Final E2E and independent review PASS; authenticated principal is displayed. |
| CUR-E2E-05 | Medium; AC-07/08 | Final public absence assertion used the admin heading `TRL`, while the public localized label is `Nível de maturidade tecnológica (TRL)`. | Attempt 1: changed only the selector to match the user-visible public heading; API Testcontainers separately asserts HUMAN/MISSING/null/original-null projection. | Final E2E and independent review PASS. |
| CUR-REV-01 | High; AC-09/task 5.2 | First independent review showed `FixtureStructuredExtractionProvider` used the entire excerpt as a fabricated literal quote when the preserved provider artifact had no `quoteFragment`; E2E made the warning assertion conditional. | Attempt 1: provider emits only literal quotes present in the excerpt; citations with no quote are omitted to conform to strict schema. E2E requires the public detail to report no field evidence. PostgreSQL regression asserts the unquoted regions locator is not persisted. Interim attempts to persist a null-quote locator/citation were reverted after audit-package completeness and schema checks rejected them. Frozen source/provider fixtures and historical persisted records are unchanged. | Targeted tests, final clean verify, E2E, audit-package regressions and independent review PASS. |
| CUR-HIST-01 | Medium; AC-04/task 2.3/4.2 | First independent review found the history query returned only the latest 100 rows without a continuation mechanism. | Attempt 1: API now returns validated chronological pages (max 50), total count and UI previous/next controls; OpenAPI and OpenSpec describe bounded pagination; Testcontainers regression seeds 101 revisions and verifies all pages (50/50/1) and order. | Targeted tests, final clean verify, UI checks and independent review PASS. |
| CUR-DOC-01 | Low; AC-07 / demonstration accuracy | Second independent review found test and demo guide called a confirmed-absence review a “cited correction,” which overstated what the workflow proves. | Attempt 1: renamed the E2E scenario and corrected the demo guide to say it confirms TRL absence with complete review, justification, and document/version locator without a fabricated quote; unsupported deadline proposal is rejected. | Final offline Playwright 3/3 and independent wording review PASS. |

## Test integrity

- Tests added/changed: backend security/session/isolation, typed revision and transition, concurrency, public publication projection and API integration tests; migration clean/upgrade coverage; deterministic provider quote-provenance assertion; assertion that the unverifiable regions locator is not persisted as evidence; 101-row revision pagination regression; React component tests; offline Playwright coverage for Finep catalog/detail/evidence, CNPq filter/detail/documents/evidence, admin session/cookie/CSRF, actor spoofing, approval, rejection, public projection, original extraction, and safe handling of the unsupported regions reference.
- Demonstrated test defect, if any: none.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: no accepted criterion was removed. Regression tests assert both unchanged technical state and separate human publication; offline E2E tests exercise the production browser/API paths with deterministic connectors.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: production identity provider is not included; OIDC is a required gate before production administration. One demo administrator has no role-management workflow. Semantic validity of human assertions still depends on reviewer judgment; technical linkage alone does not prove the cited text semantically supports the value.
- Decisions made (link ADR/OpenSpec artifact) or none: isolated demo profile with Spring Security sessions; credentials supplied only by environment; auth denied by default elsewhere; original extraction records immutable; approved review projected separately.
- OpenSpec tasks marked complete and evidence: tasks 1.1–6.3 (18/18) are checked after implementation, Testcontainers, browser E2E and independent review PASS.
- First implementation verification before independent review: backend clean verify PASS (161 tests); frontend lint/typecheck/28 tests/build PASS; Playwright 3/3 PASS; OpenSpec strict PASS; Compose config PASS; diff check PASS.
- Verification after first review corrections: targeted Finep/provider tests PASS (20 tests); backend clean verify PASS (162 tests); frontend lint/typecheck/28 tests/build PASS; Playwright 3/3 PASS; OpenSpec strict PASS; Compose config PASS; diff check PASS.
- Verification after documentation correction: Playwright 3/3 PASS; OpenSpec strict, Compose config and diff check PASS. No functional source changed after full backend/frontend checks.
- Final state rationale (PASS, RETRY, or ESCALATE): PASS. All 18 tasks are marked complete, final backend/frontend/OpenSpec/browser checks passed, the independent reviewer confirmed task accuracy and no residual blockers, and no files were changed during review.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.
