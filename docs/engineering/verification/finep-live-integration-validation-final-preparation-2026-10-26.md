# Verification record: finep-live-integration-validation / final preparation 2026-10-26

## Run identity

- Change and cycle: `finep-live-integration-validation` / final isolated preflight preparation.
- OpenSpec tasks in scope: 1.2, 1.4, 1.5 and offline evidence applicable to 3.2. Tasks 2.1–2.3 are not run; no live request is authorized in this run.
- Date/time (UTC): 2026-10-26.
- Implementer context: primary Codex context.
- Independent reviewer context/mechanism: separate read-only collaboration context `/root/final_preparation_review`.
- Final state: `PASS` for offline operational readiness; live execution remains explicitly unapproved and unperformed.

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | User authorization; Change A task 1.2 | Review all pending changes without discarding them; no fixture, schema, migration, operational data, or Change B changes. | `git status`, complete diff review, fixture/migration path checks. |
| AC-02 | Change A task 1.2; isolation requirement | A unique Compose project with unique named volumes, PostgreSQL database/user, loopback-only published ports, S3 endpoint and bucket passes local preflight; existing manual app and data remain untouched. | Compose `ps`/`inspect`, HostIp validation, DB read-only identity query, read-only HeadBucket absence check; teardown only the run-specific Compose project. |
| AC-03 | Change A task 1.2; failure scenarios | Missing, mismatched, overridden, ambiguous, shared or unsafe isolation configuration is rejected before Flyway, Finep/OpenAI access, or writes. | Existing negative tests and targeted runner tests; zero external Finep/OpenAI requests. |
| AC-04 | User authorization; offline E2E verification | Offline Playwright runs using isolated database/storage and distinct HTTP/API/frontend ports; script does not reuse or terminate the manual application and cleans only its run-specific process group, Compose project, volumes and temp files. | Configurable-port E2E script run and post-run port/container checks. |
| AC-05 | User authorization; Change A task 1.5 | Confirm only presence of `OPENAI_API_KEY`; validate configured model, schema, reasoning effort, one-attempt/no-retry behavior, token bounds and timeouts locally, without printing the key or consuming tokens. | Secret-presence boolean only; provider tests use a local controlled HTTP server; runtime preflight checks `gpt-6-luna`, `finep-call-v1`, `low`, 0 retries, 64,000/12,000 token limits and 60s timeout. |
| AC-06 | User authorization; D-021 and Change A budget requirement | Candidate manifest specifies exact call 751/751092, bounded acquisition, model/schema/token/time limits, one attempt, dated official pricing, and an explicitly non-guaranteed monitored budget. A later live execution still needs explicit approval of the exact manifest and execution. | Manifest review, official pricing source and deterministic cost arithmetic; no Finep/OpenAI call. |
| AC-07 | User constraints | No real Finep/OpenAI requests, no golden fixture modification, no operational data changes, no commit/push. | Network/test evidence and final diff/status. |

## Plan and affected areas

- Planned vertical slice: revise the already-approved Change A budget policy from a provider hard-cap prerequisite to a separately approved monitored experimental budget; implement a preflight-only mode and an explicit separately approved live mode; configure loopback-only Compose resources and dedicated E2E ports; prove isolated Compose and offline browser flow; inspect OpenAI environment/config without a provider request.
- Affected files/modules: Change A proposal/design/spec/tasks, `DECISIONS.md`, typed manifest, internal Finep validation CLI/policy/execution reservation/runner/tests, Jackson Java time support, Compose binds, E2E launcher and Playwright proxy/port configuration, candidate manifest and this report.
- Known risks before implementation: OpenAI billing may differ from estimates; no token-free provider credential validation is allowed; positive Compose verification and Playwright require local Docker and free isolated ports; the existing manual app is healthy on port 8080 and must remain untouched.
- Scope/architecture approval needed: none; user explicitly approved monitored-budget behavior for this isolated experimental validation. No new architecture or schema is planned.

## Candidate manifest values (not yet approved for live execution)

