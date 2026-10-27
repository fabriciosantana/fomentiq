# Tasks

## 1. Demo-only authentication and authorization

- [x] 1.1 Add the Spring Security dependency and fail-closed security configuration; verify anonymous catalog GETs stay public, /api/admin/** is denied outside manual-demo, and missing demo credentials do not enable admin access.
- [x] 1.2 Add the manual-demo session login/logout and CSRF bootstrap using framework form login; verify BCrypt-backed credentials, session fixation protection, CSRF rejection, HttpOnly/SameSite/Secure cookie settings, and authenticated principal resolution with security integration tests.
- [x] 1.3 Document environment-only demo credential setup, isolated Compose requirements, HTTPS cookie behavior, and the OIDC-before-production gate; verify documentation contains no actual credentials.

## 2. Append-only human revision persistence

- [x] 2.1 Add an additive Flyway migration for typed opportunity field revisions and documentary references; verify migration from clean database and upgrade with existing Finep/CNPq data using Testcontainers, with no changes to historical extraction/evidence rows.
- [x] 2.2 Implement allowlisted field/value validation, same-opportunity document-version reference checks, and pending revision creation with authenticated author, previous effective value/revision, justification, and timestamp; verify valid and invalid field shapes and cross-opportunity references.
- [x] 2.3 Implement one-time PENDING-to-APPROVED/REJECTED transitions and atomic stale-baseline protection; verify original ExtractedField/Evidence remain byte-for-byte unchanged, rejected values are not published, and concurrent approvals cannot silently overwrite.

## 3. Administrative API and public publication projection

- [x] 3.1 Add bounded DTO-only admin queue, opportunity review, field revision history, create, approve, reject, and session-status APIs restricted to ROLE_ADMIN; verify anonymous, non-demo, wrong-role, invalid-CSRF, and actor-spoofing requests are denied.
- [x] 3.2 Extend existing public DTO/read queries to expose an approved human value as the effective value, its publication source/revision, and the unchanged original extraction; verify pending/rejected revisions do not change public results and legacy catalog reads remain compatible.
- [x] 3.3 Update OpenAPI for only implemented admin/session and extended read DTO behavior; verify endpoint, parameter, status, CSRF/auth, and DTO definitions match integration tests.

## 4. Administrative review interface

- [x] 4.1 Add accessible login and admin review queue for Finep and CNPq opportunities with fields needing review; verify loading, empty, error, anonymous, and session-expired states in component tests.
- [x] 4.2 Add field revision editor with supported field-specific inputs, justification, document-version/page/section/quote reference, and approve/reject actions; verify successful and invalid submission, CSRF-backed session calls, stale conflict handling, and paginated chronological history display in component tests.
- [x] 4.3 Confirm public catalog/detail visibly distinguish human-published values from original extraction and show no pending/rejected proposal as effective; verify component tests.

## 5. Structured presentation and evidence integrity

- [x] 5.1 Replace primary raw JSON rendering with localized accessible renderers for supported nested values and expandable technical metadata; verify objects/lists, dates, currency, statuses, and MISSING extraction labels in component tests.
- [x] 5.2 Reproduce the regions/budget evidence mismatch against fixture-backed persisted data; verify the root cause in extraction persistence/API/presentation, and implement only an in-scope safe containment with regression test or document the proven residual extraction risk without changing historical facts.
- [x] 5.3 Organize opportunity detail into summary, participation conditions, and documents/evidence while retaining source, history, and responsive/accessibility behavior; verify Finep and CNPq component states.

## 6. Integrated demonstration and final verification

- [x] 6.1 Extend the isolated offline demo setup with explicit manual-demo profile and environment-only administrator credentials; verify the application refuses admin activation without the isolated profile/configuration and E2E data stays confined to dedicated Compose resources.
- [x] 6.2 Run Playwright end to end: login, choose Finep and CNPq opportunities, create a revision, approve one and reject another, verify only approved value appears publicly, then inspect original extraction/evidence and revision history; verify repeatability without Finep/CNPq/OpenAI network access.
- [x] 6.3 Run backend clean verify, frontend lint/typecheck/tests/build, Flyway clean/upgrade tests, Compose validation, OpenSpec strict, audit-package/factual-review compatibility tests, and independent review; record exact results and residual risks.
