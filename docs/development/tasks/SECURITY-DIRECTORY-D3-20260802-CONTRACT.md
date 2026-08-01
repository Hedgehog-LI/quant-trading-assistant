# Task Contract: SECURITY-DIRECTORY-D3-20260802 证券目录同步基础

## Contract Identity

- Status: `FROZEN`
- Contract version: `1.0`
- Lane: `L2`
- Task branch: `codex/security-directory-d3-20260802`

冻结后由父协调者计算本文件 SHA-256，并将其记录到任务状态、CONTROL.json 和后续 TaskPacket；本文件不记录自身哈希。

## Amendment History

- `0.1 → 1.0` (frozen 2026-08-02): test-design amendments A1 (CSV parser reuse boundary), A2 (snapshot
  content identity), A3 (quality-gate thresholds/error codes/FAILED partition) and recommendations R-1..R-5
  accepted; open question Q-1 (removal semantics) resolved by the parent (absent symbol left untouched).
  See `SECURITY-DIRECTORY-D3-20260802-TEST-DESIGN.md` §6.

## Objective

完成 P1.4b-D3 证券目录同步基础（仅后端）：定义 `SecurityDirectoryProvider` 与 CSV 快照 Provider，
在解析、校验、staging/diff、质量门禁和原子发布的基础上安全、幂等、可恢复地更新本地证券目录；
复用 `market_data_sync_task` 的 `SECURITY_MASTER_SYNC` 记录执行过程；提供同步触发、任务详情和目录同步
状态 API；提供配置化的每日增量、每周全量对账调度（默认安全关闭）。Provider disabled、文件缺失或内容非法
时应用仍可启动，已有 D1 本地搜索/详情/导入及 `/stocks` CRUD 保持兼容。本轮不接真实外部网络、不碰交易/账户/订单。

## Authority

- Product/design:
  - `docs/features/SECURITY_DIRECTORY_SEARCH_DESIGN.md`（§6 Provider 与同步设计、§7 API 状态、§9 验收）
  - `docs/decisions/ADR-0009-local-first-security-directory.md`
  - `docs/development/SECURITY_DIRECTORY_SEARCH_IMPLEMENTATION_PLAN.md`（D3 段）
- API/data contract:
  - `docs/api/MARKET_DATA_API.md`（D1 已实现接口；D3 新增 sync/status）
  - `docs/DATABASE_DESIGN.md`（`stock_basic`、`stock_alias`、`market_data_sync_task`）
  - `docs/CURRENT_ARCHITECTURE_AND_MODULES.md`
  - `docs/DEVELOPMENT_WORKFLOW.md`
- Reference baseline (D1 finalized):
  - D1 contract `docs/development/tasks/SECURITY-DIRECTORY-D1-20260729-CONTRACT.md`（冻结口径、alias 规范、CSV 解析与质量约束）
  - D1 verification `docs/development/tasks/SECURITY-DIRECTORY-D1-20260729-VERIFICATION.md`
- Baseline commit: `8e4447ed86aa2cf53c0c52388a1b2d592ceb70e0`
- Baseline branch: `codex/security-directory-d1-20260729`（D1 finalized）
- Pre-existing dirty paths: none (`git status --short` empty at branch creation)
- Task branch: `codex/security-directory-d3-20260802`
- Allowed write paths:
  - `src/main/java/com/quant/trade/marketdata/**`（新增 provider 目录子包、sync service、scheduler、controller、config、constants、enums、VO/DTO、converter、manager、exception、mapper interface）
  - `src/main/java/com/quant/trade/marketdata/config/SecurityDirectoryProperties.java`（新增配置属性）
  - `src/main/resources/mapper/SecurityDirectorySyncStateMapper.xml`（新增，如确需状态表）
  - `src/main/resources/db/migration/V18__*.sql`（仅当新增状态表确有必要）
  - `src/main/resources/application.properties`（新增 `qta.market-data.security-directory.*` 默认配置，默认安全关闭）
  - `src/test/java/com/quant/trade/marketdata/**`
  - `src/test/resources/**` only for focused D3 fixtures
  - `docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-*.md`
  - after independent acceptance only: the project delivery documents explicitly required by
    `docs/DEVELOPMENT_WORKFLOW.md §2`

## Facts And Decisions

