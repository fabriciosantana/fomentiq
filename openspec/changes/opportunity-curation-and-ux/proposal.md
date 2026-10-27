# Proposal

## Why

The unified catalog exposes machine-extracted values but has no safe way for an authorized reviewer to correct incomplete or incorrect information. Structured values and evidence also need a clearer presentation so users can distinguish the published value from its original extraction and source.

## What Changes

- Add a demonstration-only authenticated administration flow for drafting, approving, rejecting, and publishing field revisions.
- Preserve extracted fields and evidence unchanged; keep every human revision, decision, author, justification, and documentary reference auditable.
- Make public opportunity reads present the latest approved human value while retaining the original extraction and review history.
- Add an administrative queue and opportunity review interface, and improve public detail presentation of structured fields.
- Investigate and safely address the reported field/evidence association mismatch.

## Capabilities

### New Capabilities
- `administrative-curation`: authenticated, environment-gated human revisions and auditable publication of opportunity fields.

### Modified Capabilities
- `opportunity-ingestion`: public catalog/detail reads present approved human revisions without changing source extraction records; detail presentation remains provenance-aware.

## Impact

Spring Security session authentication and method authorization; an additive Flyway review-history schema; administrative read/write APIs and explicit DTOs; the public opportunity read model; React Router, catalog/detail UI, and offline Playwright demonstration. No source connectors, extraction provider, fixture, or historical audit-package contract changes are included.
