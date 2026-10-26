# Verification record: finep-live-integration-validation / one authorized live run

## Outcome

**Overall: ESCALATE.** The single live execution completed technically and the isolated data was read back successfully. The report supplies the evidence for human review, but no factual homologation is claimed. The private runner's terminal result was also `ESCALATE` because evidence requires human review. This authorization is consumed; no retry or second live execution is authorized.

## Run identity and authorization

- Change/cycle: `finep-live-integration-validation`, one approved live validation.
- Human approval reference: `FOMENTIQ-LIVE-FINEP-751-20261026-APPROVED-USD1`.
- Code: `a179eaba1ddea2d5dcb801475b6a214ea6a8884c` (`main` and `origin/main` at execution start; CI run `40387914685` passed backend, frontend and OpenSpec).
- Opportunity: Finep `databaseId=751`, portal entry `751092`, title `PROINFRA 2024 EXPANSÃO`.
- Canonical source URL: the URL in the approved manifest; source identity readback matched the approved call. No alternative opportunity was processed.
- Manifest: [`finep-live-integration-validation-manifest-candidate-2026-10-26.json`](finep-live-integration-validation-manifest-candidate-2026-10-26.json). Its only change from the approved candidate was replacing `human-live-approval-pending` with the exact approval reference above.
- Isolated Compose project suffix/manifest run ID: `11d09f95-7b39-4ab6-b0b8-d8719f8935a1`.
- `IngestionRun` ID: `2e859ec1-3916-4361-b10c-ac454b65061c`. These are distinct identifiers: the first identifies the isolated validation environment/reservation and artifact directory; the second is the persisted domain run ID.
- Start/end UTC: `2026-10-26 19:46:09.461812Z` to `2026-10-26 19:46:49.805860Z` (40.344 s persisted run duration; runner wall duration 49.979 s including surrounding work).
- Pre-run worktree was clean; no commit/push was made. The v0.1.0 tag was unchanged.

## Effective limits and isolation

- Documents: maximum 18; each at most 5 MiB; aggregate at most 12 MiB. Observed: 18 PDFs; largest 1,069,861 bytes; aggregate 5,406,555 bytes. All complete objects were retrieved from isolated S3-compatible storage and recomputed SHA-256 values matched persisted versions. No partial/truncated document was accepted.
- HTTP: 27 source requests, within manifest maximum 150; redirect cap 4; approved host/URL policy enforced. Connect/read/operation/global timeouts: 10/30/45/900 s. The run completed within these limits.
- OpenAI: `gpt-6-luna`, schema `finep-call-v1`, reasoning `low`; max attempts 1, configured automatic retries 0; input/output ceilings 64,000/12,000 tokens; request timeout 60 s. One provider extraction execution is present in the isolated database. No retry or replay was made.
- Isolated PostgreSQL: `fomentiq_live` database/user, unique Compose project, exclusive volumes and loopback-only ports. Isolated S3-compatible bucket was unique to this run. The preflight guard passed before external requests. Negative/policy suite: 21 tests passed before access. Manual application health was HTTP 200 before the run and remained unaffected.
- Resources were separate from the manual demo and any operational target. Cleanup status is recorded below; only the unique run project/volumes may be removed.

## Provider usage and cost