| Type | Item |
|---|---|
| FACT | D1 已 finalized：V17 扩展 `stock_basic`、新增 `stock_alias`；`SecurityDirectoryService.importCsv/search/detail`、`SecurityDirectoryController`（import/search/detail）、`StockBasicMapper`/`StockAliasMapper` 已冻结。 |
| FACT | `market_data_sync_task(task_type VARCHAR(32), provider VARCHAR(16), status, idempotency_key, scope_json, ...)` 已由 V7/V8/V9 实现；`task_type` 文本值无 DB 枚举约束，`SECURITY_MASTER_SYNC` 文档预留。`provider` 列长 16，D3 provider code 必须不超过 16 字符。 |
| FACT | `SyncScopeLockMapper.upsert/selectForUpdate(provider, taskType, scopeHash)` + `MarketDataSyncTaskMapper.selectLatestByScope/insert/updateById` + `TransactionTemplate("txRequiresNew")` 已是项目复用的并发与留痕原语。 |
| FACT | `MarketDataConfig` 已有 `@EnableScheduling`、`marketDataClock`（Asia/Shanghai）、`txRequiresNew`、`@ConditionalOnProperty` provider 装配模式；scheduler 用 `@Scheduled(fixedDelayString=...)` + `@ConditionalOnProperty(prefix="qta.market-data.scheduler", name="enabled", matchIfMissing=true)`。 |
| FACT | `ErrorCodeEnum` 含 `BUSINESS_RULE_VIOLATION/INVALID_CANONICAL_SYMBOL/MARKET_DATA_EMPTY_RESULT/INTERNAL_ERROR` 等；D1 用 `SecurityDirectoryImportException`/`SecurityDirectoryNotFoundException` 携带 `ApiResponse` 失败封包。 |
| FACT | D1 `catalogStatus` 由本地 `MAX(source_updated_at)` + 48h 启发式推导，文档明确「D3 可替换这一临时新鲜度口径」。 |
| DECISION | 新建 `SecurityDirectoryProvider` 接口，职责只限目录快照/增量拉取与标准化为 `StockBasicDO`/`StockAliasDO`；不复用报价/K 线 `MarketDataProvider`。 |
| DECISION | CsvSnapshotSecurityDirectoryProvider 必须通过一个 D1 与 D3 共享的、可独立测试的解析组件复用 D1 冻结口径。实现路径二选一，且必须在 AC-01 evidence 中显式声明所选路径并在 diff 中可验证：(P1) D1 重构出 public `DirectoryCsvParser`（输入 byte[]/InputStream → 输出已校验的 `ParsedDirectoryBatch`，含 stock/alias 候选与 duplicate-unchanged 计数），D1 `importCsv` 改为调用它；该重构计入 D3 允许写路径并需在 D1 兼容集成测试中证明 D1 行为字节级不变。(P2) 若不动 D1，D3 必须提取一个 `SecurityDirectoryCsvParser`，并对 D1 冻结 fixtures（含 BOM/引号/同义重复/CONFLICTING_DUPLICATE/CONFLICTING_ALIAS_METADATA/改名 FORMER_NAME/枚举/RFC-3339/ISO date 全部 reasonCode）执行参数化等价测试，证明与 D1 `importCsv` 在同一输入下产出相同的 stock/alias 候选集合与失败 reasonCode。staging/diff 阶段对 "updated" 候选必须调用与 D1 `persist` 完全相同的 former-name 插入路径（旧非空 name → 恰好一条 FORMER_NAME）与 alias-identity 冲突检测（`aliasMetadataByIdentity` 等价语义），不得在 diff 层重新实现。 |
| DECISION | 同步流程必须串接五个阶段：解析(parse) → 校验(validate) → staging/diff（按 canonical_symbol 计算 inserted/updated/unchanged/removed 候选）→ 质量门禁（数量波动阈值、必填字段、唯一性、非空快照）→ 原子发布（单事务 upsert stock_basic/stock_alias）。任一阶段失败整批回滚，保留上一成功目录。 |
| DECISION | 幂等以「内容身份」为准，不以读次数为准。snapshotHash = 对规范化后快照内容（D1 `sameDirectoryData` 等价字段集合 + 排序后的 alias identity 集合）的稳定 SHA-256；snapshotId 必须由 snapshotHash 派生或等于 snapshotHash 的可读前缀，不得引入读次数/时间戳/文件路径。idempotency_key = task_type + provider + snapshotHash + mode（与 scope_hash 同源）；scope_json = {provider, snapshotId(=snapshotHash 派生), snapshotHash, mode}。`selectLatestByScope` 的匹配键在文档中明确为 (provider, task_type, snapshotHash, mode) 三元组等价比较，而非逐字段 scope_json 字符串比对。同一文件重复读取、或同内容不同路径的两个文件，均映射到同一 idempotency_key 并触发 unchanged 幂等短路。retry 任务的 idempotency_key 追加时间戳保证唯一（沿用现有 daily-bars retry 语义）。 |
| DECISION | 失败可恢复：同步失败只更新 task 状态为 `FAILED`（D3 不使用 PARTIAL_FAILED）并写错误摘要，不清空 `stock_basic`/`stock_alias`；下次成功快照才更新目录。`catalogStatus` 仍由 D1 启发式决定，新增的目录同步状态独立轻量表 `security_directory_sync_state`（V18，按 provider 维护最近成功时间/快照标识/计数/错误），不回写 `stock_basic`。 |
| DECISION | 复用 `market_data_sync_task`：task_type=`SECURITY_MASTER_SYNC`，provider=`CSV_SNAPSHOT_DIR`（≤16 字符），scope_json=`{provider, snapshotId, snapshotHash, mode(FULL/INCREMENTAL)}`；用 `SyncScopeLockMapper` 行锁防同范围并发 sibling，`selectLatestByScope` 做 PENDING/RUNNING/SUCCEEDED 幂等短路；保留 D1 既有 `parent_task_id` retry 链语义。 |
| DECISION | 新增 API（沿用 `ApiResponse<T>`）：`POST /api/v1/market-data/security-directory/sync`（手动触发，body 可选 `{mode}`，默认 `FULL`，返回 task VO）；`GET /api/v1/market-data/security-directory/sync/tasks/{taskId}`（任务详情，404 if absent）；`GET /api/v1/market-data/security-directory/status`（目录同步状态：provider enabled/configured、最近成功时间/快照、catalogStatus 沿用 D1 口径）。`POST /sync` 不得返回 provider 凭据。 |
| DECISION | 并发与可解释失败：同 scope 已有 PENDING/RUNNING/SUCCEEDED 时，`POST /sync` 返回既有 task（不重复执行）；FAILED 时允许 retry 并建立 parent_task_id。provider disabled 时 `POST /sync` 返回 HTTP 400 + `BUSINESS_RULE_VIOLATION`（沿用现有 LongPort-disabled 约定，无 409/400 歧义），状态 API 标注 `providerEnabled=false`。 |
| DECISION | 调度：新增 `SecurityDirectorySyncScheduler`，`@ConditionalOnProperty(prefix="qta.market-data.security-directory.scheduler", name="enabled", havingValue="true")`（默认关闭，非 matchIfMissing）。两个 `@Scheduled`：每日增量（cron，默认 `02 30 6 * * *` Asia/Shanghai）与每周全量对账（cron，默认 `30 4 * * MON`）。cron/delay、阈值、enabled、CSV 路径、provider enabled 全部经 `SecurityDirectoryProperties` 配置；不可配项保持常量集中。 |
| DECISION | 安全默认：`qta.market-data.security-directory.enabled` 默认 `false`；`scheduler.enabled` 默认 `false`；provider disabled / CSV 路径缺失 / 文件非法时，Bean 不阻塞应用启动（disabled provider 返回明确状态），D1 搜索/详情/导入和 `/stocks` CRUD 完全不受影响。 |
| DECISION | 单次目录缺失不得直接判定退市：质量门禁的「数量波动阈值」失败或快照为空即拒绝发布（`MARKET_DATA_EMPTY_RESULT`），不修改任何 `list_status`；退市需后续独立批次与人工确认（不在 D3 自动化）。 |
| DECISION | 质量门禁阈值与错误码（默认值，可在 `SecurityDirectoryProperties` 配置，测试使用默认值）：(1) 数量波动阈值：候选发布集行数相对上一成功目录的相对偏差 `abs(new-old)/max(old,1)` 默认 `≥ 0.30` 即拒绝；上一成功目录为空（首次）时阈值不生效（不拒绝）。失败 `lastErrorCode=BUSINESS_RULE_VIOLATION`，`errorSummaryJson={"gate":"ROW_COUNT_SWING","threshold":0.30,"previousCount":N,"candidateCount":M}`。(2) 必填字段缺失：任一候选缺失 D1 REQUIRED 字段 → 拒绝，`lastErrorCode=DAILY_BAR_VALIDATION_ERROR`，`summary={"gate":"REQUIRED_FIELD","sample":<首条缺失行 line/canonical_symbol/field>}`。(3) 唯一性：候选集内 `canonical_symbol` 重复或 alias identity 重复 → 拒绝，`lastErrorCode=DAILY_BAR_VALIDATION_ERROR`，`summary={"gate":"UNIQUENESS","conflicts":[...]}`。(4) 非空快照：候选发布集为空（解析后 0 行，或全为 removed）→ 拒绝，`lastErrorCode=MARKET_DATA_EMPTY_RESULT`，`summary={"gate":"EMPTY_SNAPSHOT"}`。任一门禁失败：整批不发布，`task.status=FAILED`（D3 不使用 PARTIAL_FAILED，因为五阶段任一失败整批回滚，无部分发布），`stock_basic`/`stock_alias` 内容与失败前逐行字节等价（test 以 before/after 全表内容快照断言，非 count-only），且不修改任何 `list_status` 列（test 显式断言失败前 DELISTED/LISTED/UNKNOWN 行的 `list_status` 在失败后未变）。 |
| DECISION | removal 语义（Q-1 已由父协调者裁定）：D1 是纯增量 upsert 且 ADR-0009 明确「退市证券不物理删除」。因此 D3 FULL 每周对账**不**因某 canonical_symbol 在后续快照中缺失就删除或自动 DELISTED；缺失符号保持原样（逐行字节等价，无 `list_status` 变化）。退市只来自快照中显式 `list_status=DELISTED` 值，且单次缺失绝不强制 DELISTED。 |
| DECISION | API HTTP 语义：provider disabled 时 `POST /sync` 返回 HTTP 400 + `ApiResponse.fail(BUSINESS_RULE_VIOLATION)`（沿用现有 LongPort-disabled 约定），且不创建 task、不回显凭据/路径。`POST /sync` 成功返回 `SecurityDirectorySyncTaskVO{id, taskType=SECURITY_MASTER_SYNC, provider=CSV_SNAPSHOT_DIR, status, scopeJson, idempotencyKey, counts, startedAt, finishedAt, parentTaskId}`；`GET /status` 返回 `SecurityDirectoryStatusVO{providerEnabled, providerConfigured, lastSuccessAt, lastSnapshotId, catalogStatus, catalogUpdatedAt, stale, degraded}`，`catalogStatus/catalogUpdatedAt/stale/degraded` 沿用 D1 启发式，`lastSuccessAt/lastSnapshotId` 来自 V18 `security_directory_sync_state`。三个端点的响应体均不得出现 `path|token|secret|key|credential|password` 字段或回显 CSV 路径。 |
| DECISION | 调度（R-2 已采纳 option a）：`SecurityDirectorySyncScheduler` 用 `@ConditionalOnProperty(prefix="qta.market-data.security-directory.scheduler", name="enabled", havingValue="true")`（**不带** matchIfMissing），默认关闭时 bean 不装配；并提供可直接调用的测试 seam `triggerDailySync(LocalDateTime)` / `triggerWeeklyReconciliation(LocalDateTime)`（镜像 `MarketDataIntradayScheduler.scanAt`），分别生成 `mode=INCREMENTAL` / `mode=FULL` 的 `SECURITY_MASTER_SYNC` task。 |
| DECISION | V18 `security_directory_sync_state` 表在本轮范围内（非「按需」）：按 provider 维护最近成功时间、最近快照标识、计数摘要与最近错误；不回写 `stock_basic`。`GET /status` 的 `lastSuccessAt/lastSnapshotId` 依赖此表。 |
| DECISION | D1 `catalogStatus` 非回归：D3 未发生任何成功同步时，`catalogStatus/catalogUpdatedAt/stale/degraded` 必须与 D1 启发式逐字段等价（EMPTY/READY + MAX(source_updated_at) + 48h）；D3 仅在其之上叠加同步状态字段。 |
| DECISION | 不新建第二套证券主数据；不接 LongPort/商业源；不下载全市场外部目录；不实现 LongPort Metadata Enricher；调度默认关闭且不做真实外联。 |
| DECISION | 不修改历史 migration；如确需状态表，使用 V18；不修改 V1-V17。 |
| ASSUMPTION | 本地 CSV 快照路径由运维手动放置；provider 仅读取本地文件，不做网络下载。 |
| ASSUMPTION | 「每周全量对账」在 D3 体现为调度入口 + FULL mode 复用同一同步管线；对账差异报告以 task 计数与 sync_state 表达，不单独建账本。 |
| ASSUMPTION | D3 不改变 D1 搜索/详情/catalogStatus 启发式；status API 在 catalogStatus 之上额外暴露同步状态字段。 |
| OPEN_QUESTION | None blocking. Disposable MySQL runtime remains conditional on safe Docker availability and is reported separately from H2 automation. |

