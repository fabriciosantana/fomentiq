# Tasks

## 1. Offline preflight and isolation

- [ ] 1.1 Create a per-run manifest template and preflight checklist covering approved call identity/URL, document and byte ceilings, configured model/schema, total provider-attempt cap, spend cap, isolated database/storage targets and required environment-variable presence without values; verify the checklist is complete before any external request.
- [ ] 1.2 Prepare a uniquely named disposable Compose/database/object-storage environment and verify its effective JDBC target, S3-compatible endpoint/bucket and cleanup scope are separate from the manual-demo volumes; verify a failed isolation check stops before network access.
- [ ] 1.3 Obtain explicit human approval for the exact manifest values and record the approval reference in the sanitized run record; verify no value is inferred from this change's planning approval.

## 2. Bounded live integration

- [ ] 2.1 Run one Finep discovery for the approved external ID/canonical URL in the isolated environment; verify the discovered identity is exact, source snapshots are persisted before extraction, and no other opportunity is ingested.
- [ ] 2.2 Download only approved linked documents; verify redirects remain within the configured public-host policy, content type/size/hash checks pass, document versions and original bytes are stored in the isolated bucket, and failures leave an auditable terminal `IngestionRun`.
- [ ] 2.3 Execute the approved real OpenAI extraction with the configured model/schema and request-attempt ceiling; verify the response is schema-valid, citations match supplied excerpts, usage is captured when reported, no fixture fallback occurs, and the approved spend cap is not exceeded.

## 3. Offline regression and review

- [ ] 3.1 Review every critical field returned by the live run against its exact preserved document version/quote/locator; verify unsupported values remain `MISSING`, unresolved divergence remains `CONFLICT`, and no field is reported as confirmed solely from model output.
- [ ] 3.2 Run isolated PostgreSQL/Testcontainers deterministic regressions for identical-input idempotency and the existing synthetic changed-call variant; verify unchanged versions are not duplicated and earlier call/document/field/evidence history and run attribution remain preserved, without another paid provider request.
- [ ] 3.3 Produce a sanitized validation report with UTC duration, run status/ID, target identity, document/field counts, model/schema, reported token usage, official pricing URL/date/calculation, failures, reviewer outcome, residual risks and exactly one terminal `PASS`, `FAIL` or `ESCALATE`; verify credentials and sensitive provider payloads are absent.
- [ ] 3.4 Independently review the report and acceptance evidence, then stop/remove only the dedicated validation Compose project and volumes; verify the manual-demo/operational datasets are unchanged and no validation service or temporary secret remains.
