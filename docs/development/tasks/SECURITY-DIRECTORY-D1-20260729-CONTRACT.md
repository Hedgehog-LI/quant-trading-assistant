# Task Contract: SECURITY-DIRECTORY-D1-20260729 证券目录与模糊检索 D1

## Contract Identity

- Status: `FROZEN`
- Contract version: `1.0`
- Frozen at: `2026-07-29T15:51:03Z`
- Frozen by parent run: `/root`
- Lane: `LONG_HIGH_RISK`

冻结后由父协调者计算本文件 SHA-256，并将其记录到任务状态和后续 TaskPacket；本文件不记录自身哈希。

## Objective

完成 P1.4b-D1 后端证券目录与确定性模糊检索基础：通过更高版本 Flyway migration 扩展既有
`stock_basic`、新增 `stock_alias`，提供可审计且可重复执行的本地 CSV 目录导入、搜索和详情 API，
并保持既有证券 CRUD 兼容。正常搜索和目录导入不访问报价、K 线、Longbridge 或采集任务。

## Authority

- Product/design:
  - `docs/features/SECURITY_DIRECTORY_SEARCH_DESIGN.md`
  - `docs/decisions/ADR-0009-local-first-security-directory.md`
  - `docs/development/SECURITY_DIRECTORY_SEARCH_IMPLEMENTATION_PLAN.md`
  - `docs/ai/HANDOFF_2026-07-17_security_directory_search.md`
- API/data contract:
  - `docs/api/MARKET_DATA_API.md`
  - `docs/DATABASE_DESIGN.md`
  - `docs/CURRENT_ARCHITECTURE_AND_MODULES.md`
  - `docs/DEVELOPMENT_WORKFLOW.md`
- Baseline commit: `8c7d131da052cc9fc39f6d9b6e3158d4cc33f640`
- Baseline branch: `main`
- Pre-existing dirty paths: none (`git status --short` empty)
- Task branch: `codex/security-directory-d1-20260729`
- Allowed write paths:
  - `src/main/java/com/quant/trade/marketdata/**`
  - `src/main/resources/mapper/StockBasicMapper.xml`
  - `src/main/resources/mapper/StockAliasMapper.xml`
  - `src/main/resources/db/migration/V17__*.sql`
  - `src/test/java/com/quant/trade/marketdata/**`
  - `src/test/resources/**` only for focused D1 fixtures
  - `docs/development/tasks/SECURITY-DIRECTORY-D1-20260729-*.md`
  - after independent acceptance only: the project delivery documents explicitly required by
    `docs/DEVELOPMENT_WORKFLOW.md §2`

## Facts And Decisions

