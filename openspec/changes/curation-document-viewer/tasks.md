# Tasks

## 1. Secure preserved content and deadline projection

- [x] 1.1 Add opportunity-scoped bounded document-content reads and the authenticated admin endpoint; verify exact ownership, SHA-256, media signatures, byte limit, safe headers, and anonymous/cross-call/oversize rejection with backend tests.
- [x] 1.2 Project the latest approved human deadline in the review queue with existing absence/invalid-value semantics; verify source fallback, approved correction, and confirmed absence without modifying historical rows.

## 2. Integrated document review panel

- [x] 2.1 Prefer the evidence-linked document/version and allow explicit document/version selection; verify evidence-present, evidence-absent, changed-reference, and evidence-version-beyond-first-page cases without inventing or misbinding citations.
- [x] 2.2 Add preserved PDF preview/page navigation, safe new-tab access, DOCX download fallback, and responsive collapse/expand/full-screen controls; verify PDF response headers, page links, unsupported types, error states, and narrow viewport behavior.

## 3. End-to-end verification

- [x] 3.1 Extend isolated offline Playwright to select a field, open its persisted PDF without leaving review, navigate to a supported page, submit a revision, and verify consistent published deadline across queue/detail/public views for Finep/CNPq.
- [x] 3.2 Run backend clean verify, frontend lint/typecheck/tests/build, offline Playwright, Compose and OpenSpec strict; record risks and obtain read-only independent review PASS.
