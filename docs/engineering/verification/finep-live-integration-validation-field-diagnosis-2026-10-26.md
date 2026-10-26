# Verification record: finep-live-integration-validation / factual field diagnosis

## Run identity

- Change/cycle: `finep-live-integration-validation` / offline diagnosis of the single live run.
- OpenSpec scope: diagnostic evidence for task 3.1; **task 3.1 is intentionally not marked complete**.
- Live run under review: `2e859ec1-3916-4361-b10c-ac454b65061c`.
- Date: 2026-10-26 UTC.
- Implementer: primary Codex context. Independent reviewer: `/root/live_validation_independent_review`, separate read-only collaboration context; review outcome `PASS`.
- No Finep/OpenAI requests, no database/storage access, no source/test/fixture/task edits, and no re-ingestion were performed in this diagnosis.
- Final state: `ESCALATE` because the exact live provider input and full persisted field payload were not retained in the sanitized run artifacts, and the live `counterpart` result is demonstrably incomplete.

## Acceptance matrix

| ID | Requirement / task | Acceptance criterion | Evidence and limitation |
|---|---|---|---|
| AC-01 | OpenSpec task 3.1 | Compare each of the six critical fields against source-version evidence without treating a model response as proof. | Previous sanitized live report and JSON, golden fixture `expected.json`, 18 fixture PDFs, exact code path reviewed. Live raw provider response and full selected-excerpt list are not preserved. |
| AC-02 | User authorization | Separate live-run facts, frozen-fixture facts, and inference; do not synthesize missing live artifacts. | Evidence labels in this report; offline selector output is explicitly labeled a replay, not the live request. |
| AC-03 | User authorization / project provenance rule | Identify source version, applicable clause/page, and amendments for each field. | PDF SHA-256 equality between all 18 retained live PDF copies and frozen fixture PDFs; PDFBox page-level reading; fixture review manifest. |
| AC-04 | User authorization | Distinguish selection, extraction, validation, and genuine source absence. | `DocumentExcerptSelector`, `StructuredExtractionService`, `DeterministicFieldValidator`; persisted statuses/quotes from previous report. |
| AC-05 | User authorization | Recommend minimal code/tests without implementing or changing scope. | Findings and proposed regression cases below; no code, schema, fixture, or task modifications. |

## Plan and scope

1. Read the project sources of truth, `finep-live-integration-validation` proposal/design/spec/tasks, and the earlier live-run report/JSON.
2. Verify whether retained PDFs match the frozen fixture by SHA-256, then inspect only local fixture/source artifacts and code.
3. Compare live-reported critical-field states/values to the reviewed fixture candidates and source succession.
4. Run a deterministic **fixture-only** reproduction of the excerpt selector to assess selection risks. Do not claim its output was the historical live request.
5. Record stable findings, propose narrow regression tests, request independent review, and leave OpenSpec task 3.1 untouched.

Affected repository file: this new report only. No architectural decision or implementation change is authorized by this diagnostic.

## Evidence categories

### A. Preserved live-run evidence

The earlier sanitized report records the live `IngestionRun` as `SUCCEEDED`, one OpenAI extraction execution, the six critical field summaries, and the evidence actually attached to non-missing fields. It records these live field outcomes:

- `deadline`: `NULL`, `MISSING`, no evidence.
- `totalBudget`: `NULL`, `MISSING`, no evidence.
- `trl`: `NULL`, `MISSING`, no evidence.
- `counterpart`: a value containing only one municipal range (up to 50,000 residents, 0.1%–4%), empty `exempt` and state/federal arrays, `VALIDATED`; its evidence quotes that one range from the second rectification, PDF p. 14, segment `68b42ba3-6937-4dea-a610-7fc7eb6e20b5:14`.
- `eligibility`: `VALIDATED`, reported in the previous sanitized report as public ICTs/private ICES, SISGON registration, MCTI-linked exclusions, and entity-only submission; citation from the second rectification, PDF p. 4, segment `68b42ba3-6937-4dea-a610-7fc7eb6e20b5:4`.
- `requiresIct`: `true`, `VALIDATED`, with the p. 4 eligibility quotation.

Important preservation limitation: the previous report/runner JSON do **not** contain the live OpenAI raw response, the complete normalized JSON for every field, the selected excerpt list/text, or the stored extraction input digest. Those values existed in the disposable run database, which was removed during approved cleanup. This report does not recreate them. As a result, the exact complete text payload sent in that live request cannot now be attested from retained artifacts.

### B. Frozen-fixture evidence

The 18 PDFs retained for offline review match all 18 PDFs under `backend/src/test/resources/fixtures/finep/proinfra-2024-expansao-751/pdf/` by SHA-256. The exact document inventory and hashes are in [the live-run report](finep-live-integration-validation-live-run-2026-10-26.md). The fixture's `expected.json` is a human-reviewed candidate reference, not a production extraction response and not proof of what the live model returned. Its own review method says this explicitly.

