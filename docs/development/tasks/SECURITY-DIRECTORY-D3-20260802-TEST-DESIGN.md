# SECURITY-DIRECTORY-D3-20260802 Test Design Artifact

**Role**: TEST_DESIGNER (clean, FRESH_ONLY) — `TD-20260802-01`, repair round 0
**Task**: `SECURITY-DIRECTORY-D3-20260802` (L2, CONTRACT_DRAFTED, contract v0.1, hash `6049de3da2420e5d6579f1939020c0d012109e6535667976e0e38bae8a5307d0`)
**Assigned ACs**: AC-01..AC-06
**Scope of this artifact**: challenge contract v0.1 for falsifiability, then derive a black-box matrix. No files written during the role run; parent persists.

---

## 1. Verdict

**`AMENDMENTS_REQUIRED`**

The contract is internally coherent and reuses the correct primitives (`SyncScopeLockMapper`, `selectLatestByScope`, `txRequiresNew`, `market_data_sync_task`, D1 enum/alias domains). However, three blocking ambiguities prevent falsifiable AC-02/AC-04/AC-05 evidence and risk a silent D1 divergence or data-integrity regression. They must be pinned before freeze. None of the three expands product scope; each resolves an existing ambiguity the contract already half-asserts.

---

## 2. Blocking Amendments (cap = 3)

### A1 — Pin HOW the CsvSnapshot provider reuses D1 frozen parse/normalize/alias rules (falsifiability + D1-compat + data-integrity)

**Risk**: Highest. Without this, "reuse" is unfalsifiable and the implementer must either (a) duplicate D1's private parser (the duplicate is not D1-frozen and will not be tested against D1 fixtures), or (b) refactor frozen D1 (out of scope; D1 write paths are excluded and D1 is `FROZEN`).

**Evidence from repo**:
- `src/main/java/com/quant/trade/marketdata/service/SecurityDirectoryService.java` — `parseAndValidate` (line 199), `parseRow` (line 302), `parseAliases` (line 346), `sameDirectoryData` (line 719), `sameAliasMetadata` (line 699), `aliasMetadataByIdentity` (line 680), `sameRow` (line 665) are all `private`. The only public entry is `importCsv(InputStream, long)` at line 92, which is `@Transactional` and writes through `persist(batch)`. There is no extractable parser.
- D1 contract line 60: "A CSV file is fully parsed and validated before writes ... Identical duplicate normalized rows are processed once and counted unchanged; conflicting rows for the same canonical symbol reject the file ... A valid batch upserts by canonical_symbol; a changed formal name inserts the old nonblank name as one FORMER_NAME."

**Exact contract text to change** — replace FactsAndDecisions row 61 (`DECISION | 提供 CsvSnapshotSecurityDirectoryProvider ... 解析与校验完全复用 D1 ...`) with:

> `DECISION | CsvSnapshotSecurityDirectoryProvider 必须通过一个 D1 与 D3 共享的、可独立测试的解析组件复用 D1 冻结口径。实现路径二选一，且必须在 AC-01 evidence 中显式声明所选路径并在 diff 中可验证：(P1) D1 重构出 public DirectoryCsvParser（输入 byte[]/InputStream → 输出已校验的 ParsedDirectoryBatch，含 stock/alias 候选与 duplicate-unchanged 计数），D1 importCsv 改为调用它；该重构计入 D3 允许写路径并需在 D1 兼容集成测试中证明 D1 行为字节级不变。(P2) 若不动 D1，D3 必须提取一个 SecurityDirectoryCsvParser，并对 D1 冻结 fixtures（含 BOM/引号/同义重复/CONFLICTING_DUPLICATE/CONFLICTING_ALIAS_METADATA/改名 FORMER_NAME/枚举/RFC-3339/ISO date 全部 reasonCode）执行参数化等价测试，证明与 D1 importCsv 在同一输入下产出相同的 stock/alias 候选集合与失败 reasonCode。staging/diff 阶段对 "updated" 候选必须调用与 D1 persist 完全相同的 former-name 插入路径（旧非空 name → 恰好一条 FORMER_NAME）与 alias-identity 冲突检测（aliasMetadataByIdentity 等价语义），不得在 diff 层重新实现。`

