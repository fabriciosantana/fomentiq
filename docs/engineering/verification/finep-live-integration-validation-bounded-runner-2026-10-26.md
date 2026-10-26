# Verification record: finep-live-integration-validation / bounded runner 2026-10-26

## Run identity

- Change and cycle: `finep-live-integration-validation` / bounded offline preflight controls.
- OpenSpec tasks in scope: 1.2, 1.4, 1.5 and applicable offline regression portions of 2.1–2.3 and 3.2–3.4. No Finep/OpenAI live execution.
- Date/time (UTC): 2026-10-26.
- Implementer context: primary Codex context.
- Independent reviewer context/mechanism: separate read-only collaboration reviewer; initial review found F-06–F-09, follow-ups found F-10–F-12, and the final correction review passed with no new code finding.
- Final state: `ESCALATE` (no verified OpenAI hard USD cap; Playwright could not run without touching the active manual app).

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | Change A requirements; task 1.4 | Strict typed/versioned manifest, exact Finep identity, matching human approval, no unknown/incomplete values; malformed or absent manifest refuses before Spring/Flyway. | Loader/policy/CLI tests; fixed target 751/751092 and canonical URL; UUID run ID and fixed report path. |
| AC-02 | Change A isolation requirement; task 1.2 | Effective JDBC/database/user and S3 endpoint/bucket match a unique Compose project, labels, ports, and exact mounted volumes; ambiguous/shared targets and Spring/property overrides refuse before source access or writes. | Guard and runtime configuration checks; negative guard unit tests. Runtime override sources are rejected; S3 endpoint userinfo/query/fragment are refused. Actual dedicated Compose positive path remains unexecuted. |
| AC-03 | Change A bounded acquisition requirement; task 1.5 | Document count, file/aggregate/metadata bytes, HTTP hops/redirects, connection/read/operation/global deadlines are applied while receiving; exceeding any bound cancels/rejects rather than accepting a truncated artifact. | Bounded subscriber unit tests; `IngestionLimitsTest`; Finep URL policy regressions; Testcontainers ingestion suite. Direct live-host connector streaming remains untested by design. |
| AC-04 | Change A private runner requirement; task 1.4 | Explicit private CLI flag only; no normal-startup/HTTP trigger; sanitized deterministic output and exit status. | `FinepLiveValidationManifestLoaderTest`: missing manifest exits 2 before Spring; main intercepts the explicit flag before `SpringApplication.run`. |
| AC-05 | Change A provider/cost gate; task 1.5 / 2.3 | Manifest attempt/input/output/time limits constrain the existing provider abstraction; conservative maximum estimate includes all attempts; if hard spend ceiling is unavailable, emit `ESCALATE` and send zero Finep/OpenAI requests. | Local HTTP provider tests verify retries and limits; policy estimator test; CLI preflight returns `ESCALATE` after guard and before ingestion because current OpenAI provider has no verifiable server-side USD cap. No real provider calls. |
| AC-06 | User authorization | Preserve v0.1.0 behavior, golden fixture and schema; no migration, no Change B, no Finep/OpenAI access, no commit/push. | Diff contains no fixture/migration/Change B changes; tests use Testcontainers, fixture/provider and local HTTP servers. |
| AC-07 | Existing task 3.2 transaction/integrity regression | Keep independently committed `IngestionRun`, rollback, retry/idempotency, snapshots and evidence attribution. | Existing Testcontainers ingestion integration tests passed, including controlled SQL rollback and retry/version tests. |

## Plan and affected areas

- Implemented a typed per-run manifest and policy, bounded Finep response/document streaming, bounded extraction request attempts/tokens/deadlines, and a private pre-Spring CLI that checks manifest, environment, database identity and inspected Compose resources.
- The CLI intentionally stops with `ESCALATE`; it does not start the Spring context or write application data because no verifiable hard USD spending ceiling is available. It writes a sanitized preflight report only after successful isolation checks. Invalid manifests refuse before any database, Compose or external API operation.
- Affected areas: `backend/src/main/java/com/fomentiq/{FomentiqApplication,extraction,ingestion}`, backend tests, `backend/src/main/resources/application.yml`, Change A `proposal.md`, `design.md`, `spec.md`, `tasks.md`, `DECISIONS.md`, this report.
- Preserved carried-forward uncommitted artifacts: `openspec/changes/finep-live-integration-validation/tasks.md` prior task 1.1 completion and `docs/engineering/verification/finep-live-integration-validation-preflight-2026-10-26.md`. The preflight report was not overwritten.
- No database migration, golden fixture change, frontend source change, new service, public API, scheduler or infrastructure was introduced.

## Proposed execution limits

Fixture measurement: 18 PDFs; 5,406,555 aggregate bytes; largest PDF 1,069,861 bytes; discovery JSON 10,013 bytes; call JSON 9,526 bytes; document catalog JSON 53,538 bytes.

