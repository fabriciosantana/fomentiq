# Design

## Context

See `proposal.md` for motivation and `specs/finep-live-validation/spec.md` for acceptance behavior. The v0.1.0 runtime is a Spring modular monolith. `FinepIngestionService` orchestrates a `SourceConnector`; a separate per-call `REQUIRES_NEW` unit persists the call, snapshots, document versions and extraction; `IngestionRunLifecycle` persists run status in independent transactions. PostgreSQL and S3-compatible storage are not one distributed transaction. The connector uses configured Finep URLs and fail-closed host/redirect policy. OpenAI is called through the existing structured provider; reported usage metadata is available when returned.

## Goals / Non-Goals

**Goals:**
- Establish one bounded live observation of a user-approved Finep opportunity and one budgeted real structured-extraction request.
- Keep all write targets disposable and isolated from the local manual-demo data and any operational dataset.
- Separate evidence from live integration from deterministic offline evidence for idempotency/version semantics.
- Produce one sanitized, independently reviewable result record with objective terminal status.

**Non-Goals:**
- Build a new provider, cache, generic live-test framework, operational dashboard or production environment.
- Change the database schema, public API, golden fixture, transaction boundaries or live source policy.
- Automatically retry a paid request, ingest multiple opportunities, or treat this plan as authorization to access external services.

## Decisions

### D1 — Require a preflight run manifest
Before network access, require a reviewed manifest naming one Finep external ID/canonical URL, allowed document count and aggregate bytes, configured model/schema, maximum total OpenAI attempts (including automatic retries), and maximum spend. If the source identity cannot be confirmed, stop before the call. This keeps the run reproducible and enforces the user's per-run authorization boundary. Alternative considered: use the golden fixture's URL as an implicit live target; rejected because it may no longer be current or approved for a live run.

### D2 — Isolate with existing local infrastructure
Use the existing Docker Compose PostgreSQL and S3-compatible service under a unique Compose project, database/volumes and bucket, with an explicit validation-only environment. Verify effective JDBC URL and storage endpoint/bucket before starting ingestion. Reject command-line and Spring/Fomentiq environment or system-property overrides, including properties whose names are consumed as environment placeholders, so they cannot supersede manifest-bound targets or limits; reject storage endpoint userinfo, query, fragment and non-root paths. Do not point this execution at the manual evaluation volumes or any operational system. Tear down only the uniquely named validation resources after exporting sanitized measurements. Alternative considered: add a new cloud staging service; rejected because Compose can provide the necessary separation without new infrastructure.

### D3 — Separate live integration from repeatable persistence evidence
Use the live run once for real discovery, bounded document retrieval and the approved OpenAI request. Verify idempotency and changed-version preservation with the existing deterministic PostgreSQL/Testcontainers path and synthetic fixture variant; do not send another paid request merely to repeat unchanged content. The report must label which claims are live and which are offline. A second paid call is allowed only if it is explicitly included in a later run manifest.

### D4 — Reuse the existing transaction and evidence model
Do not change `IngestionRun` lifecycle, per-call transaction, field provenance or object-store abstraction for this validation. Confirm the run reaches a terminal status and relational writes match the committed result; document that an object upload preceding SQL rollback can leave an unreferenced content-addressed object in the isolated bucket. No cleanup behavior is added.

### D5 — Treat evidence review and monitored cost accounting as gates
Review every critical extracted value against exact preserved source/version excerpts after extraction. Before the call, estimate the maximum cost from token ceilings and dated official pricing and require an explicitly approved per-run budget. Afterward, calculate estimated cost from provider-reported input/output usage; do not infer actual usage from request size. Missing usage or a budget overrun is `ESCALATE`; there is one total attempt and no automatic retry.

### D6 — Validate before Spring initializes persistence
The explicit mode is a private command-line entrypoint intercepted before Spring starts. It validates the manifest and approval reference, compares effective runtime targets, inspects the uniquely named Compose containers and mounted volumes, then verifies PostgreSQL identity and an unused S3 bucket read-only. A preflight-only mode reports readiness without starting Spring, Flyway, Finep or OpenAI. A later live invocation must separately pass the exact approved manifest/reference and an explicit execution flag. Normal startup has neither a live-run hook nor an HTTP trigger. This is scoped internal tooling; it adds no public API, schema, scheduler or service.

### D7 — Carry one immutable budget through the ingestion path
The manifest is parsed into an immutable typed policy and passed through the existing Finep ingestion service/connector/transaction path. Finep responses are consumed with byte-bounded subscribers; document count and aggregate bytes are checked before each next download. Over-limit data is rejected, never stored as a successful truncated artifact. Existing call transaction and independent `IngestionRun` lifecycle remain unchanged.

