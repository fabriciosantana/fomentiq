# Verification record: `finep-audit-package` implementation — 2026-10-26

## Run identity and scope

- Change: `finep-audit-package`; tasks 1.1–1.3, 2.1–2.3, 3.1–3.4.
- Implementer: primary Codex context.
- Independent reviewer: separate read-only context `/root/final_preparation_review`.
- Scope excluded: `finep-factual-review`, Finep/OpenAI online access, fixture changes, database schema/migrations, public API changes, and new infrastructure.
- Final status: **PASS**.
- Commit/push: not performed; approval remains pending.

## Acceptance evidence

| Criterion | OpenSpec mapping | Evidence |
|---|---|---|
| AC-01 Private archive, restricted access, five-year retention, no automatic deletion | Tasks 1.1, 2.3 | Configurable filesystem archive validates a dedicated marked directory outside the workspace, configured POSIX group, owner/group modes, symlink/path safety; unsafe existing roots are rejected without permission changes. Retention is recorded as five years, with no cleanup timer. `LocalPrivateAuditArchiveTest` passed. |
| AC-02 Exact provider input persisted before invocation, no DB changes | Task 1.2 | The extraction service writes the canonical selected excerpt/schema/parameter artifact before calling the provider; a write failure prevents invocation. The full digest identifies the private artifact and is bound to extraction execution. Unit and integration tests passed. |
| AC-03 Versioned package contents and run/version reference graph | Tasks 1.3, 2.1 | Internal export accepts a terminal run and includes sanitized run/authorization metadata, snapshots, calls and versions, original documents, exact provider inputs, schema, structured result, extraction history, fields/evidence and a six-field human-review template. Fixture-backed PostgreSQL integration export passed. |
| AC-04 Deterministic inventory, source hashes, evidence references and fail-closed completeness | Task 2.2 | Archive includes per-file byte length and SHA-256 plus detached `SHA256SUMS`. Offline verification checks snapshot and PDF source hashes, excerpts/digests, evidence quote/page locators, opportunity/version/run references and closed inventory. Tampering with snapshot bytes while refreshing both inventories still fails on the source hash. |
| AC-05 Verification without PostgreSQL, S3, Finep or OpenAI | Task 3.1 | `AuditPackageVerifier` is a plain offline component with local manifest schema and package bytes as inputs. CLI verification branches before Spring startup. A fixture-backed package was exported and reopened/verified using the verifier; no external provider or service was called. |
| AC-06 Provenance and secret sanitization | Task 2.3 | Package and per-artifact origins distinguish `LIVE`, `FIXTURE`, `RECONSTRUCTED` and generated review output. Allowlisted serialization excludes storage keys, provider raw payloads and auth headers; URL sanitization and fail-closed secret checks are exercised. A scan of changed text files found no common credential/token patterns. |
| AC-07 Cleanup blocked until export, archive verification and authorized-project binding pass | Task 3.3 | Cleanup script exports, verifies the reopened private package, checks run/authorization/Compose-project identity, and verifies exact project services/volumes before teardown. `test-finep-live-cleanup-gate.sh` passed export-failure, verify-failure and project-mismatch cases. No timer deletes packages. |
| AC-08 Six critical fields remain reviewable without asserting human approval | Tasks 2.1, 3.2 | The package contains a pending factual-review template with extracted value/state and available evidence references for `deadline`, `totalBudget`, `eligibility`, `counterpart`, `trl` and `requiresIct`; it does not alter field validation state or mark factual approval. |
| AC-09 Preserve first-run evidence limitations and project constraints | Task 3.4 and user restrictions | Demo documentation says the first live run’s deleted exact excerpts/raw response cannot be reconstructed. A simulated reconstructed/historical run remains incomplete. No calls to Finep/OpenAI, database migrations, golden-fixture changes, public endpoints or infrastructure were introduced. |

## Implementation plan and boundaries

1. Add private archive storage and lifecycle policy without changing PostgreSQL schema.
2. Capture canonical provider-visible excerpts, effective descriptor/schema and full digest before provider invocation.
3. Export a terminal run and its source/extraction provenance to a deterministic private ZIP.
4. Validate manifest, inventory, source hashes and cross-file references using a bounded offline verifier.
5. Gate cleanup on package verification and exact isolated Compose project identity.

The only persistent archive destination is the configured private filesystem directory. The application does not depend on RustFS or provider internals for package verification. No `finep-factual-review` behavior was implemented.

## Commands and results

