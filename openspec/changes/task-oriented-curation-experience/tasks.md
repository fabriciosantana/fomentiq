# Tasks

## 1. OpenSpec and baseline
- [x] 1.1 Validate the task-oriented curation contract and record implementation acceptance criteria.

## 2. Administrative navigation and queue
- [x] 2.1 Implement the concise paginated opportunity queue with search, real agency/source context, empty/loading/error/session states, and a clear continue-review action.
- [x] 2.2 Implement the opportunity overview with task-oriented field groups, accurate status/progress, and navigation to pending fields.

## 3. Field review workflow
- [x] 3.1 Implement one-field confirmation and correction using pending VALUE revisions and the existing typed editors.
- [x] 3.2 Implement confirmed absence using existing CONFIRMED_ABSENCE semantics and explicit documentary-review acknowledgement.
- [x] 3.3 Present field evidence and document/version selection in context, with safe public source links and progressive history/technical details.
- [x] 3.4 Reuse existing approve/reject transitions, show pending/publication outcomes, and preserve extraction provenance.

## 4. Verification
- [x] 4.1 Add component coverage for queue, overview, grouped fields, review actions, evidence gaps, history, session/error states, and responsive behavior.
- [x] 4.2 Extend offline E2E to complete a review journey using real Finep and CNPq fixture-backed opportunities without external services.
- [x] 4.3 Run backend/frontend/Playwright/Compose/OpenSpec checks and record independent review and residual risks.
