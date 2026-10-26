# Proposal

## Why

The v0.1.0 domain and API demonstrate one Finep source, but the current ingestion coordinator, persistence adapter and source identifiers still assume Finep. To consolidate opportunities from more than one publisher without confusing publisher, funding agency and opportunity identity, the proven single-source path needs the smallest explicit multi-source extension.

## What Changes

- Generalize the current orchestration and connector contracts only for the requirements demonstrated by Finep and one second source, while preserving `IngestionRun` audit and per-call transaction boundaries.
- Represent source publisher and source-specific external identity separately from the responsible funding agency and `CallForProposals`.
- Define source-specific discovery, artifact, document and version identity; do not use content hashes or titles as domain identity.
- Keep uncertain cross-source duplicate opportunities separate and expose conservative duplicate candidates without automatic merging.
- Add a CNPq connector as the second source, with deterministic fixture-driven tests and an optional separately approved public-source smoke run.
- Unify persisted opportunities in the same PostgreSQL catalog/API and add agency/source filters to the existing catalog contract and UI.
- Verify both connectors in one database/API with Testcontainers and offline fixtures; CI must not require CNPq, Finep or OpenAI access.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `opportunity-ingestion`: support multiple publishers, source-specific identities and versions, conservative duplicate candidates, and a catalog filterable by funding agency and publisher.

## Impact

Expected areas include `ingestion` ports/orchestration, Finep and CNPq infrastructure adapters, `funding` identity/persistence migrations, run lifecycle, search/API/OpenAPI, and frontend catalog filters. PostgreSQL, Flyway, the modular monolith and the S3-compatible store remain. No matching, microservices, queues, new storage/search infrastructure, submission workflow, authentication changes, or automatic opportunity fusion are proposed. Any live CNPq smoke run or OpenAI extraction requires its own explicit execution authorization; this planning change performs neither.
