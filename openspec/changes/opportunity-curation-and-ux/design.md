# Design

## Context

The backend is a modular Spring Boot monolith. Public reads are DTO projections from JdbcOpportunityReadAdapter; extracted fields and evidence are append-preserving records. There is no existing identity provider, authentication, curation table, or administrative route. Finep and CNPq fixture ingestion already persists into one isolated manual-evaluation Compose project.

See proposal.md for motivation and the two delta specs for observable requirements.

## Goals / Non-Goals

**Goals:**
- Add one environment-configured demonstration administrator using Spring Security sessions.
- Preserve extraction/evidence as immutable technical history and record curation in an additive relational model.
- Publish only approved revisions through existing public DTOs, with explicit attribution.
- Demonstrate the complete workflow offline for both existing publishers and improve the field detail presentation.

**Non-Goals:**
- Production identity management, OIDC, multiple roles/users, password reset, multi-tenancy, public self-service editing, cross-source deduplication, extraction changes, fixture changes, or a new audit-package format.

## Decisions

1. **Fail-closed demonstration security.** Add Spring Security as the sole authentication framework. A general filter chain keeps public opportunity reads and health public while denying /api/admin/**. A higher-priority chain and administrative controllers are present only under the explicit manual-demo profile and enable marker. That profile requires a username and password supplied through environment variables; startup hashes the supplied password with BCrypt and stores only the hash in the in-memory UserDetails. Missing configuration does not enable admin access. Use Spring Security form login/session fixation protection, expose a CSRF token for the SPA, and require ROLE_ADMIN at the server method boundary. Login/logout and every mutation remain CSRF protected. Session cookie settings use HttpOnly and SameSite=Lax; Secure is true for the HTTPS forwarded Codespace demonstration. OIDC is required before any production administrative exposure.

   Alternative: use OIDC immediately. Rejected for this isolated increment because there is no configured identity provider and it would add external setup beyond the single-admin vertical slice. HTTP Basic was rejected because browser-managed credentials complicate logout and CSRF safety for mutation endpoints.

2. **Separate append-only curation records.** Add a Flyway migration with an opportunity-field revision table and document-reference columns/foreign keys. Each revision stores an allowlisted field name, a schema-validated JSON value or explicit confirmed-absence kind, the effective baseline/revision ID observed when drafting, justification, document/version/page/section locator, an optional literal quote, creator/time, status, and decision identity/time/reason. A literal quote is required for a proposed value; confirmed absence may omit a quote only after an explicit full-document-review assertion and must still reference an owned document version. No update is made to ExtractedField, Evidence, source snapshots, or extraction-run records. Revision records are updated only for their single PENDING-to-terminal decision transition; terminal records are immutable. Document ownership is checked in service code and constrained relationally where possible.

   Alternative: add override columns to extracted_field. Rejected because it would mutate machine-produced facts and conflate technical validation with human approval.

3. **Optimistic publication with row locking.** Draft creation records the current approved revision baseline for that call/field. Approval locks the opportunity row inside one transaction, re-reads the latest approved revision, compares it to the recorded baseline, and changes the pending revision plus effective publication in that same transaction. A mismatch returns conflict without approving. Public reads derive the effective value from the latest approved revision ordered by decision time and ID; pending/rejected revisions are never considered.

   Alternative: maintain a separately mutable current-value table. Rejected because a derived projection avoids duplicated mutable state; a unique-current table can be considered only if measured query needs justify it.

4. **Typed values and documentary links.** Reuse the existing extraction field allowlist and a new explicit per-field value validator. Accept scalar formats and structured object shapes only for supported schema fields; reject arbitrary JSON. A revision must reference a document version of the same opportunity and record available page/section context supplied by the reviewer. A proposed value also requires a literal quote; confirmed absence does not fabricate a quote and instead requires an explicit completed-document-review assertion plus a document/version locator. Technical linkage establishes source traceability, not semantic proof; the UI identifies the value as human-reviewed and does not label the source quote as independently verified. Confirmed absence is a distinct revision value kind with a reason and documentary review locator; null by itself is not interpreted as absence.

5. **API compatibility.** Keep existing read endpoints and legacy summary fields. Extend field DTOs with original extracted value, effective published value/source and approved revision identifier; existing value remains the effective value for compatibility. Summary deadline/program/instrument values use approved revisions when available. Add bounded, authenticated DTO-only admin endpoints for review queue, revision history, create, approve, reject, session status, login/logout/CSRF support. No JPA entity is serialized.

6. **Readable detail view and evidence diagnosis.** Replace primary raw JSON blocks with typed, localized recursive rendering for known objects/lists; technical IDs/locators remain in expandable audit sections. Keep original extraction and human publication visually distinct. An absent technical field is labeled as not identified by extraction unless an approved human absence decision exists. The frozen provider response associates a `regions` record with section 5.2 of the budget clause and supplies no literal quote. The deterministic adapter must preserve that absence and the extraction persistence layer must omit unverified citations that lack a literal quote; the detail API/UI must report that no field evidence is available rather than inventing a quotation or showing the unrelated clause. Do not rewrite historical evidence or infer semantic support. The raw source-provider artifact and this residual provenance mismatch remain auditable, but no unsupported association is published as evidence.
7. **Bounded revision history.** The admin revision-history API returns pages of at most 50 rows with deterministic chronological ordering and a total count; the UI exposes previous/next navigation so no history is silently omitted. This replaces the initial arbitrary latest-100 cap, which could hide older decisions.

7. **Demonstration isolation.** Enable the demo admin chain only for the manual-demo profile with FOMENTIQ_ADMIN_DEMO_ENABLED and non-empty environment credentials. Startup accepts exactly two resource tuples: the persistent manual project (`fomentiq-manual-evaluation`, DB `fomentiq`/15432, S3-compatible `fomentiq-manual-evaluation`/19000) or the disposable curation E2E project (`fomentiq-curation-e2e-<pid>`, DB `fomentiq_e2e` and run-selected loopback port, matching project-named bucket and S3 port). In either case startup inspects Compose project labels, loopback bindings, and project-specific volumes before Flyway/JPA. Any other profile combination or resource identity is denied. E2E uses independent ports, generated credentials, HTTPS browser access, refuses existing project resources, and removes only its own generated project. No external connector/provider runs in the E2E flow.

## Risks / Trade-offs

- [Single in-memory administrator is not a production identity system] → activate only with manual-demo, fail closed elsewhere, document OIDC gate.
- [A human-provided document locator can be linked correctly but can still misstate source meaning] → require justification and quote/locator; label human review and retain evidence for independent inspection.
- [Curated JSON field type drift] → explicit allowlist/type validation and field-specific API tests; no arbitrary JSON.
- [Future revision concurrency] → transactionally compare the recorded published baseline under call-row lock; stale approval returns conflict.
- [CSRF/session behavior through Codespace HTTPS forwarding] → secure SameSite session cookie and real Playwright login/mutation test through the frontend proxy.
- [Existing extraction may attach semantically mismatched evidence] → never synthesize a quote from the excerpt; omit the unverifiable field citation from the read model and explicitly show that evidence is unavailable, while preserving original source artifacts and documenting the residual.
- [Human review history can grow beyond one response] → use bounded pages and explicit navigation rather than an arbitrary silent row cap.

## Migration Plan

1. Apply an additive Flyway migration that creates only curation tables/constraints and indexes; do not rewrite historical field/evidence rows.
2. Deploy application with no manual-demo profile: all existing public reads continue; administrative routes remain denied/unregistered.
3. Start the dedicated demo profile only with the dedicated manual-evaluation database/storage and explicitly configured administrator environment values.
4. Roll back application code by disabling/stopping the demo profile. The additive revision table may remain; do not drop reviewer history during rollback.
