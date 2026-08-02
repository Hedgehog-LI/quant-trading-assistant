# 板块分析（相对强弱 / 轮动持续性 / 龙头贡献 / 量价确认 / 异动提醒）可开发设计

> 版本：v1.0 · 状态：规划（P1.7，未实现，本文为可开发设计，不落业务代码）
> 关联：`../../BUILD_CHECKLIST.md`、`../api/MARKET_DATA_API.md` §5（规划）、`../DATABASE_DESIGN.md` 板块分析规划表（V19+）、`../../development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md`、`MARKET_SECTOR_CATALOG_DESIGN.md`、`MARKET_SECTOR_AUTOMATIC_COLLECTION_DESIGN.md`、`MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`、`MARKET_ALERT_RULES_DESIGN.md`。
> 前置事实：板块原始事实表（V14/V15）`market_sector_watch`、`market_sector_snapshot`、`market_sector_member_snapshot`、`market_sector_ranking_config`、`market_sector_ranking_batch`、`market_sector_ranking_item` 已落地；`market_data_alert`（V7）已存在并可复用；最新 migration V18，本设计规划 V19+。

## 0. 设计状态与定位

本文档是**规划/未实现**的板块分析层（P1.7）可开发设计。它只产出设计文档与实现计划，不写任何业务代码、Flyway SQL、MyBatis XML、Java/TS/React 实现。所有规划表标注为 V19+ 的独立新表，所有规划接口标注为“规划/未实现”，均不被表述为已实现。

本系统是**只读研究工具**：所有派生指标只读原始事实表，**禁止写回** `market_sector_snapshot`、`market_sector_member_snapshot`、`market_sector_ranking_*` 与 `stock_*` 原始事实表。衍生结果只存新表，可重算、可丢弃、可下线，原始事实不可变。第一版不生成买卖指令、不自动交易、不连接券商、不读取密钥、不引入黑盒 ML 评分。

> **风险声明**：本系统是决策辅助工具，所有指标与提醒仅为观察提示，不构成投资建议，不预测收益，不产生交易动作。

## 1. 用户目标与场景

| 角色 | 目标 | 场景 |
| --- | --- | --- |
| 个人投资者 | 决策辅助 | 盘后比较同市场各板块的相对强弱与轮动持续性，识别当前领涨/领跌板块，辅助做“看哪个板块”的研判 |
| 个人投资者 | 只读研究 | 查看板块涨幅由哪些龙头成分解释、量价是否同向确认，做复盘与归因 |
| 个人投资者 | 异动观察 | 在板块出现异常涨跌、量能放大、强弱反转时收到 INFO/WARN/HIGH 提醒，作为后续跟进提示 |

系统目标边界：

- **决策辅助、只读研究、不生成买卖指令**。所有指标只读已落库的板块原始事实，做可解释白盒计算并落库为衍生指标快照；提醒事件复用 `market_data_alert`，是观察提醒而非交易信号。
- 输入只消费**已落库**的板块收盘/成分/榜单快照与基准证券日 K，不新增 provider 外联、不接收实时推送、不在分析窗口内反向调用 provider。

## 2. 范围与非目标

做（第一版）：

1. 五大可解释白盒公式：相对强弱、轮动持续性、龙头贡献、成交量确认（量价确认）、异动提醒。
2. 衍生指标快照表（V19+ 独立新表）+ 复用 `market_data_alert` 的 `SECTOR_*` 提醒事件。
3. REST 查询接口（规划，见 §API 设计概要与 `MARKET_DATA_API.md` §5）。
4. 前端页面与图表规格 + mock 契约建议（实际前端实现留作独立仓库子任务）。
5. 缺失数据/停牌/跨市场时区/样本不足/口径变更的统一失效处理。

不做（第一版明确非目标）：

- **不自动交易**、不生成买卖指令、不下单、不连券商、不读密钥、不承诺收益。
- **不做不可解释黑盒 ML 评分**，不引入机器学习隐式打分或因子暗箱；所有公式必须白盒可复算。
- **不写回原始事实表**（`stock_*`、`market_sector_*` 事实表、`market_sector_ranking_*`）。
- 不新增 provider 外联通道、不接实时推送。
- 不替代 P1.5/P1.6 的原始事实采集；分析层只消费其已落库结果。
- 不在前端仓库做实际实现（本仓库只给规格与 mock 契约建议）。

