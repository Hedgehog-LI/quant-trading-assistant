# Task Contract: P17-SECTOR-ANALYTICS-DESIGN-20260802 板块相对强弱/轮动持续性/龙头贡献/异动提醒可开发设计

## Contract Identity

- Status: `FROZEN`
- Contract version: 1.1
- Frozen at: 2026-08-02T00:00:00Z
- Frozen by parent run: codex-parent-p17-sector-design-1
- Test-design: `READY_TO_FREEZE`（TD-RUN-1，0 blocking amendments；AMEND-REC-01/02/03 已采纳，见 `P17-SECTOR-ANALYTICS-DESIGN-20260802-TEST-DESIGN.md`）
- Lane: `L0`

After freezing the file, the parent computes its SHA-256 and records it in task state and TaskPackets. The
contract does not contain its own hash.

## Objective

冻结 P1.7 板块分析层的**可开发设计**（不写业务代码）：从产品、量化研究、数据工程和架构四个角度定义板块
相对强弱、轮动持续性、龙头贡献、成交量确认和异动提醒的可解释公式与口径；明确基准、时间窗口、缺失数据、
停牌、跨市场时区、样本不足和失效场景；区分原始事实、衍生指标与提醒事件，禁止把分析结果写回原始行情表；
给出数据模型、MyBatis/Flyway 边界、API、前端页面与图表设计；并把后续实现拆为可并行开发的子任务（每个子
任务列独占写路径、依赖、AC、测试与合并顺序）。第一版不生成买卖指令、不自动交易、不做不可解释模型评分。

本任务只产出**设计文档与任务拆分 artifact**，不修改任何业务代码、`BUILD_CHECKLIST.md`、`AI_HANDOFF.md` 或
总日志，这些统一留给后续集成任务。

## Authority

- Product/design: `docs/features/MARKET_SECTOR_CATALOG_DESIGN.md`（P1.5 目录与关注快照基线）、`docs/features/MARKET_SECTOR_AUTOMATIC_COLLECTION_DESIGN.md`（P1.6 全市场榜单与关注板块自动采集）、`docs/features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`（行情资产三层：原始事实/可复用衍生统计/任务质量治理、提醒三层分类）、`docs/features/MARKET_ALERT_RULES_DESIGN.md`（行情提醒规则与 `market_data_alert` 复用边界）。
- API/data contract: `docs/api/MARKET_DATA_API.md`（`/api/v1/market-data/sector-catalog/*` 现状、`ApiResponse<T>`、错误码语义）、`docs/DATABASE_DESIGN.md`（V14-V18 事实表与命名约定）。
- Architecture: `docs/CURRENT_ARCHITECTURE_AND_MODULES.md`、`docs/DEVELOPMENT_ROADMAP.md`、`AGENTS.md`（推荐包结构 `factor/indicator`、MyBatis+XML、Flyway、不自动交易原则）。
- Baseline commit: `563e84a573426800b3f6aa8e4e0525bc5314b3a8`
- Baseline branch: `codex/p17-sector-analytics-design-20260802`（已在 `main` 之上的干净工作树）
- Pre-existing dirty paths: 无（工作树干净）
- Allowed write paths（仅文档/artifact，禁止业务代码）:
  - `docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md`（**新建**主设计文档）
  - `docs/api/MARKET_DATA_API.md`（**只追加** §5 板块分析接口设计章节，标记“规划/未实现”，不改动现有 §1-§4 已实现事实）
  - `docs/DATABASE_DESIGN.md`（**只追加**板块分析规划表章节，标记“规划 V19+”，不改动 V1-V18 已实现表段落）
  - `docs/development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md`（**新建**任务拆分与并行开发计划）
  - `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-*.md`（本任务自身 artifact：TEST-DESIGN/IMPLEMENTER/REVIEW/VERIFICATION/FINALIZATION 等）
  - `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-CONTROL.json`（机器控制文件）
  - `scripts/tests/p17-sector-analytics-design-structure.test.mjs`（本任务新建的只读静态结构/交叉引用/污染探测校验脚本，非业务代码，与 SLICE-01 一致；TEST-DESIGN AMEND-REC-01 采纳）

