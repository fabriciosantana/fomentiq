# Proposal

## Why

The v0.1.0 vertical slice proves ingestion, persistence, extraction and presentation offline, but it does not establish that the current public Finep portal and the configured OpenAI Responses API work together for a deliberately selected opportunity. A bounded, auditable live validation is needed before relying on that integration operationally.

## What Changes

- Implement the minimum internal controls needed to make a later controlled run possible: typed approved manifest, fail-closed local isolation guard, bounded acquisition and a private CLI runner for one Finep call.
- Define a controlled online run for one specifically approved Finep call and a bounded document set.
- Validate real Finep discovery/download and one real OpenAI structured extraction, only after separate human authorization naming the opportunity and approved estimated spending budget. The experimental run records provider usage and estimated cost; an absolute server-enforced spend ceiling is not a prerequisite.
- Isolate the validation database and S3-compatible bucket/prefix from development and operational datasets; never use the golden fixture as an online write target or modify it.
- Evaluate critical values against preserved source passages, and assess evidence, identity, idempotency and version preservation.
- Record sanitized run outcome, duration, model/schema, reported token usage, estimated cost, failures and residual risks.
- Classify the result as PASS, FAIL or ESCALATE with explicit thresholds; no automatic retry, scope change, or production write follows from the result.

## Capabilities

### New Capabilities
- `finep-live-validation`: controlled, isolated and reviewable validation of the existing Finep connector and configured structured extraction provider against one approved live opportunity.

### Modified Capabilities

None.

## Impact

This approved implementation slice may add the isolated preflight guard, typed manifest, bounded internal runner and offline tests. It does not authorize production deployment, a new Finep ingestion, an OpenAI call in this preparation cycle, a new provider, golden-fixture changes, schema/API changes, or a PostgreSQL schema migration. The target opportunity, document/byte/time limits, model settings, one total provider attempt, and estimated spending budget must be approved before live execution. The estimate is not a hard cap; the runner records returned usage/cost and escalates on missing usage or budget overrun. This change does not authorize live access without separate approval of the exact manifest and execution.
