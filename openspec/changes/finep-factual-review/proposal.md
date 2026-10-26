# Proposal

## Why

The live extraction showed that technical validation does not establish that a critical fact is complete or factually approved. Reviewers need a traceable decision for each critical field, grounded in preserved source versions and explicit conditions, while keeping that decision distinct from automated extraction states.

## What Changes

- Define a human review record for `deadline`, `totalBudget`, `eligibility`, `counterpart`, `trl` and `requiresIct`.
- Require documentary value, source version, page, literal quotation, locator, applicable conditions/exceptions, outcome, rationale, reviewer and review date.
- Define `APROVADO`, `REPROVADO`, `INCONCLUSIVO` and `AUSÊNCIA CONFIRMADA` as factual review outcomes, separate from technical field states.
- Require complete evidence for conditional rules and prevent a partial band from being approved as a complete rule.
- Support review against a complete, verified audit package and preserve conflicting source claims without automatic precedence.

## Capabilities

### New Capabilities
- `finep-factual-review`: Detached, evidence-based human adjudication of critical extracted fields.

### Modified Capabilities
- None.

## Impact

Adds a versioned factual-review record format and offline validation rules. It depends on the `finep-audit-package` change for the immutable evidence package contract. It does not alter extraction states, the public API, database schema, or live provider behavior.