## 3. 四视角结论

### 3.1 产品经理视角

- 板块分析是 P1.5 目录与 P1.6 全市场榜单之上的“研判层”：用户已有板块涨跌与榜单，还需要回答“谁在持续强、谁只是当日脉冲、涨幅由谁解释、量价是否确认、是否值得跟进提醒”。
- 必须保持“决策辅助”定位：任何指标与提醒都不得演化为买卖指令；前端必须显式标注“不构成投资建议”。
- 异动提醒是对“值得多看一眼”的提示，而非择时信号；提醒强度分级（INFO/WARN/HIGH），并允许用户忽略。

### 3.2 量化研究视角

- 相对强弱采用可解释白盒（Mansfield-style RS + 同市场板块内 RS-rank 百分位），避免黑盒。基准可配置（`tracking_symbol`），缺基准时用同市场板块等权均值并显式标记 `BENCHMARK_TYPE=SECTOR_EQUAL_WEIGHT`，不静默编造指数。
- 轮动持续性用相邻交易日排名的相关系数（Spearman）与连续领涨/领跌天数刻画稳定性，区分“连续强”与“一日强”。
- 龙头贡献度推荐成交额加权为主、净流入加权为辅，给出可解释占比公式，并区分市值/成交额/净流入三种口径（OPEN_QUESTION，本设计给出推荐与降级策略）。
- 所有公式必须显式记录输入字段、时间窗口、基准、样本最小门槛与失效场景，以保证可复盘、可证伪。

### 3.3 数据工程视角

- 严格区分三层：**原始事实**（不可变，P1.5/P1.6 落库）、**衍生指标**（V19+ 新表，幂等可重算）、**提醒事件**（复用 `market_data_alert`）。
- 衍生计算只读原始事实表；禁止 UPDATE/写回/回写/覆盖原始事实表。衍生表用更高 Flyway 版本（V19+），主键 `id bigint auto_increment`，金额 `decimal(20,6)`，幂等键覆盖 `(sector_identity, as_of_date/trade_date, window)`。
- 时序以收盘快照序列为主轴（`market_sector_ranking_batch` CLOSE + `market_sector_snapshot`）；盘中 `INTRADAY` 快照仅用于当日异动与成交量确认的区间增量计算，且必须处理日内累计值跨日重置。
- 样本不足/停牌/缺失/口径变更统一标 `INSUFFICIENT_SAMPLE` 或 `STALE`/`ORIGIN_CHANGED` 并降级，不静默编造。

### 3.4 架构视角

- 分析层归属推荐为 `com.quant.trade.marketdata.analysis` 子包（与现有 sector 代码同模块、同事务边界、不直连 provider），其中衍生指标计算落在 `analysis/derived/`，异动提醒评估器落在 `analysis/alert/`，调度器落在 `marketdata/scheduler/`（与现有分钟线 scheduler 同包），逻辑稳定后再评估是否上移到顶层 `factor/indicator`（`AGENTS.md` 推荐包，当前未创建）。本设计采用 marketdata.analysis 子包，避免跨模块依赖与过早抽象。
- 读服务只读原始事实表 + 写衍生表 + 复用 `market_data_alert`；不写原始表。Scheduler 仅做衍生重算与提醒评估，不调用 provider。
- 与 `MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md` 的“行情资产三层（原始事实/可复用衍生统计/任务质量治理）”一致：本层属于“可复用衍生统计”，质量治理复用 `market_data_alert`。

## 4. 原始事实 / 衍生指标 / 提醒事件 三层模型

| 层 | 存储 | 读写边界 | 可变性 |
| --- | --- | --- | --- |
| 原始事实 | `market_sector_snapshot`、`market_sector_member_snapshot`、`market_sector_ranking_batch`、`market_sector_ranking_item`、`market_sector_watch`、`market_sector_ranking_config`、`stock_daily_bar`、`stock_quote_snapshot` | 分析层**只读**这些原始事实表 | 不可变（P1.5/P1.6 已保证） |
| 衍生指标 | V19+ 新表：`sector_relative_strength_snapshot`、`sector_rotation_persistence_snapshot`、`sector_leader_contribution_snapshot`、`sector_volume_confirmation_snapshot` | 衍生读服务读取原始事实表 → 计算 → 写入新衍生表（幂等可重算） | 可重算、可丢弃、可下线 |
| 提醒事件 | 复用 `market_data_alert`（V7），新增 `alert_type=SECTOR_*` | 提醒评估器读取衍生指标 + 原始快照 → 写 `market_data_alert` | INFO/WARN/HIGH，可 resolve |