Candidate exact manifest values (not an online approval): maximum 18 documents; 5 MiB/document; 12 MiB aggregate; 256 KiB discovery and document metadata; 2 MiB public OAuth page; 64 KiB token response; 150 HTTP hops total; four redirects per request; 10 seconds connect, 30 seconds read, 45 seconds per operation, 900 seconds global; `gpt-6-luna`; `finep-call-v1`; one attempt; 64,000 UTF-8 input bytes as a conservative token upper bound; 12,000 output tokens. The built-in immutable safety maxima are 10 MiB/file, 25 MiB aggregate, 1 MiB discovery, 2 MiB metadata/OAuth page, 64 KiB token response, 150 HTTP hops, and the listed timeout ceilings. A numeric USD budget and current price rates are deliberately not proposed without an approved, dated official pricing source. Even with a conservative estimate, the present provider/account path cannot enforce a hard USD cap, so no external call is allowed.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `mvn -B -f backend/pom.xml -Dtest=FinepLiveValidationPolicyTest,FinepLiveValidationRuntimeOverridesTest test` | PASS, exit 0 | 10 targeted tests passed for endpoint URI validation and environment-placeholder, Spring/system/CLI override refusal. |
| `mvn -B -f backend/pom.xml clean verify` | PASS, exit 0 | Final clean run passed 58 tests, including F-12 placeholder-name JVM override regressions; Testcontainers PostgreSQL applied all 3 migrations from empty schemas. |
| `npm run lint` (`frontend/`) | PASS, exit 0 | Oxlint passed. |
| `npm run typecheck` (`frontend/`) | PASS, exit 0 | TypeScript passed. |
| `npm test -- --run` (`frontend/`) | PASS, exit 0 | 24 tests passed. |
| `npm run build` (`frontend/`) | PASS, exit 0 | Vite production build passed. |
| `./scripts/run-offline-mvp-e2e.sh` | BLOCKED before setup, exit 1 | Script found a healthy app already using port 8080 and refused to reuse it. No Compose project was started or torn down; active manual app was left untouched. |
| `openspec validate finep-live-integration-validation --strict` | PASS | Change artifacts validate after F-10–F-12 isolation criteria were clarified in spec/design/task 1.2. |
| `git diff --check` | PASS | No whitespace errors after code/report/spec updates. |
| `docker compose config -q` | PASS, exit 0 | Current Compose configuration parses; no services were started by this command. |
| Finep/OpenAI online calls | NOT RUN | Explicitly prohibited in this execution; all integration tests use fixtures, local HTTP servers or Testcontainers. |

## Independent review

