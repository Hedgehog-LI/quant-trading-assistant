# Repair 1 Self Check: SECURITY-DIRECTORY-D1-20260729

## Identity

- Role run: `IMP-20260729-R1`
- Repair round: `1`
- Contract hash: `0c16a3510ca7e8c34354c42ce78babcd1ffff3f4ffbf83d91debd74a7db6b500`
- Input candidate: `ec101b3bfe81a117a3024af3d7aa46b683046bfa`
- Finding fingerprint:
  `CR01-LIKE|CR02-MVC-BINDING|CR03-TIMESTAMP-PRECISION|CR04-CONFLICT-COUNT|CR05-NONEMPTY-SNAPSHOT`
- Result: `SELF_CHECKED`; fresh review and independent verification remain required.

## Finding Closure

| Finding | Repair and evidence |
|---|---|
| CR-01 | A distinct escaped `likeQuery` uses `!` as the literal escape in every candidate LIKE. Tests seed decoys and literal `%%`, `__`, `!!` values, asserting exact result and candidate count one. |
| CR-02 | Marketdata-controller-scoped highest-precedence advice maps missing parameters and type mismatches to HTTP 400 `ApiResponse/PARAM_ERROR`; MockMvc covers missing q and invalid limit/boolean. |
| CR-03 | V17 uses `DATETIME(6)`; RFC-3339 values normalize to UTC and microseconds. Tests assert `.900123`, idempotent repeat, exact 48h fresh and +1ns stale. |
| CR-04 | Failed rows use unique physical CSV line numbers. Three conflicts assert `total=3/failed=3`; 60 invalid lines assert `failed=60` and error list cap 50. |
| CR-05 | Side-effect test seeds non-empty daily-bar, quote-snapshot and sync-task fixtures and compares every selected PK/content column; provider remains zero-interaction. |

The benchmark report now states that H2 is not MySQL evidence and one repeated hot key per query class may
understate mixed-key production latency.

## Commands And Results

| Command | Result |
|---|---|
| `./mvnw -Dtest=SecurityDirectoryIntegrationTest,SecurityDirectoryControllerTest test` | exit 0; 17 tests |
| Earlier focused attempt | exposed exception-advice precedence and a null-field JSON assertion; both corrected before the passing rerun |
| `./mvnw test` | exit 0; 361 tests, 0 failures/errors, 1 opt-in benchmark skipped |
| `./mvnw package` | exit 0; same test result; JAR/repackage succeeded |
| `./mvnw -Dqta.security-directory.benchmark=true -Dtest=SecurityDirectorySearchBenchmarkTest test` | exit 0; 1/1 |
| `git diff --check` | exit 0 |
| Allowed-path, V1-V16, credential and runtime-artifact scans | exit 0; no violations |

## Repaired Benchmark

- Dataset: 50,000 securities and 100,000 aliases.
- Matrix: 400 warmups, 1,600 measured, 200 samples in each of eight classes.
- Overall P95: `167.526708 ms`.
- Per-class P95 maximum: `175.447667 ms`; every class below 300ms.
- Raw evidence: 2,001 CSV lines plus parseable JSON under ignored `target/security-directory-benchmark/`.
- Environment: Java 17.0.6, H2 2.3.232, macOS 26.3 aarch64, 8 processors.

## Unverified

- MySQL 8.4 runtime: `NOT_VERIFIED`
- Docker HTTP runtime: `NOT_VERIFIED`
- Deployment: `NOT_VERIFIED`
