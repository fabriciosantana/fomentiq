# Design

## Context

The current React admin screen renders every field as an expanded `<details>` and uses JSON textareas for structured objects. It already receives effective and original field values, human publication metadata, evidence, document identity, responsible-agency relations, and field schema names through existing DTOs. Backend validation and persistence are field-typed; this change is presentation-only.

## Goals / Non-Goals

**Goals:**
- Localize reviewer-facing labels and values in one shared presentation module.
- Make review sequential and focused, with progress derived from human-published fields.
- Edit supported scalar and nested structures with controls that preserve all existing branches and nulls.
- Keep agency relations read-only and accurately display multiple agencies.

**Non-Goals:**
- No backend, database, API/OpenAPI, extraction-schema, audit-package, or source-evidence changes.
- No changes to authentication, CSRF, revision transitions, or publication semantics.
- No agency relationship editing, new fields, or new validation rules.

## Decisions

1. **Shared presentation mapping:** add a frontend module for field labels, technical-state labels, revision-state labels, status/qualifier labels, and pt-BR formatting. Use it in both public detail and administration. Persisted names and API values remain unchanged.
2. **Single selected field editor:** replace one-editor-per-field rendering with a summary list and a selected editor panel. Derive progress from fields with an approved human publication, while keeping the count distinct from AI validation status.
3. **Typed structured controls:** implement controls for the shapes already enforced by `CuratedFieldValueValidator`: editable text/date/date-time, money, booleans, TRL, text and URL lists, evaluation criteria, eligibility, counterpart ranges/exemptions, regions, project funding tiers, and required documents. Start from the complete current value and update only the edited path; do not serialize or hide untouched branches.
4. **Agency relation remains authoritative for display:** show `responsibleAgencies` including primary role as read-only data. The extracted `agency` property uses the friendly label “Agência responsável” in public field presentation but is not offered as an admin text editor because it cannot update the relationship model. Adding agency editing would require a separately approved domain/API decision.
5. **Document/version coupling:** preserve the existing query by call and selected document; clear version selection whenever the document changes and display a version label tied to that document. Continue to submit document and version IDs validated by the backend.

Alternatives considered: keeping JSON textareas is error-prone for composite rules; adding a generic schema-driven form framework would be excessive for the small set of existing field shapes; enabling agency text editing would create contradictory representations without updating the agency relations.

## Risks / Trade-offs

- [A nested editor could drop an unrendered property] → tests compare complete input and output structures, including nulls, all counterpart ranges, exclusions, and agency multiplicity; controls are limited to current schema keys.
- [Localized presentation could alter the persisted value] → formatting functions are display-only; submissions use typed raw state and existing backend validation.
- [Progress may be mistaken for factual correctness] → label the metric “campos homologados” and do not infer it from `VALIDATED`; human publication remains separately identified.

## Migration Plan

No data or API migration. Rollback consists of reverting the frontend presentation change; persisted revisions and contracts are unaffected.

## Open Questions

None. Editing responsible-agency relationships is explicitly excluded and must be separately specified if needed.