**Rationale**: AC-01 ("CSV provider 复用 D1 解析规则") and AC-02 ("改名写入 FORMER_NAME") cannot be asserted unless the reuse boundary is concrete. "Reuse" today means "trust the implementer"; that fails the falsifiability bar.

### A2 — Pin snapshot identity so "same snapshot → idempotent unchanged success" is testable (idempotency falsifiability)

**Risk**: High. The contract asserts idempotency by "source id + content hash" but `scope_json` includes both `snapshotId` and `snapshotHash`, and `MarketDataSyncTaskMapper.selectLatestByScope` matches on `scope_json` (not a hash). If `snapshotId` is a per-read generated id, two reads of the identical file produce different `scope_json` and bypass the `selectLatestByScope` short-circuit — idempotency silently fails.

**Evidence from repo**:
- `src/main/java/com/quant/trade/marketdata/dao/MarketDataSyncTaskMapper.java` line 17: `selectLatestByScope(provider, taskType, scopeJson)`.
- `docs/DATABASE_DESIGN.md` line 279: "幂等键：task_type + provider + scope_hash".
- D3 contract line 65: `scope_json={provider, snapshotId, snapshotHash, mode(FULL/INCREMENTAL)}`.

**Exact contract text to change** — replace FactsAndDecisions row 63 (`DECISION | 幂等：同一快照...`) with:

> `DECISION | 幂等以「内容身份」为准，不以读次数为准。snapshotHash = 对规范化后快照内容（D1 sameDirectoryData 等价字段集合 + 排序后的 alias identity 集合）的稳定 SHA-256；snapshotId 必须由 snapshotHash 派生或等于 snapshotHash 的可读前缀，不得引入读次数/时间戳/文件路径。idempotency_key = task_type + provider + snapshotHash + mode（与 scope_hash 同源）；scope_json = {provider, snapshotId(=snapshotHash 派生), snapshotHash, mode}。selectLatestByScope 的匹配键在文档中明确为 (provider, task_type, snapshotHash, mode) 三元组等价比较，而非逐字段 scope_json 字符串比对。同一文件重复读取、或同内容不同路径的两个文件，均映射到同一 idempotency_key 并触发 unchanged 幂等短路。retry 任务的 idempotency_key 追加时间戳保证唯一（沿用现有 daily-bars retry 语义）。`

**Rationale**: AC-02 ("同一快照重复同步结果幂等") and AC-04 ("同 scope 已有 PENDING/RUNNING/SUCCEEDED 时返回既有 task") are only falsifiable if "same snapshot" has a pinned definition.

### A3 — Pin quality-gate default thresholds, per-gate error codes, and FAILED-vs-PARTIAL_FAILED partition (failure-retain + evidence falsifiability)

**Risk**: Medium-High. AC-02 requires "task FAILED + 错误摘要" and "stock_basic/stock_alias 内容等同失败前", but the contract leaves the count-swing threshold, the per-gate error code, and the FAILED/PARTIAL_FAILED choice unspecified.

**Evidence from repo**:
- `src/main/java/com/quant/trade/common/exception/ErrorCodeEnum.java`: `MARKET_DATA_EMPTY_RESULT` (line 55), `BUSINESS_RULE_VIOLATION` (line 21), `DAILY_BAR_VALIDATION_ERROR` (line 42), `INVALID_CANONICAL_SYMBOL` (line 39), `INTERNAL_ERROR` (line 58).
- D3 contract line 70: "数量波动阈值失败或快照为空即拒绝发布（MARKET_DATA_EMPTY_RESULT），不修改任何 list_status".

**Exact contract text to change** — augment FactsAndDecisions row 62 (五阶段) and row 70 (安全默认) with a new pinned block:

> `DECISION | 质量门禁阈值与错误码（默认值，可在 SecurityDirectoryProperties 配置，测试使用默认值）：(1) 数量波动阈值：候选发布集行数相对上一成功目录的相对偏差 abs(new-old)/max(old,1) 默认 ≥ 0.30 即拒绝；上一成功目录为空（首次）时阈值不生效（不拒绝）。失败 lastErrorCode=BUSINESS_RULE_VIOLATION，errorSummaryJson={"gate":"ROW_COUNT_SWING","threshold":0.30,"previousCount":N,"candidateCount":M}。(2) 必填字段缺失：任一候选缺失 D1 REQUIRED 字段 → 拒绝，lastErrorCode=DAILY_BAR_VALIDATION_ERROR，summary={"gate":"REQUIRED_FIELD","sample":<首条缺失行 line/canonical_symbol/field>}。(3) 唯一性：候选集内 canonical_symbol 重复或 alias identity 重复 → 拒绝，lastErrorCode=DAILY_BAR_VALIDATION_ERROR，summary={"gate":"UNIQUENESS","conflicts":[...]}。(4) 非空快照：候选发布集为空（解析后 0 行，或全为 removed）→ 拒绝，lastErrorCode=MARKET_DATA_EMPTY_RESULT，summary={"gate":"EMPTY_SNAPSHOT"}。任一门禁失败：整批不发布，task.status=FAILED（D3 不使用 PARTIAL_FAILED，因为五阶段任一失败整批回滚，无部分发布），stock_basic/stock_alias 内容与失败前逐行字节等价（test 以 before/after 全表内容快照断言，非 count-only），且不修改任何 list_status 列（test 显式断言失败前 DELISTED/LISTED/UNKNOWN 行的 list_status 在失败后未变）。`

**Rationale**: AC-02 enumerates "空快照/数量波动/唯一性/必填" as separate failure modes but gives no thresholds or codes; without them each is a different un-testable claim. Pinning FAILED (not PARTIAL_FAILED) matches the all-or-nothing publish semantics and removes a fork the contract currently leaves open.

---

## 3. AC-01..AC-06 Test Matrix (black-box)

Test class naming convention: new D3 tests live under `src/test/java/com/quant/trade/marketdata/` and `.../provider/`, `.../service/`, `.../scheduler/`. All assertions are on observable state (DB content snapshots, HTTP JSON, task VO fields, bean presence), never on private method structure.

### AC-01 — Provider abstraction, CSV snapshot provider, disabled fallback, D1 full compatibility

| Case | Fixture / input | Expected observable result | Evidence type |
|---|---|---|---|
| CSV provider reuses D1 parse byte-identically | D1 frozen fixtures (BOM, RFC-4180 quotes, typed aliases, `HK.02498`, `US.AAPL`, duplicate-unchanged, CONFLICTING_DUPLICATE pair, CONFLICTING_ALIAS_METADATA pair, bad enum/date/symbol) | `CsvSnapshotSecurityDirectoryProvider.fetch()` returns candidate sets identical to D1 `importCsv` persist input; on invalid input raises same reasonCode set as D1 | Parameterized equivalence test `SecurityDirectoryCsvParserEquivalenceTest` (depends on A1) |
| Disabled provider application start | `enabled=false`, missing CSV path, malformed CSV | Spring context loads; provider bean resolves to `DisabledSecurityDirectoryProvider`; status API `providerEnabled=false` | `SecurityDirectoryDisabledContextTest` |
| Missing CSV path with provider enabled | `enabled=true`, configured path does not exist | Context loads; provider `providerConfigured=false` explainable; D1 search still returns results | `CsvSnapshotProviderMissingPathContextTest` |
| Malformed CSV at path with provider enabled | `enabled=true`, non-UTF-8 / wrong-header file | Context loads; provider `fetch()` raises D1-equivalent parse failure; no partial catalog mutation | `CsvSnapshotProviderMalformedFileTest` |
| D1 import non-regression under D3 enabled | D3 enabled + `POST /security-directory/import` valid D1 CSV | D1 import counts byte-identical to baseline; stock/alias content equal | `SecurityDirectoryImportUnderD3EnabledTest` |
| D1 search/detail non-regression under D3 enabled/disabled | D1 fixtures; `GET /securities/search?q=应流股份`, `q=ylgf`, `q=2498`, `q=AAPL`; `GET /securities/SH.603308` | Identical item lists, `matchedBy`, `catalogStatus` to baseline | Parameterized `SecurityDirectorySearchUnderD3Test` |
| `/stocks` CRUD non-regression | D1 legacy rows; POST/GET/PUT/DELETE `/stocks` | Legacy CRUD responses unchanged; V17 lifecycle mapping preserved | `StocksCrudUnderD3RegressionTest` |

