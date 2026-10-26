# Tasks

## 1. Package-bound review contract

- [x] 1.1 Confirm the implemented `finep-audit-package` version and verifier contract; add compatibility checks that reject unsupported package versions and incomplete verification results.
- [x] 1.2 Define versioned JSON Schema and examples for six critical field rows, package ID/root checksum, provenance, conditions/exceptions, reviewer and UTC decision timestamp; verify schema fixtures.

## 2. Decision completeness rules

- [x] 2.1 Specify and implement outcome-specific validation for `APROVADO`, `REPROVADO`, `INCONCLUSIVO` and `AUSÊNCIA CONFIRMADA`; verify invalid combinations fail with stable gap codes.
- [x] 2.2 Require full branch and exception coverage for conditional fields, including counterpart and eligibility; verify a single supported band cannot be approved as a complete rule.
- [x] 2.3 Preserve competing claims and superseding review history without changing technical field states; verify the source package checksum remains unchanged.

## 3. Offline validation and reviewer guidance

- [x] 3.1 Add offline tests for supported values, missing evidence versus confirmed absence, unresolved retification conflict, incomplete package, missing locator and each required field; verify no database, storage or provider is initialized.
- [x] 3.2 Document the human review procedure and a six-field matrix template, including source-scope declaration and the distinction between factual outcomes and technical states; verify examples satisfy the schema.
- [x] 3.3 Record the historical first-run limitations from preserved artifacts only; verify no fixture-derived or reconstructed passage is labeled as captured live evidence.
