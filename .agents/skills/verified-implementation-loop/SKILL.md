---
name: verified-implementation-loop
description: Orchestrate approved OpenSpec implementation with bounded retries, independent review, and a persistent evidence record.
---

# Verified Implementation Loop

Use this skill for authorized implementation tasks. It orchestrates existing skills and does not replace OpenSpec or authorize new scope.

## Preconditions

1. Read `AGENTS.md` and the applicable project context documents.
2. For product or behavior changes, identify the approved OpenSpec change and exact tasks. Use `openspec status` and the official `$openspec-apply-change` instructions when applying a change. Do not edit the official `$openspec-apply-change` skill.
3. For explicitly authorized process/documentation maintenance with no product behavior impact and no applicable OpenSpec task, derive acceptance criteria from the user's authorization and record `OpenSpec: N/A` with the reason. If OpenSpec applies but the change/task is missing, invalid, ambiguous, or does not authorize the work, return `ESCALATE` before implementation.
4. Use `$spec-driven-change` when applicable.
5. Create a run report at `docs/engineering/verification/<change>-<cycle>.md` from `docs/engineering/verification/TEMPLATE.md`. If no named cycle exists, use a concise task identifier. Never overwrite an earlier run report; choose a new run suffix.

## Loop

### SPEC

- Read the approved proposal, relevant specs, design, and tasks. For an authorized process/documentation-only change with OpenSpec marked N/A, use the explicit authorization as the source requirements.
- Build an acceptance matrix that maps each criterion to its OpenSpec requirement/task or authorized request and planned evidence. Include relevant security, data integrity, regression, and architecture constraints.
- Do not reinterpret or broaden requirements. A mismatch or missing decision is `ESCALATE`.

### PLAN

- Select the smallest vertical change and list exact checks before editing.
- Record the plan, affected areas, and known risks in the run report.
- Stop for human approval before scope changes or material architectural decisions.

### IMPLEMENT → TEST

- Implement only approved tasks; preserve fixtures and existing acceptance criteria.
- Run the narrowest useful checks, then every required verification for affected areas using `$code-change-verification`.
- Record exact commands, exit statuses, and concise outcomes. Do not claim checks that were not run.
- A failing test is evidence to investigate, not permission to weaken it. Change a test only when its defect is demonstrated against the approved spec; retain equivalent or stronger acceptance coverage and record the rationale. If the test change alters acceptance intent, `ESCALATE`.

### INDEPENDENT REVIEW

- Use a genuinely separate context, preferably the available collaboration reviewer mechanism. Give that reviewer only the approved spec/task context, acceptance matrix, complete diff, test results, and the review questions. The reviewer must evaluate spec adherence, implementation quality, regressions, security, and residual risks.
- The reviewer may read files and run non-mutating checks, but must not edit implementation, tests, specs, or the report. Verify that the worktree did not change during review. Any reviewer-authored change invalidates the review and is `ESCALATE` until handled by the implementer and independently reviewed again.
- If a separate context/isolation mechanism is unavailable, or the reviewer cannot inspect the required evidence, return `ESCALATE`; implementer self-review cannot be `PASS`.
- Request findings with stable IDs (`F-01`, `F-02`, ...), severity, spec/acceptance mapping, evidence, and a concrete disposition. Keep an ID for the same underlying failure across reviews, even if its wording changes.

### FIX → VERIFY

For each finding, use the following terminal outcomes:

- **PASS** — all acceptance criteria map to passing evidence; required checks pass; the independent reviewer finds no unresolved blocking issue; residual risks are recorded; no unapproved scope or architecture change remains.
- **RETRY** — a concrete finding is reproducible and fixable within approved scope, and fewer than three correction attempts have been used for that finding. Fix only the affected issue, rerun impacted checks, and request a new independent review. Count each correction attempt against the stable finding ID; do not reset, split, or rename an unresolved issue to evade the limit.
- **ESCALATE** — the same finding remains after three correction attempts; independent review is unavailable; a requirement/spec conflict, scope change, critical risk, or unapproved material architectural decision appears; or evidence is insufficient. Stop dependent work and request human direction.

The maximum is three correction attempts per identified finding across the run, not three attempts per review cycle. A newly discovered, genuinely distinct finding gets its own ID; rewording, splitting, or relabeling the same underlying defect does not create a new allowance.

After fixes, rerun all checks affected by the fixes and the complete required verification set. Recheck the diff and OpenSpec status/validation when OpenSpec applies. When the run is explicitly process/documentation-only with OpenSpec marked N/A, verify the authorization-to-acceptance mapping and report why OpenSpec is not applicable. Do not mark OpenSpec tasks complete unless their specified behavior is fully implemented and independently verified.

## Human approval gates

- `PASS` is not permission to commit or push. Stop and request explicit human approval before either action.
- Stop and request explicit approval before changing scope or making/incorporating a material architectural decision.
- Do not start a later cycle merely because the current cycle passes.

## Run record

Maintain the report throughout the loop. Include:

- change/cycle, date, implementer and independent reviewer context;
- OpenSpec task IDs and acceptance matrix;
- plan and affected files;
- each command, result, and relevant environment limitation;
- stable finding IDs, severity, attempts, fixes, and review outcomes;
- decisions, residual risks, and final `PASS`, `RETRY`, or `ESCALATE` state;
- explicit confirmation that commit/push and future-cycle gates remain pending unless separately approved.

Never put secrets, tokens, credentials, or sensitive provider payloads in the report. Keep previous reports immutable; create a new report per run.
