# Verification record: finep-extraction-factual-quality / FQ-R1 diagnostic

## Run identity

- Change and cycle: `finep-extraction-factual-quality` / `FQ-R1 diagnostic`
- OpenSpec change/tasks in scope: open task 2.3; this run is diagnosis/proposal only and does not resume implementation attempts.
- Date/time (UTC): 2026-10-27
- Implementer context: primary Codex context
- Independent reviewer context/mechanism: separate read-only collaboration context `/root/fq_r1_architecture_diagnostic_review`
- Final state: `ESCALATE` for implementation; independent review `PASS` for the diagnosis/proposal.

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 | User request; preserve current worktree | Inspect current implementation and pre-existing uncommitted changes without changing implementation, schemas, tests, fixtures, tasks, or discarding any work. | `git status --short --untracked-files=all` inspected before and after; only this new diagnostic record was added by this run. |
| AC-02 | OpenSpec task 2.3; FQ-R1 | Reproduce one false-`VALIDATED` counterexample each for counterpart category/range, budget qualifier, and ICT requirement. | Read-only JShell calls against compiled current application classes returned `VALIDATED` for all three cases. |
| AC-03 | Architecture/product principles | Distinguish literal source substring and ID linkage from evidence that proves the semantic subject-predicate-value relationship. | Code-path analysis of `verifiedEvidence`, `validateComposite`, `supportsClause`, `budgetSupported`, and `requiresIctSupported`. |
| AC-04 | User request | Compare the three specified alternatives and recommend the smallest safe approach preserving v1/audit compatibility and avoiding state/schema expansion without approval. | Trade-off analysis below. |
| AC-05 | `AGENTS.md` independent review rule | Obtain independent review of counterexamples and recommendation, with no reviewer edits. | Reviewer confirmed all three reproductions and approved the proposal with stated condition; no files were changed by reviewer. |

## Plan and affected areas

- Planned vertical slice: read current OpenSpec/design and validator path → construct ephemeral, in-memory counterexamples → compare alternatives → independent read-only review.
- Affected files/modules: no implementation, schema, test, fixture, task, or existing report files were modified. This new diagnostic report is the only change created by this run.
- Known risks before analysis: counterexamples must reflect current implementation rather than an earlier draft; `VALIDATED` is technical status and must not be conflated with factual approval.
- Scope/architecture approval needed: yes. Any v2 contract enrichment or policy that gates factual approval requires explicit human approval before implementation.

## Cause and reproduced counterexamples

The current v2 checks establish referential and literal integrity, but not semantic entailment:

- `StructuredExtractionService.verifiedEvidence` only saves a citation when its quote is a literal substring of the selected excerpt. This proves the quote was present in the selected text.
- `validateComposite` checks that clause citation IDs resolve to those literal citations, then `eligibilityClausesMatch` / `counterpartClausesMatch` compare the provider's structured JSON with the provider's own clause objects. That proves internal consistency of the response, not that the source states the claim.
- `supportsClause` looks for lexical anchors in one sentence, but searches range endpoint numbers across the entire quote.
- `budgetSupported` looks for an amount and qualifier words across the entire quote without binding them to one monetary expression.
- `requiresIctSupported` combines a mention of ICT with obligation vocabulary across the entire quote without identifying the obligated actor or obligation predicate.

The following read-only reproduction used the current `backend/target/classes` and Maven dependency classpath via JShell. It did not access Finep/OpenAI or modify repository source files.

| Case | Minimal source quote | Structured claim | Current result and why |
|---|---|---|---|
| Wrong counterpart category | `Municipalities up to 50,000 people: counterpart rate 0.2% to 4%. State and federal districts: counterpart rate 0.1%. No exemptions apply.` | Municipal range `0.1–4%` for municipalities up to 50,000 people; clause and value agree with each other. | `VALIDATED`. The selector finds municipal anchors in the first sentence; `numberAppears` finds `0.1` in the separate state/federal sentence and `4` in the municipal sentence. The later comparison flattens categories and compares the provider's value to its own clause, so it does not detect the category swap. |
| Wrong budget qualifier association | `O orçamento global é exatamente R$ 500.000.000,00. Projetos podem receber até R$ 800.000.000,00.` | Global budget `BRL 500000000.00`, qualifier `UP_TO`. | `VALIDATED`. `budgetSupported` finds the amount in the first sentence and `até` in the second; it does not bind the qualifier to the matched amount. |
| ICT obligation on the wrong subject | `A ICT deverá apresentar certidão de regularidade. Proponentes podem ser universidades, institutos ou empresas.` | `requiresIct=true` for the opportunity's eligible proposer/executor. | `VALIDATED`. The quote contains `ICT` and `deverá`; the current check does not determine that the duty applies to submitting a certificate rather than requiring the proposer/executor to be an ICT. |

