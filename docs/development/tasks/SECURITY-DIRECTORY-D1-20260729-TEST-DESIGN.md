# Test Design: SECURITY-DIRECTORY-D1-20260729

## Identity

- Role: `qta-test-designer`
- Role run ID: `TD-20260729-01`
- Lane: `LONG_HIGH_RISK`
- Baseline: `8c7d131da052cc9fc39f6d9b6e3158d4cc33f640`
- Contract reviewed: version `0.1` draft
- Assigned ACs: `AC-01..AC-07`
- Role result: initial `CONTRACT_BLOCKED`; parent accepted the contract amendments recorded below.

## Defects And Parent Resolution

| Risk | Defect | Parent resolution |
|---|---|---|
| BLOCKER | Catalog staleness had no threshold, clock or null/boundary rule. | Accepted: D1 uses fixed UTC clock semantics, `MAX(source_updated_at)`, and strict 48-hour threshold; empty/null cases are explicit. |
| BLOCKER | CSV headers, enum domains, alias encoding and response counts were not frozen. | Accepted and added to contract. |
| BLOCKER | Legacy `delisted` and new `list_status` could disagree; alias FK deletion behavior was unclear. | Accepted: migration/backward-update/import effective-state rules and alias cascade are frozen. |
| HIGH | Validation-before-write did not prove transaction rollback on late persistence failures. | Accepted: one transaction and exact pre-state restoration are required and tested. |
| HIGH | Multi-channel de-duplication, matchedBy precedence and collation-independent ties were incomplete. | Accepted: one result per stock, explicit channel enum/precedence, NFKC/case-folded name key and canonical final tie are frozen. |
| HIGH | Raw numeric matching could collapse HK and A-share identities. | Accepted: leading-zero relaxation is HK-only and cross-market results are never merged. |
| HIGH | Query/file/detail boundary errors and empty/no-result distinctions were missing. | Accepted as AC-03/AC-08 test requirements. |
| HIGH | The benchmark could be under-sampled or skewed. | Accepted: fixed seed, at least 50k securities/100k aliases, eight classes, 400 warmups and 1,600 measured searches with per-class P95. |
| MEDIUM | Side-effect evidence based only on counts was weak. | Accepted: throwing collaborators plus primary-key/content snapshots. |
| MEDIUM | Build acceptance and post-acceptance delivery finalization were circular. | Accepted: AC-07 ends at candidate gates; delivery-document changes are a post-verdict parent obligation. |

MySQL runtime was recommended as required by the test designer but remains conditional because the user explicitly
allowed Docker only when safe. H2 automation cannot substitute for MySQL runtime evidence; an unavailable or
unsafe Docker environment is recorded as `RUNTIME NOT_VERIFIED`.

## Independent Test Matrix

| ID | Dimension | Required cases |
|---|---|---|
| S-01 | STATIC | V1-V16 unchanged; V17 additive; search indexes, alias unique key and cascade FK present. |
| S-02 | STATIC | Ranking has an explicit final tie-break and no DB encounter-order/default-collation dependency. |
| S-03 | STATIC | Search/import dependency graph contains no provider, quote, K-line, HTTP or sync-task path. |
| A-01 | AUTOMATION | Fresh migration plus pre-V17 active/delisted row mapping and complete legacy CRUD regression. |
| A-02 | AUTOMATION | UTF-8 BOM, CRLF, quoted comma/newline, blank optional cells and typed aliases. |
| A-03 | AUTOMATION | First import, exact re-import, changed formal name, alias normalization and exact counters. |
| A-04 | AUTOMATION | Invalid header/UTF-8/enum/date/timestamp/symbol/market consistency, identical/conflicting duplicates. |
| A-05 | AUTOMATION | A forced failure after partial DAO work proves transactional rollback of stocks and aliases. |
| A-06 | AUTOMATION | Blank/whitespace q, one Latin character, one Han character, mixed query, limits 0/1/100/101 and invalid filters. |
| A-07 | AUTOMATION | Canonical/raw/formal/prefix/alias/pinyin/contains channels and exact `matchedBy`. |
| A-08 | AUTOMATION | `HK.02498`, `SZ.002498`, and queries `2498/02498/002498` with and without ordered market filters. |
| A-09 | AUTOMATION | Multi-channel de-duplication, every score band and listed/market/name/canonical tie-breaks. |
| A-10 | AUTOMATION | Empty, ready, no-match, exact freshness threshold, one instant stale and null source timestamp with fixed UTC clock. |
| A-11 | AUTOMATION | Detail canonical normalization, found response, absent symbol and inactive security. |
| A-12 | AUTOMATION | Throwing provider spies and protected table/content snapshots for search/import. |
| A-13 | AUTOMATION | Fixed-seed 50k/100k benchmark, eight query classes, 400 warmups and 1,600 measured requests. |
| R-01 | RUNTIME | If safe: disposable MySQL 8.4 migration, import, search/detail and invalid-batch rollback. |
| D-01 | DEPLOYMENT | Always `NOT_VERIFIED`; no remote deployment or existing volume mutation is authorized. |

## Fixture And Environment Requirements

- Pre-V17 active/delisted rows and unchanged legacy CRUD payloads.
- `SH.603308`, `HK.02498`, `US.AAPL`, and `SZ.002498` for the numeric-collision case.
- Same-name securities across markets; listed/unknown/delisted rows; multiple security types.
- One security matching several channels; fixtures for every score and tie band.
- Former-name transition, aliases equal after normalization, and equal alias text on different stocks.
- CSV fixtures for BOM, CRLF, RFC-4180 quotes/newlines, invalid UTF-8, bad headers/types, duplicates and late failure.
- Fixed `Clock` at threshold minus one nanosecond, exact threshold and plus one nanosecond.
- Providers disabled or replaced with throwing spies.
- Fixed-seed benchmark data and raw latency evidence recording Java/JVM, DB, CPU, memory, OS and percentile method.

## Ready Rows

The parent accepted AC-01..AC-08 as frozen in the active contract. AC-07 covers the frozen implementation
candidate only; project delivery documents are updated after the verifier permits delivery.

`READY_FOR_IMPLEMENTATION` after the accepted amendments are persisted and the contract hash is frozen.
