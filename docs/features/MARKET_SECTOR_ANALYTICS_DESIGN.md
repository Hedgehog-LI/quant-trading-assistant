# 板块分析（相对强弱 / 轮动持续性 / 收益贡献与交易集中度 / 量价确认 / 异动提醒）可开发设计

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
| 个人投资者 | 只读研究 | 查看板块涨幅由哪些成分的收益贡献解释、成交额与净流入集中度如何、量价是否同向确认，做复盘与归因 |
| 个人投资者 | 异动观察 | 在板块出现异常涨跌、量能放大、强弱反转时收到 INFO/WARN/HIGH 提醒，作为后续跟进提示 |

系统目标边界：

- **决策辅助、只读研究、不生成买卖指令**。所有指标只读已落库的板块原始事实，做可解释白盒计算并落库为衍生指标快照；提醒事件复用 `market_data_alert`，是观察提醒而非交易信号。
- 输入只消费**已落库**的板块收盘/成分/榜单快照与基准证券日 K，不新增 provider 外联、不接收实时推送、不在分析窗口内反向调用 provider。

## 2. 范围与非目标

做（第一版）：

1. 五大可解释白盒公式：相对强弱（N 日对数相对收益 + RS-rank 百分位）、轮动持续性（市场级 Spearman + 板块级位次指标）、收益贡献与交易集中度（严格拆分）、成交量确认（量价确认，六状态）、异动提醒。
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

- 相对强弱采用可解释白盒（N 日对数相对收益 `relativeReturn_N` + 同市场板块内 RS-rank 百分位），避免黑盒。基准可配置（`tracking_symbol`），缺基准时用同市场板块等权均值并显式标记 `benchmark_type=SECTOR_EQUAL_WEIGHT`，不静默编造指数。本设计不使用 Mansfield 名称，公式为完全闭式。
- 轮动持续性拆为两层：市场级（相邻交易日全市场 `rank_no` 向量的 Spearman ρ，存市场级表）与板块级（平均位次、位次标准差、头部桶占用率、连续领涨/领跌天数、位次变化），区分“连续强”与“一日强”。
- 严格区分“收益贡献”与“交易集中度”两类语义：收益贡献为 `weight · memberReturn`（权重优先前收盘价×流通股本，缺失降级等权），交易集中度为 top-K 成交额占比与正/负/绝对净流入集中度。两者表名、字段、接口、页面不得混用。
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
| 衍生指标 | V19+ 新表：`sector_relative_strength_snapshot`、`sector_rotation_market_stability`（市场级）、`sector_rotation_sector_persistence`（板块级）、`sector_member_return_contribution`（收益贡献）、`sector_turnover_concentration`（交易集中度）、`sector_volume_confirmation_snapshot` | 衍生读服务读取原始事实表 → 计算 → 写入新衍生表（幂等可重算） | 可重算、可丢弃、可下线 |
| 提醒事件 | 复用 `market_data_alert`（V7），新增 `alert_type=SECTOR_*` | 提醒评估器读取衍生指标 + 原始快照 → 写 `market_data_alert` | INFO/WARN/HIGH，可 resolve |

> **禁止写回声明（强制边界）**：禁止写回 `market_sector_snapshot`、`market_sector_member_snapshot`、`market_sector_ranking_batch`、`market_sector_ranking_item`、`market_sector_watch`、`market_sector_ranking_config`、`stock_daily_bar`、`stock_quote_snapshot` 等原始事实表；衍生读服务只读原始事实表，不得写回、回写或覆盖原始事实，衍生结果只存新表。原始事实不可变，衍生结果可重算可下线。下线某衍生公式只需停掉对应计算与查询，不回滚也不污染原始事实。

字面 token 要求：本设计显式包含 `原始事实`、`衍生指标`、`提醒事件` 三类，并以“禁止写回/不得写回原始事实表”作为强约束。

## 5. 五大可解释公式

每个公式均给出五要素：输入（输入字段来源）、窗口（计算时间窗口）、基准（比较基准与配置）、样本（样本最小门槛）、失效（失效场景与降级）。所有公式为白盒可解释，不引入黑盒/ML 隐式评分。

### 5.1 相对强弱（N 日对数相对收益 / RS-rank 百分位）

- **输入**：板块日收益（`DECIMAL(20,8)` 日涨跌率）的来源按排名范围区分，两条来源均只读原始事实表、均以百分数口径统一 `/100` 转小数：
  - `rank_scope=FULL_MARKET`：全市场每个板块的日收益取 `market_sector_ranking_item.change_rate`（V15 排名项，含全市场每个 CLOSE 批次的全部板块，仅取 `snapshot_type='CLOSE'`）。
  - `rank_scope=WATCHED_ONLY`：被关注板块的日收益取 `market_sector_snapshot.change_rate`（V14，仅存被 watch 板块的 CLOSE 快照，`trigger_type='CLOSE'`）。
  **强制事实**：现有原始事实表**没有**板块指数点位/价格列，只有 `change_rate`（日涨跌率）与 `year_to_date_change_rate`；因此相对强弱不得引用任何板块指数价格项，必须由 `change_rate` 重建合成净值序列。基准侧若配置了 `market_sector_watch.tracking_symbol`，则用该证券 `stock_daily_bar.close_price`（V5，`DECIMAL(20,6)` 收盘价列；非 `close`，V5 实际列名为 `close_price`，见 `src/main/resources/db/migration/V5__add_market_data_tables.sql:31`）的日收益序列；**计算 N 个日收益需要该证券连续 `N+1` 个 `close_price`**（窗口内 N 个交易日各取 `close_price(t)/close_price(t-1)-1`，需窗口首日前一交易日的收盘价作为 t0）；少于 `N+1` 个连续收盘价时 tracking symbol 基准不可用；输入字段仅来自原始事实表，只读。
