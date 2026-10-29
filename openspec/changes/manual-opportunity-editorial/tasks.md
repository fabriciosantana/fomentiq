## 1. Persisted manual editorial domain
- [x] 1.1 Add additive Flyway schema for manual opportunities, sections, typed fields, publication snapshot, optimistic version, and append-only audit events.
- [x] 1.2 Add manual editorial domain/application ports and explicit DTO validation for the six approved field types and bounded content.
- [x] 1.3 Add PostgreSQL adapters and transactional aggregate save/publication operations; preserve existing ingested and curation rows.
- [x] 1.4 Add Testcontainers coverage for clean migration, upgrade preservation, ordering, typed values, stale-write conflict, and atomic publication changes.

## 2. Secured editorial API
- [x] 2.1 Add demo-isolated, session-authenticated and CSRF-protected administrative routes for list, create, read, save, preview, publish, and unpublish.
- [x] 2.2 Add published-only public catalog and detail endpoints with bounded pagination, search, explicit DTOs, and 404 for draft/withdrawn IDs.
- [x] 2.3 Add/update OpenAPI and API/security integration tests, including proof that legacy extraction/curation routes remain compatible.

## 3. Production frontend integration
- [x] 3.1 Preserve legacy catalog/detail and curation under non-competing routes and make manual editorial catalog the root and manual editor the `/admin` experience.
- [x] 3.2 Implement the opportunity administration list and simple search with create/edit actions and publication state.
- [x] 3.3 Implement the prototype-aligned editor for main details, ordered sections/fields, six typed controls, accessible reordering/removal, and unsaved-change protection.
- [x] 3.4 Implement faithful preview and explicit save-draft, publish/update-publication, and unpublish flows with clear status and error feedback.
- [x] 3.5 Implement the public published-only catalog and editorial detail using safe typed rendering, pt-BR formatting, responsive layout, and no internal metadata.
- [x] 3.6 Add frontend tests for editor flows, values/types/order, preview, publication visibility, draft privacy messaging, errors, responsiveness, and legacy navigation.

## 4. End-to-end verification and delivery readiness
- [x] 4.1 Add or update isolated Playwright setup so this journey uses exclusive temporary resources and does not affect manual demo or persisted data.
- [x] 4.2 Verify the complete offline journey: authenticated creation, two sections and different field types, reorder, draft exclusion, preview, publish, public list/detail, edit without implicit publish, unpublish, and public 404.
- [x] 4.3 Run backend/frontend/Playwright/Compose/OpenSpec verification and independent read-only review; record evidence and remaining risks.