| Type | Item |
|---|---|
| FACT | `stock_basic` exists from V5 with stable `id`, unique `canonical_symbol`, legacy `name/market/list_date/delisted`, and existing `/api/v1/market-data/stocks` CRUD. |
| FACT | V1-V16 are published and immutable; D1 must use V17 or higher without editing history. |
| FACT | Canonical symbols support `SH/SZ/BJ/HK/US`; HK is five digits internally and US tickers are upper-case. |
| FACT | D1 is local only. External directory providers, Longbridge metadata enrichment, scheduler sync and status endpoints belong to D3. |
| DECISION | Extend `stock_basic`; do not introduce `security_master` or any second security identity. |
| DECISION | Add `stock_alias` with `AliasType`, normalized alias, data source and effective-date metadata; uniqueness is `(stock_basic_id, normalized_alias, alias_type)`. |
| DECISION | Add local multipart CSV import at `POST /api/v1/market-data/security-directory/import`; this is a manual local import, not the D3 provider sync endpoint. |
| DECISION | CSV multipart part name is `file`. Input is strict UTF-8 with optional BOM and RFC-4180 quoting. Required headers are `canonical_symbol,name,market,exchange,currency,security_type,list_status,data_source,source_updated_at`; optional headers are `name_cn,name_hk,name_en,short_name,pinyin_full,pinyin_abbr,list_date,source_hash,aliases`. Unknown headers, missing required headers and duplicate headers are invalid. |
| DECISION | CSV enum domains are: market `SH/SZ/BJ/HK/US`; security type `STOCK/ETF/INDEX/REIT/FUND/BOND/WARRANT/OPTION/FUTURE/OTHER`; list status `LISTED/DELISTED/UNKNOWN`; alias type `FORMER_NAME/OLD_TICKER/SHORT_NAME/ENGLISH/TRADITIONAL/USER`. `source_updated_at` is RFC-3339 with offset and stored as UTC; `list_date` is ISO `yyyy-MM-dd`. |
| DECISION | `aliases` entries use `ALIAS_TYPE:LANGUAGE:VALUE` separated by `|`; language may be empty, value is nonblank, and the first two colons delimit type/language so value may contain further colons. Empty entries, unknown types and unescaped `|` are invalid. Alias normalization is Unicode NFKC, trim, internal-whitespace collapse and lower-case with `Locale.ROOT`. |
| DECISION | File limit is 50 MiB and 200,000 data rows. Missing/empty file, malformed UTF-8/CSV, header or semantic failures use the existing `ApiResponse` failure envelope with stable error code; oversize uses HTTP 413 and other invalid input uses HTTP 400. Errors contain bounded line, field, stable reason code and sanitized message, never a full raw row. |
| DECISION | A CSV file is fully parsed and validated before writes and all stock/alias writes execute in one transaction. Any validation, conversion, unique-constraint or persistence failure restores the exact pre-import state. Identical duplicate normalized rows are processed once and counted unchanged; conflicting rows for the same canonical symbol reject the file and identify both lines. A valid batch upserts by `canonical_symbol`; a changed formal `name` inserts the old nonblank name as one `FORMER_NAME`. |
| DECISION | Successful import counts are `totalRows`, `inserted`, `updated`, `unchanged`, `aliasesInserted`, `aliasesUnchanged`, `formerNamesAdded`, `failed` and bounded `errors`. |
| DECISION | V17 sets legacy `list_status` to `DELISTED` where `delisted=true`, otherwise `UNKNOWN`. Effective delisting is `delisted=true OR list_status=DELISTED`. CSV keeps both fields consistent (`DELISTED -> true`, other statuses -> false). Legacy update to delisted maps to `DELISTED`; update to active maps a prior `DELISTED` to `UNKNOWN`. Legacy updates preserve all other directory fields. Alias FK uses cascade delete so existing allowed stock deletion remains compatible. |
| DECISION | Search endpoint is `GET /api/v1/market-data/securities/search`; detail endpoint is `GET /api/v1/market-data/securities/{canonicalSymbol}`. Both use `ApiResponse<T>`. |
| DECISION | Search parameters are `q`, optional ordered `markets`, optional `types`, `includeDelisted=false`, and `limit=20` (bounded to 1..100). Chinese needs at least one character; Latin/digit-only queries need at least two. |
| DECISION | Search ranking is score descending: canonical exact 100, raw symbol exact 95, formal-name exact 90, formal-name prefix 80, alias exact/prefix 75, pinyin full/abbr prefix 70, name/alias contains 50. Within equal score: listed first, caller market order, normalized display name, then `canonical_symbol`. |
| DECISION | Search returns at most one row per `stock_basic`, selects its highest-scoring channel, and reports `matchedBy` as one of `CANONICAL_SYMBOL_EXACT/RAW_SYMBOL_EXACT/FORMAL_NAME_EXACT/FORMAL_NAME_PREFIX/ALIAS_EXACT/ALIAS_PREFIX/PINYIN_FULL_PREFIX/PINYIN_ABBR_PREFIX/NAME_CONTAINS/ALIAS_CONTAINS`. Within a score, exact precedes prefix, full pinyin precedes abbreviation, and name contains precedes alias contains. |
| DECISION | Numeric leading-zero relaxation is HK-only: `2498` and `02498` may match `HK.02498`; it never rewrites `SZ.002498`. Distinct cross-market candidates remain separate results. |
| DECISION | If `markets` is absent all markets have equal preference. When present, first occurrence defines preference and duplicates are removed retaining order. The name tie key is NFKC-normalized, trimmed, whitespace-collapsed and case-folded independently of DB collation, then `canonical_symbol` is the final tie-break. |
| DECISION | Search response items contain canonical/raw symbol, display/formal names, market, exchange, currency, security type, list status and matchedBy. Response metadata contains `catalogStatus`, `catalogUpdatedAt`, `stale` and `degraded`; D1 derives status locally and never queries a provider. |
| DECISION | `catalogStatus` is `EMPTY` when no rows exist; then `catalogUpdatedAt=null`, `stale=false`, `degraded=false`. Otherwise status is `READY`, `catalogUpdatedAt=MAX(source_updated_at)`, `degraded=false`, and `stale=true` when that maximum is null or the injected UTC clock is strictly later than `catalogUpdatedAt + PT48H`. Exact equality is not stale. D3 may replace this documented D1 heuristic with sync-status truth. |
| DECISION | Existing `/stocks` requests and responses remain source/binary compatible: migration defaults and nullable additions allow legacy inserts; `name`, `market`, `listDate` and `delisted` semantics remain valid. |
| ASSUMPTION | UTF-8 CSV with an optional BOM and RFC-4180 quoting is sufficient for D1; no XLS/XLSX or remote URL ingestion is added. |
| ASSUMPTION | A reproducible H2 dataset of at least 50,000 securities and 100,000 aliases is the mandatory automation performance gate; the report must state actual hardware/JVM/database and may not generalize it to deployment MySQL. |
| OPEN_QUESTION | None blocking. Disposable MySQL runtime remains conditional on safe Docker availability and is reported separately from H2 automation. |