- **窗口**：默认窗口 20 / 50 / 120 个交易日（`window` 维度，对应短期/中期/长期）。每个窗口独立产出一份 `relativeReturn_N` 与 RS-rank 百分位。
- **基准**：基准可由用户在 `market_sector_watch.tracking_symbol` 配置（推荐宽基指数/行业 ETF 的统一证券代码），并写入 `benchmark_type='TRACKING_SYMBOL'` 与 `benchmark_symbol`；该基准需连续 `N+1` 个 `close_price` 才能构造 N 个日收益（见“输入”）。未配置 `tracking_symbol`（或配置但不足 `N+1` 个连续收盘价）时，使用**等权平均净值序列**作为基准，写 `benchmark_type='SECTOR_EQUAL_WEIGHT'`、`benchmark_symbol=null`。**等权基准与 rank set 必须同源同范围**：
  - **FULL_MARKET**：等权基准 = 同市场、同交易日历、同频率（CLOSE）的**全部有效板块**（全市场等权），附 `quality_reason='BENCHMARK_EQUAL_WEIGHT_FALLBACK'`。
  - **WATCHED_ONLY 降级**：等权基准 = **被关注集合（被 watch 板块）的等权**，与 rank set 同源（仅被 watch 板块），另附 `quality_reason='RANK_SCOPE_WATCHED_ONLY'`；**不得在全市场历史缺失时仍声称使用全市场等权基准**。
  不静默编造指数。
- **样本**：按市场交易日历取目标窗口内**连续 N 个交易日**（`asOfDate` 及其前 N-1 个交易日）的 CLOSE 快照；窗口内任一应有 CLOSE 缺失即不满足门槛（不前向填补、不跳过缺口），整条结果标 `quality_status=INSUFFICIENT_SAMPLE` 并降级展示，不产 HIGH 提醒。且同市场当日有效板块数 ≥ 阈值（默认 ≥ 5）。
- **失效**：provider 口径变更（行业分类或 provider 板块 ID 在窗口内变更）→ 在变更点断档并标 `ORIGIN_CHANGED`，不跨口径拼接；样本不足；成分大面积停牌；跨市场混算（必须按 CN/HK/US 各自 ZoneId 对齐交易日，禁止混入同一序列）；基准证券缺失（tracking_symbol 配置但无连续 `N+1` 个 `close_price`）→ 降级为与 rank set 同范围的等权基准（FULL_MARKET 用全市场等权、WATCHED_ONLY 用被关注集合等权）并记 `quality_reason`；合成净值序列出现 ≤0（对数无定义）→ 标 `INSUFFICIENT`。

公式（闭式，白盒）：

1. **单位假设与日收益口径**：`change_rate` 字段存储的是日涨跌率。若 provider 以百分数存储（如 `2.5` 表示 2.5%），计算前先除以 100 转小数；若已为小数则直接使用。统一约定 `sectorReturn(t) = change_rate(t) / 100`（按百分数口径，设计默认假设；若实施时确认 provider 存储为小数，则在公式层去掉 `/100`，并在 `parameter_hash` 中记录该口径）。`sectorReturn(t)` 即板块在交易日 t 的日收益率（小数）。
2. **合成净值序列**：以 `index(t0) = 1.0` 为基准点（t0 为窗口起始的前一基期），按 `index(t) = index(t-1) * (1 + sectorReturn(t))` 逐日累乘重建板块合成净值序列。基准侧使用相同方法构造 `index_baseline(t)`：tracking_symbol 用其日收益（`close_price(t)/close_price(t-1) - 1`，`close_price` 为 `stock_daily_bar` 收盘价列，需连续 `N+1` 个 `close_price` 覆盖窗口内 N 个交易日及其前一日）累乘；等权基准用当日**与 rank set 同范围的板块集合**（FULL_MARKET 用全市场有效板块、WATCHED_ONLY 用被 watch 板块）`sectorReturn` 的算术均值 `meanSectorReturn(t)` 累乘。
3. **N 日对数相对收益（返回单位为自然对数，可 ×100 表百分比）**：

   ```
   relativeReturn_N(t) = ln(index_sector(t) / index_sector(t-N)) - ln(index_baseline(t) / index_baseline(t-N))
   ```

   返回单位说明：`ln(x)` 表示 x 的自然对数（无量纲相对收益，自然单位）；需展示百分比时 `relativeReturn_N * 100`。本公式不引入 260 指数年化或标准化（减均值除标准差）操作，直接比较同窗口对数收益。
4. **RS-rank 百分位（average-rank，high-better）**：在同市场、同窗口、当日全部有效板块的 `relativeReturn_N` 集合上做升序平均秩排序（并列取平均秩），再换算百分位：

   ```
   rs_rank_percentile = (ascRank - 1) / (n - 1)
   ```

   其中 `n` 为参与排名的有效板块数，`ascRank` 为该板块的升序平均秩（最强板块 `ascRank = n` → 百分位 1.0，最弱 `ascRank = 1` → 百分位 0.0）。前端展示可再 `×100` 表 0~100。并列板块取相同百分位，避免并列被强制打散。

   **排名范围（rank_scope）**：RS-rank 的排名对象与 rank set 必须同源同口径——都是“各板块实际计算出的 N 日 `relativeReturn_N`”，**禁止用单日 `change_rate` 代替 N 日相对收益**。两种来源：
   - **(a) 首选 FULL_MARKET**：读取截至 `asOfDate` 的**连续 N 个交易日**全市场 CLOSE 榜单历史（`market_sector_ranking_batch`/`market_sector_ranking_item`，`snapshot_type='CLOSE'`），对**全市场每个板块**按其逐日 `change_rate` 重建 N 日合成净值（同 §5.1 公式 2/3）并计算 `relativeReturn_N`；只有同 market、window、trade date 下**实际计算成功**的 `relativeReturn_N` 才能进入 rank set。rank_scope=`FULL_MARKET`，涵盖完整市场，结果无范围降级。
   - **(b) WATCHED_ONLY 降级**：当全市场榜单历史不足 N 个连续交易日（无法为全市场板块重建 N 日净值）时，降级为仅对被 watch 板块的 CLOSE 快照（`market_sector_snapshot.change_rate`）重建 N 日净值并计算 `relativeReturn_N`，rank set 退化为“被 watch 板块集合”，rank_scope=`WATCHED_ONLY`，此时 `quality_reason='RANK_SCOPE_WATCHED_ONLY'`、`quality_status` 降级（如 `STALE`/`INSUFFICIENT_SAMPLE` 之一，由样本门槛判定），前端必须显式提示“仅在被关注板块范围内排名，不可与全市场直接比较”。**等权基准同步退化为被关注集合等权**：若同时使用等权基准，`benchmark_type='SECTOR_EQUAL_WEIGHT'` 且基准范围 = 被 watch 板块集合（与 rank set 同源），并在 `quality_reason` 同时注明 `RANK_SCOPE_WATCHED_ONLY`；不得在全市场历史缺失时仍声称使用全市场等权基准。
   - **样本不足（INSUFFICIENT_SAMPLE）**：当 FULL_MARKET 与 WATCHED_ONLY 都无法满足 §5.1 第 5 点的 N 个连续交易日门槛时，结果为 `INSUFFICIENT_SAMPLE`，不产出 RS-rank。

   注意：RS-rank 百分位是“在该 market+window 的可用板块集合内、以各板块实际 N 日 `relativeReturn_N` 排名”；`rank_scope` 字段记录实际使用的范围。`rank_scope` 取值 `FULL_MARKET` / `WATCHED_ONLY`。禁止把某一天的全市场 `change_rate`（如 `market_sector_ranking_item.change_rate` 的单日值）当作 N 日 `relativeReturn_N` 参与排名——两者指标口径不一致，会污染排名语义。