### C. Offline selector replay and inference

The current `DocumentExcerptSelector` was run locally against the frozen fixture PDFs in source-catalog order. This is a deterministic **fixture replay only**; it is not represented as a preserved live payload. Since the live PDFs hash-match the fixture and the selector is deterministic, the replay is diagnostic evidence of the selector's coverage behavior, but does not replace the missing historical request artifact.

The replay selected 18 page excerpts from the first six documents after title-priority filtering; it did not select second-rectification p. 12 (budget) or p. 15 (final deadline). It selected p. 14 of the second rectification but truncated that page to its first 1,400 characters, retaining the first municipality band while excluding the subsequent bands. It also selected the second-rectification communiqué's first page only as a final 200-character excerpt after the total-character budget was nearly exhausted; the 06/06/2025 date was outside that prefix. The replay selected p. 4 for eligibility, but the 1,400-character prefix did not reach item 3.9 (private non-profit organizations' three-year operation requirement).

This is consistent with the live citations and omissions, but the missing live input digest/excerpt artifact means it remains an inference about that run. It is not proof of the exact live request body.

## Version and field comparison for human review

The source is PROINFRA 2024 Expansão (`databaseId=751`, portal `751092`). Page references below are PDF pages. Hash abbreviations point to the exact full SHA-256 values in the linked live-run inventory.

| Critical field | Live result | Applicable fixture/source evidence and version succession | Diagnosis / human review |
|---|---|---|---|
| `deadline` | `NULL`, `MISSING`, no live evidence. | Original edital (`12_12-2024_EDIP2024_EditaL.pdf`, `e8fa665a…`), p. 15, item 9.1: proposal submission deadline 25/04/2025. First rectification (`23_12_2024_Exp_2024_1a_Rerratificacao.pdf`, `3f441d0c…`), p. 15, item 8.1: still 25/04/2025. Second rectification (`07_04_2025_Edital_PROINFRA2024-Expansao_2aRerratificacao.pdf`, `97e2b60f…`), p. 15, item 8.1: 06/06/2025; item 8.3 states 17h00 Brasília. The accompanying second-rectification communiqué, p. 1, announces the extension. Fixture expected value: `2025-06-06T17:00:00-03:00`, qualifier implicit in the exact deadline text. | This is not a legitimate absence from the source. The fixture selector replay did not carry p. 15 and clipped the communiqué before the date. Live request omission versus provider miss cannot be conclusively distinguished because the exact live excerpts were not preserved. Likely selector coverage failure; confirm against human review. |
| `totalBudget` | `NULL`, `MISSING`, no live evidence. | Original edital, p. 12, item 6.1: up to R$700,000,000. First rectification, p. 12, item 5.1: up to R$500,000,000. Second rectification, p. 12, item 5.1: “até o limite de R$500.000.000,00”. Fixture expected value: BRL 500,000,000, qualifier `UP_TO`. | The applicable latest call notice states a total budget. The fixture selector replay did not select p. 12 and no selected prefix contained the R$500m passage. Likely selection miss, not validation failure; full live request absence cannot be proven after cleanup. Preserve the historical 700m value as superseded evidence, not as a competing current value. |
| `eligibility` | `VALIDATED`; previous sanitized summary records only part of the rule and p. 4 evidence. Full live field JSON was not retained. | Second rectification, p. 4, items 3.1–3.9. Base eligibility: public ICTs or private ICTs qualified as ICES, registered in SISGON. Item 3.1.1 excludes MCTI-linked research units/social organizations; item 3.2 restricts submission by linked units; item 3.5 requires federal public executors to partner with an eligible support institution; item 3.9 requires private non-profit ICTs to have operated regularly for three years. First and second rectifications include the explicit MCTI exclusion; it is an amendment detail, not wording to attribute to the original notice. Fixture expected JSON includes `federalExecutorRequiresSupportInstitution=true` and `privateNonprofitMinimumOperatingYears=3` in addition to base eligibility. | The available live summary does not show all clauses in the fixture candidate. The p. 4 excerpt was cited, but a fixture replay of the 1,400-character prefix reaches item 3.5 and not item 3.9. The field may be partial and `VALIDATED` is not justified by one citation alone; full live JSON is unavailable to settle exact omissions. Human review should not treat it as exhaustive. |
| `counterpart` | `VALIDATED`; live JSON summary has one range (municipal ≤50,000, 0.1%–4%), `exempt=[]`, and state/federal ranges `=[]`. Evidence is only the first range on second rectification p. 14. | The original edital p. 14, section 8; first rectification p. 14, section 7; and second rectification p. 14, section 7.1–7.3 contain the same conditional bands. Fixture expected value includes: municipal ≤50k 0.1%–4%; >50k in PNDR/Sudene/Sudam/Sudeco priority areas 0.2%–8%; ≤200k in MCTI-listed disaster-vulnerable areas 0.1%–5%; ≤200k coastal/estuary risk in the MMA list 0.1%–5%; other municipalities 1%–20%; priority states/DF 0.1%–10%; other states 2%–20%. Sections 8.3/7.3 exempt federal ICTs and private non-profits as proposer/convenente. | Concrete incomplete extraction. Evidence proves one band only. Fixture replay confirms the p. 14 excerpt was truncated at 1,400 characters, before later conditional bands/exemptions. The validator's `counterpart` rule checks only that the JSON is an object with at least one key and that some evidence exists; empty arrays and omitted categories do not fail validation. This explains how a partial object became `VALIDATED`. Human review must reject the current field as complete. |
| `trl` | `NULL`, `MISSING`, no live evidence. | Frozen `expected.json` deliberately records `trl=null`, `MISSING`, no evidence. The three call notices do not state a TRL threshold. A literal corpus scan found `TRL`/“Technology Readiness Level” zero times in the 18 PDFs. “nível de maturidade tecnológica” occurs in four results PDFs as a result-table column, not as a call eligibility requirement. | `MISSING` is appropriate for the call requirement. Result notices must not be used to invent a TRL criterion. Human review may confirm the distinction between a result-table label and an applicant requirement. |
| `requiresIct` | `true`, `VALIDATED`, evidence from second-rectification p. 4. | Second rectification, p. 4, item 3.1: proposals are eligible from public ICTs or private ICTs qualified as ICES. Fixture expected value is `true`; its evidence also cites p. 3 definition of executing institution and p. 4 eligibility. | Evidence directly supports that the call is ICT-focused. The boolean validator checks type/value and evidence. This result is better supported than the other two `VALIDATED` structured summaries, though human confirmation remains the product gate. |

