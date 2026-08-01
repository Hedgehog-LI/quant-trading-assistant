# Independent Verification: SECURITY-DIRECTORY-D1-20260729

Verifier: `qta-final-verifier`
Role run ID: `FV-20260729-01`
Baseline: `8c7d131da052cc9fc39f6d9b6e3158d4cc33f640`
Contract/diff base: `48b52b9`
Contract SHA-256: `0c16a3510ca7e8c34354c42ce78babcd1ffff3f4ffbf83d91debd74a7db6b500`
Candidate mode: `COMMIT`
Candidate commit: `f3ba47597d54abe9a3fe391e7e8c4834fa0c94ae`
Candidate tree hash: `cd69250db8808986f8685b91b5d11ea673f6b9bf`
Patch SHA-256: `3eb9086274ca6a25b1ba2c2f3e45307ea7c66bdaa18544d452184f56af900f9e`
Review generation / repair round: `3 / 2`
Review result: `CR-20260729-03 / REVIEW_CLEAR`
Independence confirmed: `YES`
Disposable worktree: `/private/tmp/qta-security-directory-verify.JZXtbE`
Tracked candidate unchanged: `YES`

## Findings

| Severity | AC-ID | Finding | Evidence |
|---|---|---|---|
| — | AC-01..AC-08 | No actionable P0-P2 candidate finding. | Full production diff, V17, mapper, service, controller and test inspection; required static and automation gates passed. |
| INFO | AC-01/AC-04 | Disposable MySQL/application runtime was unavailable and was not simulated. | Read-only `docker info` failed because the Docker socket does not exist. |
| INFO | AC-06 | Benchmark is H2/MySQL-mode, repeats one hot key per class, and is not deployment/MySQL evidence. | Raw report records both limitations; all frozen H2 thresholds passed. |

## Acceptance Criteria

| AC-ID | Result | Evidence | Notes |
|---|---|---|---|
| AC-01 | PASS | V17 additive inspection; migration test; legacy CRUD/lifecycle/cascade regression; focused/full suites. | MySQL runtime remains conditional and unverified. |
| AC-02 | PASS | BOM/quoted multiline, idempotency, former-name alias, duplicate/conflict accounting, isolated negative matrix, 200,001-row boundary and forced late rollback tests. | Exact stock/alias rollback assertions passed. |
| AC-03 | PASS | Channel/filter/HK-padding collision tests, query/limit/filter boundaries and stable HTTP 4xx envelopes. | Ordered canonical-symbol assertions passed. |
| AC-04 | PASS | Explicit score/listed/market/NFKC-name/canonical comparator review; repeated ranking matrix and matchedBy assertions. | SQL recalls candidates; Java performs final code-point scoring and canonical tie-break. |
| AC-05 | PASS | Production dependency inspection; non-empty daily-bar, quote, sync-task, sync-plan and portfolio-price snapshots; zero provider interactions. | Search/import depends only on stock/alias mappers and clock. |
| AC-06 | PASS | Enabled fixed benchmark: 50,000 securities, 100,000 aliases, 400 warmups, 1,600 measured searches. | Overall P95 `178.420375ms`; per-class maximum `186.131542ms`; all `<300ms`; zero misses. |
| AC-07 | PASS | Scope/history/secret/artifact/static scans; focused tests; full test; package; benchmark; unchanged before/after identity. | All required commands exited 0. |
| AC-08 | PASS | EMPTY/READY/no-match/null-time, exact 48h/+1ns, fresh/stale, detail found/normalized/not-found HTTP tests. | Frozen catalog/detail distinctions passed. |

## Verification Dimensions

| Dimension | Required | Result | Evidence |
|---|---|---|---|
| STATIC | YES | PASS | Identity, diff, migration, allowlist, forbidden-path, secret and provider-side-effect scans exit 0. |
| AUTOMATION | YES | PASS | 65 focused tests; 377 full/package tests with 0 failures/errors; benchmark 1/1. |
| RUNTIME | CONDITIONAL | NOT_VERIFIED | Docker socket absent; no container/volume mutation; H2 not promoted. |
| DEPLOYMENT | NO | NOT_VERIFIED | No deployment command was authorized or executed. |

## Command Ledger

| Gate | Command/result |
|---|---|
| Contract/candidate/patch | Contract SHA, HEAD, tree, ancestry and patch all matched the TaskPacket. |
| Diff/scope/history/artifacts | `git diff --check` and allowlist/V1-V16/runtime-artifact scans passed. |
| Secrets/side effects | Added-line credential and production provider/network/task/price dependency scans passed. |
| Focused | `./mvnw -Dtest=SecurityDirectoryMigrationTest,SecurityDirectoryIntegrationTest,SecurityDirectoryControllerTest,StockDataServiceTest test` exit 0; 65 tests. |
| Full | `./mvnw test` exit 0; 377 tests, 0 failures/errors, 1 opt-in benchmark skipped. |
| Package | `./mvnw package` exit 0; same 377-test result and executable JAR. |
| Benchmark | `./mvnw -Dqta.security-directory.benchmark=true -Dtest=SecurityDirectorySearchBenchmarkTest test` exit 0; 318.1s. |
| Raw evidence | 2,001 CSV lines; eight class counts, zero misses and nearest-rank percentiles independently reproduced. |
| Docker probe | `docker info --format '{{json .ServerVersion}}'` exit 1; socket absent. |

Two exploratory regex commands were discarded from evidence: one over-broad rule matched the fixed Java
namespace, and one had invalid shell quoting. The corrected scans above passed.

## Before/After Candidate Identity

| Point | HEAD | Tree | Contract SHA-256 | Patch SHA-256 | Tracked status |
|---|---|---|---|---|---|
| Before gates | `f3ba47597d54abe9a3fe391e7e8c4834fa0c94ae` | `cd69250db8808986f8685b91b5d11ea673f6b9bf` | `0c16a351…b6b500` | `3eb90862…00f9e` | empty |
| After gates | `f3ba47597d54abe9a3fe391e7e8c4834fa0c94ae` | `cd69250db8808986f8685b91b5d11ea673f6b9bf` | `0c16a351…b6b500` | `3eb90862…00f9e` | empty |

## Verdict

`CONDITIONALLY_ACCEPTED`

## Required Follow-Up

- Delivery finalization is permitted for the verified candidate.
- MySQL/application runtime and deployment must remain `NOT_VERIFIED`.
- When a safe Docker daemon becomes available, run isolated MySQL 8.4 migration plus representative
  import/search/detail HTTP checks before claiming runtime or deployment evidence.
- No code repair is required.