5. **缺失交易日处理（冻结规则）**：相对强弱以“按市场交易日历取目标窗口内连续 N 个交易日”为**唯一**口径。窗口内任何应有 CLOSE 快照缺失（如停牌/采集失败/交易日未对齐）→ 整条结果标 `quality_status=INSUFFICIENT_SAMPLE`，**不前向填补、不跳过缺口后继续声称连续有效**。本版本**不采用**“最近 N 个有效交易日”的前向扩展口径（若未来采用，须显式说明窗口前向扩展且不得再使用“连续 N 日”描述）。窗口完整 → 合成净值序列连续可用。
6. **零/负净值处理**：`index(t) > 0` 是对数前提。由于从 1.0 起累乘且 `1+sectorReturn > 0`（即日收益 > -100%），正常情况 `index` 恒正；若因数据异常出现 `index ≤ 0`，对数无定义，该板块标记 `INSUFFICIENT` 并跳过排名。
7. **基准不可用降级**：tracking_symbol 无连续 `N+1` 个 `close_price`（不足以构造 N 个日收益）→ 回退到与 rank set 同范围的等权基准（FULL_MARKET 全市场等权 / WATCHED_ONLY 被关注集合等权），写 `benchmark_type=SECTOR_EQUAL_WEIGHT` 与 `quality_reason='BENCHMARK_TRACKING_SYMBOL_INSUFFICIENT'`（WATCHED_ONLY 下另附 `RANK_SCOPE_WATCHED_ONLY`），保留结果但前端降级提示。

数值示例（完整演算，对应 golden 测试 GOLDEN-01）：

设 5 个交易日，板块日收益 `sectorReturn = [+0.02, -0.01, +0.03, +0.015, -0.005]`，基准日收益 `baselineReturn = [+0.01, +0.005, -0.002, +0.008, +0.003]`，`index(t0)=1.0`：

- 板块合成净值：`1.0 → 1.02 → 1.0098 → 1.040094 → 1.05569541 → 1.0504169329`
- 基准合成净值：`1.0 → 1.01 → 1.01505 → 1.0130199 → 1.0211240592 → 1.0241874314`
- 取 N=3，末日（t=5）相对收益，`t-N` 为第 2 个交易日：`relativeReturn_3 = ln(1.0504169329 / 1.0098) - ln(1.0241874314 / 1.01505) = 0.030473196953448606`（约 3.0473%）。

RS-rank 百分位示例（对应 golden 测试 GOLDEN-02）：5 个板块 `relativeReturn` 为 A=0.05、B=0.03、C=0.03、D=0.01、E=-0.02；升序平均秩为 E=1、D=2、B=C=3.5（并列）、A=5；百分位（`(ascRank-1)/(n-1)`，n=5）为 E=0、D=0.25、B=C=0.625、A=1.0，B 与 C 因并列取相同百分位。

- 同一板块同窗口每日收盘后产出一份衍生快照，幂等键 `(sector_identity, as_of_date, window, formula_version)`（含 `formula_version`，见 §6 版本血缘）。

### 5.2 轮动持续性（市场级 Spearman + 板块级位次指标，两层拆分）

本节明确拆分**市场级**（衡量整个市场板块位次稳定性）与**板块级**（衡量单板块位次序列特征）两层指标，分别落到独立表，互不混淆。

- **输入**：`market_sector_ranking_batch`（`snapshot_type=CLOSE`）下、`market_sector_ranking_item` 的 `rank_no`（V15，`INT NOT NULL`）明细，按 `(market_code, trade_date)` 分组、按 `rank_no` 升序。输入只读原始事实表。
- **窗口**：默认 5 / 10 / 20 个交易日（短/中/长）。每个窗口独立计算持续性。
- **基准**：不引入外部指数。市场级以同市场全板块 `rank_no` 向量为对象；板块级以单板块在窗口内的位次序列为对象。
- **样本**：连续收盘交易日数 ≥ 窗口长度；窗口内板块集合需对齐（见下）。低于门槛标 `INSUFFICIENT_SAMPLE` 并降级。
- **失效**：provider 口径变更（行业分类/provider 板块 ID 变更）→ 在变更点断档标记 `ORIGIN_CHANGED`；A 股集合竞价 `INTRADAY` 快照不得当作连续竞价 CLOSE 序列（必须只用 `snapshot_type=CLOSE`，集合竞价不计入连续位次）；板块集合不一致时按交集对齐并记录 `quality_reason`。

#### 5.2.1 市场级：sector_rotation_market_stability（Spearman ρ）

- **语义**：衡量**整个市场**板块位次的稳定性，不归属任何单板块。键为 `(market_code, trade_date, window, formula_version)`，**不按 sector_identity 存储**（市场级一行 = 一个市场一个交易日一个窗口）。
- **公式**：对相邻两个交易日（t-1, t），取该市场在两日都出现的板块集合（交集对齐，剔除任一日缺失的板块）的全板块 `rank_no` 向量，分别计算各自的**平均秩**向量（并列取平均秩）。**有并列时，Spearman ρ 定义为两个平均秩向量的 Pearson 相关系数**（无并列简化公式在并列下不成立）：

  ```
  ρ = Pearson(R_avg(t-1), R_avg(t))
    = Σ_i (R_i − R̄)(R'_i − R̄') / sqrt( Σ_i (R_i − R̄)² · Σ_i (R'_i − R̄')² )
  ```

  其中 `R_i`/`R'_i` 为板块 i 在两日的平均秩，`R̄`/`R̄'` 为两日平均秩向量的均值，`n` 为交集板块数。仅当两日所有板块位次均**无并列**时，才可使用无并列简化公式 `ρ = 1 − 6·Σd_i² / (n·(n²−1))`（`d_i` 为两日秩之差），该简化公式在并列下会错误高估/低估相关性。窗口内的持续性分取窗口内相邻交易日 ρ 的算术均值。