> **禁止写回声明（强制边界）**：禁止写回 `market_sector_snapshot`、`market_sector_member_snapshot`、`market_sector_ranking_batch`、`market_sector_ranking_item`、`market_sector_watch`、`market_sector_ranking_config`、`stock_daily_bar`、`stock_quote_snapshot` 等原始事实表；衍生读服务只读原始事实表，不得写回、回写或覆盖原始事实，衍生结果只存新表。原始事实不可变，衍生结果可重算可下线。下线某衍生公式只需停掉对应计算与查询，不回滚也不污染原始事实。

字面 token 要求：本设计显式包含 `原始事实`、`衍生指标`、`提醒事件` 三类，并以“禁止写回/不得写回原始事实表”作为强约束。

## 5. 五大可解释公式

每个公式均给出五要素：输入（输入字段来源）、窗口（计算时间窗口）、基准（比较基准与配置）、样本（样本最小门槛）、失效（失效场景与降级）。所有公式为白盒可解释，不引入黑盒/ML 隐式评分。

### 5.1 相对强弱（Relative Strength / RS-rank）

- **输入**：板块收盘快照序列（`market_sector_ranking_batch` 中 `snapshot_type=CLOSE` 的 `rank_no` 与 `market_sector_snapshot` 的 `change_rate` / `year_to_date_change_rate` / `total_turnover_amount`）以及基准 `tracking_symbol` 的日 K（`stock_daily_bar` 的 `close`）或同市场板块等权均值。输入字段仅来自原始事实表，只读。
- **窗口**：默认窗口 20 / 50 / 120 个交易日（`window` 维度，对应短期/中期/长期）。每个窗口独立产出一份 RS 值与 RS-rank 百分位。
- **基准**：基准可由用户在 `market_sector_watch.tracking_symbol` 配置（推荐宽基指数/行业 ETF 的统一证券代码）；缺基准时使用同市场全板块等权均值，并显式标记 `benchmark_type=SECTOR_EQUAL_WEIGHT`，不静默编造指数。
- **样本**：连续收盘快照数 ≥ 窗口长度（如 20 日窗口需 ≥ 20 个连续交易日 CLOSE 快照），且同市场当日有效板块数 ≥ 阈值（默认 ≥ 5）。低于门槛时标记 `quality_status=INSUFFICIENT_SAMPLE` 并降级展示，不产 HIGH 提醒。
- **失效**：provider 口径变更（行业分类或 provider 板块 ID 在窗口内变更）→ 在变更点断档并标 `ORIGIN_CHANGED`，不跨口径拼接；样本不足；成分大面积停牌；跨市场混算（必须按 CN/HK/US 各自 ZoneId 对齐交易日，禁止混入同一序列）。

公式要点（白盒）：

- Mansfield-style RS：`RS_normalized = ((P_sector / P_baseline) ^ (260/N) ... )`，N 取窗口长度；具体实现采用标准化比率并对历史序列做标准化（减均值除标准差）以得到可比较的相对强弱分。
- RS-rank：在同市场当日所有有效板块 RS 值集合内计算百分位（0~100），100 表示最强。
- 同一板块同窗口每日收盘后产出一份衍生快照，幂等键 `(sector_identity, as_of_date, window)`。

### 5.2 轮动持续性（Rotation Persistence）

- **输入**：`market_sector_ranking_batch`（CLOSE）的 `rank_no` 序列与 `market_sector_ranking_item` 的排名明细，按交易日排序。输入只读原始事实表。
- **窗口**：默认 5 / 10 / 20 个交易日（短/中/长）。每个窗口独立计算持续性。
- **基准**：基准为同市场全板块当日排名集合（rank 在该集合内的相对位次变化），不引入外部指数。
- **样本**：连续收盘交易日数 ≥ 窗口长度，且窗口内无大缺口（缺失交易日占比 < 阈值，默认 < 30%）。低于门槛标 `INSUFFICIENT_SAMPLE` 并降级。
- **失效**：provider 口径变更 → 断档标记 `ORIGIN_CHANGED`；窗口内快照缺失超过阈值；A 股集合竞价 `INTRADAY` 快照被误当作连续竞价 CLOSE 序列（必须只用 `snapshot_type=CLOSE`，集合竞价不计入连续位次）。

