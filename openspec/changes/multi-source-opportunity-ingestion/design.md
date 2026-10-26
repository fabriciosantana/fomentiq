# Design: minimal multi-source ingestion

## Context

The v0.1.0 implementation is a modular monolith with a generic `SourceConnector` port and Finep-only orchestration/persistence. The port exposes discovery, linked-document discovery and download, but `FinepIngestionService` selects the concrete Finep connector and `JpaIngestionPersistence` resolves the agency as `FINEP`. `CallForProposals` stores agency identity, Finep `externalId` and `portalEntryId`; it does not model a publishing source separately. `IngestionRun.sourceType` is already source-labelled. The catalog read adapter, DTOs, OpenAPI and frontend filters currently have no source-publisher or responsible-agency filter.

The change should extend only what is required to run Finep and one second publisher through the same PostgreSQL database, persistence rules and read API. Keep the modular monolith, Flyway, Testcontainers and S3-compatible storage. Keep the independent `IngestionRun` lifecycle and the per-call relational transaction boundary: a failed call rolls back its relational unit, while the independent run record can report the result. Object storage and PostgreSQL are not one distributed transaction; a storage write followed by a relational rollback can leave an orphan object, which remains an explicit cleanup risk.

## Source comparison and selection

Research checked on 2026-10-26 used public official sources:

| Criterion | CNPq | FAPDF |
|---|---|---|
| Public discovery | Centralized official page for calls open for submission, with call-specific pages and links to call documents. No supported public API was confirmed in this planning review. | Official site publishes calls and service information, but discovery is spread across program/year pages, notices, news and document links. No supported public API was confirmed in this planning review. |
| Document access | The sampled official call page linked its call PDF directly. Public reading does not require the application-submission login. | Official notices and PDFs are public, but paths and document identifiers are more varied. |
| Identity | Call number and year appear in official page paths/titles, providing a practical source-native identity for one bounded connector. Validate collisions/renumbering against the selected call before implementation. | Public notices often expose edital/year and SEI or document identifiers, but the catalog shape and identifiers vary by program/page. |
| Authentication boundary | Public calls/documents can be read without using the gated application workflow. Do not access or automate login-protected Carlos Chagas submission functions. | Public site documents can be read; SIGFAPDF submission functions require sign-in and are out of scope. |
| Fit and complexity | Some calls require company/ICT participation and may be relevant, but eligibility is per-call and must be established from the source document. | Stronger direct fit for DF startups and innovation programs; discovery appears more fragmented, so connector selection/coverage is less predictable. |

Sources: [CNPq open calls](https://www.gov.br/cnpq/pt-br/chamadas/abertas-para-submissao), [sample CNPq call page](https://www.gov.br/cnpq/pt-br/assuntos/chamadas-publicas/chamadas-2026/chamada-no-34-2026), [CNPq call submission service](https://www.gov.br/pt-br/servicos/submeter-proposta-de-projeto-de-pesquisa), [FAPDF official site](https://www.fap.df.gov.br/), [FAPDF services](https://www.fap.df.gov.br/carta-de-servicos/), [SIGFAPDF](https://sigfapdf.df.gov.br/).

Recommend CNPq as source two for the first bounded technical integration: public discovery is more centralized and the sampled source exposes direct call documents and a call number/year pattern. This is a technical sequencing recommendation, not a claim that CNPq is more relevant to every Fomentiq user. Select one call whose official document establishes the intended company/ICT eligibility before implementation. FAPDF may be a better later source for DF-specific relevance, after its discovery and identity patterns are surveyed more deeply.

## Proposed minimal model and flow

- Keep `FundingAgency` as the responsible funding body and `CallForProposals` as the opportunity identity.
- Add a distinct publisher/source identity and a source reference that associates one publisher-native record with an opportunity. Source-native IDs are unique within a publisher. Finep `databaseId=751` remains the external opportunity reference and `751092` remains the portal entry reference for that same source record.
- Keep source snapshots, document identities and document versions tied to their source reference. Hashes detect byte changes; they do not identify a call or document.
- Extend the existing connector contract only where the CNPq fixture proves the required metadata is absent. Do not create a general scraping framework or source plug-in framework.
- Replace Finep-only selection in orchestration with explicit configured connector selection, preserving the existing independent run transaction and per-call transaction semantics. A source failure must remain attributable to that source; one call's relational failure must not corrupt prior committed calls.
- Give CNPq a narrowly scoped adapter for public call listing/detail/document URLs, with source-specific allowlisted hosts, redirect validation, response limits and explicit failure if public structure changes or a login/token is required.
- Keep uncertain cross-source matches as separate opportunities. Only authoritative cross-references or the approved deterministic candidate rule may create a “possible duplicate” relation; never merge or select a canonical record automatically. Any verified many-source-to-one-opportunity mapping requires explicit review and retains both source histories.
- Add source and responsible-agency filters to existing read DTOs, API/OpenAPI, bounded query and catalog UI. Do not expose persistence entities or internal storage keys.
- Test Finep and CNPq fixtures through real persistence and the same API/PostgreSQL integration environment. All normal CI connector tests remain offline.

## Dependencies and sequencing

1. Establish source identity and the additive migration/backfill while preserving Finep identity.
2. Generalize the connector selection and per-source run orchestration; keep a Finep regression green.
3. Implement the selected CNPq public connector against frozen local fixtures and URL safety tests.
4. Exercise both sources, idempotency, changed documents and transaction failures in PostgreSQL/Testcontainers.
5. Extend read API/OpenAPI and frontend filters; verify existing filters and pagination remain stable.
6. Run independent review, strict OpenSpec validation and the full offline verification set. Any live smoke test is a separate, explicitly authorized activity.

The source comparison and selection should be revisited if inspection of the chosen CNPq call reveals unstable identity, inaccessible documents or no fit for approved eligibility. That would be a scope/selection question, not a reason to silently switch the source.

## Risks and boundaries

- Source publisher and responsible agency can differ; collapsing them would make filtering and provenance incorrect.
- Existing Finep identifiers must be backfilled without duplicating call `751` or changing portal entry `751092` semantics.
- Exact-title/deadline/year duplicate candidates can produce false positives; they must remain review candidates only.
- Public sites can change layout, link hosts or redirects; fail closed and preserve auditable run failures.
- PostgreSQL rollback cannot roll back S3-compatible writes; possible orphan objects must be observable and cleanup remains manual/out of scope.
- CNPq call suitability and its current URLs need a human-approved target before live access. CI must use fixtures and make no Finep/CNPq/OpenAI requests.
- This design does not authorize implementation, live ingestion, OpenAI usage, production writes, automatic merging, new infrastructure or changes to the Finep golden fixture.

## Decisions required before implementation

- Approve a particular CNPq call and define the accepted publisher identity/key after inspecting its official page and document.
- Approve the exact deterministic duplicate-candidate relation and user-facing wording; this change does not authorize automatic linking or merging.
- Review any proposed durable model/schema naming and migration before it is adopted.