> Slice/file/line cap 口径（TEST-DESIGN AMEND-REC-02 采纳）：治理角色 artifact（`*-TEST-DESIGN.md`/`*-IMPLEMENTER.md`/`*-REVIEW.md`/`*-VERIFICATION.md`/`*-FINALIZATION.md`/`*-CONTROL.json`）不计入 file/line cap；文档行数不计入 500 行 cap，仅 `.mjs` 静态脚本的代码行计入。产品文件数 = 5（4 docs + 1 script）≤ 8。

## Facts And Decisions

| Type | Item |
|---|---|
| FACT | 现存板块事实表（V14/V15）`market_sector_watch`、`market_sector_snapshot`（含 V15 字段 `snapshot_bucket_time/trigger_type/expected_member_count/valid_member_count/delayed_member_count/unmapped_member_count/quality_status`）、`market_sector_member_snapshot`、`market_sector_ranking_config`、`market_sector_ranking_batch`、`market_sector_ranking_item` 已落地；最新 migration 为 V18，新分析表规划为 V19+。 |
| FACT | `market_data_alert`（V7）已定义 `alert_type/severity/canonical_symbol/quote_time/trade_date/message/trigger_value_json/resolved`，severity 取值 `INFO/WARN/HIGH`，现有 alert_type 仅有 `PROVIDER_NOT_CONFIGURED/SYNC_FAILED/EMPTY_DAILY_BARS/STALE_QUOTE`；分析层异动提醒应复用此表并新增 `SECTOR_*` 类型，不新建第二套告警表。 |
| FACT | 板块 Java 代码位于 `com.quant.trade.marketdata.{controller,service,manager,dao,model,vo,dto,provider,constant}`，无独立 `sector` 子包；14 个 `/sector-catalog/*` 端点已实现于 `MarketSectorCatalogController`；`industryRankings/industryPeers` 是 LongPort HTTPS 透传，非衍生分析。 |
| FACT | 顶层包为 `agent/common/dashboard/journal/marketdata/portfolio/review/risk/tradeplan/watchlist`；`AGENTS.md` 推荐的 `factor/indicator` 包**尚未创建**。新衍生指标逻辑应在设计中给出归属（推荐 `marketdata` 下分析子包或顶层 `factor/indicator`，本设计需明确推荐并说明）。 |
| FACT | 前端在**独立仓库**（`docs/FRONTEND_ARCHITECTURE.md`），本仓库无 React 代码；`docs/mock/MOCK_REMOTE_CONTRACT.md` 无板块分析 mock 契约。前端页面与图表设计在本仓库只能给出规格与 mock 约定建议，实际前端实现留作后续独立子任务。 |
| FACT | P1.5 已声明“后续分析层……派生指标必须记录公式、窗口、样本覆盖和失效场景，不覆盖原始快照”；P1.6 声明“停机恢复后只采最新有效时间桶，不能补写无法还原的历史盘中截面”、A 股集合竞价快照不可等同连续竞价成交事实、板块成交额/量/净流入可能是日内累计值（区间增量需处理跨日重置）。 |
| FACT | 系统是只读研究工具：不自动交易、不连券商、不存密钥、不承诺收益、分析不生成买卖指令（`AGENTS.md`、P1.5/P1.6 设计、`MARKET_DATA_WORKBENCH` 产品边界一致）。 |
| DECISION | 采用推荐可逆方案：板块分析层分为**衍生指标快照**（只读派生，新表存储，幂等重算）+ **提醒事件**（复用 `market_data_alert`）；**绝不写回** `market_sector_snapshot`、`market_sector_member_snapshot`、`market_sector_ranking_*`、`stock_*` 等原始事实表。衍生结果可重算、可丢弃、可下线，原始事实不可变。 |
| DECISION | 衍生指标全部使用**可解释白盒公式**（Mansfield/RS-rank 类相对强弱、连续排行位次/排名相关性的轮动持续性、龙头贡献度=成分加权和占比、量价确认=板块涨跌方向与板块成交额/成交量变化一致性、异动=阈值+Z-score），不引入黑盒模型评分或机器学习隐式打分。所有公式记录输入字段、窗口、基准、样本最小门槛与失效条件。 |
| DECISION | 时间窗口与基准：相对强弱与轮动持续性默认使用**收盘快照序列**（`market_sector_ranking_batch` CLOSE + `market_sector_snapshot`）作为主序列，盘中 `INTRADAY` 快照仅用于当日异动与成交量确认的增量计算；基准市场指数/ETF 由用户在关注记录 `tracking_symbol` 配置，缺基准时使用同市场全市场板块等权均值并显式标记 `BENCHMARK_TYPE=SECTOR_EQUAL_WEIGHT`，不静默编造指数。 |
| DECISION | 缺失/停牌/样本不足：成分停牌按 `trade_status` 与 `is_delayed` 排除并计入 `excluded_member_count`；有效样本数低于门禁（默认 < 板块成分下限，CN≥8、HK/US≥5 或 < 预期成分 50%）时衍生结果标记 `INSUFFICIENT_SAMPLE`，前端降级展示且不产 HIGH 提醒；跨市场时区严格按 P1.6 `ZoneId`（CN `Asia/Shanghai`、HK `Asia/Hong_Kong`、US `America/New_York`）对齐交易日，禁止跨市场混合同一序列。 |
| DECISION | `MyBatis/Flyway` 边界：新分析表走更高版本 Flyway（V19+），SQL 写在 `src/main/resources/mapper/*.xml`，主键 `id bigint auto_increment`，金额/价格 `decimal(20,6)`，时间 `created_at/updated_at`，幂等键覆盖 `(sector_identity, as_of_date/trade_date, window)`；衍生表与分析读服务只读原始事实表，不反向 UPDATE 原始表。 |
| DECISION | 把后续实现拆为可并行子任务，每个子任务给出独占写路径、依赖、AC、测试与合并顺序（见 §Implementation Slices 与实现计划文档）；本设计任务不实现任何子任务代码。 |
| ASSUMPTION | Longbridge 行业分类与 provider 板块 ID 在分析窗口内稳定；若 provider 口径变更，衍生历史序列在变更点断档并标记，不跨口径拼接（沿用 P1.6 失效场景）。 |
| ASSUMPTION | 第一版只消费**已落库**的板块快照与成分快照，不新增 provider 外联；异动提醒基于阈值，不依赖实时推送通道。 |
| OPEN_QUESTION | （设计需给出明确推荐，不阻塞）龙头贡献度是否区分“市值加权 vs 成交额加权 vs 净流入加权”——本设计推荐成交额加权为主、净流入加权为辅并给出公式。 |

