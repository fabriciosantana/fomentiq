## 1. Source identity and persistence foundation (offline)

- [ ] 1.1 Define publisher/source-reference semantics in the domain and persistence design, including the distinct responsible agency and opportunity identities; acceptance: a source-native ID is scoped to its publisher and hashes/titles never become domain identity.
- [ ] 1.2 Add an additive Flyway migration and backfill for existing Finep records; acceptance: database-clean migration and upgrade-path Testcontainers tests pass, and Finep `databaseId=751` plus portal entry `751092` resolve to exactly one existing opportunity/source reference.
- [ ] 1.3 Extend persistence for source-specific snapshots, document references and version history; acceptance: source histories remain independently queryable and unchanged input is idempotent while changed bytes create a new version.

## 2. Multi-source orchestration (offline)

- [ ] 2.1 Generalize connector registration/selection and orchestration for explicitly configured sources; acceptance: Finep remains the same source identity and status/count semantics, with no network in automated tests.
- [ ] 2.2 Preserve independent `IngestionRun` creation/finalization and per-call relational transaction boundaries; acceptance: Testcontainers proves rollback isolation, durable terminal run status, retry safety and no inconsistent relational rows.
- [ ] 2.3 Add offline orchestration tests for multiple configured connectors and same external ID under distinct publishers; acceptance: no identity collision, and a failing source/call is attributable without losing separately committed work.

## 3. CNPq second connector (offline first)

- [ ] 3.1 Approve and freeze one supported public CNPq call's identity and sanitized page/document fixtures; acceptance: the official call has reviewed eligibility relevance, stable number/year identity, and fixtures contain no credentials or disallowed personal data.
- [ ] 3.2 Implement the narrow CNPq connector for discovery, linked public documents and bounded download; acceptance: fixture tests cover the selected page/document shape, identity, snapshots, hashes, limits and explicit unsupported/login-required failure.
- [ ] 3.3 Apply source-specific URL/redirect/public-address validation; acceptance: tests reject unapproved hosts, private/local destinations, unsafe redirects, oversized responses and unsupported content types.
- [ ] 3.4 Verify idempotency and document/call version changes using deterministic fixtures; acceptance: replay creates no duplicate call/version, while a changed fixture adds a version preserving prior bytes and provenance.
- [ ] 3.5 Run both source fixtures through the deterministic extraction provider and persistence; acceptance: extracted fields/evidence retain source document version and run attribution for Finep and CNPq, with no OpenAI or source network requests.

## 4. Conservative cross-source duplicate candidates (offline)

- [ ] 4.1 Implement only the approved candidate relation and retain distinct opportunity identities; acceptance: authoritative cross-reference or the approved exact normalized title plus matching deadline and year produces an explainable candidate, while fuzzy similarity/shared hash does not.
- [ ] 4.2 Add API/read-model DTO and tests for candidate evidence; acceptance: candidate basis is visible, no automatic merge occurs, and missing/ambiguous identity remains separate.

## 5. Unified API and catalog filters (offline)

- [ ] 5.1 Add responsible-agency and source-publisher filters to the existing bounded paginated API; acceptance: OpenAPI matches implementation, deterministic ordering/pagination remain, and parameterized queries prevent injection.
- [ ] 5.2 Update catalog DTO/client/UI with accessible source and agency filters; acceptance: both sources can be distinguished, filters compose with existing search/status/deadline/instrument/region/theme filters, and loading/error/empty states remain correct.
- [ ] 5.3 Add PostgreSQL/Testcontainers API integration tests with Finep and CNPq records in the same database; acceptance: both fixture flows' extracted fields/evidence are queryable, source/agency filtering returns only matching records, Finep `751` remains singular, and document/evidence links resolve to their owning opportunity.

## 6. Verification and optional live work

- [ ] 6.1 Run full offline verification: backend clean verify, frontend lint/typecheck/tests/build, Flyway clean/upgrade tests, Compose validation and strict OpenSpec validation; acceptance: all pass without network access to Finep, CNPq or OpenAI.
- [ ] 6.2 Complete independent review with spec, diff and test evidence; acceptance: separate-context review returns PASS or findings are corrected within the per-finding retry limit, with persistent report and residual risks.
- [ ] 6.3 (Optional, separately authorized) perform a bounded public CNPq smoke run; acceptance: explicit target, isolated data, sanitized report and approval are recorded before any request, and the result cannot be represented as broad source coverage.