- **处理**：板块集合变化 → 取两日交集并记录 `valid_sample_size`（交集大小）与 `quality_reason='SECTOR_SET_CHANGED'`；任一日缺失该板块 → 从该相邻对中剔除（不参与该对 ρ）；分类口径变更 → 在变更点断开 ρ 序列并标 `ORIGIN_CHANGED`，不跨口径拼接；并列 `rank_no` → 用平均秩并走 Pearson 分支。
- **数值示例（GOLDEN-04）**：day1 全板块平均秩 `R1=[1,2,3,4,5]`，day2 原始值 `[2,2,4,4,5]` → 平均秩 `R2=[1.5,1.5,3.5,3.5,5]`。R2 存在并列，**必须用 Pearson 相关系数**：`R̄1=3`、`R̄2=3`，`Σ(x−x̄)(y−ȳ)=9`、`Σ(x−x̄)²=10`、`Σ(y−ȳ)²=9`，故 `ρ = 9/√(10·9) = 9/√90 ≈ 0.9486832981`。若误用无并列简化公式 `ρ = 1 − 6·1/(5·24) = 0.95`，会在并列下高估相关性，**不适用于本例**。该值归属 `market_code + trade_date + window`，存入市场级表 `sector_rotation_market_stability`，不重复存入任何 sector 记录。

#### 5.2.2 板块级：sector_rotation_sector_persistence（位次序列指标）

- **语义**：衡量**单板块**在窗口内位次序列的强度与稳定性。键为 `(sector_identity, as_of_date, window, formula_version)`。
- **指标（全部基于窗口内该板块的 `rank_no` 序列，n = 市场板块总数）**：
  - `mean_rank_percentile`：窗口内每日位次百分位 `rs_rank_percentile(t) = (n - rank_no(t)) / (n - 1)`（high-better，rank=1 最强 → 1.0，rank=n 最弱 → 0.0）的算术均值。
  - `rank_percentile_std_dev`：上述位次百分位序列的总体标准差（除以样本数，非 n-1）。
  - `top_bucket_occupancy_rate`：窗口内 `rank_no ≤ ceil(n·0.2)`（即头部桶，n 的 20% 上取整）的交易日占比。
  - `consecutive_leading_days`：从窗口末尾向前数，`rank_no == 1`（领涨）的连续交易日数。
  - `consecutive_lagging_days`：从窗口末尾向前数，`rank_no == n`（领跌）的连续交易日数。
  - `rank_change`：窗口末尾 `rank_no` 减窗口起始 `rank_no`（负值表示位次上升）。
- **处理**：板块集合变化 → 板块若在窗口内某日缺失，则该日不计入其位次序列，`valid_sample_size` 记录实际有效天数，`quality_reason` 注明；分类口径变更 → 断开序列标 `ORIGIN_CHANGED`；并列 `rank_no` 由 provider 已排定，直接使用。
- **数值示例（GOLDEN-03）**：某板块 5 日窗口 `rank_no = [3,2,2,1,1]`，市场板块总数 n=5：
  - 位次百分位序列 `[0.5, 0.75, 0.75, 1.0, 1.0]` → `mean_rank_percentile = 0.8`，`rank_percentile_std_dev = 0.18708286933869706`。
  - `top_bucket_occupancy_rate`（`rank ≤ ceil(5·0.2)=1`）= 2/5 = 0.4。
  - `consecutive_leading_days`（末尾 rank==1 连续）= 2。
  - `consecutive_lagging_days`（末尾 rank==5 连续）= 0。
  - `rank_change` = 1 - 3 = -2（位次上升 2）。

幂等键：市场级 `(market_code, trade_date, window, formula_version)`；板块级 `(sector_identity, as_of_date, window, formula_version)`（均含 `formula_version`，见 §6 版本血缘）。

### 5.3 收益贡献与交易集中度（严格拆分两类语义）

本节严格区分两类不同的语义，落到两张独立表，表名、字段、接口、页面均不得混用：

- **收益贡献（贡献）** = `weight · memberReturn`，刻画“板块涨幅由哪些成分解释”，是一种收益归因，单位是小数（收益），各成分加总逼近板块涨幅。
- **交易集中度（集中度）** = 某子集金额 / 总金额，刻画“成交/资金有多集中在少数成分”，是一种占比，无量纲（0~1），与涨跌方向无关。

不得把成交额占比称为“涨幅贡献/收益贡献”，不得把收益贡献称为“集中度”。

#### 5.3.1 真实收益贡献：sector_member_return_contribution

- **输入**：`market_sector_member_snapshot` 的 `current_price` / `previous_close` / `change_rate` / `turnover_amount` / `net_inflow` / `total_shares` / `circulating_shares`（V14）与 `market_sector_snapshot` 的 `change_rate`（板块聚合涨跌幅）。输入只读原始事实表。
- **窗口**：默认单快照当日（当日一份成分快照即可计算）+ 可选 N 日均值（默认 N=5，平滑贡献度排名）。
- **基准**：基准为板块自身聚合值 `market_sector_snapshot.change_rate`（板块涨跌幅），`sum_contribution` 与之对齐，残差 `residual = sector_return − sum_contribution`。
- **权重（白盒，优先基本面权重，缺失降级等权）**：

  ```
  rawWeight(i, t-1) = previousClose(i, t-1) · circulatingShares(i, t-1)
  weight(i, t-1)    = rawWeight(i, t-1) / Σ_j rawWeight(j, t-1)        （归一化到和为 1）
  ```

  当某成分 `circulating_shares` 缺失（null）时，对该板块全部成分降级为等权 `weight(i) = 1 / m`（m 为有效成分数），并记录 `weight_method='EQUAL_WEIGHT_FALLBACK'` 与 `quality_status`；否则 `weight_method='FREEFLOAT_PRICE'`。本设计不引入外部总股本数据源（ASSUMPTION）。
