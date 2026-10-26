# Proposal

## Why

The first live validation demonstrated that a successful ingestion does not by itself leave enough portable evidence to reproduce what was reviewed: the disposable database and object store were removed, while the sanitized report lacked the exact provider excerpts and full persisted field record. A bounded, private export made before cleanup is needed so an auditor can verify source provenance, extraction inputs/outputs, and completeness without PostgreSQL, S3, Finep, or OpenAI.

## What Changes

- Add an internal, explicit exporter for a terminal `IngestionRun` that writes a versioned self-contained audit package before its disposable environment is removed.
- Include allowlisted execution authorization/manifest metadata, run and source identities, document metadata/versions/hashes, permitted original snapshots/documents, exact selected provider excerpts, effective schema/provider parameters, structured response, persisted fields/evidence/history, and a blank factual-review matrix.
- Include a deterministic file inventory with SHA-256 hashes and completeness manifest; refuse to label a package complete when required files, excerpts, evidence locators or relational references are absent or fail integrity checks.
- Add an offline verifier that validates schemas, checksums, file inventory, document/page/quote references, run attribution and package completeness without external services or live databases.
- Store packages only in a designated private, access-controlled, encrypted destination separate from disposable Compose resources and GitHub/public Git. Do not copy secrets, OAuth/API tokens, storage credentials, internal storage keys or unredacted sensitive URL parameters.
- Preserve the distinction among live artifacts, frozen fixture artifacts, and later offline reconstructions; never backfill missing first-run evidence as if captured during that run.

## Capabilities

### New Capabilities

- `finep-audit-package`: export, integrity-check and offline-verify a bounded audit package for a Finep `IngestionRun`.

### Modified Capabilities

None.

## Impact

Expected implementation areas are a private internal export/verify command, allowlisted serializers for existing run/document/extraction records, a package manifest/schema, and tests using fixtures and temporary local files. The exporter reads the existing PostgreSQL/S3-compatible data before teardown; it does not add public API, UI, database schema, or infrastructure service. An unavailable or disallowed artifact is recorded as absent and makes the package incomplete, not silently reconstructed. The destination and retention duration require explicit owner approval before implementation; a proposed policy is in `design.md`.

This change does not authorize another Finep/OpenAI run, reconstruct the first run's deleted database contents, publish documents/payloads to GitHub, or change the golden fixture.
