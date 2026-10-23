# Tasks

## 1. Repository foundation
- [ ] 1.1 Create `backend/` Spring Boot project and module boundaries.
- [ ] 1.2 Create `frontend/` React + TypeScript project.
- [ ] 1.3 Create Docker Compose with PostgreSQL/pgvector and S3-compatible storage.
- [ ] 1.4 Configure CI for backend, frontend and OpenSpec validation.

## 2. Domain and persistence
- [ ] 2.1 Implement `FundingAgency`, `FundingProgram`, `CallForProposals`, `CallVersion`.
- [ ] 2.2 Implement `CallDocument`, `CallDocumentVersion`.
- [ ] 2.3 Implement `ExtractedField`, `Evidence`, `IngestionRun`, including required UTC `createdAt` and `ingestionRunId` FK; preserve previous values/states and original attribution on idempotent reprocessing.
- [ ] 2.4 Add Flyway migrations and repository integration tests.

## 3. Source ingestion
- [ ] 3.1 Define minimal `SourceConnector` port.
- [ ] 3.2 Before task 3.3, resolve official artifact access and freeze Finep PROINFRA 2024 Expansão (ID 751) HTML/PDFs, SHA-256 manifest, reviewed expected values/evidence and deterministic provider response per `FINEP_GOLDEN_FIXTURE.md`; capture remains pending. Check repository visibility decision D-013 before adding artifacts.
- [ ] 3.3 Implement Finep discovery for configured source entry point.
- [ ] 3.4 Persist source snapshot before extraction.
- [ ] 3.5 Discover linked call documents.
- [ ] 3.6 Download, validate, hash and store documents.
- [ ] 3.7 Detect unchanged versus new versions idempotently.
- [ ] 3.8 Record `IngestionRun` status and metrics.

## 4. Structured extraction
- [ ] 4.1 Define typed extraction schema for MVP fields.
- [ ] 4.2 Define `StructuredExtractionProvider` port.
- [ ] 4.3 Implement OpenAI infrastructure adapter using only environment configuration (`OPENAI_API_KEY`, `OPENAI_MODEL`, `EXTRACTION_TIMEOUT_SECONDS`, `EXTRACTION_MAX_RETRIES`), explicit failure behavior and a deterministic test provider; keep domain vendor-independent.
- [ ] 4.4 Persist raw extraction response where appropriate for audit/debug.
- [ ] 4.5 Persist evidence locators for extracted critical fields.
- [ ] 4.6 Implement deterministic validation.
- [ ] 4.7 Implement `CONFLICT`, `MISSING`, `EXTRACTED`, `VALIDATED` states.

## 5. API
- [ ] 5.1 Implement paginated opportunity list endpoint.
- [ ] 5.2 Implement filters for status, deadline, instrument, region and theme.
- [ ] 5.3 Implement opportunity detail endpoint.
- [ ] 5.4 Implement document/version history endpoint.
- [ ] 5.5 Implement evidence endpoint/representation.
- [ ] 5.6 Publish read-only OpenAPI contract; verify no administrative mutation/ingestion endpoints. Use scheduler/internal command, with no login in this slice.

## 6. Frontend
- [ ] 6.1 Implement opportunity catalog page.
- [ ] 6.2 Implement filters and search.
- [ ] 6.3 Implement opportunity detail page.
- [ ] 6.4 Display validation state and source provenance for critical fields.
- [ ] 6.5 Implement documents and version timeline.
- [ ] 6.6 Add link to original official source.

## 7. Verification
- [ ] 7.1 Backend unit/integration tests pass.
- [ ] 7.2 Frontend lint/typecheck/tests pass.
- [ ] 7.3 Playwright flow catalog → detail → evidence passes.
- [ ] 7.4 Reprocessing same fixture creates no duplicate version.
- [ ] 7.5 Modified fixture creates a new version.
- [ ] 7.6 Critical field without evidence cannot be `VALIDATED`.
- [ ] 7.7 OpenSpec validation/verify passes.
- [ ] 7.8 Document reproducible offline fixture demo and separate real Finep/OpenAI execution, with environment prerequisites and accurate failure reporting.
- [ ] 7.9 Verify every extracted field, including MISSING, has createdAt and a valid ingestionRunId; changed reprocessing preserves history and identical reprocessing preserves original attribution.
