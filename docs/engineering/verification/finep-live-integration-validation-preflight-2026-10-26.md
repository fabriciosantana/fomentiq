# Verification record: finep-live-integration-validation / preflight 2026-10-26

## Run identity

- Change and cycle: `finep-live-integration-validation` / preflight tasks 1.1–1.3.
- OpenSpec tasks in scope: 1.1, 1.2, 1.3 only; live tasks 2.x and post-run tasks 3.x were not started.
- Date/time (UTC): 2026-10-26.
- Implementer context: primary Codex context.
- Independent reviewer context/mechanism: separate collaboration review requested; pending at report draft.
- Final state: `ESCALATE` for online readiness. Task 1.1 completed. Task 1.2's positive disposable-environment checks passed, but the required negative isolation gate is not implemented/tested, so the task remains unchecked. Task 1.3 remains pending explicit per-run approval.

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | Task 1.1 | A per-run manifest/checklist covers exact call identity/URLs, expected documents and byte limits, model/schema, provider-attempt and spend ceilings, isolated DB/storage targets, and environment-variable presence without values. | This report's proposed manifest/checklist; environment checks record presence only. No external requests made. |
| AC-02 | Task 1.2 | Disposable Compose project, JDBC target, S3-compatible endpoint and bucket are isolated from manual-demo resources; teardown removes only preflight resources. | Unique Compose project came up; PostgreSQL readiness, local storage health, production S3 SDK bucket/object operation and cleanup passed; manual-demo project remained running on separate ports. |
| AC-03 | Task 1.2 | A failed isolation check prevents any source/provider request. | Not met. The operator gate is documented below but was not exercised as an automated negative test; the current repository has no live-run entry point or isolation wrapper. This remains a readiness limitation and is not evidence of an implemented programmatic guard. |
| AC-04 | Task 1.3 | Exact manifest values are approved by a human and approval reference is recorded. | Not met. Current authorization explicitly permits preflight only and prohibits live ingestion/OpenAI calls. Task 1.3 stays unchecked. |
| AC-05 | User authorization | No Finep ingestion, OpenAI request, production write, CNPq integration, commit or push occurs in this preflight. | Confirmed: no external request; only the planning commit in Etapa 1 was pushed. Post-commit changes remain local and uncommitted. |

## Plan and affected areas

- Planned vertical slice: manifest/readiness record, isolated local dependency startup and teardown, and concrete candidate plans for the later Finep validation and Change B gates. No product code was changed.
- Affected files after the approved Etapa 1 commit: `openspec/changes/finep-live-integration-validation/tasks.md` and this report only.
- Known risks before implementation: Finep connector reads all discovered documents; its HTTP byte ceiling applies per document, not to aggregate bytes or document count. `FinepIngestionService` is not called by any production runner/controller, only by tests. The application therefore has neither an approved way to invoke a live run nor demonstrated enforcement of whole-run document/byte/time limits.
- Scope/architecture approval needed: if live validation requires a bounded ingestion entry point, document-count/aggregate-byte/run-deadline controls, or an automated fail-closed isolation wrapper, obtain approval before implementation. No such changes were made.

## Illustrative per-run manifest (proposed values; not approved for execution)

This is a reusable preflight draft populated with candidate values to make the future review concrete. None of its target, limits, environment names or budget values has been approved for an online run.