Reproduction output:

```text
wrong counterpart band => VALIDATED
budget amount with unrelated UP_TO qualifier => VALIDATED
ICT document duty misread as applicant requirement => VALIDATED
```

This is a demonstrated false-positive path. Literal quote membership, citation IDs, page locators, and lexical overlap are valuable integrity/provenance checks, but none alone proves the source asserts the relationship between category/subject, predicate, value, conditions, and exceptions.

## Alternatives and trade-offs

| Alternative | Benefits | Limitations / compatibility |
|---|---|---|
| 1. Harden the current validator conservatively | Smallest code surface; no database migration or technical state; v1 parser and audit bytes can remain unchanged. A whitelist parser can recognize explicitly supported constructions and return `EXTRACTED` on ambiguity. | Natural-language legal text has varied syntax. A sentence boundary or co-occurrence heuristic is still unsafe when one sentence contains several bands, values, actors, or duties. The parser must bind complete expressions and fail closed; coverage of supported forms will be intentionally narrow. |
| 2. Add evidence per structured assertion in v2 | Makes provenance explicit for each range, exemption, eligibility criterion, budget qualifier, and ICT requirement. Typed subject/predicate/category/value/condition plus exact quote subspan and offsets makes audit and review clearer. | The model's own assertion-to-citation assignment is not proof. It must still be checked by deterministic parsing or human review. Alters strict provider schema/output and may affect persisted normalized JSON, API DTOs, and audit-package verifiers. Keep v1 reader and old v1 packages unchanged. Whether existing v2 artifacts must remain readable needs inventory before any version decision. |
| 3. Require human factual review where the link is not machine-verifiable | Matches the project's `LLM interprets; rule validates; source proves` principle and avoids pretending uncertain semantics were proven. Reuses separate factual-review outcomes; does not change technical `ValidationStatus`. | Adds review effort and does not itself repair technical false-positive `VALIDATED` states. The technical validator must fail closed to `EXTRACTED` whenever its exact proof rule does not pass; factual approval remains separate and cannot silently promote the technical status. |

## Recommendation

Recommend a bounded combination of alternatives 1 and 3, with alternative 2 considered as an additive v2 evidence-shape improvement if approved:

1. First define, in OpenSpec design/spec, a small set of explicitly supported deterministic relation grammars. For example, a counterpart range is verifiable only when the category, condition and both endpoints belong to one parsed rule expression; a budget qualifier must bind to the same amount expression; and the ICT predicate must state that the relevant proponent/executor is required to be an ICT. A matching word elsewhere in the quote is insufficient.
2. If no parser can prove the complete relation unambiguously, persist the extracted candidate/evidence but keep the technical field `EXTRACTED`. Use the existing factual-review workflow for human adjudication; do not introduce a new technical state and do not promote a field to `VALIDATED` from the human decision.
3. Consider adding exact claim-level source subspans/locators and typed assertion attributes to the unreleased v2 contract. Such metadata improves traceability but is not a proof by itself. A parser or a human must verify the relation.
4. Preserve all `finep-call-v1` schema definitions, parsing and historical package verification unchanged. Keep audit-package outer format v1 and database schema unchanged unless a demonstrated compatibility test proves that impossible; any such finding requires a separate approval.

This is the smallest safe operational policy because it avoids a broad NLP/ontology subsystem, new technical states, migrations, or infrastructure, while making unsupported cases fail closed. However, approval is needed before deciding whether to amend the v2 contract or limit the change to validator behavior. The design/spec must be updated before implementation, and task 2.3 must remain open until the new acceptance cases pass independent review.

## Proposed measurable acceptance criteria