- Evidence supplied: approved Change A artifacts, acceptance matrix, full diff, Maven/frontend outputs and E2E limitation.
- Reviewer had a separate context: yes. Initial result: `ESCALATE` with F-06–F-09. Re-review identified F-10/F-11 and F-12; final code review returned `PASS` for the offline safeguards with no new finding. Live readiness remains `ESCALATE` for the hard-spend-cap and environment limitations.
- Reviewer was instructed not to modify implementation, tests, specs or this report: yes.
- Worktree was checked after review: yes; no reviewer-authored file changes appeared.
- Spec adherence, implementation quality, regression and security review: `PASS` for implemented offline safeguards and F-06–F-12 corrections; live execution readiness remains `ESCALATE`.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| F-01 | High; AC-03 / task 1.5 | Original ingestion path lacked document count, aggregate bytes and total deadlines; previous HTTP body buffering did not bound streaming. | Attempt 1: add immutable caps and bounded/cancelled streaming for Finep and OpenAI; tests cover subscriber overflow, manifest maxima and provider input/output/attempt limits. Direct Finep live-host stream test is intentionally absent; local hosted integration is still needed. | Pending. |
| F-02 | High; AC-04 / task 1.4 | No internal production path existed to enforce explicit approval before startup side effects. | Attempt 1: add explicit pre-Spring CLI activation and missing-manifest refusal; normal startup remains unchanged; gate writes sanitized report and returns nonzero. | Pending. |
| F-03 | High; AC-02 / task 1.2 | No isolation guard previously confirmed actual project labels/ports/mounts or DB identity. | Attempt 1: compare effective env, read PostgreSQL identity, inspect Compose services/labels/ports/volumes, use UUID-scoped bucket/project/report, and reject shared/mismatched targets. Unit negative case passes; full positive Docker Compose path not exercised. | Pending. |
| F-04 | Critical; AC-05 / task 2.3 | OpenAI adapter/account has no verified per-call USD hard cap. | Attempt 1: implement conservative estimate and make CLI stop with sanitized `ESCALATE`, zero Finep/OpenAI requests. This is an explicit policy gate, not a code defect; resolving it requires an approved enforceable account/provider cap. | Expected `ESCALATE` until human supplies/verifies a hard cap. |
| F-05 | Medium; AC-06 / prior offline Playwright acceptance | Offline Playwright script refuses to run while port 8080 is occupied by a healthy app. | Attempt 1: run the isolated script; it safely refused before starting containers. Did not stop/reuse the active manual app. | Requires an unused 8080 for a later rerun; no implementation correction. |
| F-06 | High; AC-02 / task 1.2 | Initial reviewer found isolation did not confirm that the target bucket was absent/unused. | Attempt 1: require UUID-scoped bucket and read-only S3 `HeadBucket`; allow only confirmed not-found and refuse existing/ambiguous buckets. Mock tests cover absent, existing and denied responses. | Corrected; independently re-reviewed with no remaining code finding. |
| F-07 | Medium; AC-02 / task 1.2 | Initial reviewer found Docker stdout could block before `waitFor` timeout. | Attempt 1: use asynchronous bounded output capture, wait at most 10 seconds, and forcibly terminate on timeout. | Corrected; independently re-reviewed with no remaining code finding. |
| F-08 | Medium; AC-04 / task 1.4 | Initial reviewer found arbitrary HTTPS pricing URI with query/userinfo could be copied to the sanitized report. | Attempt 1: accept only exact official pricing URI(s), without userinfo/query/fragment; add rejection test. | Corrected; independently re-reviewed with no remaining code finding. |
| F-09 | High; AC-03 / task 1.5 | Initial reviewer found missing controlled connector streaming/read-timeout and aggregate Testcontainers evidence. | Attempt 1: add local `HttpServer` exact-cap and overflow/cancellation tests plus a stalled-read bounded-failure test; Testcontainers aggregate-byte rejection checks rollback and durable FAILED run. A full run exposed a brittle assertion about exception internals. Attempt 2: accept the documented timeout exception or its HTTP wrapper while retaining elapsed-time and single-request assertions; direct subscriber tests still assert timeout/cancellation. | Corrected; final clean rerun passes; no acceptance criterion was removed. |
| F-10 | High; AC-02 / task 1.2 | Independent reviewer found Spring environment, system-property, or command-line overrides could supersede the manifest-bound datasource/storage targets if a future runner starts Spring. | Attempt 1: reject all Spring/Fomentiq environment and system-property override namespaces; accept only the three explicit runner CLI options; add negative tests for datasource/storage property sources and command-line override; clarify spec/design/task 1.2. | Targeted tests and final full Maven run pass; independently re-reviewed with no remaining code finding. |
| F-11 | Medium; AC-02 / task 1.2 | Independent reviewer found S3 endpoint URIs accepted userinfo, query, or fragment components. | Attempt 1: reject endpoint userinfo/query/fragment and non-root paths; add manifest policy regression tests; clarify spec/design/task 1.2. | Targeted tests and final full Maven run pass; independently re-reviewed with no remaining code finding. |
| F-12 | High; AC-02 / task 1.2 | Independent reviewer found JVM properties named like environment placeholders (for example `DATABASE_URL`, `S3_ENDPOINT`, `OPENAI_MODEL`) can take precedence over environment variables and were not rejected. | Attempt 1: reject JVM properties for every environment placeholder consumed by application configuration and add regression cases for database, storage and model names; clarify requirement/design/task wording. | Targeted 10-test run and final full clean verify (58 tests) pass; independent reviewer returned PASS for this correction. |

## Test integrity

- Tests added: manifest malformed/missing/unknown/oversize cases; exact opportunity and document/attempt maxima; Compose mismatch guard; byte subscriber overflow/full body; provider retry/input/deadline bounds; immutable request/deadline caps.
- Existing tests preserved: PostgreSQL rollback/FAILED run, PARTIAL document status, retry/idempotency, synthetic new version, extraction/evidence, golden fixture and API.
- Demonstrated test defect: the idle-read connector test assumed only one exception wrapping shape; execution showed the body subscriber timeout is sometimes propagated directly and sometimes wrapped. The test now accepts both representations and retains bounded-time, one-request, and direct subscriber cancellation/timeout assertions.
- Test weakening/removal: none.
- No test contacted Finep or OpenAI. The local provider HTTP server used only test data and a placeholder test key.

## Risks, decisions, and final verification

- Residual risks: live acquisition was not run; full Compose-positive preflight was not run; Playwright did not run due the active manual app; no enforceable OpenAI dollar cap is available; S3 and PostgreSQL remain non-atomic as documented in D-019.
- Decisions: D-020 records private pre-Spring CLI, fail-closed resource identity and the hard-cap limitation; Change A design documents candidate safety limits and no approved spend amount.
- OpenSpec tasks completed by this run: none. Existing task 1.1 remains checked. Tasks 1.2, 1.4, 1.5 and live/independent acceptance tasks remain unchecked because a positive isolated Compose preflight and the gated live validation have not been completed.
- Final state rationale: `ESCALATE` because no verifiable OpenAI hard USD cap is available; the dedicated Compose-positive preflight and Playwright remain unverified. The manual app still returns healthy on port 8080 and was not stopped or reused. No external request was made.
- Commit/push: not authorized/performed. Change B and implementation work remain untouched.