- **收益贡献（白盒）**：

  ```
  memberReturn(i, t)    = change_rate(i, t) / 100        （与 §5.1 同口径；小数）
  memberContribution(i, t) = weight(i, t-1) · memberReturn(i, t)
  sum_contribution       = Σ_i memberContribution(i, t)
  sector_return          = change_rate(sector, t) / 100  （取自 market_sector_snapshot.change_rate）
  residual               = sector_return − sum_contribution
  ```

  `residual` 反映成分加总与板块聚合口径的差异（排除成分、口径误差、延迟行情不一致）。`memberContribution` 可正可负（与成分涨跌同号），单位为小数收益。
- **样本**：有效成分数门槛 CN ≥ 8、HK/US ≥ 5，或 ≥ `expected_member_count` 50%。低于门槛标 `INSUFFICIENT_SAMPLE` 并降级，不产 HIGH 提醒。
- **失效**：成分停牌（按 `trade_status` 与 `is_delayed` 排除并计 `excluded_member_count`）；延迟行情导致 `change_rate`/价格口径不一致 → 降级；累计值跨日重置未处理时收益贡献按当日快照计算（不跨日累计）。
- **数值示例（GOLDEN-05）**：3 个成分，`weights(t-1) = [0.5, 0.3, 0.2]`（已归一），`memberReturns = [0.04, -0.02, 0.06]`：
  - `memberContribution = [0.5·0.04, 0.3·(-0.02), 0.2·0.06] = [0.02, -0.006, 0.012]`。
  - `sum_contribution = 0.026`；若板块加权收益也是 0.026，则 `residual ≈ 0`（tol 1e-9）。
  - 若实际 `sector_return = 0.05`（因排除了部分成分），则 `residual = 0.05 − 0.026 = 0.024`。

#### 5.3.2 交易集中度：sector_turnover_concentration

- **输入**：`market_sector_member_snapshot` 的 `turnover_amount` / `net_inflow`（V14）与 `market_sector_snapshot` 的 `total_turnover_amount`（V14，板块聚合）。输入只读原始事实表。
- **窗口**：默认单快照当日 + 可选 N 日均值。
- **基准**：成交额集中度的基准为板块 `total_turnover_amount`；净流入集中度的基准为成分绝对流量 `absSum`（不用可能为零/负的板块净流入总额）。
- **成交额集中度（占比，白盒）**：

  ```
  top_k_turnover_share = Σ top-K 成分 turnover_amount / 板块 total_turnover_amount
  ```

  top-K 为按 `turnover_amount` 降序的前 K 名（默认 K=3/5）。这是“集中度”（占比），**不是**“收益贡献/涨幅贡献”。
- **净流入集中度（强制不除以可能为零/负的板块净流入总额，白盒）**：板块 `total_net_inflow` 可能为零或负，因此净流入集中度统一以**绝对流量** `absSum = Σ |member net_inflow|` 为分母，避免除以零或负号失真：

  ```
  positiveFlowConcentration   = Σ 正净流入成分 net_inflow / absSum
  negativeFlowConcentration   = |Σ 负净流入成分 net_inflow| / absSum
  absoluteFlowConcentration(topK) = Σ top-K |member net_inflow| / absSum
  ```

  - **零分母处理（强制）**：当所有成分净流入均为零（`absSum == 0`）时，净流入集中度不可计算，**不除零**，直接标记 `quality_status=INSUFFICIENT` 并附 `quality_reason='ZERO_NET_INFLOW'`，三个集中度字段置空。
- **样本**：与 §5.3.1 同。
- **失效**：成分停牌/延迟排除并计 `excluded_member_count`；累计值跨日重置须先用相邻快照差分或仅用 CLOSE 快照对齐，避免虚假集中度。
- **数值示例（GOLDEN-06）**：4 个成分净流入 `net_inflows = [+100, +50, -30, +20]`：
  - `posSum = 170`，`negSum = -30`，`absSum = 200`，`total = 140`。
  - `positiveFlowConcentration = 170/200 = 0.85`。
  - `negativeFlowConcentration = |-30|/200 = 0.15`。
  - `absoluteFlowConcentration(top2) = (100+50)/200 = 0.75`。
  - 全零情形 `net_inflows = [0,0,0,0]` → `absSum=0` → 返回 `INSUFFICIENT`，不除零。

幂等键：收益贡献表 `(sector_identity, trade_date, window, formula_version)`；交易集中度表 `(sector_identity, trade_date, window, formula_version)`（均含 `formula_version`，见 §6 版本血缘）。

### 5.4 量价确认（六状态）

- **输入**：`market_sector_snapshot` 的 `change_rate`（板块涨跌方向，V14）与 `total_turnover_amount` / `total_volume`（板块成交额/成交量，V14）。区间增量须处理日内累计跨日重置：当日成交优先用 CLOSE 快照或相邻快照差分（同桶差）取值，避免日内累计值跨日重置导致的虚假信号。输入只读原始事实表。
- **窗口**：当日（量价是否同向）+ 近 5 日 CLOSE 均量（判断是否放量/缩量）。默认窗口为当日 + 近 5 日 CLOSE 均值。
- **基准**：基准为板块自身近期均量（近 5 日 `total_turnover_amount` CLOSE 均值），判断当日成交相对历史是放量还是缩量。
- **样本**：有可用成交数据且非全停牌；近 5 日 CLOSE 成交快照数 ≥ 3。低于门槛标 `INSUFFICIENT_SAMPLE`（映射到 `INSUFFICIENT` 状态）并降级。
- **失效**：累计值（成交额/成交量）跨日重置未处理（必须 CLOSE 或同桶差）；延迟行情（`is_delayed=true`）导致成交失真 → 保留状态、附 `quality_reason='DELAYED_QUOTE'`，不改变状态；小盘低成交板块（成交额绝对值过低）易产生虚假信号 → 降级展示，不产 HIGH 提醒。

公式（白盒，六状态判定）：

```
turnoverRatio = todayTurnover / mean(turnover over 5-day CLOSE window)        （量比）
upVolume      = turnoverRatio >= 1.1                                            （默认阈值，放量）
```

状态映射（涨跌方向 × 是否放量）：