1. For each `VALIDATED` counterpart range, one individually locatable source span must prove the applicable category, condition and both endpoints together. A range assembled from two categories/sentences, or a JSON/clause pair unsupported by the span, remains `EXTRACTED`.
2. For each `VALIDATED` budget, one source expression must bind the exact amount and its qualifier (`UP_TO`, `EXACT`, `AT_LEAST`, or range endpoints). An amount in one sentence and a qualifier referring to another amount remains `EXTRACTED`.
3. `requiresIct=true` validates only when a source expression identifies the relevant proposer/executor as the subject, a mandatory predicate, and ICT as the required type. A duty for an ICT to submit a document does not qualify. `false` likewise requires an explicit source-backed negation of that same predicate.
4. Citation substring validation, IDs and locator correctness remain necessary integrity gates, but tests explicitly demonstrate they are not sufficient by themselves.
5. Positive fixtures exercise each supported exact relation grammar; negative fixtures include all three reproduced cases, swapped categories, cross-sentence number/qualifier combinations, wrong actor/obligation, and ambiguous multi-band text. Ambiguous constructions stay `EXTRACTED`.
6. Existing v1 provider/audit/factual-review fixtures and checksums remain valid. If v2 is amended, test both current v2 artifacts (if any exist) and new v2 output; preserve API DTO and package verification compatibility or escalate before implementation.
7. No migration or new technical status is introduced without separate human approval. Human factual decisions remain separate and do not update technical states.
8. No `VALIDATED` status is awarded solely from model-provided completeness flags, phrase co-occurrence, or a claim-to-citation ID supplied by the model.

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
| `git status --short --untracked-files=all` | PASS, inspected before and after | All pre-existing modifications/untracked files were preserved. This report was added after inspection; no existing file was edited in this run. |
| `mvn -q -f backend/pom.xml dependency:build-classpath -Dmdep.outputFile=/tmp/fomentiq-fq-r1-classpath` | PASS, exit 0 | Output path is outside the repository. |
| JShell against `backend/target/classes` and the dependency classpath | PASS reproduction | All three cases returned `VALIDATED`; no network calls. Counterexample input is shown above. |
| `openspec validate finep-extraction-factual-quality --strict` | PASS, exit 0 | Existing OpenSpec artifact set remains valid; no OpenSpec artifact was changed in this run. |
| `git diff --check` | PASS, exit 0 after report creation | No whitespace errors in tracked changes; the report is new/untracked and inspected directly. |

## Independent review

- Evidence supplied: current validator/service/provider code, current OpenSpec spec/design, the three minimized inputs and observed JShell outputs, alternative analysis and acceptance criteria.
- Reviewer had a separate context: yes, `/root/fq_r1_architecture_diagnostic_review`.
- Reviewer was instructed not to modify implementation, tests, specs, or the report: yes.
- Worktree was checked before and after review; reviewer made no changes: yes.
- Spec adherence, implementation quality, regressions, and security reviewed: yes, for this diagnostic/proposal only.
- Result: `PASS` for the diagnosis and proposed direction. Reviewer confirmed all three false-positive paths and recommended strict deterministic parsing plus independent human factual review. The reviewer explicitly cautioned that richer v2 assertion metadata alone does not prove semantics.

## Findings and correction attempts

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
| FQ-R1 | High; task 2.3 and “Validate eligibility and counterpart as complete evidenced rules”. | Three current-code reproductions above return `VALIDATED` on category/qualifier/actor mismatch. | All three correction attempts were consumed in the preceding implementation run. This diagnostic makes no correction and does not reset, split, or reclassify the finding. | Still open. Implementation remains `ESCALATE` until an explicit human architectural decision and a newly authorized plan. |

## Test integrity

- Tests added/changed: none.
- Demonstrated test defect, if any: not applicable.
- How approved acceptance criteria and equivalent/stronger coverage were preserved: no implementation/test criteria changed; this record proposes additional acceptance cases only.
- Any test weakening/removal: no.

## Risks, decisions, and final verification

- Residual risks/debts: technical `VALIDATED` can be a false positive for the three relation types demonstrated. Do not rely on these states for factual homologation until FQ-R1 is resolved. The proposed rules can cause false negatives by leaving ambiguous claims `EXTRACTED`; this is preferable to false-positive validation and must be surfaced to factual review.
- Decisions made (link ADR/OpenSpec artifact) or `none`: no architectural decision was made. Proposed decision requiring approval: keep the v1 reader immutable, use fail-closed supported relation parsing with human fallback, and decide whether the unreleased v2 contract needs typed claims and exact subspan locators.
- OpenSpec tasks marked complete and evidence: none in this run; task 2.3 remains unchecked.
- Final verification after fixes: no fixes were made. OpenSpec strict validation and repository diff check passed after writing this report.
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`): `ESCALATE` for implementation because FQ-R1 exhausted its three corrections. `PASS` applies only to the independent diagnosis/proposal.
- Human approval still required before commit/push: yes.
- Human approval still required before scope/architectural change: yes.

No Finep/OpenAI calls were made. No commit or push was performed.
