# Proposal

## Why

The v0.1.0 vertical slice proves ingestion, persistence, extraction and presentation offline, but it does not establish that the current public Finep portal and the configured OpenAI Responses API work together for a deliberately selected opportunity. A bounded, auditable live validation is needed before relying on that integration operationally.

## What Changes

- Define a controlled online run for one specifically approved Finep call and a bounded document set.
- Validate real Finep discovery/download and one real OpenAI structured extraction, only after a separate human authorization that names the opportunity and spending limit.
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

Planning covers the existing `FinepSourceConnector`, Finep URL/download policy, `FinepIngestionService`, isolated PostgreSQL/S3-compatible test configuration, `OpenAiStructuredExtractionProvider`, and verification records. It does not authorize production deployment, a new Finep ingestion, an OpenAI call, a new provider, golden-fixture changes, schema/API changes, or application implementation. The target opportunity, document limit, model settings, request count and spending ceiling must be approved before any live execution.