- Target: Finep `externalId=751`, portal `751092`, canonical URL `https://www.finep.gov.br/e/chamada-publica/222684/751092`; allow only `www.finep.gov.br`.
- Documents: maximum 18; 5 MiB/document; 12 MiB aggregate.
- Responses: discovery 256 KiB; document metadata/catalog 256 KiB; OAuth page 2 MiB; token response 64 KiB; 150 HTTP requests total; maximum four redirects.
- Timeouts: connect 10 s; read 30 s; operation 45 s; whole run 900 s; OpenAI request 60 s.
- OpenAI: `gpt-6-luna`; `finep-call-v1`; reasoning `low`; 64,000 input tokens; 12,000 output tokens; exactly one total attempt and zero retries.
- Official standard short-context prices: USD 0.10 / 1M input tokens and USD 0.50 / 1M output tokens, dated 2026-10-26. At the token ceilings, conservative estimate: `(64,000 × 0.10 + 12,000 × 0.50) / 1,000,000 = USD 0.0124`.
- Proposed budget: USD 1.00 for this isolated one-attempt experiment. It is not approved yet, is monitored from provider-reported usage, and is not a guaranteed provider billing cap. The candidate manifest carries `approvalReference=human-live-approval-pending`; that marker is explicitly rejected if live execution is requested. The exact manifest, a non-pending approval reference and the execute flag require separate owner approval.
- Isolated resources: unique Compose project `fomentiq-finep-live-<run UUID>`, named volumes derived from that project, dedicated `fomentiq_live` database/user, a UUID-scoped S3 bucket, and distinct loopback-only ports.
- Candidate file: `docs/engineering/verification/finep-live-integration-validation-manifest-candidate-2026-10-26.json`. It contains no credentials and is not an authorization to execute.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `openspec status --change finep-live-integration-validation` | PASS | Spec-driven; 1/12 tasks complete before this run. |
| `openspec instructions apply --change finep-live-integration-validation --json` | PASS | Confirmed task IDs and source paths; only 1.1 checked. |
| `mvn -B -f backend/pom.xml -Dtest='FinepLiveValidation*Test,IngestionLimitsTest,OpenAiStructuredExtractionProviderTest,FinepIngestionIntegrationTest' test` | PASS (30 tests) | Included PostgreSQL/Testcontainers and local controlled OpenAI HTTP tests; no Finep/OpenAI endpoint calls. |
| `npm run lint` | PASS | Frontend lint. |
| `npm run typecheck` | PASS | TypeScript project check. |
| `npm test -- --run` | PASS (24 tests) | Component/unit tests. |
| `npm run build` | PASS | Production frontend build. |
| `scripts/run-offline-mvp-e2e.sh` | PASS (1 Playwright test) | Isolated project `fomentiq-cycle7-e2e-362294-78`; ports 18080/14173/25432/29000/29001; fixture persisted to isolated PostgreSQL and viewed through real API/UI. Script removed its project, volumes and process group. Manual app on :8080 returned HTTP 200 after the run. |
| Positive isolated Compose + private preflight using the candidate manifest | PASS | Project `fomentiq-finep-live-11d09f95-7b39-4ab6-b0b8-d8719f8935a1`; loopback ports 25432/29000/29001; DB/user matched read-only; empty UUID bucket verified by local HeadBucket; report `docs/engineering/verification/finep-live-validation/11d09f95-7b39-4ab6-b0b8-d8719f8935a1-preflight.json`; resources and volumes removed. Output: `READY_FOR_SEPARATE_LIVE_APPROVAL`, estimate USD 0.0124, manifest budget USD 1.00, model/schema/reasoning `gpt-6-luna`/`finep-call-v1`/`low`, one attempt, external Finep/OpenAI requests 0. |
| `OPENAI_API_KEY` presence/config check | PASS (presence only) | Key present; value never printed. Effective model fallback `gpt-6-luna`, reasoning `low`, schema `finep-call-v1`, default retries changed to 0, token ceilings 64,000/12,000, timeout 60s. No API request/token use; credential validity cannot be verified without an external request. |
| `openspec validate finep-live-integration-validation --strict` | PASS | Strict validation passed. |
| `docker compose config -q` | PASS | Compose parsed without printing interpolated configuration. |
| `git diff --check` | PASS | Final check after all source/report edits. |
| `mvn -B -f backend/pom.xml clean verify` | PASS — 70 tests, 0 failures/errors/skips | Clean build, Checkstyle, PostgreSQL/Testcontainers migrations and integrations, extraction/provider tests and packaging all passed. An earlier stale build and an unused test import failed; both were corrected before this final clean pass. |
| Earlier verification attempts | RETRY, corrected | One pre-final non-clean Maven run used stale compiled retry-policy classes and failed the new two-attempt regression; a subsequent clean check reported one unused test import. The import was removed and the final clean verify above passed with the latest source. No test was weakened. |

## Independent review