### AC-02 — Five-stage pipeline, idempotency, failure-retain, sync_task reuse

| Case | Fixture / input | Expected observable result | Evidence type |
|---|---|---|---|
| Happy-path FULL publish | Valid snapshot (3 new + 1 rename + 1 DELISTED) | `stock_basic`/`stock_alias` after == expected content; task `SUCCEEDED`, counts correct; `security_directory_sync_state.lastSnapshotId/lastSuccessAt` updated | `SecurityDirectorySyncServiceHappyPathTest` (full content snapshot) |
| Idempotent re-sync of same snapshot | Same file content read twice (different time/path) | Second sync: 0 inserted/0 updated; no duplicate rows; `selectLatestByScope` returns first SUCCEEDED; no new task row | `SecurityDirectorySyncIdempotencyTest` (depends on A2) |
| Rename writes exactly one FORMER_NAME | Existing name="旧名"; new name="新名" | `stock_basic.name="新名"`; exactly one `FORMER_NAME` alias; re-sync adds no second | `SecurityDirectorySyncRenameFormerNameTest` (depends on A1) |
| Forced late publish failure preserves previous catalog | Inject failure at publish stage | `stock_basic`/`stock_alias` byte-equal to pre-sync; task `FAILED`, error set; sync_state unchanged | `SecurityDirectorySyncPublishFailureRetainsCatalogTest` |
| Empty snapshot rejected, no list_status mutation | Pre-seed DELISTED + LISTED; provider returns 0 candidates | Reject; `MARKET_DATA_EMPTY_RESULT`, `gate=EMPTY_SNAPSHOT`; content + `list_status` unchanged | `SecurityDirectorySyncEmptySnapshotRejectTest` (depends on A3) |
| Row-count swing rejected | previous=100, candidate=50 (swing 0.5 ≥ 0.30) | Reject; `BUSINESS_RULE_VIOLATION`, `gate=ROW_COUNT_SWING` summary; catalog unchanged | `SecurityDirectorySyncRowCountSwingRejectTest` (depends on A3) |
| Swing threshold boundary | previous=100, candidate=71 (0.29 < 0.30) publish; candidate=70 (0.30) reject | Boundary ≥ rejects | `SecurityDirectorySyncRowCountSwingBoundaryTest` |
| First-publish empty-catalog swing bypass | Previous empty, candidate 10 | Publish succeeds | covered by boundary test class |
| Uniqueness gate | Duplicate `canonical_symbol`/alias identity post-normalize | Reject; `DAILY_BAR_VALIDATION_ERROR`, `gate=UNIQUENESS`; catalog unchanged | `SecurityDirectorySyncUniquenessGateTest` |
| Required-field gate | Missing `exchange`/`currency`/`source_updated_at` | Reject; `DAILY_BAR_VALIDATION_ERROR`, `gate=REQUIRED_FIELD` sample identifies row; catalog unchanged | `SecurityDirectorySyncRequiredFieldGateTest` |
| mode differentiation | FULL then INCREMENTAL same snapshot | Two distinct scope identities → two tasks both execute | `SecurityDirectorySyncModeIsolationTest` (depends on A2) |
| Removal semantics (parent-resolved) | FULL snapshot drops a previously-published canonical_symbol | Per parent decision Q-1: absent symbol is left untouched (not deleted, not auto-DELISTED); only rename/add/list_status-explicit changes publish | `SecurityDirectorySyncRemovalSemanticsTest` |

### AC-03 — sync trigger / task detail / status API contract

