# ADR-0015: A 股历史数据底座正式数据模型与 Provider 边界

- 状态：Proposed（随任务 QTA-V2-DATA-FOUNDATION-V21 实施，候选状态）
- 日期：2026-08-16
- 关联：ADR-0014、`MARKET_RESEARCH_MR0_PROVIDER_MATRIX.md`（冻结探针证据）、`QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-REPORT.md` §MR-1 输入边界、任务契约 `docs/development/tasks/QTA-V2-DATA-FOUNDATION-V21-CONTRACT.md`

## 背景

MR-0 冻结了指标字典与 Provider 能力矩阵，MR-1 交付了样本级市场全景；MR-2（资金与轮动）需要全 A、可追溯、可回补的历史数据底座。现有 `stock_daily_bar`/`stock_basic`/`market_calendar` 与采集引擎可复用，但缺少数据集/版本/发布、PIT 行业成分、覆盖水位、回补任务与质量门禁的正式模型。MR-0 `mr0_*` 为 PoC 表，不得被正式能力直接依赖。

## 决策

### 1. Provider 与数据源边界（每项含状态，全部可追溯）

| 数据 | 来源 | 授权 | 状态 | 口径/单位 |
| --- | --- | --- | --- | --- |
| 全 A 证券池快照 | SINA_PUBLIC `getHQNodeData node=hs_a` | 无凭据公共端点 | EXPERIMENTAL（VERIFIED 探针 2026-08-15；非官方、无 SLA，授权边界未声明） | 市值源万元→入库元；换手源 %→小数 |
| 历史日 K | TENCENT_PUBLIC `newfqkline/get` | 无凭据公共端点 | EXPERIMENTAL（同上） | 价格元、量手→股、额万元→元；**仅 NONE 复权** |
| 历史日 K（兜底） | CSV/规范化快照导入 | 本地文件、可审计 | IMPORT（无外联） | schema 冻结为元/股/元，`data_source=IMPORT_CSV_*` |
| 交易日历 | CSV 导入；无日历时由基准指数日 K 推导（INDEX_KLINE_DERIVED，显式假设） | 本地 | IMPORT / DERIVED | `market_calendar(market_code, trade_date)`，复用 V10 表 |
| 行业分类体系 | SINA_INDUSTRY（新浪互斥行业） | 无凭据 | EXPERIMENTAL | **非申万行业，禁止混称/混算**；taxonomy 表登记 |
| PIT 行业成分 | CSV 导入（含 effective_from/effective_to）；公共源仅当前成分 | 本地 | IMPORT（PIT）；公共源当前成分=显式时点假设，质量族标记 CURRENT_MEMBERSHIP_FOR_HISTORY | 半开区间 `[from, to)`，`to` NULL=至今 |
| 行业/个股官方资金流 | TUSHARE（宣称） | 需 token，仓库无凭据 | **BLOCKED / NOT_VERIFIED** | 凭据就绪前不建正式表；`mr0_stock_money_flow_daily` 仅 PoC 事实 |
| 分钟 K / 实时快照 | 既有引擎（LongPort 等） | 既有 | 不变 | 本 ADR 不改变 |

**红线（继承 MR-1-BND-C）**：不把相对收益/涨跌幅×成交额等价量指标包装成资金流；不把 SINA_INDUSTRY 冒充申万；不把当前成分回填历史宣称 PIT 正确；跨 Provider 混算必须显式标注。公共网页接口只能标记实验性/降级来源，未获生产证据不得宣称生产稳定。

### 2. 数据模型（V24 `mdf_*` 表族，全部新增、不复制既有事实）

复用：日 K 事实=`stock_daily_bar`、证券主数据=`stock_basic`、交易日历=`market_calendar`。

新增（详见 V24 migration 与 `DATABASE_DESIGN.md`）：
- `mdf_dataset`（数据集定义：dataset_code 唯一；market/frequency/bar_type/provider_code/adjust_type/口径描述；`current_version_id` 发布指针）
- `mdf_dataset_version`（版本：dataset_id+version_code 唯一；status 状态机 DRAFT→BACKFILLING→QUALIFYING→QUALIFIED/REJECTED→RELEASED/RETIRED；start/end_date；来源与计数摘要）
- `mdf_universe_snapshot`（股票池快照：provider+symbol+as_of 唯一；市值元/换手小数）
- `mdf_industry_taxonomy` + `mdf_industry_membership`（分类体系与 PIT 成分：taxonomy+industry+symbol+effective_from 唯一；半开区间；`to` NULL=至今；重叠由质量检查+导入校验拒绝）
- `mdf_coverage_watermark`（覆盖水位：version+symbol 唯一；first/last_date、row_count、expected/covered_days、coverage_ratio）
- `mdf_backfill_task`（回补任务：dataset_code/market/provider/frequency/adjust/start/end/chunk 大小；status；计划/成功/失败/跳过/写入计数；claim_token 防并发）
- `mdf_backfill_chunk`（分片：task+chunk_index 唯一；symbol 范围+日期窗口；status PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED；attempts/last_error；断点=按 chunk 状态续跑）
- `mdf_import_batch`（导入批次：kind/provider/file_name/file_hash；inserted/updated/skipped/rejected；error_report_json）
- `mdf_quality_result`（质量结果：version+check_code 唯一；status OK/WARN/FAIL；affected_count；detail_json）

原始事实（日 K/日历/证券）与衍生结果（覆盖/质量）边界：事实表可追溯 `data_source`+`fetched_at`；`mdf_*` 结果表可重算、可下线。

### 3. 回补执行与发布

- 回补任务在现有采集引擎机制上实现（短事务 + claim token + `sync_scope_lock` 行锁防并发），不改写既有引擎。
- Provider 调用统一节流（最小间隔）+ 指数退避重试；HTTP 401/403/权限类错误不重试、立即失败留痕。
- 质量门禁（13 族）通过且非空数据才可发布；发布=事务内旧版本 RETIRED + 指针切换；失败版本保留可查但不得成为研究默认版本。

### 4. 后果

- 优点：MR-2 获得可回补、可验证、可追溯的数据底座；无凭据时 CSV 通道保证闭环可验证。
- 风险：公共源结构变化会使 EXPERIMENTAL 回补失败——由质量门禁与任务失败留痕暴露，不静默；正式生产化需另立 ADR（Tushare/其他凭据源）。
- 不做：资金流正式表（BLOCKED）、分钟级回补、自动交易。