- Evidence supplied: approved Change A artifacts, acceptance matrix, complete diff and test/command results.
- Reviewer used a separate collaboration context, was instructed not to modify any files, and made no changes.
- First review identified missing live execution, public Compose binds and later manifest/parser issues. Corrections added explicit live gate, loopback enforcement, Jackson Java-time parsing, JSON-lines Compose parsing, pending-approval rejection, atomic run reservation and post-usage assessment. A second review found approval placeholder, premature PASS and concurrent-run risks; those were corrected and re-reviewed.
- Final second review after all latest corrections: PASS. Reviewer used a separate context, made no changes, confirmed pending approval is rejected for live execution, `PASS` is withheld until evidence review, atomic reservation blocks concurrent/repeated run IDs, loopback binding is enforced, cost uses reported tokens, and no unresolved blocking findings remain.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| F-01 | High — missing explicit live execution path (AC-06) | First review found CLI only returned preflight readiness; no authorized mode to run ingestion and account post-response usage. | Attempt 1: added `--fomentiq.finep.live-validation.execute=true`, Spring non-web runner after preflight, token/cost report and separate live report path. No execution flag used in this run. | Re-review PASS. |
| F-02 | High — published Compose ports were not loopback-only (AC-02/03) | Docker inspect showed `HostIp=0.0.0.0`; numeric port equality did not establish local-only isolation. | Attempt 1: Compose defaults bind to `127.0.0.1`; inspector rejects non-loopback HostIp; regression test rejects `0.0.0.0`. | Re-review PASS. |
| F-03 | High — pending approval marker could be passed to live mode (AC-06) | Reviewer found manifest/CLI equality accepted `human-live-approval-pending`. | Attempt 1: live mode now rejects approval references containing `pending` or starting `preflight-only`; regression test added. Preflight remains permitted. | Re-review PASS. |
| F-04 | High — runner could label execution PASS before factual review (AC-06) | Runner set `requiresHumanEvidenceReview=true` but originally emitted `PASS` on successful ingest/cost. | Attempt 1: pure assessment now returns `ESCALATE/human_evidence_review_required` until human evidence review; added tests for missing usage, over-budget, failed ingestion and review gate. | Re-review PASS. |
| F-05 | Medium — concurrent live processes could spend twice (AC-06) | Bucket HeadBucket is a preflight check and has a race before object storage writes. | Attempt 1: atomic per-run `CREATE_NEW` reservation before Spring/ingestion; an existing lock/report refuses rerun; uncertain failure leaves lock fail-closed. Regression test added. | Re-review PASS. |
| F-06 | Medium — Compose `ps --format json` output is JSON Lines, not an array (AC-02) | Positive preflight initially rejected the actual two-service project as unrecognized. | Attempt 1: bounded parser accepts JSON Lines and arrays; regression test added. | Positive preflight PASS. |
| F-07 | Medium — manifest could not deserialize `LocalDate` and CLI could not serialize report (AC-06) | Real candidate preflight failed before validation because bare Jackson mapper lacked Java-time module. | Attempt 1: added `jackson-datatype-jsr310`, registered modules in manifest loader/CLI, and valid typed manifest regression test. | Positive preflight PASS. |
| F-08 | Low — Checkstyle caught an unused import in a new test (verification) | Clean verify stopped before tests and identified `assertThat` as unused. | Attempt 1: removed the unused import; final clean verify passed. | Corrected and verified. |

## Test integrity

- Tests added/changed: bounded acquisition, OpenAI attempt/request parameters, manifest serialization/loading, policy/budget, loopback binding, Compose JSON Lines parsing, pending approval rejection, atomic run reservation and post-run cost/status assessment. No test removes an acceptance criterion.
- Demonstrated test defect, if any: no unresolved test defect; the unused import was a static-check failure, not a test change.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: regression tests assert the approved exact target/bounds and refuse unsafe cases; OpenAI tests use only a local controlled HTTP server. No external Finep/OpenAI network fixture or paid API test is used.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: provider billing has no verified hard USD cap; USD 1.00 remains a proposed, unapproved monitored budget; the key is present but its validity was not tested; reported token usage may be unavailable, in which case the runner escalates; successful ingestion still requires a human evidence review before PASS; this run made no real online Finep/OpenAI validation.
- Decisions made: D-021 updates the experimental validation policy; official pricing is documented in the manifest/report, not treated as immutable architecture.
- OpenSpec tasks marked complete and evidence: no task status changed in this preparation run; live tasks 2.1–2.3 and evidence-review tasks remain pending separate authorization/execution.
- Final verification after fixes: `mvn -B -f backend/pom.xml clean verify` passed (70 tests); frontend lint, typecheck, unit tests (24), and build passed; isolated Playwright passed (1); `docker compose config -q`, `git diff --check`, credential-pattern scan, and OpenSpec strict all passed; independent review PASS.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `PASS` for preparation. The candidate remains unapproved for live use; the separate approval reference and `--fomentiq.finep.live-validation.execute=true` are still required. No live Finep/OpenAI request, paid token use, commit, or push occurred.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.

No credentials, tokens or sensitive provider payloads belong in this report.