公式要点（白盒）：

- 相邻交易日排名相关系数（Spearman）：对窗口内每日全板块 rank 序列做两两 Spearman 相关，取均值作为持续性分（越接近 1 表示位次越稳定）。
- 连续领涨/领跌天数：板块 rank 连续处于头部（如前 N%）或尾部（后 N%）的连续交易日数。
- 幂等键 `(sector_identity, as_of_date, window)`。

### 5.3 龙头贡献（Leader Contribution）

- **输入**：`market_sector_member_snapshot` 的 `change_rate` / `turnover_amount` / `net_inflow`（成分个股资金字段）与 `market_sector_snapshot` 的聚合值 `total_turnover_amount` / `total_net_inflow` / `change_rate`（板块聚合基准）。输入只读原始事实表。
- **窗口**：默认单快照当日（当日一份成分快照即可计算贡献度）+ 可选 N 日均值（默认 N=5，平滑贡献度排名）。
- **基准**：基准为板块自身聚合值（板块总成交额/总净流入/板块涨跌幅），贡献度 = 个成分值 / 板块聚合值，加和应≈1。
- **样本**：有效成分数门槛：CN ≥ 8、HK/US ≥ 5，或 ≥ 预期成分数 50%（取 `expected_member_count`）。低于门槛标 `INSUFFICIENT_SAMPLE` 并降级，不产 HIGH 提醒。
- **失效**：成分停牌（按 `trade_status` 与 `is_delayed` 排除并计 `excluded_member_count`）；延迟行情（`is_delayed=true`）导致净流入/成交额口径不一致；累计值跨日重置未处理（成交额/净流入若为日内累计，跨日比较必须先用相邻快照差分或仅用 CLOSE 快照对齐）。

公式要点（白盒）：

- 成交额加权为主：`leader_share = sum(top_k 成分 turnover_amount) / 板块 total_turnover_amount`，top_k 默认取前 3/5。
- 净流入加权为辅：`net_inflow_share = sum(top_k 成分 net_inflow) / 板块 total_net_inflow`，仅当净流入口径可靠（非全延迟、累计值已差分对齐）时计算，否则置空并标记。
- OPEN_QUESTION（推荐）：是否区分市值/成交额/净流入加权——本设计推荐**成交额加权为主、净流入加权为辅**，市值加权作为可选（成分市值需额外维护，第一版不强制）。
- 幂等键 `(sector_identity, trade_date, window)`。

### 5.4 成交量确认（量价确认）

- **输入**：`market_sector_snapshot` 的 `change_rate`（板块涨跌方向）与 `total_turnover_amount` / `total_volume`（板块成交额/成交量），区间增量须处理日内累计跨日重置（用相邻快照差分或仅取 CLOSE 快照对齐）。输入只读原始事实表。
- **窗口**：当日（量价是否同向）+ 5 日均量（判断是否放量/缩量）。默认窗口为当日 + 近 5 日均值。
- **基准**：基准为板块自身近期均量（近 5 日 `total_turnover_amount` 均值），判断当日成交相对历史是放量还是缩量。
- **样本**：有可用成交数据且非全停牌；近 5 日成交快照数 ≥ 3。低于门槛标 `INSUFFICIENT_SAMPLE` 并降级。
- **失效**：累计值（成交额/成交量）跨日重置未处理；延迟行情（`is_delayed`）导致成交失真；小盘低成交板块（成交额绝对值过低）易产生虚假背离信号 → 降级展示，不产 HIGH 提醒。

公式要点（白盒）：

- 量价同向 = 确认：板块 `change_rate` 与成交额/成交量变化方向一致（涨且放量 / 跌且放量）记为 `CONFIRMED`；方向背离（涨且缩量 / 跌且缩量）记为 `DIVERGENCE`（警示）。
- 量比：`volume_ratio = 当日 total_turnover_amount / 近5日均值`，>1 放量、<1 缩量。
- 幂等键 `(sector_identity, trade_date)`。

