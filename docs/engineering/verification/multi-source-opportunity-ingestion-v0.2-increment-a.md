# Verification record: multi-source-opportunity-ingestion / v0.2-increment-a

## Run identity

- Change and cycle: `multi-source-opportunity-ingestion` / Fomentiq v0.2.0 Increment A preflight
- OpenSpec change/tasks in scope: `multi-source-opportunity-ingestion`, tasks 1.1–6.2; task 6.3 remains separately authorized live work
- Date/time (UTC): 2026-10-27
- Implementer context: primary Codex context
- Independent reviewer context/mechanism: not run; implementation is blocked before the review stage by an explicit architectural approval gate
- Final state: `ESCALATE`

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | Task 3.1; user authorization | Confirm whether CNPq call 30/2026 is a supported, relevant, publicly accessible pilot with stable number/year identity and official documents | Official CNPq open-calls page, call detail page and linked 29-page notice inspected read-only; details below. Candidate passes source/relevance/access preflight. No fixture was frozen yet. |
| AC-02 | Tasks 1.1–1.3; explicit architectural gate | Define separate publisher, source reference, opportunity, and potentially multiple responsible agencies without changing Finep identity | Existing migrations and design inspected. Current schema has one `call_for_proposals.agency_id`, unique `(agency_id, external_id)`, snapshots keyed only to `call_id`, and document identity keyed to `call_id`; approved multi-agency/source-reference migration shape is not yet present. |
| AC-03 | Tasks 4.1–4.2; user authorization | Cross-source candidates remain separate and are implemented only if they do not block the vertical slice | Current OpenSpec specifies an exact-title/deadline/year candidate, but design explicitly lists the duplicate rule as pending approval. Recommendation: defer this feature for Increment A and amend tasks/spec before implementation. |
| AC-04 | Acceptance; user authorization | No calls to Finep/OpenAI, no Finep golden fixture changes, no operational database writes, and no commit/push | No Finep/OpenAI requests or application ingestion were run. No code, schema, fixture, OpenSpec task or Git history was changed. Only this verification report was added. |

## Plan and affected areas

- Planned vertical slice: source validation and approved offline fixtures, additive source/agency identity model, two-source ingestion, unified API/UI filters, offline E2E.
- Affected files/modules: none modified. Read-only inspection covered `openspec/changes/multi-source-opportunity-ingestion/`, domain and architecture docs, migrations V1–V3, connector/persistence/API code, and official CNPq pages.
- Known risks before implementation: source publisher and funding agencies are not modeled separately; existing call identity is agency-scoped; current opportunity API has a singular agency DTO; a CNPq/CAPES joint call has two funding contributors; source snapshots and document identities lack publisher/source-reference ownership.
- Scope/architecture approval needed: yes. The change design itself lists exact CNPq identity, duplicate relation, and durable model/schema decisions as required before implementation. The user also explicitly requires escalation when such a decision remains unapproved.

## CNPq call 30/2026 source assessment

Read-only official-source checks performed on 2026-10-27:

- Official CNPq open calls: <https://www.gov.br/cnpq/pt-br/chamadas/abertas-para-submissao>
- Official numbered call page: <https://www.gov.br/cnpq/pt-br/chamadas/todas-as-chamadas/chamadas-2026/chamada-no-30-2026>
- Official call detail: <https://www.gov.br/cnpq/pt-br/chamadas/todas-as-chamadas/chamadas-2026/chamada-no-30-2026/chamada-publica-cnpq-N-30-2026>
- Official notice PDF: <https://www.gov.br/cnpq/pt-br/chamadas/todas-as-chamadas/chamadas-2026/chamada-no-30-2026/chamadapublica30_2026_MAIDAI_2766773.pdf>

Evidence observed:

- The open-calls page lists “Chamada Pública CNPq/CAPES Nº 30/2026 – Programa de Mestrado e Doutorado Acadêmico para Inovação – MAI/DAI”, with submissions through 2026-12-11.
- The numbered detail page exposes the official call PDF, Annexes II–IV and FAQ without a login or application-submission workflow.
- The notice identifies itself as CNPq/CAPES No. 30/2026. Its object supports ICT proposals for MAI/DAI and describes research, technological development and innovation in partnerships with companies, government and/or third-sector entities (PDF pp. 1–2).
- Eligibility requires the proponent to be an ICT representative and the executing institution to be an ICT with a recognized graduate program; the partner institution can be an enterprise, government body or third-sector entity (PDF pp. 2–4). This is a relevant but ICT-led opportunity, not a direct company-only grant.
- The notice allocates R$ 55,000,000 from CNPq and R$ 7,440,000 from CAPES (PDF p. 5), so both agencies have a documented funding role while CNPq is the publisher hosting this source record.
- The official page and notice support a source-native number/year key `30/2026`; no separate stable numeric database identifier was observed. The source identity is therefore plausible but its durable representation still needs approval.

Assessment: the proposed pilot is adequate for a bounded catalog/source integration because it is currently open, relevant to innovation, has a stable official number/year label, and links public documents. Its ICT-led eligibility must be displayed faithfully. The selected page and documents have not been copied into or frozen as repository fixtures; task 3.1 is therefore not complete.

## Minimum architectural proposal requiring approval

