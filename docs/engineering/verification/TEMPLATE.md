# Verification record: <change> / <cycle>

> Copy this template to `<change>-<cycle>.md` for an actual run. Do not use this template itself as evidence of a completed run. Keep completed run records immutable; start a new record for a new run.

## Run identity

- Change and cycle:
- OpenSpec change/tasks in scope, or `N/A` with reason for authorized process/documentation-only work:
- Date/time (UTC):
- Implementer context:
- Independent reviewer context/mechanism:
- Final state: `RETRY` / `ESCALATE` / `PASS`

## Approved specification and acceptance criteria

| Criterion ID | OpenSpec requirement/task or authorization item | Acceptance criterion | Verification evidence |
|---|---|---|---|
| AC-01 |  |  |  |

## Plan and affected areas

- Planned vertical slice:
- Affected files/modules:
- Known risks before implementation:
- Scope/architecture approval needed: none / describe and stop

## Commands and results

| Command | Result/exit status | Evidence or limitation |
|---|---|---|
|  |  |  |

## Independent review

- Evidence supplied: approved spec/tasks, acceptance matrix, complete diff, test results.
- Reviewer had a separate context: yes / no (if no, state `ESCALATE`).
- Reviewer was instructed not to modify implementation, tests, specs, or this report: yes / no.
- Worktree was checked before and after review; reviewer made no changes: yes / no.
- Spec adherence, implementation quality, regressions, and security reviewed: yes / no.

## Findings and correction attempts

Use stable IDs for underlying problems. Count no more than three correction attempts per finding across the run; rewording or splitting a finding does not reset its count.

| Finding ID | Severity and acceptance mapping | Evidence | Attempt (1–3) and correction | Re-review outcome |
|---|---|---|---|---|
|  |  |  |  |  |

## Test integrity

- Tests added/changed:
- Demonstrated test defect, if any:
- How approved acceptance criteria and equivalent/stronger coverage were preserved:
- Any test weakening/removal: no / describe; if it changes acceptance intent, state `ESCALATE`.

## Risks, decisions, and final verification

- Residual risks/debts:
- Decisions made (link ADR/OpenSpec artifact) or `none`:
- OpenSpec tasks marked complete and evidence:
- Final verification after fixes:
- Final state rationale (`PASS`, `RETRY`, or `ESCALATE`):
- Human approval still required before commit/push: yes
- Human approval still required before scope/architectural change: yes

Do not include secrets, tokens, credentials, or sensitive provider payloads in this record.
