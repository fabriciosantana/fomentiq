# Design: Finep Opportunity Ingestion

## Context

See `proposal.md` for motivation. The repository starts from a greenfield MVP using React, Spring Boot, PostgreSQL and S3-compatible object storage.

## Goals

- Establish the minimum reusable ingestion architecture.
- Preserve source evidence before interpretation.
- Keep extraction provider-independent.
- Make ingestion deterministic and testable with fixtures.
- Deliver one end-to-end vertical slice.

## Non-Goals

- Build a generic crawler framework.
- Optimize for high-volume distributed processing.
- Infer company eligibility or matching scores.
- Introduce microservices.

## Decisions

### D1 — Adapter per source
Use a `SourceConnector` application port and a Finep adapter. Do not over-generalize HTML selectors into a universal scraping DSL yet.

Alternative considered: generic crawler configuration. Rejected because one source is insufficient to discover stable abstractions.

### D2 — Evidence first
Persist raw page/document artifacts before structured extraction.

Alternative: extract directly from remote content. Rejected because source mutation would destroy auditability and reproducibility.

### D3 — SHA-256 change detection
Use cryptographic content hash to deduplicate document versions and source snapshots.

### D4 — Provider-independent extraction
Expose a `StructuredExtractionProvider` port returning typed candidates plus evidence locators. Use an infrastructure `OpenAiStructuredExtractionProvider` with Responses API and strict Structured Outputs; no vendor SDK/types in domain code. Configure exclusively through `OPENAI_API_KEY`, `OPENAI_MODEL`, `EXTRACTION_REASONING_EFFORT`, `EXTRACTION_TIMEOUT_SECONDS` and `EXTRACTION_MAX_RETRIES`. Defaults are model `gpt-6-luna` and effort `low`. Fail explicitly on missing configuration/provider failure, preserve raw output for audit, and never silently substitute test data. Record model/schema metadata, token usage and run correlation without secrets. Offline tests inject a deterministic provider.

### D5 — Deterministic critical validation
Validate deadlines, monetary values, TRL bounds and evidence presence in application code. LLM confidence is informative, not authoritative.

### D6 — Modular monolith
Keep ingestion, documents, extraction and funding catalog as modules in one Spring Boot deployment.

### D7 — Named golden fixture
Use **PROINFRA 2024 Expansão**, Finep external ID `751`, following [the fixture contract](../../../FINEP_GOLDEN_FIXTURE.md). The corpus was frozen on 2026-10-23 from the current portal entry `751092`; its official `databaseId=751` preserves identity. It contains discovery/call HTML, official discovery/call/document JSON, 18 PDFs, source URLs, capture timestamps, SHA-256 manifest and reviewed field/evidence candidates. Documents are loaded dynamically through the public page API/token flow; a bare document-list request returns 403. Capture the API responses as evidence rather than assuming links exist in initial HTML. Offline tests replay those responses without tokens/network. The HTML fixtures remove script elements and redact auth parameters because the page embeds credentials; manifest records the transformation and both source/stored hashes. JSON/PDF bytes remain unchanged. This documented test-fixture exception does not permit losing production evidence. The fixture-local deterministic candidate response must be adapted to the production schema in tasks 4.1–4.3. Do not silently switch calls.

### D8 — Extraction execution audit
Persist required `ExtractedField.createdAt` (UTC) and `ingestionRunId` (FK), alongside call version, method and evidence. `IngestionRun` covers collection, extraction and validation, including internal reprocessing; a separate `ExtractionRun` is unnecessary in this slice. Changed values/states create new audit records preserving previous fields/evidence. Identical reprocessing does not duplicate fields or reassign their original run; the new run still records its own outcome.

### D9 — Read-only public API surface
Expose catalog reads only. Ingestion runs via scheduler or internal operator command, with no HTTP trigger/reprocessing endpoint, admin UI or login. Authentication is required before any future administrative operation is exposed; such exposure requires a separate approved change.

### D10 — Bounded, evidence-grounded extraction input
Preserve original PDF versions, extract text page-by-page, prioritize amendment/notice/clarification documents and select a bounded set of relevant excerpts before provider invocation. Initial limits: six documents, 24 excerpts, 1,400 characters per excerpt and 24,000 characters total. A provider citation is usable only if its segment was supplied and its quote occurs verbatim in that segment. Never send all PDFs or an unbounded document body in one request.

## Data flow

```text
Scheduler
  → FinepSourceConnector.discover
  → fetch source snapshot
  → hash / compare
  → persist CallVersion
  → discover documents
  → download / hash / persist versions
  → text extraction
  → StructuredExtractionProvider
  → deterministic validators
  → ExtractedField (createdAt, ingestionRunId) + Evidence
  → API
  → React UI
```

## Failure handling

- Remote source unavailable → ingestion run failed/retriable; previous data remains valid.
- Document download failure → call remains visible with explicit incomplete state.
- Extraction failure → raw source remains preserved; mark extraction pending/failed.
- Conflicting critical values → mark `CONFLICT`; do not choose silently.
- Missing evidence → critical field cannot become `VALIDATED`.

## Security

- Allowlist/validate outbound hosts used by configured source connectors.
- Prevent arbitrary URL fetching from user input.
- Enforce download size/type limits.
- Store secrets outside Git.

## Test strategy

1. Parser unit tests against the frozen Finep 751 package, fixed clock and deterministic provider, with no Finep/OpenAI network access.
2. Integration tests with PostgreSQL/Testcontainers.
3. Object storage contract tests.
4. End-to-end backend fixture ingestion.
5. API contract tests.
6. Frontend component tests.
7. Playwright critical flow.
8. Audit FK/timestamp and changed/unchanged reprocessing integration tests.
9. Separate real Finep/OpenAI demonstration with environment configuration; not a CI dependency.
10. Contract check that no administrative mutation endpoints are exposed.

## Migration Plan

Greenfield. Create schema with Flyway. All future schema changes use additive migrations.

## Risks / Trade-offs

- **Finep HTML changes** → isolate selectors in adapter and monitor parser failures.
- **PDF extraction quality** → preserve original and allow reprocessing.
- **LLM non-determinism** → schema validation + golden fixtures + provenance.
- **Premature abstraction** → restrict generic contracts to proven needs.
