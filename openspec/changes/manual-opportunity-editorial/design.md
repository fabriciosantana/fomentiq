# Design

## Context and goals

The approved standalone prototype at `prototypes/manual-opportunity-editorial/` establishes the workflow, visual hierarchy, and six field types. The production application already has a modular Spring Boot backend, PostgreSQL/Flyway, a React/TypeScript frontend, session authentication and CSRF for isolated demo administration, and legacy ingestion/curation routes. This change connects the approved authoring experience to durable storage without changing historic source-ingestion records.

Goals:
- Make manual authoring the primary product workflow and public root catalog.
- Keep source-ingested opportunity and curation records intact and reachable through legacy routes.
- Ensure save, preview, and publication are separate actions.
- Prevent public reads of drafts and stale concurrent writes.
- Reuse the existing modular boundaries and demo security rather than create parallel infrastructure.

Non-goals: AI extraction, additional sources, changing historical extraction or review semantics, OIDC, production deployment, rich-text/HTML authoring, generic form builders, and new infrastructure.

## Persistence boundary

Use a separate manual editorial aggregate, not `CallForProposals`, because that entity represents source identity and its ingestion/document/version/evidence history. Add Flyway tables for the manual opportunity and append-only editorial audit events. Store the aggregate’s ordered sections and typed fields as one bounded JSONB value with strings for scalar values and arrays of strings for lists. This keeps save, reorder, and publication snapshot operations atomic without adding a generic form schema or child-table synchronization. Validate the JSON shape, field types, and ordering at the application boundary. Keep the migration additive. Do not persist or return JPA entities directly.

Use `row_version` optimistic locking for aggregate saves and publication transitions. A stale expected version returns HTTP 409 before child rows or audit records are changed. Aggregate content changes and their audit event are committed in one database transaction.

## Draft and published projection

Keep editable aggregate content separate from a `published_content` JSONB snapshot on the manual opportunity. `DRAFT` has no public projection. Saving an existing published opportunity updates its editable aggregate and row version but leaves the current published snapshot untouched. Explicit publish validates the complete aggregate and atomically copies the current editorial representation into the public snapshot, changes state to `PUBLISHED`, and appends an audit event. Unpublish changes state to `DRAFT` and removes public visibility atomically; retain prior audit events. Public reads use only the published snapshot and state predicate. A missing, draft, or withdrawn identifier is indistinguishable to a public caller and returns 404.

This snapshot boundary is necessary to satisfy the approved rule that saving does not publish automatically. It also keeps each public response internally consistent while an administrator edits a later version.

## API and security

Add explicit DTO/port/adapter/controller boundaries for manual editorial data. Public read routes expose only published DTOs. Administrative create, list, detail, save, preview, publish, and unpublish routes use `/api/admin/manual-opportunities/**`, the existing `manual-demo` profile and `fomentiq.admin.demo.enabled=true` gate, the existing ADMIN session, and CSRF. Do not modify existing curation controller routes. Validate title/institution/summary lengths, section/field counts and lengths, enum/type value shapes, date and non-negative decimal currency, and HTTP(S) URLs without user-info. Render content using React text nodes; links get safe schemes and `noopener noreferrer`.

Recommended route split: `/` and `/catalog/:id` for manual editorial public content; `/admin` for manual authoring; retain the previous extracted catalog/detail on `/radar` and `/opportunities/:callId`, and the existing curation experience on `/admin/curation` and `/admin/curation/opportunities/:callId`. Existing public API contracts stay unchanged; add versioned-independent `/api/editorial/opportunities/**` and `/api/admin/manual-opportunities/**` endpoints with OpenAPI DTO schemas.

## Audit and limits

Audit events record actor, action (`CREATED`, `SAVED`, `PUBLISHED`, `UNPUBLISHED`), aggregate version, and UTC timestamp. Do not include secrets or unrelated historical extraction data. Establish explicit limits for field counts and content lengths and validate both server-side and in the UI. Use pagination and bounded page sizes for admin and public lists.

## Verification strategy

- PostgreSQL/Testcontainers: clean migration, upgrade while Finep/CNPq rows exist, aggregate ordering and typed values, optimistic conflict, atomic save/publish/unpublish and immutable legacy rows.
- Security/API tests: anonymous and CSRF rejection, demo-profile/property gate, published-only public reads including direct draft ID, and explicit DTO sanitization.
- Frontend component tests: editor interactions, field type controls, ordering, dirty-state protection, preview, publish and unpublish, escaped content, accessible/responsive rendering, and separate legacy routes.
- Isolated Playwright journey: create, save draft, prove public 404/list exclusion, preview, publish, browse/detail, edit without implicit publication, explicitly republish, then unpublish and prove public exclusion.
- No Finep/CNPq/OpenAI access in automated tests.
