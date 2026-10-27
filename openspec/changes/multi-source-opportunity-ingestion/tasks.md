## 1. Source identity and persistence foundation (offline)

- [x] 1.1 Define `SourcePublisher`, `CallSourceReference`, and `CallFundingAgency` semantics; acceptance: source ID is scoped to publisher, multiple evidenced agencies are associated to one opportunity, and neither title nor hash becomes domain identity.
- [x] 1.2 Add additive Flyway migration/backfill; acceptance: clean and V3-upgrade Testcontainers pass, Finep `databaseId=751` plus portal entry `751092` remain exactly one opportunity/source reference, and `agency_id`/legacy API fields remain compatible.
- [x] 1.3 Extend persistence for source-reference-owned snapshots, documents and version history; acceptance: each publisher history is independently queryable and replay is idempotent while changed bytes create a version.

## 2. Multi-source orchestration (offline)

- [x] 2.1 Generalize connector registration/selection and orchestration for explicitly configured sources; acceptance: Finep remains the same source identity and status/count semantics, with no network in automated tests.
- [x] 2.2 Preserve independent `IngestionRun` creation/finalization and per-call relational transaction boundaries; acceptance: Testcontainers proves rollback isolation, durable terminal run status, retry safety and no inconsistent relational rows.
- [x] 2.3 Add offline orchestration tests for multiple configured connectors and same external ID under distinct publishers; acceptance: no identity collision, and a failing source/call is attributable without losing separately committed work.

## 3. CNPq second connector (offline first)

- [x] 3.1 Freeze the approved CNPq/CAPES call 30/2026 identity and sanitized public page/document fixtures; acceptance: `publisher=CNPQ`, `externalId=30/2026`, official URL, reviewed ICT-led innovation relevance, document inventory and per-file SHA-256 are preserved with no credentials or disallowed personal data.
- [x] 3.2 Implement the narrow CNPq connector for discovery, linked public documents and bounded download; acceptance: fixture tests cover the selected page/document shape, identity, snapshots, hashes, limits and explicit unsupported/login-required failure.
- [x] 3.3 Apply source-specific URL/redirect/public-address validation; acceptance: tests reject unapproved hosts, private/local destinations, unsafe redirects, oversized responses and unsupported content types.
- [x] 3.4 Verify idempotency and document/call version changes using deterministic fixtures; acceptance: replay creates no duplicate call/version, while a changed fixture adds a version preserving prior bytes and provenance.
- [x] 3.5 Run both source fixtures through the deterministic extraction provider and persistence; acceptance: extracted fields/evidence retain source document version and run attribution for Finep and CNPq, with no OpenAI or source network requests.

## 4. Conservative cross-source duplicate candidates (offline; deferred to a later increment)

- [ ] 4.1 (Deferred by approved Increment A decision; do not implement here.) Implement a separately approved candidate relation while retaining distinct opportunity identities; acceptance rule remains to be designed and approved. The title/deadline/year example is not approved for implementation.
- [ ] 4.2 (Deferred by approved Increment A decision; do not implement here.) Add API/read-model DTO and tests for candidate evidence after task 4.1 is approved and implemented; no automatic merge is ever permitted.

## 5. Unified API and catalog filters (offline)

- [x] 5.1 Add responsible-agency and source-publisher filters to the existing bounded paginated API; acceptance: OpenAPI matches implementation, deterministic ordering/pagination remain, and parameterized queries prevent injection.
- [x] 5.2 Update catalog DTO/client/UI with accessible source and agency filters; acceptance: both sources can be distinguished, filters compose with existing search/status/deadline/instrument/region/theme filters, and loading/error/empty states remain correct.
- [x] 5.3 Add PostgreSQL/Testcontainers API integration tests with Finep and CNPq records in the same database; acceptance: both fixture flows' extracted fields/evidence are queryable, source/agency filtering returns only matching records, Finep `751` remains singular, and document/evidence links resolve to their owning opportunity.

## 6. Verification and optional live work

- [x] 6.1 Run full offline verification: backend clean verify, frontend lint/typecheck/tests/build, Flyway clean/upgrade tests, Compose validation, offline Playwright and strict OpenSpec validation; acceptance: all pass without network access to Finep, CNPq or OpenAI.
- [x] 6.2 Complete independent review with spec, diff and test evidence; acceptance: separate-context review returns PASS or findings are corrected within the per-finding retry limit, with persistent report and residual risks.
- [ ] 6.3 (Optional, separately authorized) perform a bounded public CNPq smoke run; acceptance: explicit target, isolated data, sanitized report and approval are recorded before any request, and the result cannot be represented as broad source coverage.