| 板块涨跌 | 是否放量 | 状态 | 语义 |
| --- | --- | --- | --- |
| `changeRate > 0` | upVolume | `UP_CONFIRMED` | 涨且放量，方向得到成交确认 |
| `changeRate > 0` | 非 upVolume | `UP_UNCONFIRMED` | 涨但缩量，方向未得成交确认 |
| `changeRate < 0` | upVolume | `DOWN_CONFIRMED` | **跌且放量是方向确认，不是背离** |
| `changeRate < 0` | 非 upVolume | `DOWN_UNCONFIRMED` | 跌但缩量，方向未得成交确认 |
| `changeRate == 0` | 任意 | `NEUTRAL` | 持平 |
| 样本不足/成交缺失/零分母 | — | `INSUFFICIENT` | 成交数据不足或近 5 日均量为零 |

**关键语义**：下跌放量（`changeRate < 0` 且 `turnoverRatio >= 1.1`）记为 `DOWN_CONFIRMED`（方向确认），**不是**背离。本设计不使用 `CONFIRMED` / `DIVERGENCE` 作为唯二状态。

数值示例（GOLDEN-07，默认阈值 1.1）：

| changeRate | turnoverRatio | 状态 |
| --- | --- | --- |
| +0.02 | 1.3 | `UP_CONFIRMED` |
| +0.02 | 0.9 | `UP_UNCONFIRMED` |
| -0.02 | 1.3 | `DOWN_CONFIRMED` |
| -0.02 | 0.9 | `DOWN_UNCONFIRMED` |
| 0.0 | 1.0 | `NEUTRAL` |
| 样本不足/成交缺失 | — | `INSUFFICIENT` |

- 当日累计值处理：使用 CLOSE 快照成交或同一 `snapshot_bucket_time` 相邻快照的差分作为当日成交，避免日内累计值在收盘后重置导致 `todayTurnover` 错误。
- 幂等键 `(sector_identity, trade_date, formula_version)`（含 `formula_version`，见 §6 版本血缘）。

### 5.5 异动提醒（Anomaly Alert）

- **输入**：上述派生指标（相对强弱 `relativeReturn_N`/RS-rank、市场级 Spearman 与板块级持续性、收益贡献与交易集中度、量价确认六状态）与原始快照（`market_sector_snapshot` 的 `change_rate` / `total_turnover_amount` / `total_net_inflow`）。输入只读原始事实表与衍生表。
- **窗口**：当日 vs 近 N 日均值/标准差（默认 N=20）。窗口内做 Z-score。
- **基准**：基准为板块自身历史分布（近 N 日该板块某指标的均值与标准差），不引入外部阈值集合。
- **样本**：历史样本 ≥ 阈值（默认 ≥ 20 个有效交易日）才能算 Z-score；否则仅阈值判断（如涨跌幅超过固定阈值），并在提醒中标注 `EVIDENCE=THRESHOLD_ONLY`。
- **失效**：样本不足（< 20）只能阈值判断；数据陈旧（最新快照 `quote_time` 超过阈值）→ 降级或不产提醒；延迟行情导致指标失真。

公式要点（白盒，阈值 + Z-score）：

- Z-score：`z = (当日值 - 近N日均值) / 近N日标准差`，`|z|` 超过阈值（默认 2.0）记为异常。
- 阈值：涨跌幅绝对值超过固定阈值（如 |change_rate| > 3%）、量比超过阈值（如 > 2.0）。
- 复用 `market_data_alert`（V7），新增 `alert_type=SECTOR_RS_REVERSAL` / `SECTOR_VOLUME_CONFIRMATION` / `SECTOR_TURNOVER_CONCENTRATION` / `SECTOR_RANK_JUMP` 等；`severity` 取 `INFO/WARN/HIGH`；`trigger_value_json` 存派生指标快照与上下文。

> **强制声明**：异动提醒是观察提醒，**不是买卖指令、不是投资建议**，不预测收益、不产生交易动作。前端必须在每条提醒旁标注“仅供参考，不构成投资建议”。

## 6. 数据模型（规划 V19+，独立新表，只读原始事实）

> 状态：**规划 V19+**，未实现。以下表为独立新表，绑定 Flyway V19+（V19、V20、...，具体由实现计划 ST-1 拆分），不复用 V1-V18 既有版本号。所有衍生表只读原始事实表，不写回原始事实表。规划区域不含“已实现”描述。

### 6.1 统一版本血缘列（所有衍生表强制含下列字段）

每一张规划衍生表（§6.2~§6.7）都必须包含下列版本与血缘列，公式升级写新 `formula_version` 行，旧行 `is_latest=false` 且 `superseded_at` 填值，**绝不覆盖**：

- `formula_code` varchar(64) — 公式标识（如 `RELATIVE_RETURN_LOG`、`ROTATION_SPEARMAN`、`MEMBER_RETURN_CONTRIBUTION`、`TURNOVER_CONCENTRATION`、`VOLUME_CONFIRMATION`）。
- `formula_version` varchar(16) — 公式版本（如 `v1`、`v2`），幂等键必含。
- `parameter_hash` varchar(64) — 输入参数（窗口、阈值、基准、权重口径、单位假设等）的内容哈希，便于重算命中。
- `source_provider` varchar(32) — 原始事实 provider。
- `source_batch_id` bigint 或 `source_snapshot_id` bigint — 来源榜单批次/快照标识（取与该表最贴近的来源粒度）。
- `source_date_range` varchar(64) — 来源日期区间（如 `2026-06-01~2026-07-31`）。
- `calculated_at` datetime — 计算时间。
- `quality_status` varchar(32) — `OK`/`INSUFFICIENT_SAMPLE`/`INSUFFICIENT`/`STALE`/`ORIGIN_CHANGED`。
- `quality_reason` varchar(128) — 降级原因（如 `SECTOR_SET_CHANGED`/`ZERO_NET_INFLOW`/`DELAYED_QUOTE`/`BENCHMARK_EQUAL_WEIGHT_FALLBACK`）。
- `valid_sample_size` int — 实际有效样本数。
- `is_latest` boolean — 是否当前最新版本（默认 true，被新版本取代后置 false）。
- `superseded_at` datetime — 被新版本取代的时间（可空）。

版本策略（所有衍生表统一）：

- **重算**：同 `formula_version` + 同幂等键命中既有行 → 覆盖业务指标字段（`is_latest=true`），不新增行。
- **版本升级**：写入新 `formula_version` 行（`is_latest=true`），将同幂等键下旧 `formula_version` 行置 `is_latest=false` 并填 `superseded_at`，旧结果保留可查、不被覆盖。
- **废弃**：停掉对应计算与查询即可（旧行保留，不回滚也不污染原始事实）。
- **历史查询**：按 `(幂等键前缀, formula_version)` 或 `is_latest=false` 查询历史版本；默认查询返回 `is_latest=true`。

