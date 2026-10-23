# AGENTS.md

## Mission
Build **Fomentiq**, a trustworthy innovation-funding intelligence and management platform. Prefer correctness, traceability, and small verified increments over speed or architectural novelty.

## Source of truth
Before changing behavior, read:
1. `PROJECT_CONTEXT.md`
2. `PRODUCT_REQUIREMENTS.md`
3. `MVP_SCOPE.md`
4. `DOMAIN_MODEL.md`
5. `ARCHITECTURE.md`
6. relevant OpenSpec specs/change

## Mandatory workflow
- Behavioral change: use OpenSpec first.
- Do not implement an unapproved capability outside `MVP_SCOPE.md`.
- Keep changes small and vertically testable.
- Before coding, use `$spec-driven-change` when applicable.
- Before declaring implementation complete, use `$code-change-verification`.

## Engineering rules
- Backend: Java + Spring Boot.
- Frontend: React + TypeScript.
- Database: PostgreSQL; migrations with Flyway.
- Preserve raw source evidence and document versions.
- Critical extracted facts MUST have provenance.
- Never silently reconcile conflicting source data.
- Domain code MUST NOT depend directly on a specific LLM vendor.
- Prefer deterministic validation for dates, money, enums, IDs and eligibility rules.
- No secrets in repository.
- Avoid microservices, event buses, Elasticsearch and extra infrastructure unless a documented requirement justifies them.

## Testing
Every behavior change requires tests at the cheapest adequate level. Critical ingestion flow requires integration tests. Use Testcontainers for database integration. Keep a deterministic Finep fixture for regression tests.

## Completion
A task is not done until:
- code builds;
- tests pass;
- relevant OpenSpec tasks are checked;
- behavior matches spec;
- docs/ADRs are updated when a decision changed;
- no unresolved critical TODO is hidden.