## Scope

### In Scope

- `SecurityDirectoryProvider` 接口 + `DisabledSecurityDirectoryProvider` 兜底 + `CsvSnapshotSecurityDirectoryProvider`（默认可审计）。
- `SecurityDirectorySyncService`：解析→校验→staging/diff→质量门禁→原子发布；幂等、失败保留旧目录、复用 `market_data_sync_task`/`SyncScopeLockMapper`/`txRequiresNew`。
- `SecurityDirectorySyncScheduler`：每日增量 + 每周全量对账，默认安全关闭，全部配置化。
- `SecurityDirectoryProperties`：enabled、provider、scheduler.enabled/cron、CSV 路径、数量波动阈值等。
- 同步触发/任务详情/目录同步状态 REST API + VO/DTO + Controller（沿用 `ApiResponse`/`ErrorCodeEnum`）。
- `security_directory_sync_state` 状态表（V18，如确需）+ Mapper/XML；不回写 `stock_basic`。
- 常量集中（`SecurityDirectoryConstants`：`SECURITY_MASTER_SYNC`、`CSV_SNAPSHOT_DIR`、mode、cron 默认、阈值默认）。
- D1 兼容性测试：现有目录导入/搜索/详情/`/stocks` CRUD 在 D3 启用/禁用 provider 下均不退化。
- D3 单元/集成测试：解析复用、幂等、失败保留旧目录、并发防重、质量门禁拒绝、调度默认关闭、provider disabled 可启动。
- 项目级 API/DB/architecture/development/acceptance/handoff/build 文档仅在 verifier 许可交付后同步。