### 5.5 异动提醒（Anomaly Alert）

- **输入**：上述派生指标（相对强弱 RS/RS-rank、轮动持续性、龙头贡献、量价确认）与原始快照（`market_sector_snapshot` 的 `change_rate` / `total_turnover_amount` / `total_net_inflow`）。输入只读原始事实表与衍生表。
- **窗口**：当日 vs 近 N 日均值/标准差（默认 N=20）。窗口内做 Z-score。
- **基准**：基准为板块自身历史分布（近 N 日该板块某指标的均值与标准差），不引入外部阈值集合。
- **样本**：历史样本 ≥ 阈值（默认 ≥ 20 个有效交易日）才能算 Z-score；否则仅阈值判断（如涨跌幅超过固定阈值），并在提醒中标注 `EVIDENCE=THRESHOLD_ONLY`。
- **失效**：样本不足（< 20）只能阈值判断；数据陈旧（最新快照 `quote_time` 超过阈值）→ 降级或不产提醒；延迟行情导致指标失真。

公式要点（白盒，阈值 + Z-score）：

- Z-score：`z = (当日值 - 近N日均值) / 近N日标准差`，`|z|` 超过阈值（默认 2.0）记为异常。
- 阈值：涨跌幅绝对值超过固定阈值（如 |change_rate| > 3%）、量比超过阈值（如 > 2.0）。
- 复用 `market_data_alert`（V7），新增 `alert_type=SECTOR_RS_REVERSAL` / `SECTOR_VOLUME_DIVERGENCE` / `SECTOR_LEADER_CONCENTRATION` / `SECTOR_RANK_JUMP` 等；`severity` 取 `INFO/WARN/HIGH`；`trigger_value_json` 存派生指标快照与上下文。

> **强制声明**：异动提醒是观察提醒，**不是买卖指令、不是投资建议**，不预测收益、不产生交易动作。前端必须在每条提醒旁标注“仅供参考，不构成投资建议”。

## 6. 数据模型（规划 V19+，独立新表，只读原始事实）

> 状态：**规划 V19+**，未实现。以下表为独立新表，绑定 Flyway V19+（V19、V20、...，具体由实现计划 ST-1 拆分），不复用 V1-V18 既有版本号。所有衍生表只读原始事实表，不写回原始事实表。规划区域不含“已实现”描述。

### 6.1 sector_relative_strength_snapshot（规划 V19+）

用途：板块相对强弱（Mansfield-style RS + RS-rank 百分位）衍生快照，只读 `market_sector_ranking_batch`/`market_sector_snapshot`/`stock_daily_bar`。

核心字段：

- `id` bigint 主键 auto_increment
- `sector_identity` varchar(96) — 板块稳定身份（provider_code + provider_sector_id 或 watch_id 派生）
- `market_code` varchar(8) — CN/HK/US
- `as_of_date` date — 基准交易日
- `window` smallint — 窗口长度（20/50/120）
- `benchmark_type` varchar(32) — `TRACKING_SYMBOL` / `SECTOR_EQUAL_WEIGHT`
- `benchmark_symbol` varchar(32) — 实际使用的基准统一证券代码（可空）
- `rs_value` decimal(20,6)
- `rs_rank_percentile` decimal(20,6) — 0~100
- `quality_status` varchar(32) — `OK` / `INSUFFICIENT_SAMPLE` / `STALE` / `ORIGIN_CHANGED`
- `valid_sample_size` int
- `created_at` / `updated_at` datetime

索引/幂等：

- unique `uk_sector_rs(sector_identity, as_of_date, window)`
- index `idx_sector_rs_market_date(market_code, as_of_date)`

### 6.2 sector_rotation_persistence_snapshot（规划 V19+）

用途：板块轮动持续性（相邻交易日排名 Spearman 相关 + 连续领涨/领跌天数），只读 `market_sector_ranking_batch`/`market_sector_ranking_item`。

核心字段：

- `id` / `sector_identity` / `market_code` / `as_of_date` / `window`(5/10/20)
- `rank_spearman_mean` decimal(20,6)
- `consecutive_leading_days` int
- `consecutive_lagging_days` int
- `quality_status` / `valid_sample_size` / `created_at` / `updated_at`

索引/幂等：

