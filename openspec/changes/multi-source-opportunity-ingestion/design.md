# Design: minimal multi-source ingestion

## Context

The v0.1.0 implementation is a modular monolith with a generic `SourceConnector` port and Finep-only orchestration/persistence. The port exposes discovery, linked-document discovery and download, but `FinepIngestionService` selects the concrete Finep connector and `JpaIngestionPersistence` resolves the agency as `FINEP`. `CallForProposals` stores agency identity, Finep `externalId` and `portalEntryId`; it does not model a publishing source separately or multiple responsible agencies. `IngestionRun.sourceType` is already source-labelled. The catalog read adapter, DTOs, OpenAPI and frontend filters currently have no source-publisher or responsible-agency filter.

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

The approved pilot is **Chamada Pública CNPq/CAPES Nº 30/2026 — Programa de Mestrado e Doutorado Acadêmico para Inovação (MAI/DAI)**. The official open-calls page and notice were reviewed on 2026-10-27. The source is public, currently open through 2026-12-11, linked from the official CNPq call listing, and its notice states that ICTs submit innovation/research proposals in partnership with companies, government or third-sector organizations. This is an ICT-led opportunity, not a company-only grant. The notice identifies both CNPq and CAPES funding (R$ 55,000,000 and R$ 7,440,000 respectively). Source publisher is CNPq; CNPq is the primary funding agency to preserve the existing singular API contract, with CAPES associated as an additional responsible funding agency. These roles must be displayed without inferring responsibilities beyond the call text.

Official source URLs:

- Open calls: <https://www.gov.br/cnpq/pt-br/chamadas/abertas-para-submissao>
- Numbered call: <https://www.gov.br/cnpq/pt-br/chamadas/todas-as-chamadas/chamadas-2026/chamada-no-30-2026>
- Call detail: <https://www.gov.br/cnpq/pt-br/chamadas/todas-as-chamadas/chamadas-2026/chamada-no-30-2026/chamada-publica-cnpq-N-30-2026>
- Official notice: <https://www.gov.br/cnpq/pt-br/chamadas/todas-as-chamadas/chamadas-2026/chamada-no-30-2026/chamadapublica30_2026_MAIDAI_2766773.pdf>

The source-native external identity is `30/2026` scoped to publisher `CNPQ`; no separate numeric portal ID was found. FAPDF remains a possible future source after separate discovery research.

## Approved minimal model and flow

- Keep `CallForProposals` and its UUID as the opportunity identity. Add `SourcePublisher`, `CallSourceReference` and `CallFundingAgency` concepts. Each source reference is unique by `(publisher, externalId)` and maps to one opportunity; neither titles nor hashes identify domain entities.
- Preserve `call_for_proposals.agency_id` as the compatibility primary agency and retain all existing singular API agency fields. The new `CallFundingAgency` association contains every responsible/funding agency, including the primary. Finep records are backfilled with Finep as primary. For the CNPq pilot, CNPq is primary and CAPES additional; the API adds agency/source lists and agency filtering matches any associated agency.
- Finep source identity remains publisher `FINEP`, external ID `751`, portal entry ID `751092`. CNPq source identity is publisher `CNPQ`, external ID `30/2026`, canonical detail URL above.
- Keep source snapshots and logical source-document identities tied to the source reference; document versions remain immutable and associated with the existing call/document domain IDs. Hashes detect byte changes; they do not identify a call or document.
- Use a Flyway migration that preserves existing rows, call UUIDs, legacy columns and API behavior. Create publisher/reference/agency association tables, backfill every existing opportunity and Finep source reference, and add source-reference ownership to snapshots/documents. Remove the obsolete `(agency_id, external_id)` uniqueness constraint because it would reject two distinct publishers using the same native ID and funding agency; enforce source identity only at `(publisher_id, external_id)` in `CallSourceReference`. Validate clean installs and upgrades from V3 using PostgreSQL/Testcontainers.
- Extend the existing connector contract only where the CNPq fixture proves the required metadata is absent. Do not create a general scraping framework or source plug-in framework.
- Replace Finep-only selection in orchestration with explicit configured connector selection, preserving the existing independent run transaction and per-call transaction semantics. A source failure must remain attributable to that source; one call's relational failure must not corrupt prior committed calls.
- Give CNPq a narrowly scoped adapter for public call listing/detail/document URLs, with source-specific allowlisted hosts, redirect validation, response limits and explicit failure if public structure changes or a login/token is required.
- Keep cross-source opportunity identities separate; do not merge or select a canonical record. The approved duplicate-candidate capability and user-facing UI remain explicitly deferred to a later increment and are not part of this vertical slice. The corresponding requirement and tasks remain tracked, not deleted or marked complete.
- Add source and responsible-agency filters to existing read DTOs, API/OpenAPI, bounded query and catalog UI. Do not expose persistence entities or internal storage keys.
- Test Finep and CNPq fixtures through real persistence and the same API/PostgreSQL integration environment. All normal CI connector tests remain offline.

## Dependencies and sequencing

1. Freeze the reviewed CNPq page, notice, linked public documents, capture metadata and hashes; establish source identity and additive migration/backfill while preserving Finep identity.
2. Generalize the connector selection and per-source run orchestration; keep a Finep regression green.
3. Implement the selected CNPq public connector against frozen local fixtures and URL safety tests.
4. Exercise both sources, idempotency, changed documents and transaction failures in PostgreSQL/Testcontainers.
5. Extend read API/OpenAPI and frontend filters; verify existing filters and pagination remain stable.
6. Run independent review, strict OpenSpec validation, Compose validation and the full offline verification set, including offline Playwright. Automated tests must use only fixtures and never call Finep, CNPq or OpenAI. Any connector smoke run is separate and not authorized by this increment.

The source comparison and selection should be revisited only if fixture capture demonstrates the selected public shape is unsupported, official documents cannot be accessed without restricted login, or the number/year identity cannot be retained. Do not silently substitute another call.

## Risks and boundaries

- Source publisher and responsible agency can differ; collapsing them would make filtering and provenance incorrect.
- Existing Finep identifiers must be backfilled without duplicating call `751` or changing portal entry `751092` semantics.
- Cross-source duplicate-candidate classification is deferred; duplicate opportunities must remain separate and no automatic fusion is implemented. The candidate rule requires a later explicit design review.
- Public sites can change layout, link hosts or redirects; fail closed and preserve auditable run failures.
- PostgreSQL rollback cannot roll back S3-compatible writes; possible orphan objects must be observable and cleanup remains manual/out of scope.
- CNPq call 30/2026 and identity are approved for fixture and offline connector implementation. Public fixture retrieval is authorized only for this call. CI must use fixtures and make no Finep/CNPq/OpenAI requests.
- This design does not authorize a live connector smoke run, OpenAI usage, production writes, automatic merging, new infrastructure or changes to the Finep golden fixture.

## Approved decisions and deferred requirements

- Approved: `SourcePublisher`, `CallSourceReference`, and `CallFundingAgency`; preserve opportunity UUID, `agency_id`, and legacy API fields; CNPq is primary for CNPq/CAPES 30/2026; source identity is `CNPQ / 30/2026`; no automatic merging.
- Deferred: implement/present cross-source duplicate candidates (tasks 4.1–4.2). This capability remains in the spec and tasks for a later increment. The title/deadline/year example is not yet an approved candidate rule and must not be implemented in Increment A.