## Scope

### In Scope

1. 新建主设计 `docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md`，包含：用户目标与场景、范围与非目标、四视角（产品/量化研究/数据工程/架构）结论、五大可解释公式（相对强弱、轮动持续性、龙头贡献、成交量确认、异动提醒）含输入/窗口/基准/样本门槛/失效场景、原始事实 vs 衍生指标 vs 提醒事件分层、数据模型（规划表 V19+ 字段与索引）、MyBatis/Flyway 边界、API 设计（规划端点）、前端页面与图表设计、mock 约定建议、风险与失效边界（含“不构成投资建议”）。
2. 追加 `docs/api/MARKET_DATA_API.md` §5“板块分析（规划/未实现）”章节，列出规划端点、请求/响应示例、错误码（复用 `ApiResponse<T>` 与 `MARKET_SECTOR_PROVIDER_UNAVAILABLE` 等），明确标记“规划，未实现”。
3. 追加 `docs/DATABASE_DESIGN.md` 板块分析规划表段落，列出 V19+ 规划表（如相对强弱快照、轮动持续性快照、龙头贡献快照、量价确认快照、异动提醒来源标记），明确“规划”状态与幂等键，不改 V1-V18 已实现表。
4. 新建 `docs/development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md`，把后续实现拆为可并行子任务（≥4 个，覆盖：数据模型+计算服务+DTO/Mapper、衍生 API+DTO、异动提醒评估器+`market_data_alert` 写入、前端页面+图表+mock 契约），每个子任务列独占写路径、依赖、AC、测试、合并顺序与并行/串行关系。
5. 本任务治理 artifact（TEST-DESIGN/IMPLEMENTER/REVIEW/VERIFICATION/FINALIZATION）与 `*-CONTROL.json`。