### 6.2 sector_relative_strength_snapshot（规划 V19+）

用途：板块相对强弱（N 日对数相对收益 `relativeReturn_N` + RS-rank 百分位）衍生快照。数据来源按 rank_scope 区分（只读原始事实表，不写回）：`FULL_MARKET` 读连续 N 个交易日全市场 CLOSE 榜单历史 `market_sector_ranking_batch`/`market_sector_ranking_item`（`snapshot_type='CLOSE'`，取各板块 `change_rate`）；`WATCHED_ONLY` 读被关注板块 CLOSE 快照 `market_sector_snapshot`（`trigger_type='CLOSE'`，取 `change_rate`）；tracking symbol 基准读 `stock_daily_bar.close_price`（需连续 `N+1` 个收盘价）。

核心字段：

- `id` bigint 主键 auto_increment
- `sector_identity` varchar(96) — 板块稳定身份（provider_code + provider_sector_id 或 watch_id 派生）
- `market_code` varchar(8) — CN/HK/US
- `as_of_date` date — 基准交易日
- `window` smallint — 窗口长度（20/50/120）
- `benchmark_type` varchar(32) — `TRACKING_SYMBOL` / `SECTOR_EQUAL_WEIGHT`
- `benchmark_symbol` varchar(32) — 实际使用的基准统一证券代码（可空）
- `relative_return_n` decimal(20,10) — N 日对数相对收益（自然单位，前端 ×100 表百分比）
- `rs_rank_percentile` decimal(20,6) — 0~1（前端 ×100 表 0~100）
- `rank_scope` varchar(16) — RS-rank 排名范围：`FULL_MARKET`（连续 N 个交易日全市场 CLOSE 榜单历史重建各板块 N 日合成净值与 `relativeReturn_N` 后排名，含全市场全部板块）/ `WATCHED_ONLY`（全市场历史不足时降级，仅 WATCHED 板块 CLOSE 快照重建排名，附 `quality_reason='RANK_SCOPE_WATCHED_ONLY'`，降级展示）
- 统一版本血缘列（见 §6.1）+ `created_at` / `updated_at` datetime

索引/幂等：

- unique `uk_sector_rs(sector_identity, as_of_date, window, formula_version)` — 含 `formula_version`
- index `idx_sector_rs_market_date(market_code, as_of_date)`

### 6.3 sector_rotation_market_stability（规划 V19+，市场级）

用途：**市场级**轮动稳定性（相邻交易日全市场 `rank_no` 向量的 Spearman ρ），键为 `(market_code, trade_date, window, formula_version)`，**不存 sector_identity**，不重复存入任何 sector 记录。只读 `market_sector_ranking_batch`/`market_sector_ranking_item`（CLOSE）。

核心字段：

- `id` / `market_code` / `trade_date` date / `window`(5/10/20)
- `rank_spearman_mean` decimal(20,6) — 窗口内相邻交易日 ρ 的均值
- 统一版本血缘列（见 §6.1）+ `created_at` / `updated_at`

索引/幂等：

- unique `uk_sector_rotation_market(market_code, trade_date, window, formula_version)` — 市场级，无 sector_identity
- index `idx_sector_rotation_market_date(market_code, trade_date)`

### 6.4 sector_rotation_sector_persistence（规划 V19+，板块级）

用途：**板块级**位次序列指标，只读 `market_sector_ranking_item`（CLOSE `rank_no`）。

核心字段：

- `id` / `sector_identity` / `market_code` / `as_of_date` / `window`(5/10/20)
- `mean_rank_percentile` decimal(20,6) — 窗口内位次百分位均值
- `rank_percentile_std_dev` decimal(20,6) — 位次百分位总体标准差
- `top_bucket_occupancy_rate` decimal(20,6) — 头部桶占用率（rank ≤ ceil(n·0.2)）
- `consecutive_leading_days` int — 末尾 rank==1 连续天数
- `consecutive_lagging_days` int — 末尾 rank==n 连续天数
- `rank_change` int — 末-首 rank_no
- 统一版本血缘列（见 §6.1）+ `created_at` / `updated_at`

索引/幂等：

- unique `uk_sector_rotation_sector(sector_identity, as_of_date, window, formula_version)` — 含 `formula_version`
- index `idx_sector_rotation_sector_market_date(market_code, as_of_date)`

### 6.5 sector_member_return_contribution（规划 V19+，收益贡献）

用途：板块真实收益贡献（`weight · memberReturn`），只读 `market_sector_member_snapshot`/`market_sector_snapshot`，不写回。

核心字段：

- `id` / `sector_identity` / `market_code` / `trade_date` date / `window`(1/5)
- `weight_method` varchar(32) — `FREEFLOAT_PRICE` / `EQUAL_WEIGHT_FALLBACK`
- `sum_contribution` decimal(20,10) — 成分贡献之和
- `sector_return` decimal(20,10) — 取自 snapshot `change_rate`（小数）
- `residual` decimal(20,10) — `sector_return − sum_contribution`
- `top_contributors_json` text — 成分明细（canonical_symbol、weight、memberReturn、memberContribution）
- `excluded_member_count` int / `valid_member_count` int
- 统一版本血缘列（见 §6.1）+ `created_at` / `updated_at`

索引/幂等：

- unique `uk_sector_contribution(sector_identity, trade_date, window, formula_version)` — 含 `formula_version`
- index `idx_sector_contribution_market_date(market_code, trade_date)`

### 6.6 sector_turnover_concentration（规划 V19+，交易集中度）

用途：板块交易集中度（成交额占比 + 正/负/绝对净流入集中度），只读 `market_sector_member_snapshot`/`market_sector_snapshot`，不写回。**禁止**把成交额占比称为“涨幅贡献/收益贡献”。

核心字段：

- `id` / `sector_identity` / `market_code` / `trade_date` / `window`(1/5)
- `top_k_turnover_share` decimal(20,6) — top-K 成分成交额 / 板块 total_turnover_amount
- `positive_flow_concentration` decimal(20,6) — 正净流入 / absSum（可空）
- `negative_flow_concentration` decimal(20,6) — |负净流入| / absSum（可空）
- `absolute_flow_concentration` decimal(20,6) — top-K |净流入| / absSum（可空）
- `top_k` int — 默认 3/5
- `top_concentrators_json` text — 成分明细（canonical_symbol、turnover_amount、net_inflow、share）
- `excluded_member_count` int / `valid_member_count` int
- 统一版本血缘列（见 §6.1）+ `created_at` / `updated_at`

