# Fomentiq Phase 2 — proposed scope

**Status:** planning proposal only, based on the `v0.1.0` baseline. This document does not authorize implementation, external calls, paid model usage, production writes, or changes to the MVP.

## Baseline and architecture diagnosis

The v0.1.0 release is an offline-validated Finep MVP. Its modular monolith already has a `SourceConnector` port for discovery, document discovery and download, but the only adapter and orchestration path are Finep-specific. Persistence resolves `FundingAgency` as FINEP, and `CallForProposals` stores the Finep `externalId` and `portalEntryId` without a separate publisher/source-reference model. The ingestion run is source-labelled, and the established transaction design persists run audit independently from each call's relational transaction. The catalog API and frontend expose existing opportunity filters but do not distinguish source publisher from responsible agency.

The smallest credible Phase 2 is therefore two independent OpenSpec changes: first validate one bounded real Finep run and real provider output in isolated test resources; separately evolve the existing modular ingestion path to support one additional public source, starting with deterministic offline tests. The online validation is evidence about the current connector/provider, not a prerequisite for changing source identity. The multi-source work may proceed against the offline Finep fixture even if live Finep validation is escalated, but any source/model requests remain separately approved.

The existing modular monolith, PostgreSQL/Flyway, S3-compatible storage, domain/provider boundary, provenance rules, URL safety and transaction boundaries remain constraints. No general scraping framework, microservices, event bus, queues, search cluster or infrastructure expansion is proposed.

## Change A — `finep-live-integration-validation`

Purpose: assess a single specifically approved Finep opportunity and bounded document set through the real Finep connector and configured OpenAI provider, then review critical facts against preserved evidence.

Proposed acceptance outcomes:

- A run manifest defines exact target identity/URL, host and document/byte limits, model/schema, attempt ceiling, time/spend budget, isolated database and bucket, and the human approval reference before network access.
- Discovery, source snapshots, allowed document downloads, storage and one bounded structured extraction are recorded in an isolated disposable environment. No live request falls back to fixture/provider data.
- A human checks every critical extracted field against its exact document version, quote and locator. Unsupported fields remain missing and unresolved source divergence remains conflicting.
- Offline deterministic tests prove repeat idempotency, changed-input version preservation, audit attribution and transaction behavior without another paid call.
- A sanitized report records run ID/status, elapsed time, counts, configured model/schema, provider-reported usage, cost calculation/source/date, failures and residual risks. Credentials and sensitive model payloads are excluded.
- The terminal result is `PASS`, `FAIL` or `ESCALATE`, with explicit thresholds for identity/document/evidence correctness, approved limits, run state and unresolved issues. A single bounded run does not establish broad connector reliability.

Dependencies: the existing Finep connector, OpenAI adapter and isolated Compose profile. It does not depend on Change B. Every online Finep/OpenAI request and any paid budget requires a separate explicit execution authorization; this plan makes no request.

## Change B — `multi-source-opportunity-ingestion`

Purpose: generalize only the boundaries needed to ingest Finep and one second official publisher in the same PostgreSQL catalog and read API.

Proposed acceptance outcomes:

- Publisher/source reference, responsible funding agency and opportunity are represented separately; Finep `databaseId=751` and portal entry `751092` still resolve to one opportunity.
- Source identity is scoped by publisher and native ID. Snapshots, documents and versions remain source-specific; hashes detect content changes but never identify opportunities.
- Connector selection can run multiple explicitly configured sources while preserving independent `IngestionRun` audit, per-call rollback, retry safety and attribution.
- Uncertain cross-source duplicates remain separate. Explainable candidates may be surfaced under an approved deterministic rule, with no automatic merge.
- CNPq is added as source two after one public call is selected and its eligibility and identity are reviewed. Offline fixtures exercise both sources in the same database/API; no CI network access is needed.
- The existing bounded API/catalog gains separate source-publisher and agency filters, with contract tests and accessible frontend controls.

Dependencies: no dependency on live Change A. It depends on approving a specific CNPq call and its identity before connector implementation. Live CNPq smoke access is optional and separately authorized.

## Second-source recommendation

Recommend CNPq first for technical sequencing, not as a universal relevance ranking. Its official [open calls page](https://www.gov.br/cnpq/pt-br/chamadas/abertas-para-submissao) centrally lists active calls. In the sampled [official call page](https://www.gov.br/cnpq/pt-br/assuntos/chamadas-publicas/chamadas-2026/chamada-no-34-2026), a call number/year and direct document link are visible, giving a practical bounded identity pattern and public document path. Public call reading is distinct from the [application submission service](https://www.gov.br/pt-br/servicos/submeter-proposta-de-projeto-de-pesquisa), which uses a gov.br/Carlos Chagas workflow; the connector must not automate that restricted workflow.

FAPDF has strong direct value for DF innovation and startup programs, with public notices and documents on the [official FAPDF site](https://www.fap.df.gov.br/) and its [service guide](https://www.fap.df.gov.br/carta-de-servicos/). However, discovery appears more distributed across program/year pages and document repositories, with more varied identifiers. Its [SIGFAPDF portal](https://sigfapdf.df.gov.br/) is a submission system and must not be accessed without explicit authorization. The limited planning research confirmed public documents but did not confirm a supported public API for either source. Reassess FAPDF after the CNPq connector demonstrates the source-reference contract or if the selected CNPq call lacks approved product fit.

## Incremental execution plan

1. Review and approve the two OpenSpec proposals and the Phase 2 scope; keep each change independently approvable.
2. If approved, execute Change A as an isolated validation campaign with a separately approved manifest, exact target and spend cap. Stop and escalate on unbounded cost, changed portal/auth flow, isolation failure or unsupported critical evidence.
3. In parallel or afterward, implement Change B offline-first: identity/migration design, transaction-preserving orchestration, selected CNPq fixture/connector, two-source persistence/API tests, then catalog filters.
4. Run independent review and full offline CI for Change B. Treat any live source smoke as a separate gated activity.
5. Evaluate outcomes and operational burden before proposing additional sources or broader ingestion coverage.

## Decisions, risks and approval gates

- No durable architecture decision is adopted by this proposal. Publisher/source-reference naming, additive migration/backfill, duplicate-candidate relation and the chosen CNPq call require review in the corresponding design before implementation.
- The primary integrity risk is conflating publisher, responsible agency and opportunity, or duplicating Finep call 751 during migration.
- The primary validation risk is treating one live sample as general proof; report conclusions only for the exact target and run.
- Model usage incurs cost, and provider-reported token usage/pricing may not be available or exact. Do not execute without a bounded, approved spend ceiling and sanitized measurement plan.
- Public-site layout, redirects and linked-document hosts can change; fail closed under source-specific URL policy.
- S3-compatible writes and PostgreSQL transactions are not atomic. Preserve existing audit/transaction semantics and report potential orphan objects; do not add automatic deletion as an unapproved behavior.
- Human approval is required before implementation, external requests, paid calls, target/source selection changes, schema decisions, production data access, commit or push.