### Out Of Scope

- 任何业务代码、Flyway migration `.sql`、MyBatis XML、Java/TypeScript/React 实现（均留待后续子任务）。
- 修改 `BUILD_CHECKLIST.md`、`AI_HANDOFF.md`、`docs/development/DEVELOPMENT_LOG.md`、`docs/acceptance/ACCEPTANCE_LOG.md`（统一留给后续集成/交付任务）。
- 修改现有 `MARKET_DATA_API.md` §1-§4 已实现事实段落、`DATABASE_DESIGN.md` V1-V18 已实现表段落、现有板块设计文档（P1.5/P1.6）。
- 运行 Maven、npm、Docker 或真实外部调用。

### Prohibited

- 把任何分析/衍生结果写回原始行情/板块事实表（`stock_*`、`market_sector_*` 事实表、`market_sector_ranking_*`）。
- 生成买卖指令、自动交易逻辑或不可解释黑盒/ML 隐式评分。
- 引入券商接口或密钥读取。
- 修改 `main`/`master`，合并、force push，或推送 `codex/p17-sector-analytics-design-20260802` 以外的分支。
- 创建额外递归专家团 sub-agent（设计内结论由固定四角色产出）。
- 在文档中把“规划/未实现”表或接口描述为已实现。

## Acceptance Criteria

| AC-ID | Observable behavior | Preconditions/input | Expected result | Evidence | Dimension | Owner | Status |
|---|---|---|---|---|---|---|---|
| AC-01 | 主设计文档与 API/DB/实现计划追加章节存在且金融口径自洽可开发。打开 `docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md`，它包含五大可解释公式（相对强弱、轮动持续性、龙头贡献、成交量确认、异动提醒），每个公式显式列出输入字段、窗口、基准、样本最小门槛与失效场景；并明确分层（原始事实/衍生指标/提醒事件）且声明禁止写回原始事实表。`docs/api/MARKET_DATA_API.md` 追加的板块分析章节与 `docs/DATABASE_DESIGN.md` 追加的规划表段落均标记“规划/未实现”，未把任何规划项表述为已实现。 | 工作树在 baseline `563e84a` 之上，仅含本任务允许写路径。 | 文档存在、章节齐全、五个公式字段齐全、分层与禁止写回明确、规划项均标记未实现，且未改动 V1-V18/§1-§4 已实现事实段落。 | 静态结构与交叉引用校验脚本对设计/接口/DB/实现计划四类 artifact 输出 PASS，并附命中行号证据 | STATIC | TEST_DESIGNER + IMPLEMENTER + FINAL_VERIFIER | NOT_STARTED |
| AC-02 | 原始事实不被分析结果污染。设计文档与 DB 规划段落中，所有衍生表均为独立新表（V19+），其写路径/读路径声明只读原始事实表，且 `DATABASE_DESIGN.md` V1-V18 已实现表段落与 `market_sector_snapshot/member_snapshot/ranking_*`、`stock_*` 行未出现任何被分析层 UPDATE/写回的描述。 | 同上。 | 校验脚本在所有设计/DB/API/计划 artifact 中未发现“写回/UPDATE 原始事实表”的描述，且衍生表均与新 Flyway 版本绑定，未复用既有版本号。 | 静态污染探测脚本输出 PASS（0 命中禁止模式）+ 人工核验记录 | STATIC | IMPLEMENTER + CODE_REVIEWER + FINAL_VERIFIER | NOT_STARTED |
| AC-03 | 后续实现可并行且边界清晰。`docs/development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md` 列出 ≥4 个子任务，每个子任务显式给出独占写路径（互不重叠）、依赖关系、AC、测试与合并顺序；并存在一个依赖 DAG/顺序表说明哪些可并行、哪些必须串行。 | 同上。 | 计划文档存在、子任务数 ≥4、每个子任务含独占写路径+依赖+AC+测试+合并顺序、且存在并行/串行顺序表；静态校验输出 PASS。 | 静态结构校验脚本对计划文档输出 PASS | STATIC | TEST_DESIGNER + FINAL_VERIFIER | NOT_STARTED |

