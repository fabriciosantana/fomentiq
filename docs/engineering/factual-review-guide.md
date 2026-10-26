# Finep factual review procedure

This process records human factual decisions separately from extraction validation. It is offline and read-only with respect to the audit package, PostgreSQL, object storage, Finep, and OpenAI.

## Prepare a review

1. Obtain the audit ZIP and its private access authorization. Do not copy it into Git.
2. Start with the package verifier. Only a package reported both `integrityValid=true` and `complete=true` can support a completed factual review.
3. Copy `factual-review-template-v1.json` to a private working file. It is explicitly a blank template, not a decision. Fill exactly one current decision for each required field: `deadline`, `totalBudget`, `eligibility`, `counterpart`, `trl`, and `requiresIct`.
4. Bind the record to the package ID, SHA-256 of the entire ZIP, SHA-256 of `manifest.json`, package format version, and source origin. Keep `extractedFieldId`, `extractedValue`, and technical state as shown by `extraction/field-history.json`; factual review never changes technical states.
5. For every documentary claim record its value, document version ID and hash, page, literal quotation, and non-empty locator. Claims must resolve to the PDF in the package and the literal quotation must be present on that page.
6. For `APROVADO`, cite the full applicable rule. Counterpart approval requires at least two separately evidenced conditions plus an explicit complete branch inventory and review of conditions and exceptions. Eligibility approval requires positive criteria, explicit exclusion review, and citations for each condition/exception. A reviewer records a source-backed “none found” exception if a complete review finds no exception; an empty list is not proof of completeness.
7. Use `REPROVADO` when documentary evidence supports rejecting the extracted assertion. Use `INCONCLUSIVO` when evidence is insufficient or claims conflict; preserve each competing claim and its source instead of choosing by document recency. Use `AUSÊNCIA CONFIRMADA` only with a complete search scope listing every applicable notice, amendment, FAQ, annex, and other non-result document version in the package. Result documents are not a substitute for call requirements.
8. Add reviewer identity, rationale, and UTC review time. A missing technical value may still be reviewed; “model returned no value” by itself cannot establish absence.

## Validate and preserve history

Install/build the repository backend, then run the offline verifier (it starts no Spring context):

```sh
java -jar backend/target/fomentiq-backend-0.1.0-SNAPSHOT.jar \
  --fomentiq.factual-review.verify=/private/path/run-package.zip \
  --fomentiq.factual-review.record=/private/path/reviews/<record-uuid>.json
```

Exit code `0` means the package is complete and the review record is structurally and referentially valid. It does not certify factual correctness or reviewer authorization. Any nonzero result blocks completion; the JSON output contains only a validity flag, package identifiers/checksum, and stable gap codes.

Store each decision record privately as `<recordId>.json` beside its review history. Never overwrite or delete an earlier record. A correction gets a new UUID record with `supersedes.recordId` and `supersedes.sha256`; the verifier requires the prior file and exact checksum to remain available and bound to the same package. Preserve package and review files under the approved private archive access and retention policy. The validator does not modify package bytes or database records.

## Historical first live run

The preserved live-run report records run `2e859ec1-3916-4361-b10c-ac454b65061c` as `SUCCEEDED`, with 18 documents and six field states/values. It records `deadline`, `totalBudget`, and `trl` as technical `MISSING` with no attached evidence; this does not prove that those facts are absent from source documents. It records a `counterpart` summary with one municipal range as technical `VALIDATED`, while explicitly warning that the stored summary is incomplete. The preserved diagnosis and live report cite the second-rectification PDF page 14 for that one persisted quote.

The exact provider excerpts, full live structured response, and full persisted normalized extraction payload were not retained before the disposable environment was removed. Their absence is documented in [the live-run report](verification/finep-live-integration-validation-live-run-2026-10-26.md) and [the offline diagnosis](verification/finep-live-integration-validation-field-diagnosis-2026-10-26.md). Any fixture comparison or selector replay is a separate comparator, not a captured artifact from that live run. Do not fill missing live claims from the golden fixture or retrospectively reconstructed excerpts. The first run therefore cannot receive a complete package-bound factual review under this contract.
