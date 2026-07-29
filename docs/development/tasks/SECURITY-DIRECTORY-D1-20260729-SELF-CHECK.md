# Implementer Self Check: SECURITY-DIRECTORY-D1-20260729

## Identity

- Role: `qta-implementer`
- Role run ID: `IMP-20260729-01`
- Lane: `LONG_HIGH_RISK`
- Contract: v1.0 `FROZEN`
- Contract hash: `0c16a3510ca7e8c34354c42ce78babcd1ffff3f4ffbf83d91debd74a7db6b500`
- Result: `SELF_CHECKED` only; this is not independent acceptance.
- Git writes by role: none

## Implemented Behavior

- Additive V17 extension of `stock_basic` plus `stock_alias`, indexes, legacy lifecycle backfill and alias cascade.
- Existing `/stocks` CRUD compatibility, including `delisted`/`list_status` mapping and directory-field preservation.
- Strict UTF-8/BOM/RFC-4180 multipart CSV import, 50 MiB/200k limits, typed aliases, bounded errors,
  duplicate-conflict detection, idempotent upsert, former-name aliases and transactional rollback.
- Deterministic local search with the frozen score channels/ties/filters/de-duplication and HK-only padding.
- Enhanced detail endpoint and fixed-clock EMPTY/READY/fresh/stale/null-time catalog metadata.
- Production search/import paths do not depend on provider, quote, K-line or task services.
- An opt-in fixed-seed benchmark runner writes raw CSV and JSON evidence under ignored `target/`.

## AC Evidence Claimed By Implementer

| AC | Self-check evidence |
|---|---|
| AC-01 | Migration-from-V16 test, legacy CRUD regression, lifecycle mapping, field preservation and alias cascade. |
| AC-02 | BOM/quoted multiline, repeat import, former name, alias normalization, malformed input, duplicate conflict and forced late-failure rollback. |
| AC-03/04 | All channels, filters, query bounds, HK/SZ collision, de-duplication, ordered ties and matchedBy. |
| AC-05 | Throwing provider mock has zero interactions; protected quote/bar/task rows remain content-equivalent. |
| AC-06 | Fixed 50,000-stock/100,000-alias, 400-warmup/1,600-measurement, eight-class benchmark. |
| AC-07 | Focused tests, full test, package and diff check passed. |
| AC-08 | Empty/no-match/null time, 48-hour equality/+1ns and detail found/inactive/not-found. |

## Commands And Results

| Command | Result |
|---|---|
| `./mvnw -q -DskipTests compile` | exit 0 |
| `./mvnw -q -Dtest=StockDataServiceTest test` | exit 0 |
| First focused D1 test attempt | exit 1: H2-reserved test alias `value`; fixture corrected to `protected_value` |
| First controller expectation attempt | exit 1: expected alias exact although formal prefix has higher frozen score; expectation corrected |
| `./mvnw -q -Dtest=SecurityDirectoryMigrationTest,SecurityDirectoryIntegrationTest,SecurityDirectoryControllerTest,StockDataServiceTest test` | exit 0 |
| `./mvnw -q -Dqta.security-directory.benchmark=true -Dtest=SecurityDirectorySearchBenchmarkTest test` | exit 0 |
| `./mvnw test` | exit 0; 357 tests, 0 failures, 0 errors, 1 opt-in benchmark skipped |
| `./mvnw package` | exit 0; same 357-test result and packaged JAR generated |
| `git diff --check` | exit 0 |
| Production provider/dependency isolation scan | exit 0; no forbidden dependency path found |

## Benchmark Facts

- Dataset: 50,000 securities and 100,000 aliases, fixed seed.
- Warmup/measured: 400 / 1,600; eight classes with 200 measured samples each.
- Overall P95: `168.705583 ms`.
- Per-class P95 range: `142.958500–172.495458 ms`; all classes below 300ms.
- Environment: Java 17.0.6 HotSpot 64-bit, macOS 26.3 aarch64, 8 processors, H2 2.3.232.
- Raw CSV: 2,001 lines including header.
- Boundary: this H2 result is not evidence for deployment MySQL.

## Unverified Dimensions

- Disposable MySQL 8.4 runtime: `NOT_VERIFIED`
- Docker/application curl runtime: `NOT_VERIFIED`
- Deployment: `NOT_VERIFIED`
- Project delivery documentation: intentionally unchanged until the independent verifier permits delivery.

## Proposed Candidate Message

`feat(marketdata): add local security directory search and import`