## Verification Plan

| Dimension | Required | Command or inspection | Pass rule |
|---|---|---|---|
| STATIC | Yes | `node scripts/tests/p17-sector-analytics-design-structure.test.mjs`（本任务新建的只读结构/交叉引用/污染探测静态校验脚本，对设计/API/DB/计划 artifact 做断言；非业务代码，放在 `scripts/tests/` 与本任务 artifact 同批冻结） | exit 0，输出 PASS，附每个 AC 命中行号/路径证据 |
| AUTOMATION | Yes（治理静态） | `node scripts/validate-ai-governance.mjs` + `node scripts/run-ai-governance-gates.mjs` | exit 0 |
| RUNTIME | No | 不运行 Maven/npm/Docker/外联（任务明确禁止） | NOT_REQUIRED |
| DEPLOYMENT | No | 纯设计任务，无部署 | NOT_REQUIRED |

## Implementation Slices

每个初始 slice 至多 3 ACs、8 文件、500 生产行。本任务为设计任务，单 slice 覆盖全部 3 ACs，因 artifact 高度耦合
（同一组金融口径必须同时在设计/API/DB/计划四类文档中一致）。父上下文负责跨文档一致性组装，实现者负责撰写。

| Slice ID | Coherent boundary | AC IDs | Allowed write paths | Max files | Max production-line delta |
|---|---|---|---|---:|---:|
| SLICE-01 | 板块分析层可开发设计文档族（主设计 + API §5 追加 + DB 规划追加 + 实现计划 + 静态结构校验脚本 + 本任务治理 artifact） | AC-01, AC-02, AC-03 | `docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md`、`docs/api/MARKET_DATA_API.md`、`docs/DATABASE_DESIGN.md`、`docs/development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md`、`scripts/tests/p17-sector-analytics-design-structure.test.mjs`、`docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-*.md` | 8 | 500 |

## Frozen Test Inventory

每个强制证据项有稳定 test ID 与可在机器回执中观察的精确选择器。

| Test ID | AC IDs | Kind | Required | Source path | Exact selector | Receipt path |
|---|---|---|---|---|---|---|
| TEST-01 | AC-01 | STATIC | YES | `scripts/tests/p17-sector-analytics-design-structure.test.mjs` | `node scripts/tests/p17-sector-analytics-design-structure.test.mjs`（断言主设计文档存在、五大公式各含输入/窗口/基准/样本门槛/失效场景、分层与禁止写回明确、API §5 与 DB 规划标记未实现、§1-§4 与 V1-V18 未被改动） | `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-EVIDENCE-TEST-01.json` |
| TEST-02 | AC-02 | STATIC | YES | 同上脚本 | 同脚本“污染探测”子断言（所有 artifact 不含“写回/UPDATE 原始事实表”模式，衍生表版本 > V18） | `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-EVIDENCE-TEST-02.json` |
| TEST-03 | AC-03 | STATIC | YES | 同上脚本 | 同脚本“实现计划结构”子断言（≥4 子任务、独占写路径互不重叠、含依赖+AC+测试+合并顺序、存在并行/串行顺序表） | `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-EVIDENCE-TEST-03.json` |
| TEST-GOV-01 | ALL | STATIC | YES | `scripts/validate-ai-governance.mjs` | `node scripts/validate-ai-governance.mjs` | `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-EVIDENCE-TEST-GOV-01.json` |
| TEST-GOV-02 | ALL | STATIC | YES | `scripts/run-ai-governance-gates.mjs` | `node scripts/run-ai-governance-gates.mjs` | `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-EVIDENCE-TEST-GOV-02.json` |

