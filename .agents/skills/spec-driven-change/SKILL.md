---
name: spec-driven-change
description: Use before implementing any user-visible behavior or domain change. Ensures the change is specified through OpenSpec and kept within approved scope.
---

# Spec-Driven Change

1. Read root `AGENTS.md` and relevant product/architecture docs.
2. Inspect existing OpenSpec specs and in-flight changes before creating a new capability name.
3. Use the OpenSpec spec-driven workflow: proposal → specs/design → tasks → apply → verify → archive.
4. Keep proposal focused on why/what; put implementation choices in design.
5. Requirements use MUST/SHALL and at least one `#### Scenario` with WHEN/THEN.
6. One requirement should express one behavior.
7. Resolve blocking product/architecture decisions before implementation.
8. Do not expand MVP scope without explicit approval.
9. Implement the smallest vertical slice satisfying the change.
10. Run OpenSpec validation before apply and verify behavior against the spec after implementation.