## Failure classification

1. **`deadline` and `totalBudget`: likely selection failure, not deterministic-validation failure.** The live rows are `NULL/MISSING`. `DeterministicFieldValidator.validate()` immediately returns `MISSING` for null/blank input; it does not report these as invalid dates or amounts. The applicable passages exist in the second rectification, but are absent from the fixture selector replay. Whether the live model saw a passage and still returned null cannot be decided without the discarded excerpt list/raw response.
2. **`trl`: legitimate missing call requirement.** The frozen reviewed fixture also leaves it missing; the corpus has no TRL requirement in the notices. Result documents' “maturidade tecnológica” column is not a call requirement.
3. **`counterpart`: extraction incompleteness plus validation defect.** A single field-level quote was accepted as evidence for a multi-branch conditional rule. The deterministic validator tests JSON object-ness/non-emptiness, not all branches, exceptions, or evidence coverage. Empty arrays therefore pass. This is not a provider-only failure.
4. **`eligibility`: likely partial extraction with the same coarse validation gap.** The fixture expected candidate contains requirements beyond the live report summary. The exact raw live JSON was not preserved, so the omitted live subfields cannot be enumerated as definitive DB facts.
5. **Exact historical provider input is unavailable.** `StructuredExtractionService` computes an input digest and stores raw provider response in `extraction_execution`, but both were in the disposable PostgreSQL volume removed after the run and neither was copied to sanitized artifacts. `SourceExcerpt`s are not stored as a retained request artifact. No synthetic reconstruction is presented as the live request.

## Stable findings and attempts

| Finding ID | Severity | Evidence / impact | Attempt and disposition |
|---|---|---|---|
| F-DIAG-01 | High | Critical source pages are not guaranteed in the bounded selected excerpts; fixture replay misses budget/deadline pages and clips long clauses. | Attempt 1: reproduced selector behavior offline with fixture PDFs; no implementation change authorized. Recommend field-aware clause selection and deterministic coverage regression tests. |
| F-DIAG-02 | High | `counterpart` (and potentially `eligibility`) can become `VALIDATED` with a partial object and one evidence quote. | Attempt 1: traced exact validator/service path and compared persisted summary to source/fixture. No implementation change authorized. Recommend conservative `EXTRACTED` until complete clause coverage is verifiable. |
| F-DIAG-03 | Medium | The full live selected excerpts, raw model response and full persisted normalized fields were not retained in sanitized evidence after disposable environment cleanup. | Attempt 1: checked all retained run artifacts and reports. Cannot recover from available evidence without violating the instruction not to reconstruct the historical live request. Escalated; preserve selected-excerpt audit artifact/digest in a separately approved future change. |

## Minimal correction and regression recommendations (not implemented)