## Architecture And Quality Gates

- Required architecture review: `YES`（DELIVERY_PUSH 且需在冻结前确认设计不破坏分层与单事实源；架构门禁 `scripts/check-ai-architecture.mjs` 运行于冻结候选，文档为主，告警需逐条 disposition）
- Triggered thresholds: 文档任务不触发方法行数/复杂度阈值告警的常规路径；若架构门禁报告 warning，按 report ID 逐条 disposition 并记录。
- Required layers/boundaries: 衍生指标层只读原始事实层；提醒事件复用 `market_data_alert`；分析层不直连 provider、不写回原始表。
- Responsibility-map evidence: 主设计文档“分层”章节 + DB/API 规划段落的读/写边界表。
- ADR exception and expiry: 无。

## Role Assignments

- Test designer: `qta-test-designer`（挑战 AC 与冻结测试清单）
- Implementer: `qta-implementer`（撰写设计文档族 + 静态校验脚本，并产出自检证据）
- Code reviewer: `qta-code-reviewer`（审查冻结候选 diff，功能性 + 架构两条线）
- Final verifier: `qta-final-verifier`（独立干净上下文执行 STATIC/AUTOMATION 门禁，给出唯一验收裁决）
- Omitted roles and justification: 无（四角色全用，最保守）。

## Candidate And Git Policy

- Git automation: `DELIVERY_PUSH`
- User authorization evidence: 用户明确授权“git_automation=DELIVERY_PUSH；只允许提交并推送 codex/p17-sector-analytics-design-20260802；禁止合并或推送 main，禁止 force push”。
- Task branch: `codex/p17-sector-analytics-design-20260802`
- Contract commit: `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-CONTRACT.md` + CONTROL（contract 阶段提交）
- Candidate mode: `SNAPSHOT`（文档候选；先 COMMIT 设计文档族到任务分支作为候选来源，再以 SNAPSHOT 清单覆盖候选路径用于校验/审查/核验；patchSha256 与 COMMIT 模式等价的 diff artifact 一致）
- Candidate commit: 待实现者 SELF_CHECKED 后冻结
- Candidate tree hash: 待冻结
- Patch SHA-256: 待冻结（baseline→候选 diff artifact 的 SHA-256）
- Candidate manifest path/hash: 由 `scripts/create-candidate-manifest.mjs` 生成并记录
- Checkpoint push allowed: `NO`（仅 DELIVERY_PUSH，不做 checkpoint push）
- Delivery push target: `origin/codex/p17-sector-analytics-design-20260802`
- Protected/default branch direct push: `NO`

## Checkpoint Policy

- Context budget: L0 rawTokenBudget 2,000,000；contextMeasurement=UNAVAILABLE（运行时不暴露可靠遥测，按 turn/wait/poll/repair 限制执行）。
- Persist discoveries at: 25%
- Stop opening stages at: 40%
- Mandatory fresh-context handoff at: 60%
- Maximum waits per role run: 2
- Maximum shell polls per command: 3
- Automatic compaction policy: first compaction forces handoff; second is prohibited
- Maximum repair rounds for one failure fingerprint: 2
- Lane AC cap: 3
- Blocking amendment cap: 0（L0）
- Blocking amendment history: 无
- Stop conditions: 金融口径自相矛盾、原始事实被污染、规划项被表述为已实现、或同一失败指纹重复两次 → BLOCKED（证据化记录），不向用户提问。
