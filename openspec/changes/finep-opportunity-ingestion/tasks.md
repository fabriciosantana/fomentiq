# Tasks

## 1. Repository foundation
- [x] 1.1 Create `backend/` Spring Boot project and module boundaries.
- [x] 1.2 Create `frontend/` React + TypeScript project.
- [x] 1.3 Create Docker Compose with PostgreSQL/pgvector and S3-compatible storage.
- [x] 1.4 Configure CI for backend, frontend and OpenSpec validation.

## 2. Domain and persistence
- [x] 2.1 Implement `FundingAgency`, `FundingProgram`, `CallForProposals`, `CallVersion`.
- [x] 2.2 Implement `CallDocument`, `CallDocumentVersion`.
- [x] 2.3 Implement `ExtractedField`, `Evidence`, `IngestionRun`, including required UTC `createdAt` and `ingestionRunId` FK; preserve previous values/states and original attribution on idempotent reprocessing.
- [x] 2.4 Add Flyway migrations and repository integration tests.

## 3. Source ingestion
- [ ] 3.1 Define minimal `SourceConnector` port.
- [x] 3.2 Freeze Finep PROINFRA 2024 Expansão (historical ID 751, current entry 751092) source corpus before task 3.3: sanitized HTML, official API JSON, 18 PDFs, SHA-256 manifest, reviewed field/evidence candidates, fixture-local deterministic response and synthetic change variant per `FINEP_GOLDEN_FIXTURE.md`. Captured and verified on 2026-10-23; D-013 records private visibility confirmed by the owner. Production schema/provider adaptation remains in tasks 4.1–4.3.
- [ ] 3.3 Implement Finep discovery for configured current portal entry/API, preserving historical ID 751 via databaseId and portal entry 751092; replay frozen discovery HTML/JSON in tests.
- [ ] 3.4 Persist source snapshot before extraction.
- [ ] 3.5 Discover linked call documents through the current portal document API used by the page, including its operational token flow without hardcoded credentials; preserve response JSON and replay the frozen document list in offline tests.
- [ ] 3.6 Download, validate, hash and store documents.
- [ ] 3.7 Detect unchanged versus new versions idempotently.
- [ ] 3.8 Record `IngestionRun` status and metrics.

## 4. Structured extraction
- [ ] 4.1 Define typed extraction schema for MVP fields and map fixture-local expected/provider candidates to it without changing source-backed facts.
- [ ] 4.2 Define `StructuredExtractionProvider` port.
- [ ] 4.3 Implement OpenAI infrastructure adapter using only environment configuration (`OPENAI_API_KEY`, `OPENAI_MODEL`, `EXTRACTION_TIMEOUT_SECONDS`, `EXTRACTION_MAX_RETRIES`), explicit failure behavior and a deterministic test provider consuming the mapped frozen candidate response; keep domain vendor-independent.
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
