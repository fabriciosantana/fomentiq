# Proposal

## Why

During manual curation, reviewers must leave the form to inspect source documents, and the opportunity's preserved copy cannot currently be opened from the review flow. The same evaluation found that the queue can show an extracted deadline while detail and public views show an approved human value.

## What Changes

- Add an authenticated, opportunity-scoped read path for preserved document versions, with bounded reads, integrity checks, and safe inline/download behavior.
- Add an in-context document/version viewer to the one-field review flow, preferring the version linked to evidence and preserving an official-source fallback.
- Align the administrative queue deadline with the latest approved human revision, as the detail and public projections already do.
- Add regression coverage for storage access, authorization, version ownership, page navigation, responsive viewer behavior, and cross-screen deadline consistency.

## Capabilities

### New Capabilities
- `curation-document-review`: Secure document consultation and consistent published values during human opportunity review.

### Modified Capabilities
- None. This change defines the complete curation document-review behavior in a new capability.

## Impact

- Backend document read application/infrastructure adapter, bounded S3-compatible artifact reads, and an authenticated admin read endpoint.
- Existing admin queue projection and its integration tests; no database migration.
- Frontend review page, document viewer, API client, component and offline Playwright tests.
- No changes to revision transitions, original extraction records, evidence records, public write access, or storage keys in API responses.