### Out Of Scope

- D2 前端 `SecuritySelector`、前端仓库、mock adapter、页面接入。
- D4 跨模块推广（自选股、交易计划、交易记录、风控、持仓快照表单）。
- LongPort Security List / Static Info Metadata Enricher、Tushare 或任何新商业/全市场外部目录源。
- 真实外部网络调用、真实凭据、服务器部署。
- Elasticsearch 或 ngram/fulltext。
- 改变 D1 搜索/详情/catalogStatus 冻结口径。
- 真实行情/交易/订单/账户/持仓接口。

### Prohibited

- 自动交易、券商账户、订单、成交、真实持仓、密钥读取。
- 第二套证券主数据（`security_master` 或等价表）。
- 修改 V1-V17 migration 或回填/删除既有用户数据。
- 搜索/详情/导入或 `/stocks` CRUD 行为退化或不兼容。
- push、rebase、force-push、reset、远程变更；提交 `.env`/`runtime-libs`/`node_modules`/密钥。
- 最终验收前 finalization。

## Acceptance Criteria

| AC-ID | Observable behavior | Preconditions/input | Expected result | Evidence | Dimension | Owner | Status |
|---|---|---|---|---|---|---|---|
| AC-01 | Provider 抽象、CSV 快照 provider 与 disabled 兜底，且 D1 完全兼容 | D1 fixtures + D3 enabled/disabled 两种启动；调用既有 import/search/detail/`/stocks` CRUD | disabled/缺文件/非法内容时应用正常启动；CSV 快照 provider 复用 D1 解析规则把快照标准化为 stock/alias；`/stocks` CRUD 与 D1 搜索/详情/导入响应不退化 | provider unit tests、D1 兼容集成测试、Spring 上下文加载测试 | AUTOMATION | final verifier | NOT_STARTED |
| AC-02 | 同步流程五阶段、幂等、失败保留旧目录、复用 sync_task | 有效快照、重复同一快照、改名/退市样本、强制发布失败、质量门禁失败（空快照/数量波动/唯一性/必填） | 同一快照重复同步结果幂等（无重复证券/别名，记为 unchanged 幂等成功）；改名写入 FORMER_NAME；任一阶段或发布失败整批回滚，保留上一成功目录，task 记 `FAILED`+错误摘要，`stock_basic`/`stock_alias` 内容等同失败前 | before/after stock/alias 快照、task VO 断言、强制晚期失败测试、质量门禁拒绝测试 | AUTOMATION | final verifier | NOT_STARTED |
| AC-03 | 同步触发/任务详情/状态 API 契约稳定 | 有效/无效/重复触发、provider disabled、存在/不存在 taskId、空与有目录 | `POST /sync` 成功返回 task VO，重复触发返回既有 PENDING/RUNNING/SUCCEEDED task 不重复执行，disabled 返回 HTTP 400 + `BUSINESS_RULE_VIOLATION` 且不返回凭据；`GET /sync/tasks/{id}` 命中返回详情、缺失返回 404；`GET /status` 区分 provider enabled/configured、最近成功快照/时间与 catalogStatus，不泄露凭据 | API JSON 断言、disabled 状态断言、404 与不含密钥断言 | AUTOMATION | final verifier | NOT_STARTED |
| AC-04 | 并发防重、可解释失败与可恢复 retry | 同 scope 并发 `POST /sync`；FAILED 后 retry | 行锁保证同 scope 同一时刻只有一个非终态 task；retry 建立 parent_task_id 并产生新 task；失败 task 含稳定 lastErrorCode/errorSummaryJson | 并发/幂等交互测试、retry 链断言、错误摘要断言 | AUTOMATION | code reviewer + final verifier | NOT_STARTED |
| AC-05 | 调度默认安全关闭、配置化、默认关闭下不装配、启用时按 cron 触发 | scheduler.enabled 未设置/显式 false 与=true；固定 clock/cron | 默认关闭时 `SecurityDirectorySyncScheduler` bean 不装配；启用时通过测试 seam `triggerDailySync/triggerWeeklyReconciliation` 按配置生成 SECURITY_MASTER_SYNC task（INCREMENTAL/FULL）；provider disabled 时调度可解释跳过；cron/阈值/路径/enabled 全部可配置 | scheduler bean 条件测试、固定 clock 触发/不触发测试、属性绑定测试 | AUTOMATION | final verifier | NOT_STARTED |
| AC-06 | 冻结候选通过后端与静态门禁 | review-clear commit in disposable worktree | focused tests、`./mvnw test`、`./mvnw package`、`node scripts/check-ai-architecture.mjs`、`git diff --check`、forbidden-path/secret scan 全过且候选身份不变 | 精确 exit code/log、前后 commit/tree、static scan | STATIC/AUTOMATION | code reviewer + final verifier | NOT_STARTED |