| Command | Result |
|---|---|
| `mvn -B -f backend/pom.xml clean verify` | PASS — 89 tests, 0 failures/errors/skips; Checkstyle passed; Flyway applied all 3 migrations to clean PostgreSQL 17 Testcontainers databases. |
| `mvn -B -f backend/pom.xml -Dtest=FinepIngestionIntegrationTest test` | PASS — 15 tests, including package export, missing artifacts, tamper/inventory, traversal path, missing run attribution, snapshot hash mismatch, incomplete reconstructed history, and existing ingestion/idempotency regressions. |
| `mvn -B -f backend/pom.xml -Dtest=JsonSchemaValidatorTest test` | PASS — 7 tests, including depth limits through `anyOf`, unknown/malformed keywords, unsupported regex, schema alternatives and versioned manifest schema. |
| `npm run lint` | PASS. |
| `npm run typecheck` | PASS. |
| `npm test -- --run` | PASS — 24 tests. |
| `npm run build` | PASS. |
| `./scripts/run-offline-mvp-e2e.sh` | PASS — offline fixture flow, 1 Playwright test; isolated Compose project, named volumes, ports and services were removed by the script. No Finep/OpenAI access. |
| `docker compose config -q` | PASS. |
| `bash -n scripts/cleanup-finep-live-validation.sh scripts/test-finep-live-cleanup-gate.sh` | PASS. |
| `scripts/test-finep-live-cleanup-gate.sh` | PASS — failed export/verification and mismatched authorized project block cleanup. |
| `openspec validate finep-audit-package --strict` | PASS. |
| `openspec validate --all --strict` | PASS — 5/5 items. |
| `git diff --check` | PASS. |
| Changed-file credential/token pattern scan | PASS — no common patterns detected; values were not printed. |
| Golden fixture diff check | PASS — unchanged. |

Earlier intermediate verification runs exposed and corrected: a test-provider compile error, a test wrapper that did not delegate its schema descriptor, the `anyOf` schema-validation gaps (F-06), and incomplete/missing snapshot and historical-run coverage. The final commands above were run after those corrections.

## Independent review and findings

The reviewer inspected the approved change, complete implementation diff, tests and OpenSpec artifacts in a separate read-only context. No reviewer edits occurred. Final independent outcome: **PASS**.

| Finding | Severity | Disposition / attempts |
|---|---|---|
| F-01 Existing archive directory could have permissions changed without proof it was dedicated | Medium | Resolved in attempt 1: only a newly initialized or correctly marked root with expected group/modes is accepted; unsafe existing roots are not modified. |
| F-02 Cleanup was not bound to the authorized Compose project | Medium | Resolved in attempt 1: run, sanitized approved manifest, authorization link and project identity must match before resource enumeration/removal. |
| F-03 ZIP files absent from manifest inventory could pass after checksum updates | Medium | Resolved in attempt 1: verifier rejects unlisted archive files and duplicate/unsafe paths. |
| F-04 Demo guide omitted the historical live-run evidence gap | Low | Resolved in attempt 1: documentation explicitly says deleted exact excerpts/raw output are unavailable and cannot be reconstructed as live evidence. |
| F-05 Manifest/extraction schemas were not checked against captured descriptor/result | Medium | Resolved in attempt 1: versioned manifest schema and captured extraction schema are checked; structured result must satisfy the exact captured schema. |
| F-06 `anyOf` depth reset, unrestricted regex and permissive unsupported schema keywords | Medium | Resolved in attempt 2: full structure is validated before values, with depth/node caps, strict keyword/value-shape checks and only two fixed linear hexadecimal patterns. Regression tests cover invalid alternatives even when another branch would match. |
| F-07 Snapshot source hashes were not rechecked offline | Medium | Resolved in attempt 1: verifier checks each snapshot record’s SHA-256 against archived bytes; regression recalculates package inventories after tampering and still expects failure. |
| F-08 Missing explicit regression coverage for traversal, execution attribution and incomplete historical packages | Medium | Resolved in attempt 1: PostgreSQL integration tests cover all three cases. Historical/reconstructed packages remain incomplete, and missing code revision is represented as `null`, never invented. |

The reviewer confirmed F-01–F-08 resolved, the cleanup gate and security boundaries, and required verification status. Review outcome is PASS after the final Maven verify completed successfully.

## Task completion

All 10 tasks in `openspec/changes/finep-audit-package/tasks.md` are checked `[x]`, with evidence mapped above. No task in `finep-live-integration-validation` was modified or marked complete by this implementation.

## Decisions and residual risks

- Approved policy is represented in the OpenSpec design and runtime configuration: private directory outside the repository/disposable resources; configured owner/group access; five-year retention; no automatic deletion.
- Host administrators must still provision/secure the directory and its group and provide host-level backup and encryption. The application enforces POSIX owner/group/mode and path safety but cannot attest to host identity governance, disk encryption or backup retention.
- The manifest hash inventory detects accidental or partial changes, but no digital signature/key lifecycle was introduced; coordinated rewriting by a party with write access to the private archive cannot be proven against an independent signing authority.
- A missing historical code revision is represented as `null` only in an incomplete package; the verifier refuses completeness. No source or extraction artifacts are reconstructed as live evidence.
- Package completeness is technical integrity/provenance only, not factual adjudication or human approval.
- Offline schema validation intentionally supports a bounded JSON Schema subset; new schema keywords/patterns require explicit support before packages using them can verify.

## Final gate

- No Finep/OpenAI calls were made.
- Golden fixture unchanged.
- No database schema/migration changes.
- No commit or push performed; human approval remains required.
- No work on `finep-factual-review` or Change B began.