1. Add `source_publisher` with stable publisher code (`FINEP`, `CNPQ`) and display metadata.
2. Add `call_source_reference` linking one opportunity to one publisher-native record, unique on `(publisher_id, external_id)`. Use `FINEP / 751`, preserving portal entry `751092` as a separate source identifier, and `CNPQ / 30/2026`, with the official CNPq detail URL. Do not derive identity from title or hash.
3. Add a responsible-agency association keyed by opportunity and agency with a primary designation. Backfill every existing call's current `agency_id` as its primary association; preserve `call_for_proposals.agency_id` as the compatibility primary during this increment. For call 30/2026, record CNPq and CAPES only as supported by the call's joint funding statement, with CNPq primary for existing singular API consumers.
4. Add source-reference ownership to source snapshots and logical document references, retaining the existing call links during the additive migration. This keeps each publisher's snapshot/document/version history independently queryable while retaining existing detail/document API behavior.
5. Extend API DTOs additively with `sources[]` and `responsibleAgencies[]`; retain the existing singular `agency` fields as the primary agency. Define the new agency filter to match any responsible-agency association and the source filter to match publisher code.
6. Do not change extraction states, the conservative factual policy, Finep golden fixture, object storage, or run/transaction boundaries.

Migration outline: additive V4 creates publisher/reference/agency-association tables and FKs, backfills Finep publisher references from existing calls (`external_id` and `portal_entry_id`), backfills primary agencies, then associates Finep snapshots/documents. Keep old columns and constraints during the compatibility period. Test both a clean database and an upgrade from V3 using PostgreSQL/Testcontainers; assert the existing Finep 751/751092 remains exactly one opportunity and one source reference.

## Duplicate candidate scope recommendation

Defer cross-source duplicate-candidate detection and its DTO/UI in this vertical increment. It is not needed to demonstrate both source catalogs, and the design says its exact rule awaits approval. Keep distinct identities; enforce same-publisher source-reference uniqueness and never merge opportunities. Amend OpenSpec proposal/spec/tasks to explicitly defer tasks 4.1–4.2 before implementation so no acceptance requirement is silently skipped. A later approved design can choose an explainable, evidence-backed candidate rule.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `git status --short && git log -1 --oneline && git rev-parse HEAD` | PASS | Baseline `773d44c`; worktree initially clean. |
| `openspec status --change multi-source-opportunity-ingestion` | PASS | Spec-driven change, 4/4 planning artifacts complete, 0/19 implementation tasks complete. |
| `openspec instructions apply --change multi-source-opportunity-ingestion --json` | PASS | All 19 tasks remain pending; design has unresolved decision gate. |
| Read project instructions, domain/architecture docs, proposal/design/spec/tasks, migrations V1–V3 and official apply/spec-driven skills | PASS | No implementation or task changes made. |
| Official CNPq pages and notice PDF, accessed through read-only web search/open | PASS for source assessment | No connector ingestion or fixture capture was run. Notice is 29 pages; page/paragraph evidence recorded above. |
| `git diff --check` and `git diff --no-index --check /dev/null docs/engineering/verification/multi-source-opportunity-ingestion-v0.2-increment-a.md` | PASS | No whitespace errors in tracked changes or this sole untracked report. |
| Backend/frontend/Compose/OpenSpec implementation checks | Not run | Correctly deferred because implementation is blocked pending architectural decisions. |

## Independent review

- Evidence supplied: not applicable yet; no implementation diff or test results exist to review.
- Reviewer had a separate context: no; this run stopped at the explicit architectural gate, before implementation review.
- Reviewer was instructed not to modify implementation, tests, specs, or this report: not applicable.
- Worktree was checked before and after review; reviewer made no changes: not applicable.
- Spec adherence, implementation quality, regressions, and security reviewed: no implementation to review.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| MS-ARCH-01 | High; AC-02; tasks 1.1–1.3 | Existing call schema is single-agency and snapshots/documents are keyed to call, while pilot has CNPq and CAPES funding roles. OpenSpec design marks model/schema decision pending. | 0; no correction attempted pending human approval. | Not reviewed; escalated before code changes. |
| MS-DUP-01 | Medium; AC-03; tasks 4.1–4.2 | Candidate relation has no approved exact rule in design; the user makes it conditional on not blocking delivery. | 0; recommend defer and amend scope. | Not reviewed; escalated before code changes. |

## Test integrity

- Tests added/changed: none.
- Demonstrated test defect, if any: none.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: not applicable; implementation has not started.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: no fixture is frozen yet; source identity and additive agency/source schema await approval; exact duplicate-candidate detection is deferred; no code has been changed or verified.
- Decisions made: source assessment recommends public CNPq/CAPES call 30/2026 as the pilot; proposed key is publisher `CNPQ` + external ID `30/2026`; CNPq is publisher and primary agency, with CAPES as an additional documented funder. This is a recommendation, not yet an implemented durable decision.
- OpenSpec tasks marked complete: none. Task 3.1 remains incomplete until a reviewed sanitized page/document fixture is frozen.
- Final verification after fixes: none; no fixes/implementation occurred.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `ESCALATE`; explicit OpenSpec design decisions on durable schema and duplicate relation must be resolved before those implementation areas can proceed.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.

No Finep or OpenAI calls were made. No application ingestion, database write, golden-fixture modification, task completion, commit, or push occurred.