## Scope

### In Scope

- One V17 migration compatible with the H2 test profile and MySQL 8.4 syntax used by the project.
- `stock_basic` directory fields, their enum representation and search-supporting indexes.
- `stock_alias` table, mapper/model and alias normalization.
- Existing layered backend patterns: controller/service/manager/DAO/model/DTO/VO/MapStruct converter/MyBatis XML.
- UTF-8 CSV directory import with validation, canonical normalization, idempotent upsert, former-name aliasing,
  counts and line-level failure evidence.
- Deterministic local search and enhanced security detail API.
- Compatibility tests for existing `/stocks` CRUD and old data.
- Focused unit/integration/migration tests, a reproducible performance benchmark and final `test`/`package` gates.
- Project-level API/DB/architecture/development/acceptance/handoff/build documents only after verifier permits delivery.

### Out Of Scope

- D2 frontend `SecuritySelector`, frontend repository, mock adapter and page integration.
- D3 provider facade, Longbridge directory/static-info integration, external sync, scheduler and directory status API.
- D4 cross-module form rollout.
- Real quote/K-line/history acquisition, market-wide price storage or automatic collection-task creation.
- Ticker identity history beyond string aliases; `security_identifier` remains a later conditional design.
- Changes to products, strategies, signals, risk, OpenClaw, sector analysis or trading workflows.

### Prohibited

- Trading, account, order, broker, credential or automatic-order capabilities.
- A parallel securities master table.
- Modifying V1-V16 or backfilling/deleting user data outside the additive migration.
- Any provider/network call from search or CSV import.
- Push, rebase, force-push, reset, remote mutation, secrets, `.env`, `runtime-libs`, `node_modules` or runtime artifacts.
- Finalization before the independent verifier accepts the frozen, review-clear candidate.

## Acceptance Criteria

| AC-ID | Observable behavior | Preconditions/input | Expected result | Evidence | Dimension | Owner | Status |
|---|---|---|---|---|---|---|---|
| AC-01 | Additive migration and legacy compatibility | V1-V16 schema plus legacy active/delisted rows and legacy CRUD requests | V17 applies on H2; rows/IDs survive with the frozen lifecycle mapping; old create/read/update/list/delete works, updates preserve directory fields and deletion leaves no alias orphan | Migration/schema assertions, exact legacy HTTP/DB regressions and migration inspection; conditional disposable-MySQL evidence reported separately | STATIC/AUTOMATION/RUNTIME | code reviewer + final verifier | NOT_STARTED |
| AC-02 | Typed, atomic, idempotent and explainable CSV import | BOM/quoted valid file, typed aliases, identical/conflicting duplicates, changed name, bad enum/date/symbol and forced late failure | Valid import reports exact frozen counts; repeat creates no duplicate; name change adds one `FORMER_NAME`; every invalid/persistence-failing batch restores before-state and returns bounded line evidence | Before/after stock/alias snapshots, response assertions and forced late-failure test | AUTOMATION | final verifier | NOT_STARTED |
| AC-03 | Required matches, validation and filters are deterministic | Required A/H/US fixtures, `SZ.002498` collision, same-name, all statuses/types and boundary parameters | All documented channels find expected securities; HK padding does not collapse another market; filters/defaults work; invalid q/filter/limit has stable 4xx envelope; delisted behavior is explicit | Parameterized API tests with exact bodies and ordered canonical-symbol lists | AUTOMATION | final verifier | NOT_STARTED |
| AC-04 | Ranking, de-duplication and response contract are stable | One security matches multiple channels; fixtures cover every score/tie band | One row per security; maximum score/channel precedence selects matchedBy; listed/market/name/canonical tie-breaks repeat identically; required item/catalog fields are present | Full ranking matrix, repeated calls, API JSON assertions and static ranking review | STATIC/AUTOMATION/RUNTIME | code reviewer + final verifier | NOT_STARTED |
| AC-05 | Search/import have zero provider or行情 side effects | Throwing spies and non-empty protected-table fixtures | Local operations succeed with zero collaborator calls; quote/bar/sync-task/collection/price-table primary-key/content snapshots remain equivalent | Mock interaction verification, dependency inspection and content snapshots rather than count-only assertions | STATIC/AUTOMATION | code reviewer + final verifier | NOT_STARTED |
| AC-06 | Performance evidence is reproducible and sufficiently sampled | Fixed-seed dataset ≥50,000 securities/100,000 aliases and frozen eight-class query mix | After 400 warmups, 1,600 measured searches (200/class) have overall and each-class P95 `<300ms`; any miss fails and is reported truthfully | Versioned benchmark runner plus raw/task report recording dataset, queries, environment and percentile method | AUTOMATION | final verifier | NOT_STARTED |
| AC-07 | Frozen candidate passes backend and static gates | Review-clear commit in disposable worktree | Focused tests, `./mvnw test`, `./mvnw package`, diff/forbidden-path/secret scan all pass with unchanged candidate identity | Exact exit codes/logs, before/after commit/tree and static scan | STATIC/AUTOMATION | code reviewer + final verifier | NOT_STARTED |
| AC-08 | Detail and catalog states are externally distinguishable | Empty/ready/fresh/stale/null-time data, found/absent canonical and fixed clock | Empty, no-match, fresh, exact-threshold, stale, detail found and not-found return the frozen distinct metadata/envelopes | Fixed-clock API integration tests including equality and one-instant-past boundaries | AUTOMATION | final verifier | NOT_STARTED |