## Verification Plan

| Dimension | Required | Command or inspection | Pass rule |
|---|---|---|---|
| STATIC | Yes | Frozen diff/contract inspection; `git diff --check`; forbidden-path/secret/runtime-artifact scan; mapper/migration/API compatibility review; `node scripts/check-ai-architecture.mjs` | No actionable finding; no prohibited path or historical migration change; layered responsibility clear; no scattered magic strings |
| AUTOMATION | Yes | Focused D3 tests, then `./mvnw test` and `./mvnw package` in disposable worktree | All required commands exit 0; every AC has independent evidence |
| RUNTIME | Conditional | If Docker is safely available, start isolated disposable MySQL 8.4 + packaged app, run minimal sync/idempotency/failure-retain/status curl without external provider | Fresh MySQL migration and representative HTTP semantics succeed; otherwise `NOT_VERIFIED` with reason, never inferred from H2 |
| DEPLOYMENT | No for D3 | No remote deployment or existing-volume mutation authorized | Always report `NOT_VERIFIED`; no H2/Docker result may be called deployed |

## Role Assignments

- Test designer: `qta-test-designer`, clean TaskPacket, AC-01..AC-06, no writes or commands.
- Implementer: `qta-implementer` using `qta-backend-implementation`, AC-01..AC-06 implementation and self-check only.
- Code reviewer: `qta-code-reviewer`, read-only review of the frozen candidate, AC-01..AC-06.
- Final verifier: `qta-final-verifier` using `qta-independent-verification`, disposable worktree, AC-01..AC-06.
- Omitted roles and justification: none.

## Candidate And Git Policy

- Git automation: `COMMIT`
- User authorization evidence: explicit request in the initiating task for local contract/candidate/repair-N/finalization commits.
- Task branch: `codex/security-directory-d3-20260802`
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
