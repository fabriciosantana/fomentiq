# Proposal

## Why

The current product experience is organized around automated extraction and technical review, while the approved product direction makes manual opportunity authoring and clear public presentation the main path. Before replacing any application screens or changing persistence, the team needs a separate, navigable prototype to evaluate whether a non-technical administrator can create and publish a useful opportunity page.

## What Changes

- Create an isolated browser prototype with a demonstration list, opportunity editor, custom ordered sections and fields, public preview, and simulated publication state.
- Render only published demonstration opportunities in an editorial public catalog and detail page.
- Use fictional demonstration data and browser-local in-memory state; do not connect to the existing application, APIs, backend, database, or storage.
- Keep all prototype files separate from the production frontend and preserve the current application unchanged.

## Capabilities

### New Capabilities
- `manual-opportunity-editorial`: Isolated prototype behavior for manual opportunity authoring and an editorial public catalog.

### Modified Capabilities

None.

## Impact

- A standalone directory under `prototypes/` containing static browser assets and lightweight local interaction checks.
- This OpenSpec change and a verification report.
- No backend, API, database, migration, production frontend, authentication, infrastructure, or external-service changes.