索引/幂等：

- unique `uk_sector_concentration(sector_identity, trade_date, window, formula_version)` — 含 `formula_version`
- index `idx_sector_concentration_market_date(market_code, trade_date)`

### 6.7 sector_volume_confirmation_snapshot（规划 V19+，六状态）

用途：板块量价确认（六状态 + 量比），只读 `market_sector_snapshot`，不写回。

核心字段：

- `id` / `sector_identity` / `market_code` / `trade_date`
- `change_rate` decimal(20,8)
- `turnover_amount` decimal(30,6) — 当日板块成交额（已处理累计跨日重置）
- `turnover_ratio` decimal(20,6) — 当日 / 近 5 日 CLOSE 均值
- `confirmation_status` varchar(24) — `UP_CONFIRMED`/`UP_UNCONFIRMED`/`DOWN_CONFIRMED`/`DOWN_UNCONFIRMED`/`NEUTRAL`/`INSUFFICIENT`
- 统一版本血缘列（见 §6.1）+ `created_at` / `updated_at`

索引/幂等：

- unique `uk_sector_volume(sector_identity, trade_date, formula_version)` — 含 `formula_version`
- index `idx_sector_volume_market_date(market_code, trade_date)`

### 6.8 复用 market_data_alert（不新建告警表）

异动提醒复用 V7 已实现的 `market_data_alert`，新增 `alert_type=SECTOR_*`（`SECTOR_RS_REVERSAL`、`SECTOR_VOLUME_CONFIRMATION`、`SECTOR_TURNOVER_CONCENTRATION`、`SECTOR_RANK_JUMP` 等）。`severity` 取 `INFO/WARN/HIGH`；`trigger_value_json` 存派生指标上下文与 `formula_code`/`formula_version`。**不新建第二套告警表**。读服务只读原始事实与衍生表，写 `market_data_alert`。

### 6.9 MyBatis / Flyway 边界

- 新表走更高 Flyway 版本 V19+（V19、V20...），SQL 放在 `src/main/resources/db/migration/V19__*.sql`（具体由 ST-1 决定）。
- MyBatis XML 放在 `src/main/resources/mapper/`（如 `SectorRelativeStrengthSnapshotMapper.xml`）。
- 主键统一 `id bigint auto_increment`；金额/价格 `decimal(20,6)`；时间 `created_at`/`updated_at`。
- 衍生读服务只读原始事实表，禁止 UPDATE/写回/回写/覆盖原始事实表。

## 7. API 设计（规划，未实现 — 详见 MARKET_DATA_API.md §5）

本节为概述；详细端点、请求/响应示例与错误码见 `docs/api/MARKET_DATA_API.md` §5（规划/未实现）。

- GET 板块相对强弱（按市场/窗口查询排行与单板块详情）
- GET 板块轮动持续性：市场级稳定性 + 板块级位次指标（分别端点）
- GET 板块收益贡献（按市场查询排行与单板块成分明细）
- GET 板块交易集中度（按市场查询排行与单板块集中度明细）
- GET 板块量价确认（按市场查询排行与单板块六状态）
- GET 板块异动提醒：复用现有 `/api/v1/market-data/alerts?alertType=SECTOR_*` 查询

统一前缀 `/api/v1/market-data/sector-analytics/*`（规划），统一响应 `ApiResponse<T>`。所有响应体携带 `formula_code`/`formula_version`/`quality_status`/`quality_reason`。错误码语义：分析 API **不直接外联 provider，不返回 `MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED`**；请求参数错误（market/window/date 非法）→ `VALIDATION_ERROR`（HTTP 400）；公式版本不存在（请求的 `formulaVersion` 未落库）→ 规划码 `MARKET_SECTOR_ANALYTICS_FORMULA_VERSION_NOT_FOUND`（**HTTP 404**，与参数非法返回 400 严格区分，语义即此、不再留待实现时另行决定）；尚无衍生数据 → 200 + `quality_status=NO_DERIVED_DATA`；原始不足 → 200 + `quality_status=INSUFFICIENT_RAW`；陈旧 → 200 + `quality_status=STALE`；真正无法查询 → 规划码 `MARKET_SECTOR_ANALYTICS_DATA_UNAVAILABLE`；样本不足 → 200 + `quality_status`/`quality_reason`。规划码遵循 `MARKET_SECTOR_*` 前缀（本轮不新增 ErrorCodeEnum 值，待 ST-2 落库为枚举值）。所有端点标注“规划/未实现”。

## 8. 前端页面与图表设计（规格 + mock 契约建议）

> 前端在**独立仓库**（`docs/FRONTEND_ARCHITECTURE.md`）。本仓库只给规格与 mock 契约建议，实际前端实现留作 ST-4 子任务。

页面结构（建议）：

- “板块分析”一级页面，含子标签：相对强弱 / 轮动持续性 / 收益贡献 / 交易集中度 / 量价确认；右上角“异动提醒”入口（铃铛 + 列表）。“收益贡献”与“交易集中度”是两个独立子标签，不得合并或混称。
- 每个子标签顶部显示市场切换（CN/HK/US）、窗口切换（20/50/120 等）、基准说明（`benchmark_type` 标签）与当前公式版本（`formula_version`）。

图表建议：

- 相对强弱：板块 RS 热力图（市场 × 板块，颜色编码 RS-rank 百分位）+ 板块相对收益排名条形图（带窗口切换）。
- 轮动持续性：板块位次带图（ridgeline/位次随时间漂移）+ 市场级 Spearman 时序折线 + 轮动桑基图（领涨→领跌流向）。
- 收益贡献：堆叠柱状图（板块涨幅 = 各成分收益贡献堆叠，含正负贡献）+ 成分明细表。
- 交易集中度：top-K 成交额占比饼图 + 正/负/绝对净流入集中度对比柱 + 集中成分明细表。
- 量价确认：散点图（x=change_rate, y=turnover_ratio，颜色编码六状态 `UP_CONFIRMED`/`UP_UNCONFIRMED`/`DOWN_CONFIRMED`/`DOWN_UNCONFIRMED`/`NEUTRAL`/`INSUFFICIENT`）。
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