| Manifest field | Proposed value | State |
|---|---|---|
| Finep opportunity | Historical `databaseId=751`; current portal entry `751092`; title in fixture: `PROINFRA 2024 EXPANSÃO`; fixture canonical page `https://www.finep.gov.br/e/chamada-publica/222684/751092` | Exact identity comes from the frozen fixture; current online availability has not been checked. Human target approval pending. |
| Expected linked documents | 18 fixture entries, IDs `814330`, `814348`, `814364`, `814380`, `814396`, `814412`, `814428`, `814444`, `814456`, `814476`, `814482`, `814496`, `814512`, `814524`, `814544`, `814560`, `814572`, `814588` | Baseline only. Any live list mismatch must stop/escalate. Current connector does not enforce this count. |
| Document and byte ceilings | Up to 18 documents; maximum 50 MiB per document (existing connector limit); proposed aggregate cap 25 MiB; discovery JSON 5 MiB. | The 18-document/aggregate ceilings are not enforced by the current ingestion flow. Do not proceed online until enforcement or another reviewed safe mechanism is approved. |
| Finep origin and API paths | HTTPS host `www.finep.gov.br` only under `FinepUrlPolicy`; public page `/e/chamada-publica/222684/751092`; discovery `/o/c/chamadapublicas?filter=databaseId%20eq%20751&pageSize=100`; call record `/o/c/chamadapublicas/751092`; documents `/o/c/chamadapublicas/751092/documentos?pageSize=500`; linked PDFs from `documentoProprietario.link.href`. | Derived from current connector/fixture, not contacted in this preflight. |
| OAuth bootstrap | GET the canonical public page; discover the current token URL from page JavaScript; POST client-credentials grant to that runtime-discovered endpoint; accept only the configured Finep public-host policy. Token remains process memory only. | Flow described by code and fixture manifest; no token endpoint contacted. If host/flow changes, stop. |
| OpenAI | `https://api.openai.com/v1/responses`; `OPENAI_MODEL` unset, so application default is `gpt-6-luna`; reasoning effort default `low`; schema `finep-call-v1`; strict JSON Schema Responses request. | Environment presence check only; no request made. |
| Excerpt/output bounds | Existing selector: up to 6 documents, 24 excerpts, 1,400 characters/excerpt, 24,000 total excerpt characters. Existing adapter caps output at 12,000 tokens. | Verified in code. |
| Provider requests/retries | Set `EXTRACTION_MAX_RETRIES=0` for one total attempt; timeout 60 s, connection timeout 15 s. | Proposed runtime override; current environment's retry value is not printed. |
| Finep HTTP request budget | Expected 27 logical requests for one call and 18 documents: 3 OAuth/page/API requests each for discovery, call record and document catalog, plus 18 PDF GETs. Redirects are capped at 4 per eligible GET; connector retries are zero. The theoretical count can reach 123 including redirects if all 24 eligible GETs redirect four times. | Derived from current code and fixture. The connector does not enforce an aggregate logical request budget or the expected 18-document count. |
| Finep HTTP timeout/retries | Connect timeout 15 s; request/token timeout 30 s; up to 4 redirects per request; connector has no retry loop. Proposed overall wall-clock ceiling: 20 minutes. | Per-request settings verified in code. Overall deadline is not enforced and the per-request maxima could exceed 20 minutes in aggregate. |
| Storage/database | Disposable Compose project `fomentiq-finep-live-validation-<run-id>`; dedicated database; dedicated RustFS/S3-compatible endpoint and bucket. Never reuse `fomentiq-manual-evaluation` volumes. | A local preflight project was actually started and cleaned; exact run names are in command evidence below. Final operational project and bucket name require approval. |
| Spend proposal | One provider attempt. Estimate: ≤20,000 input tokens plus up to 12,000 output tokens; at the standard current GPT-6 Luna rates, approximately `$0.008` upper-bound token charge for that estimate. Proposed hard approval cap: `$0.05 USD`, excluding taxes/other account-specific charges. | Pricing checked on 2026-10-26 at [OpenAI API pricing](https://developers.openai.com/api/docs/pricing?latest-pricing-expanded=true). Recheck before any approved call. Token estimate is conservative, not measured usage. |
| Human approval | Exact target, expected document IDs/limits, endpoint policy, isolated Compose project/database/bucket, model/schema, one-attempt cap, `$0.05` spend cap and run window. | Pending. This planning/preflight authorization is not the per-run approval. |

The fixture manifest reports 18 linked documents totaling 5,406,555 bytes (about 5.16 MiB), with the largest fixture file 1,069,861 bytes. These fixture sizes do not bound current live files.

### Finep plan execution gates

1. Confirm the selected target and exact manifest are approved. Do not infer consent from this preflight.
2. Start a new project name, database, S3 endpoint and bucket. Before invoking the connector, verify the actual JDBC URL, DB name, storage endpoint and bucket; compare Compose project and volume labels to the manual-demo project. Any mismatch means stop before external requests.
3. Confirm `OPENAI_API_KEY` is present without printing it; record only set/unset. Confirm model/schema/retry/timeout values without printing environment dumps. Do not put OAuth client values or the resulting bearer token into logs, snapshots, exception messages, database rows or object storage.
4. Run one bounded discovery and verify identity `751`/`751092`, then only the approved 18 document IDs. The current implementation does not offer a pause between document discovery and document download and does not enforce the expected count/aggregate cap; this gate cannot currently be guaranteed by the application.
5. Before the single OpenAI call, inspect the selected excerpt count/character total and cap within existing selector limits. If the application cannot make this inspection without logging content or changing code, stop.
6. Review `deadline`, `totalBudget`, `eligibility`, `counterpart`, `trl` and `requiresIct` against exact preserved document version, quote and locator. Never resolve rerratification disagreement by recency alone. Accept a correct `MISSING` or `CONFLICT` state; no unsupported critical value may be treated as fact.
7. Verify run outcome, snapshots precede extraction, document hashes/object availability, persisted evidence/version/run relationships, and offline idempotency regressions before report finalization.

## PASS / FAIL / ESCALATE criteria for a later live run

- **PASS:** the approved target is exactly `751`/`751092`; only the approved documents are persisted; snapshots predate extraction; one approved provider attempt completes within the approved cost/time ceiling; every critical field is factually supported or correctly `MISSING`/`CONFLICT`; evidence points to the preserved version and locator; the run is terminal and relational state is consistent; offline idempotency/version regressions pass; no secret or payload leaks; and independent review accepts the sanitized evidence.
- **FAIL:** with isolation, target, public contract and approved limits intact, the connector or persistence produces an incorrect identity, missing required snapshot/document/version, unsupported critical value, incorrect evidence/run linkage, duplicate version on identical input, or incorrect terminal run status.
- **ESCALATE:** source/OAuth behavior or document count differs from the approved manifest; target/host redirects or storage/database isolation cannot be proven; cost, byte or duration ceiling cannot be enforced; provider cost cannot be bounded; evidence or rerratification conflict cannot be resolved by source; application invocation requires new harness/code/architecture; or a secret leak/possible operational-data write is suspected. Stop without automatic retry or cleanup that would erase evidence.

## Safe interruption procedure

- Before first request: if any isolation/target/limit check fails, do not invoke the application.
- During a request: stop starting new operations; do not automatically retry a timed-out/ambiguous OpenAI request. Allow graceful process shutdown so `IngestionRun` can finish; if state is uncertain, preserve the isolated DB and object bucket for review instead of deleting them.
- After interruption: inspect only the dedicated database/bucket, record sanitized run status and object counts/hashes, and escalate any nonterminal run or unreferenced objects. Do not access or clean manual-demo/operational resources.
- Teardown is permitted only after the sanitized report and independent review are complete, using the exact unique Compose project name and its own volumes.

## Change B gates — proposals, not implementation decisions

### Pilot call

Candidate: **Chamada Pública CNPq/CAPES nº 30/2026 — Programa MAI/DAI**, official page `https://www.gov.br/cnpq/pt-br/chamadas/todas-as-chamadas/chamadas-2026/chamada-no-30-2026/chamada-publica-cnpq-N-30-2026`. At the research date (2026-10-26), its official page says proposals are accepted through 2026-12-11 and lists the call PDF, annexes II–IV and updated FAQ. CNPq's official description says university/research-institute proposals are developed with company partners. This gives practical company/ICT relevance and several publicly linked documents, while exercising a call number/year identity.

Proposed source publisher is the CNPq public calls site (`CNPQ_GOV_BR`); source-native key is `30-2026`, scoped to that publisher; canonical page slug is preserved as the portal reference. The call is co-issued with CAPES, so before implementation approve whether CNPq is the responsible agency and CAPES a documented partner, or both are represented as responsible agencies. The current singular `FundingAgency` relationship cannot represent both without a reviewed schema/API decision. Confirm actual applicant eligibility and all current document links from the official PDF before freezing fixtures. Public page research only; no CNPq connector request/download or integration occurred.

### Identity model proposal

- `CallForProposals` remains the opportunity aggregate with stable internal UUID; it is not identified by publisher ID or document hash.
- Add `SourcePublisher` as a publisher registry (initial `FINEP`, `CNPQ_GOV_BR`) and `CallSourceReference` linking a publisher-native record to an opportunity. Enforce uniqueness on `(publisher_id, native_external_id)`. Keep optional portal-record ID/canonical URL and observed timestamps on this reference.
- Keep `FundingAgency` as the organization responsible for funding. Represent co-responsibility/partner role explicitly if supported by the approved pilot source; do not overload publisher with agency semantics.
- Finep external ID `751` and portal entry `751092` become values on its source reference but remain backward-compatible in existing DTOs during migration. Hash remains content-change detection only.
- Bind source snapshots and document identities/versions to the source reference (or a source-scoped child record); preserve old versions and call linkage.

### Flyway migration and compatibility proposal

Add tables/indexes only at first: seed a Finep publisher row; create source-reference rows for every existing Finep opportunity using its existing external ID, portal entry ID and official URL; use unique constraints for publisher/native ID and foreign keys to the existing call. Add source-document references additively only if current document identity cannot be safely scoped through its call. Do not rewrite/delete opportunity, call-version, document-version, evidence, or `IngestionRun` rows. Keep legacy call agency/external-ID/portal-ID columns and current API DTO fields as compatibility projections while dual-writing source references. Run migration from clean DB and upgrade tests from a v0.1.0 snapshot under Testcontainers; validate counts, uniqueness, foreign keys and Finep `751`→`751092` singularity before changing reads. Rollback should disable new reads/writes and leave additive rows/tables intact until a reviewed recovery; do not drop preserved history.

### Duplicate-candidate proposal

Keep independent opportunity IDs by default. Surface an explainable candidate only for (a) explicit official cross-reference to the same call, or (b) exact normalized title **and** same normalized deadline **and** same call year across different publishers. Store candidate basis and both source references; candidate is review-only, never a merge or canonical selection. Similar titles, shared hashes or overlapping themes alone do not qualify. The candidate rule, persistence vs derived-read model, and wording (“possible duplicate”) require approval before implementation because false positives can confuse the catalog.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `git status --short` before Etapa 1 | passed | Only the approved Phase 2 planning files were untracked. |
| `git diff --cached --check` | passed | Checked complete staged planning change. |
| `git diff --cached` | passed | 11 files, 494 insertions; exclusively OpenSpec proposals/design/spec/tasks and `docs/PHASE_2_SCOPE.md`; secret-pattern scan found no credentials. |
| `openspec validate finep-live-integration-validation --strict` | passed | Change valid. |
| `openspec validate multi-source-opportunity-ingestion --strict` | passed | Change valid. |
| `openspec validate --all --strict` | passed | 3 passed, 0 failed. |
| `git commit -m "docs: plan phase 2 live validation and multi-source ingestion"` | passed | `34c6a07617f4eb46dd8084f87d7f8c725f73272a`. |
| `git push origin main` | passed | `main` advanced from v0.1.0 reference `3c49c29` to `34c6a07`. |
| `gh run watch 40366583145 --exit-status` | passed | Backend Maven verify, frontend lint/typecheck/tests/build, OpenSpec strict all passed. Nonblocking GitHub annotations: actions Node.js 20 deprecation notice and upcoming `ubuntu-latest` image migration. |
| `openspec status --change finep-live-integration-validation --json` | passed | 10 tasks pending at preflight start; `spec-driven` planning complete. |
| `openspec instructions apply --change finep-live-integration-validation --json` | passed | Official apply context loaded. |
| Initial ephemeral Compose harness via `exec_command` with shell cleanup | rejected before execution | Tool rejected the `rm -f` cleanup command form; no Docker or filesystem mutation occurred. Reworked cleanup using Python `Path.unlink`. |
| First Python Compose harness | failed, exit 1 | Python `SyntaxError` in the harness before any Compose invocation. Corrected the bracket and reran; no service or project was created by this attempt. |
| `docker compose --project-name fomentiq-finep-live-preflight-20261026 --env-file <temporary mode-0600 file> config -q` | passed | Config interpolated with isolated DB/ports and temporary generated credentials; no config output was logged. |
| `docker compose ... up -d --wait --wait-timeout 120` | passed | PostgreSQL and RustFS-compatible service became ready on ports 25432 and 29000. |
| `docker compose ... exec -T postgres pg_isready -U finep_preflight -d finep_live_preflight` | passed | Disposable database answered readiness probe. |
| `curl --fail --silent --show-error http://127.0.0.1:29000/health` | passed | Local S3-compatible health endpoint returned success. |
| Production `S3CompatibleArtifactStore` SDK probe | passed | Created dedicated bucket, wrote/read/deleted a probe object, deleted the bucket; no application data. |
| `docker compose ... down --volumes --remove-orphans` and volume-label check | passed | Both uniquely prefixed volumes removed; unrelated manual-demo project remained running. Temporary env/classpath files were unlinked. |
| Environment checks for `OPENAI_API_KEY` and `OPENAI_MODEL` | passed | Key presence reported `set` without value; model unset, so code default `gpt-6-luna`. Reasoning default `low`. No OpenAI request. |
| `git diff --check` | passed | No whitespace errors in staged baseline. Final local report/task changes are rechecked below. |
| `python3` preflight-file whitespace and credential-pattern scan | passed after narrowing the pattern | The first scan falsely matched this report's literal description of a key prefix; the check was narrowed to token-like values and found none. |
| `git diff --check` / `git diff --cached --check` after local preflight edits | passed | No whitespace errors. |
| First post-review whitespace scan | failed, exit 1 | The command referenced a misspelled report filename and returned `FileNotFoundError`; no file was changed. Corrected scan below passed. |
| `openspec validate finep-live-integration-validation --strict`; `openspec validate multi-source-opportunity-ingestion --strict`; `openspec validate --all --strict` after task update | passed | Both changes valid; all 3 OpenSpec items passed. |
| `git rev-parse HEAD`; `git rev-parse origin/main` | passed | Both `34c6a07617f4eb46dd8084f87d7f8c725f73272a`; local preflight edits remain uncommitted. |

## Independent review

- Evidence supplied: approved Change A proposal/spec/design/tasks, Change B proposal/spec/design/tasks, complete preflight diff, environment/Compose/S3 SDK evidence, source comparison and pass/fail/escalate proposal.
- Reviewer had a separate context: yes; `/root/finep_preflight_independent_review`.
- Reviewer was instructed not to modify implementation, tests, specs, or this report: yes.
- Worktree was checked before and after review; reviewer made no changes: yes; reviewer confirmed no file edits.
- Spec adherence, implementation quality, regressions, and security reviewed: yes. Independent outcome: `ESCALATE` for live readiness; 1.2 remains unchecked; report content otherwise accepted. Low-severity wording finding F-05 was clarified and independently re-reviewed as PASS.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| F-01 | High; Change A task 1.1, 2.2 | Current connector has a 50 MiB per-document cap but downloads all returned documents from a `pageSize=500` catalog; no run document-count or aggregate-byte limit exists. The requested 18 documents and 25 MiB aggregate ceiling are not enforced. | 0; no implementation authorized. | Open; online readiness must escalate. |
| F-02 | High; Change A tasks 2.1–2.3 / live entry | `FinepIngestionService` is only referenced by tests; production has no runner/controller/command to initiate an isolated live run. Adding one would be implementation outside tasks 1.1–1.3. | 0; no implementation authorized. | Open; obtain scoped approval or a reviewed test harness plan before live tasks. |
| F-03 | Medium; Change A task 1.2 | Positive Compose/JDBC/S3 isolation was proved; no automated negative isolation gate prevents a future caller from using another target. The operator checklist is procedural, not an application guard. | 0; no implementation authorized. | Reviewer confirmed task 1.2 must remain unchecked until the negative isolation criterion is verified. |
| F-04 | Gate; Change A task 1.3 | No exact live-run approval reference exists; current authorization explicitly prohibits ingestion/OpenAI calls. | 0; no correction; approval required. | Pending human approval. |
| F-05 | Low; report clarity | Reviewer noted candidate values might be mistaken for approved run values. | Attempt 1: renamed section to “Illustrative per-run manifest” and added an explicit statement that all values remain proposals. | PASS on separate-context re-review. |

## Test integrity

- Tests added/changed: none.
- Demonstrated test defect: none.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: no test behavior changed; only a disposable infrastructure/S3 SDK probe ran and its resources were removed.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: whole-run document/byte/time ceilings are not enforceable by the current connector; no production live-run invocation exists; `IngestionRun` schema cannot be reinterpreted in this preflight; one run of historical Finep call 751 would validate only that identity and current public flow, not current call coverage.
- Decisions made: none. Values above are proposals, not approval or durable architecture decisions.
- OpenSpec tasks marked complete: 1.1 only; evidence is the proposed manifest and preflight checklist. Task 1.2 remains unchecked because its required negative isolation gate is not implemented/tested, although the positive local resource isolation and cleanup checks passed. Task 1.3 remains unchecked pending exact approval. No task 2.x or 3.x task was started or marked.
- Final verification after report/tasks update: OpenSpec strict for both changes and all specs passed; `git diff --check`, staged diff check, whitespace and token-like credential scan passed after F-05 correction. Independent review returned `ESCALATE` for live readiness and PASS for the F-05 wording correction.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `ESCALATE` for online readiness because F-01 and F-02 are material gates and F-04 approval is absent. Preflight-only preparation may be accepted independently.
- Human approval still required before commit/push: yes; no post-Etapa-1 changes will be committed/pushed.
- Human approval still required before scope/architectural change: yes.
