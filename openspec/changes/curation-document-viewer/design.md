# Design

## Context

See proposal.md. The app already lists document metadata and versions without exposing storage keys. `ArtifactStore` has an opaque-key read operation, the admin API and session/CSRF security are enabled only in the isolated `manual-demo` profile, and the one-field review already binds a revision to a chosen document/version. It has no endpoint for retrieving preserved bytes.

The public catalog and admin detail use the effective approved deadline. The admin queue currently selects `call_for_proposals.deadline` directly. In the running manual database, CNPq 30/2026 has source deadline `2026-12-11` and an approved human revision `2026-10-21T12:32:00Z`; the queue therefore displays stale extracted data while detail/public views display the approved value. No historical revision or source data needs to change.

## Goals / Non-Goals

**Goals:**
- Serve only verified preserved document bytes for the exact opportunity/document/version through the existing authenticated demo boundary.
- Keep document and version selection attached to the field review, with the evidence-linked version preferred.
- Keep queue deadline projection consistent with existing detail and public projections.

**Non-Goals:**
- Public document downloads, pre-signed S3 URLs, new storage providers, document conversion/OCR, or arbitrary URL fetching.
- Changes to migrations, document records, extraction/evidence records, human revision semantics, or production authentication.

## Decisions

1. **Use a nested admin content route, never expose object storage.** Add `GET /api/admin/opportunities/{callId}/documents/{documentId}/versions/{versionId}/content` under the existing manual-demo session authorization. A parameterized query must establish the full ownership chain before storage access. This avoids a public content API and prevents a valid UUID from becoming a cross-opportunity object lookup.

2. **Use the S3-compatible application port with a bounded read.** Extend `ArtifactStore` with a maximum-byte read and implement streaming enforcement in the S3 adapter. The content reader accepts only stored PDF and DOCX media types, verifies the expected file signature and SHA-256 before returning any bytes, and rejects oversized or mismatched content. The bound is 50 MiB, matching the existing maximum accepted by the Finep document downloader; it does not expand ingestion limits.

3. **Constrain browser handling by format.** PDFs use `application/pdf` and inline disposition with `nosniff`, private/no-store caching, and same-origin framing policy. DOCX is always attachment/download. No HTML or arbitrary active content is served. The viewer uses an application-relative URL made only from validated UUID path segments; the official external URL remains subject to `safeSourceUrl`.

4. **Keep evidence and document selection distinct.** The field's evidence-linked version is preselected only after it is resolved through the existing paginated version reads, scanning later pages when needed. If no version is found or a lookup fails, the UI must not fall back to another document/version while retaining the evidence quote; explicit selection clears the unrelated quote/locator. Selecting another version never creates evidence. If no evidence exists, the UI says so and offers explicit source selection.

5. **Use browser PDF navigation for reliable page locators.** A positive integer page is represented by the PDF fragment `#page=N`; non-numeric or missing locators do not produce a navigation claim. The panel has collapse/expand controls and a responsive full-screen mode on small screens.

6. **Make the queue use the same approved deadline rule as the read API.** Add a lateral latest-approved deadline projection in the queue SQL, including confirmed-absence and invalid-date behavior consistent with `JdbcOpportunityReadAdapter`. Do not update `call_for_proposals`, extraction rows, revisions, or history.

## Risks / Trade-offs

- [A corrupt or oversized stored object could consume memory] → enforce a 50 MiB maximum while streaming and verify digest/signature before producing the HTTP response.
- [Native PDF behavior varies by browser] → provide a separate new-tab action, keep the form available, and test that the preserved endpoint is served with safe headers in Chromium.
- [Evidence DTOs do not directly include document ID] → resolve the exact evidence version against existing paginated per-document version reads; do not treat page zero as complete, and keep the reference unresolved if it is not found rather than guessing.
- [CNPq historical test revision changes the displayed value] → preserve it as an approved demo revision, show the original extraction in history, and align all projections instead of rewriting audit data.

## Migration Plan

No database migration. Deploy the backend route/read path and queue projection with the frontend. Roll back by reverting application changes; document bytes and human revision history remain untouched.

## Open Questions

None.
