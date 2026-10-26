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
Use the existing Docker Compose PostgreSQL and S3-compatible service under a unique Compose project, database/volumes and bucket, with an explicit validation-only environment. Verify effective JDBC URL and storage endpoint/bucket before starting ingestion. Do not point this execution at the manual evaluation volumes or any operational system. Tear down only the uniquely named validation resources after exporting sanitized measurements. Alternative considered: add a new cloud staging service; rejected because Compose can provide the necessary separation without new infrastructure.

### D3 — Separate live integration from repeatable persistence evidence
Use the live run once for real discovery, bounded document retrieval and the approved OpenAI request. Verify idempotency and changed-version preservation with the existing deterministic PostgreSQL/Testcontainers path and synthetic fixture variant; do not send another paid request merely to repeat unchanged content. The report must label which claims are live and which are offline. A second paid call is allowed only if it is explicitly included in a later run manifest.

### D4 — Reuse the existing transaction and evidence model
Do not change `IngestionRun` lifecycle, per-call transaction, field provenance or object-store abstraction for this validation. Confirm the run reaches a terminal status and relational writes match the committed result; document that an object upload preceding SQL rollback can leave an unreferenced content-addressed object in the isolated bucket. No cleanup behavior is added.

### D5 — Treat evidence review and cost accounting as gates
Review every critical extracted value against exact preserved source/version excerpts after extraction. Derive cost only from provider-reported usage and the official pricing effective on the run date. If usage/cost cannot be bounded, do not infer it from request size; classify `ESCALATE`.

## Risks / Trade-offs

- [The Finep public page, OAuth bootstrap or document API may change] → fail closed, preserve sanitized failure evidence, and do not broaden host policy during the run.
- [One opportunity may not exercise all extraction fields] → score only fields present in the bounded source; keep unsupported values missing/conflicting and list coverage gaps.
- [A second full ingestion could incur duplicate provider cost] → default to a single live model request and use deterministic replay for idempotency; count all automatic retries in the cap.
- [PostgreSQL rollback cannot roll back a completed S3-compatible upload] → use a dedicated disposable bucket/project and document any unreferenced objects; do not delete shared data.
- [Provider price or usage reporting changes] → record pricing URL/date and reported usage; stop/escalate if cost cannot be bounded.

## Migration Plan

No schema or deployment migration. Before the approved run, capture preflight configuration evidence without secret values. After the run, export a sanitized record, inspect the final run state and data isolation, then stop/remove only that unique local Compose project and its disposable volumes. Do not touch the manual demo project's persistent volumes.

## Open Questions

None that block implementation planning. The exact opportunity, document/byte limits, request-attempt limit and spend ceiling are deliberately per-run authorization inputs and must be approved before the external task is executed.