1. Make excerpt selection field-aware and clause/paragraph-aware. Ensure the final deadline row (second rectification p. 15 and communiqué) and total budget (p. 12) are candidates, preserve enough following text to include conditions, and do not exhaust the total budget on repeated cover/definition pages. Do not merely increase the global character cap without field coverage rules.
2. Add frozen-fixture selector tests asserting that selected excerpts contain the final deadline, R$500m total budget, all counterpart bands/exemptions, and eligibility items 3.1, 3.1.1, 3.2, 3.5 and 3.9. Assert source version/page locators and bounded size.
3. Add validator/service tests where a `counterpart` object contains one supported band and empty remaining arrays: it must not become `VALIDATED`. Add a partial eligibility case missing a cited condition. The complete fixture candidate should become valid only when every asserted clause/subvalue has verifiable provenance under the approved policy.
4. Preserve an audit record of exact selected source excerpt IDs, versions/pages, bounded text or a recoverable immutable artifact reference, and input digest before provider call. Keep credentials out; do not put sensitive raw provider response in user-facing logs. Any persistence/schema or retention change requires explicit specification and approval before implementation.
5. Treat future reprocessing as a new, separately approved operation. Do not spend again under the consumed one-call authorization or overwrite this live run's evidence/history.

These are recommendations for a future approved correction, not changes made in this diagnostic.

## Commands and results

| Command / check | Result | Limitation |
|---|---|---|
| Read project sources, OpenSpec proposal/design/spec/tasks, current live report/JSON, selector, extraction service, validator, schema, fixture expected data | PASS | Read-only. |
| Python SHA-256 comparison of the 18 retained live PDF copies against frozen fixture PDFs | PASS; all 18 matched by content hash. | Documents are public source files retained under `/tmp`; this does not restore deleted database rows/raw provider input. |
| PDFBox corpus scan across 18 frozen PDFs for deadline/budget/TRL/maturity phrases and version pages | PASS | Offline only. TRL phrase results include result-table headings; not call eligibility evidence. |
| Fixture-only `DocumentExcerptSelector` replay | PASS as diagnostic reproduction | Not the historical live OpenAI request. Exact live selected text cannot be verified because it was not preserved. |
| Code path inspection: `StructuredExtractionService` and `DeterministicFieldValidator` | PASS | Confirms `null` → `MISSING`; any evidence plus non-empty object → `VALIDATED` for eligibility/counterpart. |
| One temporary ad-hoc PDFBox printing helper compile | First attempt failed due to a syntax error in temporary `/tmp` helper; corrected helper then read the pages successfully. | No repository file or test was affected. |
| `openspec validate finep-live-integration-validation --strict` | PASS, exit 0. | OpenSpec task 3.1 remains unchecked; no task state was changed in this diagnostic. |
| `git diff --check` | PASS, exit 0. | Existing worktree changes predate this diagnostic and were preserved. |
| `git status --short` | PASS; only the pre-existing live-run/approval artifacts plus this new diagnostic report are present. | No code, fixture, or OpenSpec task file was modified by this diagnostic. |

## Independent review

- Reviewer context/mechanism: `/root/live_validation_independent_review`, a separate read-only collaboration context.
- Reviewer received this report, task 3.1/spec, the earlier sanitized live report/JSON, fixture `expected.json` and source PDFs, plus the relevant code path.
- Reviewer outcome: `PASS`, no new report finding. Independently confirmed the 18/18 PDF hash matches, version chronology (R$700m / 25-April original; R$500m / 25-April first rectification; R$500m / 06-June second rectification), selector replay boundaries, null/object validator behavior, fixture expected values, and TRL search result.
- Reviewer confirmed that no code, test, fixture, or task files were changed and made no external request or access to removed DB/storage. Existing worktree changes were preserved; only this diagnostic report is new for this run.
- Independent review does not remove the evidence limitation or the factual blocker: exact live prompt excerpts/raw provider response are unavailable, and the persisted counterpart summary was incomplete.

## Test integrity and final verification

- Tests added/changed: none. This is an offline diagnostic; no behavior was changed.
- Fixture edits: none. OpenSpec task edits: none; task 3.1 remains unchecked.
- Residual risks: the live request body, exact selected excerpt list, full live eligibility JSON, and raw provider response are unavailable after isolated cleanup. Root cause of `MISSING` values can be narrowed to selection versus provider response only probabilistically, not proven for the historical request.
- OpenSpec tasks marked complete by this diagnostic: none.
- Final state rationale: `ESCALATE` despite report-review PASS because the original live request payload cannot be verified and the incomplete `counterpart` field still requires human factual adjudication. No correction was implemented and no new Finep/OpenAI call is authorized.
- Commit/push and future live-run gates remain pending; neither was performed.
