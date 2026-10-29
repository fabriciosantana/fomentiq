# Proposal

## Why

The approved prototype organizes administrative work around opportunity review tasks, while the deployed administration currently presents a dense technical field editor. Reviewers need a clear queue, a concise opportunity overview, and a focused evidence-backed decision flow using the existing curation API.

## What Changes

- Replace the current administrative presentation with three connected experiences: review queue, opportunity overview, and one-field review.
- Reuse the existing session authentication, CSRF protection, pending revision, approval/rejection, history, and publication mechanisms.
- Group supported fields into user-facing sections and present values/evidence in pt-BR without exposing raw JSON or internal identifiers in primary views.
- Support confirmation, correction, and documented absence as pending revisions; publication remains an explicit existing approval action.
- Add frontend coverage and offline E2E coverage for Finep and CNPq fixture opportunities.

## Capabilities

### New Capabilities
- `task-oriented-curation`: Task-oriented administration of opportunity reviews using the existing authenticated curation workflow.

### Modified Capabilities
- `opportunity-curation` — extend the read-only administrative queue projection with opportunity status and deadline, and define its pending-attention count to exclude fields already published by an approved human revision. Existing authentication, write operations, revision transitions, and public API contracts remain unchanged.

## Impact

- Frontend routes and components under `frontend/src`, component tests, and the offline Playwright journey.
- Read-only administrative queue DTO/query and its OpenAPI documentation/tests; no database migration.
- OpenSpec capability and verification report.
- No changes to authentication, write API semantics, public API contracts, fixtures, or infrastructure.