- Provider execution status: `SUCCEEDED`; reported usage 15,769 input and 2,257 output tokens.
- Official pricing checked 2026-10-26: [OpenAI API Pricing](https://developers.openai.com/api/docs/pricing), Standard short-context GPT-6 Luna input USD 0.10/M and output USD 0.50/M. Calculation: `(15,769 × 0.10 + 2,257 × 0.50) / 1,000,000 = USD 0.0027054`.
- Ceiling estimate before the call was USD 0.0124 at the authorized 64k/12k token ceilings (USD 0.022 under the documented cache-write input scenario). The USD 1.00 authorization was a risk budget, **not** a provider-enforced hard spending cap.
- Model support for Responses API and Structured Outputs, plus low reasoning support, is documented on the [GPT-6 Luna model page](https://developers.openai.com/api/docs/models/gpt-6-luna).
- No credential or token value is recorded here. The key's presence was checked without printing it.

## Ingestion and preserved artifacts

- `IngestionRun`: `2e859ec1-3916-4361-b10c-ac454b65061c`; source `FINEP`; terminal status `SUCCEEDED`; detected 1, changed 19, document failures 0.
- Exactly one call identity row exists for external ID 751 and portal entry 751092; one `CallVersion` was persisted. The portal ID did not create a second opportunity.
- Snapshots were persisted before extraction: CALL SHA-256 `ebb59c254adb6bd99ac7cfd17419cdccdba9af367d3b094c37b78191580f4520`; DISCOVERY `d24ab27ae838eda46d5162a64ff9ec727989d21df817cc8ec8146d967b51ac3c`; DOCUMENT_LIST `b7a2e1e8093c13e7874d8fa96e4691462a3500205d266df96df7eb8acc368dbc`.
- 18 original PDFs were preserved; each had one `CallDocumentVersion`, content type `application/pdf`, and verified SHA-256:

| # | Document | SHA-256 |
|---:|---|---|
| 1 | `04_07_2025-PROINFRA2024Expansao_ResultadoPreliminar_Habilitacao.pdf` | `49fc34eb787b64f1348e7ddcf9f00d04143c177ac9856a797bc3854314b1c5b1` |
| 2 | `07_04_2025_Edital_PROINFRA2024-Expansao_2aRerratificacao.pdf` | `97e2b60f895b7c205e931f0d947e3da1742ccedbe573442a85d4f417dec1d003` |
| 3 | `07_04_2025_Edital_PROINFRA2024-Expansao_comunicado.pdf` | `61f10f9a28ee8e40a4a3b5db27d28549981ed95407f0c7f9f5c41a550c9e5ebd` |
| 4 | `12_12_2024_EDIP_2024_AIII.pdf` | `265f7224b54d303583b4c236e4f14b3efc72fd3347a940f44b23752bd265e0a6` |
| 5 | `12_12_2024_EDIP_2024_AII.pdf` | `76e1220af5791e8423f62b3c251a42347cfbe30eecf148783ba98a320fb52bbf` |
| 6 | `12_12_2024_EDIP_2024_AI.pdf` | `c5f3c2e9cd277714426691556ca5266c88dd50c04e6c03ca293c4b21aaf4a1d3` |
| 7 | `12_12_2024_EDIP_2024_AIV.pdf` | `88728964da191afa58c476945697dfc1e8d44fce51bb126dffe5cc5a7655d340` |
| 8 | `12_12_2024_EDIP_2024_AVII.pdf` | `f6f81b0f441727abc6b18712568e4a297c88af6f413b19221c6575eb4aec38a8` |
| 9 | `12_12_2024_EDIP_2024_AVI.pdf` | `7ac34e9a05bd9ab7170a649adc2008b3439e8bcdba201db4f2adde004adc42e2` |
| 10 | `12_12_2024_EDIP_2024_AV.pdf` | `0c4fb3ec6420232841ebbe38ea452afd7dd62c0f39991eaf4b9e7625ea88123e` |
| 11 | `12_12-2024_EDIP2024_EditaL.pdf` | `e8fa665a29e3dc32400d215cbd5daf3c8ab8ac74817c539b41e29608517c303e` |
| 12 | `18_09_2025_PROINFRA2024Expansao_ResultadoPreliminar_Merito.pdf` | `97dd909669121e0dd6e9f96fc9133da1a6af58db464af4326a1537a31a6b8feb` |
| 13 | `23_10_2025_ESCLARECIMENTO_RESULTADO_FINAL_23OUT2025.pdf` | `00a6bcdacf0769b5dd36906855ce1dcb5f09819131bc4c70b14c36af4cc79ab0` |
| 14 | `23_12_2024_Exp_2024_1a_Rerratificacao.pdf` | `3f441d0cb25f364f30b65811274701637d3e9f95adafc4282d4a8e334e5268cb` |
| 15 | `23_12_2024_Exp_2024_Cominicado.pdf` | `72ed14bd96b739d3250cac943dea5517457fe4ac4f6032afd6f156a07a8a9f14` |
| 16 | `26_11_2025_PROINFRA2024Expansao_ResultadoFinal_Merito.pdf` | `dd652d549633ec73bda2633b2e70369800756c857d6484990c3ff0e7002a23ac` |
| 17 | `27_01_2024_Telas_FAP_e_Numero_de_Caracteres.pdf` | `3b1bd4a8bfeff89cdcbc98ed57701a4bae360e9a55faa77d5f41db4be1a5b5b1` |
| 18 | `31_07_2025_PROINFRA2024Expansao_ResultadoFinal_Habilitacao.pdf` | `a77eebf90e265d7d54149722e568ad30331b86891bdf009ff50b03fa9480028c` |

The three edited call notices relevant to requirements are document 11 (original), document 14 (first rectification), and document 2 (second rectification). The extracted `counterpart` and eligibility evidence below cite document 2. The same eligibility wording and the cited municipality range also appear in documents 11 and 14. The excerpted version comparison found no conflicting value for those quoted clauses; it does not establish that every clause across the full corpus is identical. The table intentionally retains source/version provenance and does not apply a “latest document always wins” rule.

## Critical field evidence for human review

Normalized values and states below are exactly those persisted by extraction. Quotes and locators are from the preserved PDF version; page numbers are PDF page numbers. `VALIDATED` is the deterministic application state, not human homologation.

| Field | Persisted value | State | Source document / version SHA-256 | Exact excerpt and locator | Human review |
|---|---|---|---|---|---|
| `deadline` | No value (`NULL`) | `MISSING` | No linked evidence | No evidence locator or quote is attached to this extracted field. | Confirm whether a deadline is explicitly defined in an applicable call notice/rectification or should remain missing. |
| `totalBudget` | No value (`NULL`) | `MISSING` | No linked evidence | No evidence locator or quote is attached to this extracted field. | Confirm whether the notice expresses a single total call budget; do not infer from individual project/result values. |
| `eligibility` | Public ICTs or private ICTs qualified as ICES, registered in SISGON; MCTI research units and linked social organizations excluded; only entities may submit, not their linked units/branches. | `VALIDATED` | `07_04_2025_Edital_PROINFRA2024-Expansao_2aRerratificacao.pdf`, `97e2b60f895b7c205e931f0d947e3da1742ccedbe573442a85d4f417dec1d003` | “3.1. São elegíveis para apresentar proposta ICTs públicas ou ICTs privadas qualificadas como Instituição Comunitária de Educação Superior (ICES), cadastradas no SISGON, conforme itens 2.1.12, 2.1.13 e 9.1.3.” / “3.1.1. Para fins deste edital, as unidades de pesquisa e as organizações sociais vinculadas ao MCTI não estarão elegíveis, na medida em que concorrerão em edital específico.” / “3.2. Apenas Entidades poderão apresentar proposta, sendo vedada a apresentação de proposta por seus órgãos ou quaisquer outras unidades vinculadas (p.ex. institutos, centros e filiais),” PDF p. 4, segment `68b42ba3-6937-4dea-a610-7fc7eb6e20b5:4`. | Verify the full restriction and its application to the intended applicant. |
| `counterpart` | Municipal institutions up to 50,000 residents: 0.1%–4%; the persisted JSON has empty arrays for state/federal ranges. | `VALIDATED` (persisted; **incomplete against the source**) | `07_04_2025_Edital_PROINFRA2024-Expansao_2aRerratificacao.pdf`, `97e2b60f895b7c205e931f0d947e3da1742ccedbe573442a85d4f417dec1d003` | “7.1.1. Instituições Municipais: i. Municípios com até 50.000 (cinquenta mil) habitantes...................................0,1-4%” PDF p. 14, segment `68b42ba3-6937-4dea-a610-7fc7eb6e20b5:14`. The same section continues with additional municipal and state/DF ranges on pp. 14–15. | **Not accepted as a complete field.** The source contains ranges omitted from the persisted extraction. Human must decide how to represent the complete conditional ranges; do not treat the current value as a complete confirmed summary. No re-extraction/retry was made. |
| `trl` | No value (`NULL`) | `MISSING` | No linked evidence | No evidence locator or quote is attached to this extracted field. | Confirm that no TRL requirement is asserted without documentary support. |
| `requiresIct` | `true` | `VALIDATED` | `07_04_2025_Edital_PROINFRA2024-Expansao_2aRerratificacao.pdf`, `97e2b60f895b7c205e931f0d947e3da1742ccedbe573442a85d4f417dec1d003` | “3.1. São elegíveis para apresentar proposta ICTs públicas ou ICTs privadas qualificadas como Instituição Comunitária de Educação Superior (ICES)” PDF p. 4, segment `68b42ba3-6937-4dea-a610-7fc7eb6e20b5:4`. | Confirm this boolean interpretation against the full eligibility clause. |

### Version divergence and extraction limitations

- The original edital (document 11, SHA `e8fa665a29e3dc32400d215cbd5daf3c8ab8ac74817c539b41e29608517c303e`) and first rectification (document 14, SHA `3f441d0cb25f364f30b65811274701637d3e9f95adafc4282d4a8e334e5268cb`) retain a corresponding base eligibility clause and the same quoted municipal counterpart range. The explicit exclusion of MCTI-linked research units/social organizations appears in the rectification text cited by the extraction; it is an amendment/succession detail and must remain associated with that version, rather than being silently represented as wording in the original. The second rectification (document 2) is the version cited by the evidence records.
- The extraction's `counterpart` value is incomplete: it includes a supported municipality range but the source contains other conditional ranges in the same section. The persisted field status remains as produced by this one run; this report does not modify or promote it. This is a concrete residual finding for human adjudication and prevents factual acceptance of that field.
- The result of that focused comparison does not mean the documents are interchangeable or that later results notices amend the call requirements. There is no explicit field-level change relation persisted by the extractor for these clauses.
- `deadline`, `totalBudget`, and `trl` remain `MISSING`, with no evidence, and must not be treated as confirmed or inferred from outcome notices, project-level values, or date-bearing filenames.
- The application stores PDF originals; extracted PDF text is generated on demand (`has_extracted_text=false` in document version rows). The exact quoted locators above resolve to the preserved document hash and PDF page/segment.

## Regression checks after the one live execution

| Command | Result | Scope |
|---|---|---|
| `mvn -B -f backend/pom.xml clean verify` | PASS; 70 tests, 0 failures/errors/skips; build success. | Includes PostgreSQL/Testcontainers, fixture replay, identical-input idempotency and synthetic changed-call version preservation. No Finep/OpenAI test requests. |
| `npm run lint` | PASS. | No frontend source changes in this run. |
| `npm run typecheck` | PASS. | No frontend source changes in this run. |
| `npm run test -- --run` | PASS; 24 tests. | Component suite; no external services. |
| `npm run build` | PASS. | Production build completed. |
| `openspec validate finep-live-integration-validation --strict` | PASS. | Validation after marking only live tasks 2.1, 2.2, 2.3 and offline regression 3.2 complete. |
| `git diff --check` | PASS. | Report/manifest/tasks only. |
| Manual application health after run | PASS; backend `http://127.0.0.1:8080/actuator/health` and frontend `http://127.0.0.1:4173/` returned HTTP 200. | Manual application remained running; no manual Compose resource was stopped or modified. |

## Independent review

- Separate context reviewer: `/root/live_validation_independent_review`.
- Initial read-only review result: `RETRY` for this report, with findings F-LIVE-01 through F-LIVE-05: report still pre-run, missing field evidence, unexplained run ID distinction, attempt/inventory evidence insufficiently stated, and isolated resources not yet cleaned.
- Corrections: this report now records actual run and execution identifiers separately; includes all 18 filenames and SHA-256 hashes, byte totals, persisted run/extraction result, six field values/states and exact evidence/locators where present; records `MISSING` fields without invented citations; and explicitly states cleanup status and remaining factual review gate. A further source check found the persisted counterpart summary incomplete and this is disclosed rather than corrected by modifying run data. No implementation, test, manifest limit, or fixture was changed to address review findings.
- Re-review: `PASS` for report/documentation consistency, separate context, read-only. The reviewer found the earlier gaps corrected, confirmed the report remains `ESCALATE` pending human adjudication, and found no credentials. Limitation: the reviewer inspected the evidence as documented and did not independently query the isolated database or S3 bucket. The report author performed the recorded database/object readbacks before review.
- Final focused review: `PASS`. The reviewer independently recomputed the retained PDF hashes and checked the original and rectification eligibility wording. It confirmed the original contains the base clause and the explicit MCTI exclusion first appears in the rectification. It also confirmed exact-project cleanup and absence of checked temporary secret/log/classpath paths. No file was changed and no external request was made.

## Findings and correction attempts

| Finding ID | Severity | First review finding | Attempt and disposition |
|---|---|---|---|
| F-LIVE-01 | High | Report still contained pre-run placeholders and contradicted the sanitized execution record. | Attempt 1: replaced placeholders with actual run, UTC timing, counts, test results, cleanup and terminal status. Independent re-review: resolved. |
| F-LIVE-02 | High | No per-field evidence was presented for critical fields. | Attempt 1: added six-row field table with persisted values/statuses and exact source hashes, quotes and locators where evidence exists; absent fields remain explicitly evidence-free. Independent re-review: resolved as a report-evidence gap. |
| F-LIVE-03 | High | Manifest/reservation ID differed from `IngestionRun` ID without explanation. | Attempt 1: documented the reservation/environment ID and separate persisted domain run ID. Independent re-review: resolved. |
| F-LIVE-04 | Medium | Configured attempt count alone did not demonstrate execution/inventory limits. | Attempt 1: recorded the single persisted provider execution, no-retry configuration, 18 verified object hashes and observed byte/request/token totals. Independent reviewer notes it reviewed report evidence, not direct DB/S3 reads. Residual evidence limitation disclosed. |
| F-LIVE-05 | High | Dedicated Compose services and volumes remained active. | Attempt 1: after independent report review, removed only the exact labeled project/network/volumes and generated secret/log/classpath/preflight paths; verified absence and manual app health. Independent re-review: resolved. |
| F-LIVE-06 | High | Persisted `counterpart` is `VALIDATED` but omits additional conditional ranges present in the preserved source. | Attempt 1: cross-checked original and rectifications and recorded the incomplete extraction without changing persisted data. No further extraction/retry is allowed by this one-run authorization; escalated for human adjudication. |

## OpenSpec task status and cleanup

- Tasks 1.1–1.5 were complete before the live execution; task 1.3 now carries the exact approval reference.
- Tasks 2.1–2.3, 3.2–3.4 are marked complete on the live evidence, successful offline regressions, independent read-only report review, and exclusive environment cleanup. Task 3.1 remains unchecked because the persisted counterpart summary is incomplete and requires adjudication. The technical evidence review is not human factual homologation.
- Isolated Compose project `fomentiq-finep-live-11d09f95-7b39-4ab6-b0b8-d8719f8935a1` was stopped and removed with its two labeled volumes and network. Follow-up `docker ps -a` and `docker volume ls` by that exact Compose project label returned no resources. The manual demo project was untouched; backend health and frontend returned HTTP 200 after cleanup.
- Temporary environment credentials, process log, generated classpath file, and preflight directory were deleted and verified absent. Sanitized report/runner JSON and public-source PDF copies remain for human evidence review outside the repository. No credential or raw OpenAI response is retained in repository artifacts.
- OpenSpec validation, `git diff --check`, worktree review, manual-app health, and exclusive cleanup verification passed. Terminal overall classification remains `ESCALATE` pending factual human review of the six fields, especially the incomplete counterpart value.

## Residual risks and decision

- Technical execution succeeded, but no independent factual homologation is claimed. Human review of the six rows above remains required before factual acceptance.
- USD 1.00 was a risk authorization and not a hard API billing cap. Actual reported usage estimate is USD 0.0027054 at the cited prices.
- One OpenAI request was initiated and completed; this one-use approval is consumed. No retry, repeat run, other call, other model, or expanded limit is authorized.
- Overall state remains `ESCALATE` solely for human factual review of the six critical fields; the report review, offline regressions and isolated cleanup are complete. No commit/push was made at the time of the live run.
