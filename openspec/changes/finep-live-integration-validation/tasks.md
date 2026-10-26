# Tasks

## 1. Offline preflight and isolation

- [x] 1.1 Create a per-run manifest template and preflight checklist covering approved call identity/URL, document and byte ceilings, configured model/schema, total provider-attempt cap, spend cap, isolated database/storage targets and required environment-variable presence without values; verify the checklist is complete before any external request.
- [x] 1.2 Implement pre-context fail-closed isolation validation for the effective JDBC/database/user and S3 endpoint/bucket against the actual uniquely named Compose project, container labels, ports and mounted volume identities; reject command-line, Spring/Fomentiq and JVM system-property overrides (including environment-placeholder names), plus unsafe S3 endpoint URI components; acceptance: positive isolated setup passes and negative/missing/ambiguous/shared/overridden targets exit before Flyway, external requests or unauthorized writes.
- [ ] 1.3 Obtain explicit human approval for the exact manifest values and record the approval reference in the sanitized run record; verify no value is inferred from this change's planning approval.
- [x] 1.4 Implement a versioned typed validation manifest and explicit private CLI runner; acceptance: malformed/missing manifest or approval mismatch exits nonzero before Spring data initialization, preflight-only reports readiness without external requests, normal app startup performs no live run, explicit live execution requires the matching approval reference and execution flag, no HTTP ingestion endpoint exists, and report/exit code are sanitized and deterministic.
- [x] 1.5 Enforce per-document/count/aggregate/metadata byte ceilings, operation/read/connect/global timeouts, model/schema/token/attempt bounds and pre-call cost estimation; acceptance: streaming over-limit, timeout, retry-overflow, budget-overrun and missing-usage cases stop safely and are tested without real external calls; estimates are never represented as hard billing caps.

## 2. Bounded live integration

- [ ] 2.1 Run one Finep discovery for the approved external ID/canonical URL in the isolated environment; acceptance: identity is exact, only that call is persisted, snapshots precede extraction, and every response/count/deadline limit is enforced.
- [ ] 2.2 Download only approved linked documents; acceptance: redirect policy remains fail-closed, streamed bytes obey per-file/aggregate caps without truncation, document versions/original bytes are stored only in isolated storage, and failures finalize an auditable run.
- [ ] 2.3 Execute one real OpenAI extraction only after separate approval of the exact manifest and monitored estimated budget; acceptance: configured model/schema/tokens/timeout and one total attempt are enforced, automatic retries are disabled, provider usage and estimated cost are captured when reported, and missing usage or budget overrun escalates without another request. No hard provider spend cap is claimed.

## 3. Offline regression and review

- [ ] 3.1 Review every critical field returned by the live run against its exact preserved document version/quote/locator; verify unsupported values remain `MISSING`, unresolved divergence remains `CONFLICT`, and no field is reported as confirmed solely from model output.
- [ ] 3.2 Run isolated PostgreSQL/Testcontainers deterministic regressions for identical-input idempotency and the existing synthetic changed-call variant; verify unchanged versions are not duplicated and earlier call/document/field/evidence history and run attribution remain preserved, without another paid provider request.
- [ ] 3.3 Produce a sanitized validation report with UTC duration, run status/ID, target identity, document/field counts, model/schema, reported token usage, official pricing URL/date/calculation, failures, reviewer outcome, residual risks and exactly one terminal `PASS`, `FAIL` or `ESCALATE`; verify credentials and sensitive provider payloads are absent.
- [ ] 3.4 Independently review the report and acceptance evidence, then stop/remove only the dedicated validation Compose project and volumes; verify the manual-demo/operational datasets are unchanged and no validation service or temporary secret remains.