- unique `uk_sector_rotation(sector_identity, as_of_date, window)`
- index `idx_sector_rotation_market_date(market_code, as_of_date)`

### 6.3 sector_leader_contribution_snapshot（规划 V19+）

用途：板块龙头贡献度（成交额加权为主、净流入加权为辅），只读 `market_sector_member_snapshot`/`market_sector_snapshot`。

核心字段：

- `id` / `sector_identity` / `market_code` / `trade_date` / `window`(1/5)
- `leader_turnover_share` decimal(20,6) — 成交额加权龙头占比
- `leader_net_inflow_share` decimal(20,6) — 净流入加权龙头占比（可空）
- `top_k` int — 默认 3/5
- `top_leaders_json` text — 龙头成分明细（canonical_symbol、change_rate、turnover_amount、net_inflow、share）
- `excluded_member_count` int — 停牌/延迟排除计数
- `valid_member_count` int
- `quality_status` / `created_at` / `updated_at`

索引/幂等：

- unique `uk_sector_leader(sector_identity, trade_date, window)`
- index `idx_sector_leader_market_date(market_code, trade_date)`

### 6.4 sector_volume_confirmation_snapshot（规划 V19+）

用途：板块量价确认（量价同向/背离 + 量比），只读 `market_sector_snapshot`。

核心字段：

- `id` / `sector_identity` / `market_code` / `trade_date`
- `change_rate` decimal(20,6)
- `turnover_amount` decimal(20,6) — 当日板块成交额（已处理累计跨日重置）
- `volume_ratio` decimal(20,6) — 当日 / 近5日均值
- `confirmation_status` varchar(16) — `CONFIRMED` / `DIVERGENCE` / `INSUFFICIENT`
- `quality_status` / `created_at` / `updated_at`

索引/幂等：

- unique `uk_sector_volume(sector_identity, trade_date)`
- index `idx_sector_volume_market_date(market_code, trade_date)`

### 6.5 复用 market_data_alert（不新建告警表）

异动提醒复用 V7 已实现的 `market_data_alert`，新增 `alert_type=SECTOR_*`（`SECTOR_RS_REVERSAL`、`SECTOR_VOLUME_DIVERGENCE`、`SECTOR_LEADER_CONCENTRATION`、`SECTOR_RANK_JUMP` 等）。`severity` 取 `INFO/WARN/HIGH`；`trigger_value_json` 存派生指标上下文。**不新建第二套告警表**。读服务只读原始事实与衍生表，写 `market_data_alert`。

### 6.6 MyBatis / Flyway 边界

- 新表走更高 Flyway 版本 V19+（V19、V20...），SQL 放在 `src/main/resources/db/migration/V19__*.sql`（具体由 ST-1 决定）。
- MyBatis XML 放在 `src/main/resources/mapper/`（如 `SectorRelativeStrengthSnapshotMapper.xml`）。
- 主键统一 `id bigint auto_increment`；金额/价格 `decimal(20,6)`；时间 `created_at`/`updated_at`。
- 衍生读服务只读原始事实表，禁止 UPDATE/写回/回写/覆盖原始事实表。

## 7. API 设计（规划，未实现 — 详见 MARKET_DATA_API.md §5）

本节为概述；详细端点、请求/响应示例与错误码见 `docs/api/MARKET_DATA_API.md` §5（规划/未实现）。

- GET 板块相对强弱（按市场/窗口查询排行与单板块详情）
- GET 板块轮动持续性（按市场/窗口查询排行与单板块详情）
- GET 板块龙头贡献（按市场查询排行与单板块龙头明细）
- GET 板块量价确认（按市场查询排行与单板块量价状态）
- GET 板块异动提醒：复用现有 `/api/v1/market-data/alerts?alertType=SECTOR_*` 查询

统一前缀 `/api/v1/market-data/sector-analytics/*`（规划），统一响应 `ApiResponse<T>`。错误码复用 `MARKET_SECTOR_PROVIDER_UNAVAILABLE`（原始事实缺失时）、`MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED`（语义对齐，分析层不直接外联，仅在依赖原始事实不可用时透传）。所有端点标注“规划/未实现”。

## 8. 前端页面与图表设计（规格 + mock 契约建议）

> 前端在**独立仓库**（`docs/FRONTEND_ARCHITECTURE.md`）。本仓库只给规格与 mock 契约建议，实际前端实现留作 ST-4 子任务。