| Case | Fixture / input | Expected observable result | Evidence type |
|---|---|---|---|
| POST /sync success returns task VO | Valid snapshot, `mode=FULL` | HTTP 200; data VO `{id, taskType=SECURITY_MASTER_SYNC, provider=CSV_SNAPSHOT_DIR, status, scopeJson, idempotencyKey, counts, startedAt, finishedAt, parentTaskId}`; no `path|token|secret|key|credential|password` field, no echoed CSV path | `SecurityDirectorySyncControllerTest` (MockMvc) + secret-leak regex |
| Duplicate trigger returns existing task | Same `snapshotHash` while first PENDING/RUNNING/SUCCEEDED | Returns existing task (same `id`); no new task row | `SecurityDirectorySyncControllerIdempotencyTest` (depends on A2) |
| Provider disabled | `enabled=false`, POST /sync | HTTP 400 + `BUSINESS_RULE_VIOLATION`; no task row; no credential | `SecurityDirectorySyncControllerDisabledProviderTest` |
| GET /sync/tasks/{id} hit | Existing task id | HTTP 200, full VO incl. `lastErrorCode`/`errorSummaryJson` for FAILED | covered by controller test |
| GET /sync/tasks/{id} miss | Non-existent id | HTTP 404 + failure envelope | `SecurityDirectorySyncControllerTaskNotFoundTest` |
| GET /status fields | Empty/ready/fresh/stale catalog | VO `{providerEnabled, providerConfigured, lastSuccessAt, lastSnapshotId, catalogStatus, catalogUpdatedAt, stale, degraded}`; catalog fields follow D1 heuristic; sync fields from state table; no secret/path | `SecurityDirectoryStatusControllerTest` |
| Status distinguishes enabled/configured | enabled=false → `providerEnabled=false`; enabled=true missing path → `providerEnabled=true, providerConfigured=false` | Matrix assertion | covered by status test |
| No-secret-leak across all three endpoints | Path containing token-like substring | Negative regex assertions on serialized JSON | `SecurityDirectoryNoSecretLeakTest` |

### AC-04 — Concurrency de-dup, explainable failure, recoverable retry

| Case | Fixture / input | Expected observable result | Evidence type |
|---|---|---|---|
| Concurrent same-scope POST /sync | Two threads same snapshot | Exactly one task terminal; other returns same id or short-circuits on lock; no duplicate writes | `SecurityDirectorySyncConcurrencyTest` (CountDownLatch + executor) |
| FAILED → retry parent_task_id chain | First FAILED (forced publish failure); retry same snapshot | New task `parentTaskId`=first id; `idempotencyKey` differs (timestamp); retry can succeed | `SecurityDirectorySyncRetryChainTest` |
| Stable lastErrorCode/errorSummaryJson | Each gate failure (A3 cases) | Deterministic/stable across repeated failures | covered by AC-02 gate tests + `SecurityDirectorySyncErrorStabilityTest` |
| Lock release on failure | Sync fails after acquiring lock | Subsequent sync acquires lock; no orphan PENDING/RUNNING | covered by concurrency test |

### AC-05 — Scheduler default-off, configurable, no-fire when off, fires on cron when on

| Case | Fixture / input | Expected observable result | Evidence type |
|---|---|---|---|
| Default-off: scheduler bean absent | Default config | `SecurityDirectorySyncScheduler` bean NOT in context; no `@Scheduled` registration (parent-resolved: option a) | `SecurityDirectorySyncSchedulerDefaultOffContextTest` |
| Explicit off: bean absent | `scheduler.enabled=false` | Bean absent; D1 unaffected | covered by same context test |
| Enabled: cron fires via test seam | `scheduler.enabled=true`, fixed clock, valid snapshot | Direct invocation of `triggerDailySync(LocalDateTime)` / `triggerWeeklyReconciliation(LocalDateTime)` creates `SECURITY_MASTER_SYNC` task (INCREMENTAL/FULL) | `SecurityDirectorySyncSchedulerTest` (mirrors `MarketDataIntradaySchedulerTest`) |
| Enabled but outside cron window | Fixed clock not at cron instant | Test seam is a no-op; no task created | covered by scheduler test |
| Enabled but provider disabled | scheduler on, security-directory off | Scheduled invocation explainable skip (BUSINESS_RULE_VIOLATION task or skipped); no catalog mutation | `SecurityDirectorySyncSchedulerProviderDisabledTest` |
| Property binding | Custom cron/threshold/path/enabled | All `@ConfigurationProperties` bind; defaults match constants | `SecurityDirectoryPropertiesBindingTest` |

### AC-06 — Frozen candidate passes backend + static gates

