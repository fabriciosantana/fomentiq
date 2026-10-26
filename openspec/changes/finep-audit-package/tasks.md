# Tasks

## 1. Capture and package contract

- [ ] 1.1 Configure the owner-approved private archive directory, authorized-reader policy and five-year retention with no automatic deletion; verify unsafe/missing paths and access policy are rejected.
- [ ] 1.2 Persist the canonical selected-excerpt manifest before provider invocation in the private archive directory, derive its path from the full canonical input digest already stored by `extraction_execution`, and verify exact excerpt metadata/text is recoverable without database schema changes.
- [ ] 1.3 Define and version the package manifest/layout and completeness graph; add schema tests for run identity, source classification, required entries, references, checksums and incomplete status.

## 2. Export and protect the package

- [ ] 2.1 Implement an internal exporter for one terminal `IngestionRun` that gathers the sanitized approval/run data, snapshots, document versions, exact provider excerpts, schema/parameters, structured result, persisted fields/evidence/history and review-matrix template; verify each emitted record resolves to the source run and version.
- [ ] 2.2 Build a deterministic archive inventory with per-file SHA-256 and byte lengths; verify original source-document hashes and exact quote/locator references before declaring complete.
- [ ] 2.3 Apply allowlist sanitization and publish atomically only to the approved private directory outside repository/Compose data; verify secret-pattern rejection, restrictive reader permissions and five-year retention metadata without exposing credentials.

## 3. Offline verification and cleanup gate

- [ ] 3.1 Implement an offline package verifier that needs only package files and local schemas; verify no database, S3, Finep, OpenAI, credential or network initialization occurs.
- [ ] 3.2 Add tests for missing documents/excerpts/locators, mismatched checksums/references, corrupt archive paths, absent run attribution and incomplete historical runs; each must fail verification with sanitized gap codes.
- [ ] 3.3 Verify the live-run procedure exports, copies and reopens the package from the configured private directory before removing only dedicated Compose resources; demonstrate cleanup is blocked when archive completeness or destination verification fails, and that no archive cleanup timer exists.
- [ ] 3.4 Document package access, retention, export/verification commands and the explicit rule that the first run's deleted excerpts/raw response remain unavailable and cannot be reconstructed as live artifacts.