## Verification Plan

| Dimension | Required | Command or inspection | Pass rule |
|---|---|---|---|
| STATIC | Yes | Frozen diff/contract inspection; `git diff --check`; forbidden-path/secret/runtime-artifact scan; mapper/migration/API compatibility review | No actionable finding, no prohibited path or historical migration change, deterministic order has explicit final tie-break |
| AUTOMATION | Yes | Focused D1 tests and fixed 50k/100k benchmark (400 warmups + 1,600 measured), then `./mvnw test` and `./mvnw package` in disposable worktree | All required commands exit 0; every AC has independent evidence; overall and each query-class P95 genuinely `<300ms` |
| RUNTIME | Conditional | If Docker is safely available, start an isolated disposable MySQL 8.4 instance and packaged app, then run minimal import/search/detail curl without external provider | Fresh MySQL migration and representative HTTP semantics succeed; otherwise `NOT_VERIFIED` with reason, never inferred from H2 |
| DEPLOYMENT | No for D1 | No remote deployment or existing-volume mutation is authorized | Always report `NOT_VERIFIED`; no H2/Docker result may be called deployed |

## Role Assignments

- Test designer: `qta-test-designer`, clean TaskPacket, AC-01..AC-07, no writes or commands.
- Implementer: `qta-implementer` using `qta-backend-implementation`, AC-01..AC-07 implementation and self-check only.
- Code reviewer: `qta-code-reviewer`, read-only review of the frozen candidate, AC-01..AC-07.
- Final verifier: `qta-final-verifier` using `qta-independent-verification`, disposable worktree, AC-01..AC-07.
- Omitted roles and justification: none.

## Candidate And Git Policy

- Git automation: `COMMIT`
- User authorization evidence: explicit request in the initiating task for local contract/candidate/repair-N/finalization commits.
- Task branch: `codex/security-directory-d1-20260729`
- Contract commit: pending
- Candidate mode: `COMMIT`
- Candidate commit: pending
- Candidate tree hash: pending
- Patch SHA-256: pending
- Candidate manifest path/hash: not applicable
- Checkpoint push allowed: `NO`
- Delivery push target: none
- Protected/default branch direct push: `NO`

## Checkpoint Policy

- Context budget: solidify findings at 25%; do not open new workflows after 40%; checkpoint or handoff before 60%.
- Checkpoint interval: at contract freeze, candidate freeze, each finding/repair boundary and verifier verdict.
- Maximum repair rounds for one failure fingerprint: 2
- Stop conditions:
  - destructive migration or secret/credential handling is required;
  - authority documents contain an unresolvable contract conflict;
  - changed candidate cannot be rebound to a fresh review;
  - the same normalized failure fingerprint survives two repair rounds without new evidence;
  - required acceptance evidence cannot be produced truthfully.