页面结构（建议）：

- “板块分析”一级页面，含四个子标签：相对强弱 / 轮动持续性 / 龙头贡献 / 量价确认；右上角“异动提醒”入口（铃铛 + 列表）。
- 每个子标签顶部显示市场切换（CN/HK/US）、窗口切换（20/50/120 等）、基准说明（`BENCHMARK_TYPE` 标签）。

图表建议：

- 相对强弱：板块 RS 热力图（市场 × 板块，颜色编码 RS-rank 百分位）+ 板块 RS 排名条形图（带窗口切换）。
- 轮动持续性：板块位次带图（ridgeline/位次随时间漂移）+ 轮动桑基图（领涨→领跌流向）。
- 龙头贡献：堆叠柱状图（板块总成交额/净流入 = 各龙头成分贡献堆叠）+ 龙头明细表。
- 量价确认：散点图（x=change_rate, y=volume_ratio，颜色=CONFIRMED/DIVERGENCE）。
- 异动提醒：提醒流（按 severity 分色，INFO/WARN/HIGH），每条提醒含派生指标上下文与“不构成投资建议”标注。

mock 契约建议：

- 在前端仓库的 `docs/mock/MOCK_REMOTE_CONTRACT.md`（当前无板块分析 mock 契约）新增 `sector-analytics/*` 一组 mock 响应，字段与后端 VO 对齐；本仓库（后端）只在 ST-4 更新该 mock 契约建议文本，不写 React 代码。

降级展示：

- 当 `quality_status=INSUFFICIENT_SAMPLE` / `STALE` / `ORIGIN_CHANGED` 时，前端以灰显 + 文案提示，不产 HIGH 提醒。
- 每个指标卡片需标注“不构成投资建议，仅作观察提示”。

## 9. 风险与失效边界

- **本系统是决策辅助，不构成投资建议**：所有指标与提醒仅为观察提示，不预测收益、不产生交易动作。前端必须在页面与每条提醒显式标注。
- 跨市场时区：严格按 P1.6 `ZoneId`（CN `Asia/Shanghai`、HK `Asia/Hong_Kong`、US `America/New_York`）对齐交易日，禁止跨市场混入同一序列。
- 停牌与延迟：成分按 `trade_status` 与 `is_delayed` 排除并计 `excluded_member_count`；延迟行情导致净流入/成交额口径不一致时降级。
- 缺失与样本不足：低于门槛标 `INSUFFICIENT_SAMPLE` 并降级展示，不产 HIGH 提醒。
- 口径变更：provider 行业分类/板块 ID 在窗口内变更 → 在变更点断档标 `ORIGIN_CHANGED`，不跨口径拼接。
- 累计值跨日重置：成交额/成交量/净流入若为日内累计，跨日比较必须先差分或仅用 CLOSE 快照对齐，避免虚假信号。

## 10. 统一缺失数据 / 停牌 / 跨市场时区 / 样本不足 / 失效处理

- 停牌：按 `trade_status` 与 `is_delayed` 排除停牌/延迟成分，并计入 `excluded_member_count`；不参与聚合。
- 样本不足：有效样本数低于门禁（CN≥8、HK/US≥5，或 < 预期成分 50%）时衍生结果标 `INSUFFICIENT_SAMPLE`，前端降级展示，且不产 HIGH 提醒（最多 INFO）。
- 跨市场时区：严格按 CN/HK/US 各自 ZoneId 对齐交易日，不混算同一序列；基准与窗口在同市场内成立。
- 数据陈旧：最新快照 `quote_time` 超过阈值（如 CN 收盘后超过 30 分钟仍未落 CLOSE）→ 标 `STALE` 并降级。
- 口径变更：provider 口径变更 → 断档标 `ORIGIN_CHANGED`，历史序列在变更点不跨口径拼接。

## 11. 验收边界（规划，由后续实现任务满足）

- 五大公式均含输入/窗口/基准/样本/失效五要素并落库为 V19+ 衍生表。
- 原始事实零污染：所有衍生表只读原始事实表，禁止写回。
- 实现计划含 ≥4 个可并行子任务，独占写路径互不重叠。
- 静态结构/污染探测脚本对设计/API/DB/计划四类 artifact 输出 PASS。
