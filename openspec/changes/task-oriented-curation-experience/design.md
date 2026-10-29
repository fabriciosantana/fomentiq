# Design

## Context

See proposal.md. The application already has `/admin` and `/admin/opportunities/:callId`, session/CSRF login, a paginated review queue, DTO-only opportunity detail, document/version reads, pending revision creation, approval/rejection, and revision history. The approved prototype establishes the desired navigation and visual hierarchy but uses fictional in-memory data.

The queue API accepts only `page` and `size`; queue search is not a server-side capability. The implementation will search entries loaded through the existing pagination and label the scope accurately. The queue currently omits status/deadline, both stored directly on the opportunity. A minimal additive queue DTO/read projection extension for these two display values is justified to avoid hiding required user context or issuing one detail request per queue row; it does not alter write semantics, security, persistence, or public contracts.

## Goals / Non-Goals

**Goals:**
- Integrate the approved queue → overview → field review flow with real Finep and CNPq fixture-backed API data.
- Reuse all authentication, CSRF, revision, publication, concurrency, and history operations.
- Keep evidence and human-vs-extraction provenance clear while minimizing technical detail.

**Non-Goals:**
- Database, security, fixture, extraction, or audit package changes.
- A second approval mechanism or automatic publication on save.
- A redesign of the public catalog beyond verifying its existing published projection.

## Decisions

1. **Retain the existing routes and API client.** Route paths remain `/admin` and `/admin/opportunities/:callId`; navigation state may use query/hash or local component state so links remain refreshable. This avoids a second application shell or contract.
2. **Use progressive disclosure.** Queue and opportunity overview show concise labels and summaries; field evidence, technical identifiers, and revision history remain secondary or expandable.
3. **Represent actions through existing revision semantics.** Confirm and correct both create `VALUE` revisions; confirmed absence creates `CONFIRMED_ABSENCE` only after documentary review. Every save remains pending; approve/reject call the existing endpoints with row version and reason.
4. **Search only loaded queue pages.** Add bounded pagination controls and client filtering over fetched pages. Do not label visible-page counts as global totals. Server-side search is deferred unless user experience evidence proves the existing endpoint insufficient.
5. **Extend only queue display metadata and attention semantics.** Add opportunity status and deadline to the admin queue DTO/read query because the queue must present them and the current API has no values. Exclude fields already published by an approved human revision from its attention count; leave extraction state unchanged. Do not change revision/action APIs.
6. **Group known supported fields centrally.** Use the existing field label/value helpers; group fields into General, Participation, Financial conditions, and Documents/requirements. Unknown supported entries use a localized fallback section rather than exposing property names.
7. **Use only API-provided agency relationships and evidence.** Do not edit agency relations or synthesize citations. Public source links must pass the same URL safety policy used by the public experience.

## Risks / Trade-offs

- [Loaded-page-only search may miss items not yet fetched] → expose pagination and state the search scope; no false global count.
- [A document reference is not itself proof of a claim] → distinguish field evidence from document selection and retain explicit “sem citação literal” messaging.
- [Queue items may have multiple pending technical fields] → overview groups actual returned fields and identifies attention states from API values, not invented counters.
- [The approved prototype uses simpler demonstration actions] → wire each action only to the existing revision API and preserve its pending/approval semantics.

## Migration Plan

No data or API migration. Replace the administrative presentation after the new flow and tests pass; existing routes, endpoints, auth/session controls, and storage remain unchanged. Rollback is a frontend revert.

## Open Questions

None. Search is intentionally constrained to loaded queue pages by this design.
