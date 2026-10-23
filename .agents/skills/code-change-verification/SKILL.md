---
name: code-change-verification
description: Use after code, tests, schema, build configuration, API behavior, or UI behavior changes and before reporting completion.
---

# Code Change Verification

Run the narrowest useful checks first, then the complete required stack for affected areas.

## Backend
- formatting/static checks configured by repository;
- unit tests;
- integration tests for persistence/ingestion/API changes;
- build/package.

## Frontend
- lint;
- typecheck;
- unit/component tests;
- production build.

## Cross-cutting
- Flyway migrations apply from clean database;
- Docker Compose dependencies start;
- OpenSpec validate/verify relevant change;
- Playwright critical flow when behavior crosses UI/API;
- no secret or credential introduced;
- provenance invariant preserved for critical fields.

Do not claim success if a required check was skipped. Report exact commands, results and any limitation.
