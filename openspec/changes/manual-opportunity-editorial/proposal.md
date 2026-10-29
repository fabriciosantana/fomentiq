# Proposal

## Why

The visually approved manual authoring prototype offers a simpler primary workflow for administrators and a clearer editorial catalog for the public. The production application needs persisted drafts, explicit publication, and safe reader-facing pages while preserving existing ingested opportunities, extraction records, evidence, and curation history.

## What Changes

- Add an authenticated, demo-isolated workflow to create, edit, order, preview, publish, and unpublish manually authored opportunities.
- Add a public catalog and detail pages that expose only published manual opportunities and render their ordered sections and typed fields as readable editorial content.
- Preserve the existing ingestion and curation model and keep its current catalog/detail and administration accessible through legacy routes.
- Record authorship and editorial actions, validate field types and links, and reject stale edits rather than silently overwriting concurrent changes.

## Capabilities

### New Capabilities
- `manual-opportunity-editorial`: Manual opportunity authoring, editorial publication, and published-only public presentation.

### Modified Capabilities
None. Existing ingested opportunity behavior remains available through legacy routes.

## Impact

- Additive PostgreSQL/Flyway tables and explicit manual-opportunity DTOs, ports, adapters, and API routes.
- Reuse the existing Spring Security demonstration session and CSRF configuration; manual administration remains disabled outside the isolated demonstration profile.
- Make the editorial catalog the public root experience and preserve the extracted catalog and curator behind non-competing legacy routes.
- Integrate the approved prototype’s navigation, content hierarchy, responsive styling, and typed editors into the React application.
- Extend backend, frontend, migration, security, and isolated Playwright coverage. No external services or new infrastructure are required.
