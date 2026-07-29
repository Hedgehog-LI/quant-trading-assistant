# Repair 2 Self Check: SECURITY-DIRECTORY-D1-20260729

## Identity

- Role run: `IMP-20260729-R2`
- Repair round: `2`
- Contract hash: `0c16a3510ca7e8c34354c42ce78babcd1ffff3f4ffbf83d91debd74a7db6b500`
- Input candidate: `483503b3bdb27317cbb3b8c950d5fc547c168adb`
- Finding fingerprint:
  `CR06-MYSQL-COLLATION|CR07-ALIAS-METADATA|CR08-CSV-NEGATIVE-MATRIX|CR09-RANK-TIE-MATRIX|CR10-COLLECTION-PRICE-SNAPSHOT`
- Result: `SELF_CHECKED`; fresh generation-3 review and independent verification remain required.

## Finding Closure

| Finding | Repair and evidence |
|---|---|
| CR-06 | V17 adds UTF-8 `normalized_alias_key VARBINARY(1024)` for binary alias identity; SQL recalls candidates while Java performs final normalized code-point channel matching/scoring. `resume` and `résumé` remain distinct in uniqueness and matchedBy tests. |
| CR-07 | An alias identity deduplicates only when its complete persisted display/language/source metadata is equal. Single-row, duplicate-security-row and persisted metadata conflicts return `CONFLICTING_ALIAS_METADATA` and roll back. |
| CR-08 | Isolated cases cover missing header, canonical, four enum branches, date, timestamp, alias grammar/type and an actual 200,001-row file boundary with stable errors and unchanged state. |
| CR-09 | Opposing fixtures isolate score, listed, market, NFKC-name and canonical ties; full order repeats three times and multi-channel matchedBy precedence is covered. |
| CR-10 | Non-empty protected snapshots include sync-plan configuration/run claim and `portfolio_price_snapshot`, plus daily/quote/task; provider zero-interaction remains asserted. |

## Commands And Results

| Command/evidence | Result |
|---|---|
| Compile and test-compile | pass |
| Focused repair tests | 33/33 pass before the final persisted-conflict addition; final addition included in full suite |
| First full suite | exposed D1 test cleanup pollution causing two later legacy test failures; symmetric cleanup added without changing business tests |
| `./mvnw test` final | exit 0; 377 tests, 0 failures/errors, 1 opt-in benchmark skipped |
| `./mvnw package` | exit 0; same test result and executable JAR |
| Enabled benchmark | exit 0; fixed 50k/100k, 400 warmups, 1,600 measured |
| `git diff --check`; V1-V16; credential/runtime scans | pass; no historical migration or secret/runtime artifact included |

## Repaired Benchmark

- Overall P95: `172.660500 ms`.
- Per-class maximum: `177.086250 ms` (`FORMAL_NAME_PREFIX`).
- All eight classes are below 300ms.
- Raw JSON/CSV are under ignored `target/security-directory-benchmark/`.
- H2 hot-key and non-MySQL limitations remain recorded.

## Runtime And Deployment

- Disposable MySQL 8.4: `NOT_VERIFIED`. Safe `docker info` probe found no Docker daemon at the local socket;
  no container or volume was started or left behind.
- Docker HTTP runtime: `NOT_VERIFIED`.
- Deployment: `NOT_VERIFIED`.