### D8 — Monitor an approved experimental budget without claiming a provider cap
The runner uses configured input/output token ceilings and the total-attempt limit with dated official pricing to produce a conservative pre-call estimate. A separately approved estimated budget may authorize one experimental call even though the provider has no verified server-side USD cap. The implementation SHALL label the amount as an estimate, disable automatic retries, persist provider-reported usage, calculate estimated cost, and stop/escalate on missing usage or an overrun. No code or report describes this as a guaranteed billing cap.

## Risks / Trade-offs

- [The Finep public page, OAuth bootstrap or document API may change] → fail closed, preserve sanitized failure evidence, and do not broaden host policy during the run.
- [One opportunity may not exercise all extraction fields] → score only fields present in the bounded source; keep unsupported values missing/conflicting and list coverage gaps.
- [A second full ingestion could incur duplicate provider cost] → default to a single live model request and use deterministic replay for idempotency; count all automatic retries in the cap.
- [PostgreSQL rollback cannot roll back a completed S3-compatible upload] → use a dedicated disposable bucket/project and document any unreferenced objects; do not delete shared data.
- [Provider price or usage reporting changes] → require dated official pricing in the manifest; record reported usage; stop/escalate if usage is missing or the estimate exceeds the approved experimental budget.
- [Docker metadata/driver/storage identity cannot be verified before Spring refresh] → reject the command before JPA/Flyway and require a supported isolated local Compose runtime.
- [Actual billing may differ from the pre-call estimate because it is not a provider-enforced cap] → disclose this in the approval/report; use one attempt, no retries, bounded tokens, and monitor provider-reported usage. The residual billing variance remains an accepted experimental risk, not a guaranteed limit.

## Migration Plan

No schema or deployment migration. Before the approved run, capture preflight configuration evidence without secret values. After the run, export a sanitized record, inspect the final run state and data isolation, then stop/remove only that unique local Compose project and its disposable volumes. Do not touch the manual demo project's persistent volumes.

## Open Questions

The default numerical ceilings below are engineering proposals grounded in the frozen fixture and current implementation. A future online run still requires explicit approval of the exact manifest and execution. The approved spending value is an estimated budget, not a provider-enforced financial cap.

## Proposed safety maxima (per run)

- Target: Finep external ID `751`, portal entry `751092`, canonical URL exactly `https://www.finep.gov.br/e/chamada-publica/222684/751092`; host `www.finep.gov.br` only.
- Documents: 18 maximum, matching the frozen fixture; 10 MiB per document; 25 MiB aggregate as implementation safety maxima. The fixture contains 18 PDFs totaling about 5.16 MiB, largest about 1.02 MiB.
- Finep discovery response: 1 MiB; metadata/document-catalog response: 2 MiB; OAuth page 2 MiB and token response 64 KiB.
- Timeouts: connect 10 s, response/body read 30 s, per-operation 45 s, whole run 15 minutes. All values are additionally capped by hard-coded safe maxima.
- OpenAI safety maxima: model `gpt-6-luna` as a proposed default but manifest-bound; schema `finep-call-v1`; at most three attempts including retries; at most 64,000 UTF-8 input bytes as a conservative input-token upper bound; at most 12,000 output tokens.
- Spend: proposed at no more than USD 1.00 for this run, estimated from the configured token ceilings and official Standard short-context pricing. This is not a hard cap. It must be explicitly approved with the exact manifest; if estimated cost exceeds it, stop before external access; if usage is missing or reported usage estimates a cost above the budget, stop and escalate without retry.
- Report and approval reference: generated report contains no credentials, raw response or storage keys; manifest path and approval reference must match the command.

These numbers define implementation safety maxima, not an approved online manifest. Based on the frozen fixture (18 PDFs, 5,406,555 aggregate bytes, largest PDF 1,069,861 bytes; discovery 10,013 bytes; call metadata 9,526 bytes; document list 53,538 bytes), propose an execution manifest of at most 18 documents, 5 MiB per document and 12 MiB aggregate; 256 KiB discovery and document metadata; 2 MiB OAuth page and 64 KiB token response. Keep 10 seconds connect, 30 seconds read, 45 seconds per operation and 900 seconds global; use one model attempt, 64,000 conservative input-token bytes, 12,000 output tokens, and 150 total HTTP hops (worst-case acquisition: 3 authenticated API operations with bootstrap/token/response and 18 files, each allowing up to four redirects). The pricing date/rates and maximum USD budget must be supplied from a reviewed official pricing source; no numeric spend amount is approved here. These candidate values remain subject to an exact human-approved manifest and the hard-spend-cap gate.