| Case | Fixture / input | Expected observable result | Evidence type |
|---|---|---|---|
| Static: forbidden paths | Frozen diff | No V1-V17 edits, no `security_master`, no edits outside allowed paths, no secrets | forbidden-path scan |
| Static: secret scan | Frozen diff | No token/secret/key literals added | secret scan |
| Static: architecture check | Frozen diff | `check-ai-architecture.mjs` exit 0; constants centralized | arch script |
| Static: `git diff --check` | Frozen diff | Exit 0 | `git diff --check` |
| Automation: focused D3 tests | Disposable worktree | All new test classes pass | focused selection exit 0 |
| Automation: full suite | Disposable worktree | `./mvnw test` exit 0; `./mvnw package` exit 0 | exit codes + log |
| Candidate identity immutability | Before/after commit+tree | tree hash matches; no post-freeze mutation | hash comparison |

---

## 4. Environment / Fixture Requirements

- **H2 test profile** is the mandatory automation DB. All AC-02 failure-retain assertions use `@SpringBootTest @ActiveProfiles("test")` with before/after table snapshots via `StockBasicMapper`/`StockAliasMapper`.
- **D1 frozen CSV fixtures** reused verbatim (locate existing D1 fixture directory used by `SecurityDirectoryIntegrationTest`).
- **New D3 fixtures**: `src/test/resources/security-directory-d3/` — `snapshot_valid_full.csv`, `snapshot_duplicate.csv`, `snapshot_empty.csv`, `snapshot_swing.csv`, `snapshot_missing_required.csv`, `snapshot_duplicate_symbol.csv`.
- **Fixed clock**: reuse `marketDataClock` override `Clock.fixed(Instant, ZoneId.of("Asia/Shanghai"))`.
- **Mockito + MockMvc**: existing stack.
- **Concurrency test**: `CountDownLatch` + executor on H2 (existing `SyncScopeLockMapperTest` proves the pattern).
- **RUNTIME (conditional)**: disposable MySQL 8.4 only if Docker safely available; `NOT_VERIFIED` otherwise.
- **DEPLOYMENT**: always `NOT_VERIFIED` for D3.

---

## 5. Recommendations (non-blocking)

- **R-1** (ties to A3 / AC-03): Pin HTTP status for provider-disabled `POST /sync` to **400 + BUSINESS_RULE_VIOLATION** (existing LongPort-disabled convention). Remove the "409/400 中按现有约定选其一" ambiguity.
- **R-2** (ties to AC-05): Reconcile AC-05 contradiction. Adopt option (a): default-off → bean absent (matches `havingValue=true` without `matchIfMissing`); add directly-invocable `triggerDailySync(LocalDateTime)` / `triggerWeeklyReconciliation(LocalDateTime)` test seam mirroring `MarketDataIntradayScheduler.scanAt`.
- **R-3**: Pin `SecurityDirectorySyncTaskVO` and `SecurityDirectoryStatusVO` field names exactly.
- **R-4**: State that V18 `security_directory_sync_state` **is** in scope (not "if needed") — status API `lastSuccessAt`/`lastSnapshotId` require it.
- **R-5**: Add explicit non-regression assertion that D1 `catalogStatus` heuristic is unchanged by D3 when no successful sync has run.

---

## 6. Parent Resolution Notes (applied at freeze)

- **A1, A2, A3**: ACCEPTED. Applied to contract FactsAndDecisions verbatim (exact replacement text above).
- **R-1, R-2, R-3, R-4, R-5**: ACCEPTED. R-1 → provider-disabled HTTP 400 + `BUSINESS_RULE_VIOLATION`; R-2 → option (a) bean-absent-when-off + test seams; R-3 → VO field names pinned in contract; R-4 → V18 state table in scope; R-5 → D1 catalogStatus non-regression asserted.
- **Q-1 (removal semantics) — parent-resolved**: D1 is purely additive upsert with no physical deletion (ADR-0009: "退市证券不物理删除"). D3 FULL weekly reconciliation therefore does NOT auto-delete or auto-DELIST a canonical_symbol merely absent from a later snapshot; an absent symbol is left untouched. Delisting flows only from an explicit `list_status=DELISTED` value in a snapshot, and even then a single absence never forces DELISTED (single absence → leave untouched; explicit DELISTED value in snapshot → publish that lifecycle). This satisfies contract §"单次目录缺失不得直接判定退市" and stays compatible with D1's additive model. The removal-semantics test asserts: FULL snapshot omitting a previously-published symbol leaves that `stock_basic` row byte-equal (no deletion, no `list_status` change).
