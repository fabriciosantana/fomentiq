---
name: code-change-verification
description: Use after code, tests, schema, build configuration, API behavior, or UI behavior changes and before reporting completion.
---

# Code Change Verification

Run the narrowest useful checks first, then the complete required stack for affected areas.

When used by `$verified-implementation-loop`, this skill supplies verification evidence to an independent reviewer. It does not replace independent review, authorize implementation, or make a self-review sufficient for `PASS`.

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

## Independent review evidence

- Provide the reviewer the approved OpenSpec requirements/tasks when applicable, or the explicit authorization and reason OpenSpec is N/A for process/documentation-only work, plus the acceptance matrix, complete diff, and exact test/build/check results.
- The reviewer must use a separate context and must not modify implementation, tests, specifications, or verification records. The reviewer may inspect and run non-mutating checks.
- Return findings with stable IDs, severity, affected acceptance criteria, reproducible evidence, and a concrete disposition. Preserve finding identity across retries.
- Classify outcomes as `PASS`, `RETRY`, or `ESCALATE` per `$verified-implementation-loop`. No isolated review context means `ESCALATE`.
- A test change is acceptable only to correct a demonstrated test defect while preserving the approved acceptance criteria and equivalent or stronger coverage. A test change that merely makes the implementation pass is a blocking finding.

Do not claim success if a required check was skipped. Report exact commands, results, reviewer context, findings, residual risks, and any limitation in `docs/engineering/verification/<change>-<cycle>.md`.
