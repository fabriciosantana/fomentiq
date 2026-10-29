# Proposal

## Why

Visual homologation found that the curation workflow is functional but requires clearer Brazilian Portuguese, a more focused field review flow, and safe typed editors for structured values. These changes should improve review accuracy without changing persistence, API contracts, extraction, or evidence.

## What Changes

- Centralize Portuguese labels and localized display formatting for field names and states.
- Present responsible agencies from the existing opportunity-agency relation and keep them read-only in curation; do not add a free-text agency override.
- Replace the all-fields-expanded layout with a concise field summary and one selected editor, showing current value, technical state, source, approved revision, criticality, and review progress.
- Provide typed controls for the already-supported field shapes, including complete nested eligibility, counterpart, funding, region, document, criteria, TRL, list, date, money, and boolean values.
- Bind document-version selection to the selected document and retain the current provenance and audit behavior.

## Capabilities

### New Capabilities
- `curation-interface`: Localized, typed and evidence-preserving administrative review experience.

### Modified Capabilities
- None. Existing catalog and curation contracts remain unchanged; these requirements define the focused curation interface behavior.

## Impact

- Frontend curation and opportunity detail presentation, shared field-presentation helpers, and component/E2E tests.
- OpenSpec proposal/design/spec/tasks and a verification report.
- No backend, database, API/OpenAPI, extraction schema, fixture, or audit package changes are planned.
