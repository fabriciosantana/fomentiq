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
Expose a `StructuredExtractionProvider` port returning a typed candidate object plus evidence locators.

### D5 — Deterministic critical validation
Validate deadlines, monetary values, TRL bounds and evidence presence in application code. LLM confidence is informative, not authoritative.

### D6 — Modular monolith
Keep ingestion, documents, extraction and funding catalog as modules in one Spring Boot deployment.

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
  → ExtractedField + Evidence
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

1. Parser unit tests against versioned Finep fixtures.
2. Integration tests with PostgreSQL/Testcontainers.
3. Object storage contract tests.
4. End-to-end backend fixture ingestion.
5. API contract tests.
6. Frontend component tests.
7. Playwright critical flow.

## Migration Plan

Greenfield. Create schema with Flyway. All future schema changes use additive migrations.

## Risks / Trade-offs

- **Finep HTML changes** → isolate selectors in adapter and monitor parser failures.
- **PDF extraction quality** → preserve original and allow reprocessing.
- **LLM non-determinism** → schema validation + golden fixtures + provenance.
- **Premature abstraction** → restrict generic contracts to proven needs.
