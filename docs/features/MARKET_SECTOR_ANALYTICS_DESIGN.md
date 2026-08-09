# 板块分析（每日总览 / 相对强弱 / 资金趋势 / 轮动持续性 / 量价确认 / 异动提醒）设计基线

> 版本：v1.1 · 状态：专家复审修订（P1.7，未实现；只有 P1.7-A 前置门禁通过后才可开发 P1.7-B）
> 关联：`../BUILD_CHECKLIST.md`、`../api/MARKET_DATA_API.md` §5（规划）、`../DATABASE_DESIGN.md` 板块分析规划表（V19+）、`../development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md`、`MARKET_SECTOR_CATALOG_DESIGN.md`、`MARKET_SECTOR_AUTOMATIC_COLLECTION_DESIGN.md`、`MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`、`MARKET_ALERT_RULES_DESIGN.md`。
> 前置事实：板块原始事实表（V14/V15）`market_sector_watch`、`market_sector_snapshot`、`market_sector_member_snapshot`、`market_sector_ranking_config`、`market_sector_ranking_batch`、`market_sector_ranking_item` 已落地；`market_data_alert`（V7）已存在并可复用；最新 migration V18，本设计规划 V19+。

## 0. 设计状态与定位

本文档是**规划/未实现**的板块分析层（P1.7）设计基线。P1.7 分成两个强制阶段：P1.7-A 先修复数据单位、完整性、稳定身份、收盘快照、交易日历和血缘；P1.7-B 才实现每日总览与可解释指标。P1.7-A 未验收时，任何分析结果都不得标记为 `OK` 或“全市场”。

本系统是**只读研究工具**：所有派生指标只读原始事实表，**禁止写回** `market_sector_snapshot`、`market_sector_member_snapshot`、`market_sector_ranking_*` 与 `stock_*` 原始事实表。衍生结果只存新表，可重算、可丢弃、可下线，原始事实不可变。第一版不生成买卖指令、不自动交易、不连接券商、不读取密钥、不引入黑盒 ML 评分。

> **风险声明**：本系统是决策辅助工具，所有指标与提醒仅为观察提示，不构成投资建议，不预测收益，不产生交易动作。

## 1. 用户目标与场景

| 角色 | 目标 | 场景 |
| --- | --- | --- |
| 个人投资者 | 决策辅助 | 盘后比较同市场各板块的相对强弱与轮动持续性，识别当前领涨/领跌板块，辅助做“看哪个板块”的研判 |
| 个人投资者 | 只读研究 | 查看资金持续流入/流出、交易集中度和量价是否同向确认，做复盘与归因 |
| 个人投资者 | 异动观察 | 在板块出现异常涨跌、量能放大、强弱反转时收到 INFO/WARN/HIGH 提醒，作为后续跟进提示 |

系统目标边界：

- **决策辅助、只读研究、不生成买卖指令**。所有指标只读已落库的板块原始事实，做可解释白盒计算并落库为衍生指标快照；提醒事件复用 `market_data_alert`，是观察提醒而非交易信号。
- 输入只消费**已落库**的板块收盘/成分/榜单快照与基准证券日 K，不新增 provider 外联、不接收实时推送、不在分析窗口内反向调用 provider。

## 2. 范围与非目标

做（第一版 MVP）：

1. P1.7-A 数据就绪：冻结收益率单位、证明榜单完整性、建立稳定板块身份、补齐收盘快照语义、规范交易日历/时点/币种/累计字段和计算血缘。
2. P1.7-B 每日决策总览：同一屏回答领涨/领跌、相对强弱、排名变化、轮动持续性、资金方向、量价状态和数据质量。
3. 白盒指标：共同基准下的相对强弱、规范化位次轮动、资金趋势、交易集中度、严格滞后的量价确认和异动提醒。
4. REST 查询接口与前端页面；所有结果携带公式版本、参数哈希、计算批次、质量状态及可操作原因。
5. 缺失数据、停牌、延迟、跨市场时区、样本不足、口径变化和上游阻断的统一失效处理。

不做（第一版明确非目标）：

- **不自动交易**、不生成买卖指令、不下单、不连券商、不读密钥、不承诺收益。
- **不做不可解释黑盒 ML 评分**，不引入机器学习隐式打分或因子暗箱；所有公式必须白盒可复算。
- **不写回原始事实表**（`stock_*`、`market_sector_*` 事实表、`market_sector_ranking_*`）。
- 不新增 provider 外联通道、不接实时推送。
- 不替代 P1.5/P1.6 的原始事实采集；分析层只消费其已落库结果。
- 不在前端仓库做实际实现（本仓库只给规格与 mock 契约建议）。
- P1.7 MVP 不实现成分收益贡献。现有事实表无法证明 `t-1` point-in-time 成分和自由流通市值，贸然计算会产生未来函数；该能力进入 P1.7-C，待时点数据契约验收后单独设计。

## 3. 四视角结论

### 3.1 产品经理视角

- 板块分析是 P1.5 目录与 P1.6 排行样本之上的“研判层”：用户已有板块涨跌与榜单，还需要回答“谁在持续强、谁只是当日脉冲、资金与量价是否确认、哪些异常值得复核”。MVP 不回答成分收益归因。
- 必须保持“决策辅助”定位：任何指标与提醒都不得演化为买卖指令；前端必须显式标注“不构成投资建议”。
- 异动提醒是对“值得多看一眼”的提示，而非择时信号；提醒强度分级（INFO/WARN/HIGH），并允许用户忽略。

### 3.2 量化研究视角

- 相对强弱采用共同 rank set 等权基准下的 N 日对数相对收益与 RS-rank；`tracking_symbol` 只用于详情对照，禁止混入公共横截面排名。
- 轮动持续性拆为两层：排行样本级（相邻交易日按稳定身份对齐、由 `change_rate` 重算平均秩后的 Spearman ρ）与板块级（位次百分位均值/波动、头部桶占用率、连续领涨/领跌天数、位次变化），区分“连续强”与“一日强”。
- MVP 严格区分资金趋势与交易集中度；收益贡献后置到具备 point-in-time 权重的 P1.7-C。
- 所有公式必须显式记录输入字段、时间窗口、基准、样本最小门槛与失效场景，以保证可复盘、可证伪。

### 3.3 数据工程视角

- 严格区分三层：**原始事实**（不可变，P1.5/P1.6 落库）、**衍生指标**（V19+ 新表，幂等可重算）、**提醒事件**（复用 `market_data_alert`）。
- 衍生计算只读原始事实表；禁止 UPDATE/写回/回写/覆盖原始事实表。衍生表用更高 Flyway 版本（V19+），主键 `id bigint auto_increment`，金额 `decimal(20,6)`，幂等键覆盖 `(sector_identity_id, as_of_date/trade_date, window)`。
- 时序以收盘快照序列为主轴（`market_sector_ranking_batch` CLOSE + `market_sector_snapshot`）；盘中 `INTRADAY` 快照仅用于当日异动与成交量确认的区间增量计算，且必须处理日内累计值跨日重置。
- 样本不足/停牌/缺失/口径变更统一标 `INSUFFICIENT_SAMPLE` 或 `STALE`/`ORIGIN_CHANGED` 并降级，不静默编造。

### 3.4 架构视角

- 分析层归属推荐为 `com.quant.trade.marketdata.analysis` 子包（与现有 sector 代码同模块、同事务边界、不直连 provider），其中衍生指标计算落在 `analysis/derived/`，异动提醒评估器落在 `analysis/alert/`，调度器落在 `marketdata/scheduler/`（与现有分钟线 scheduler 同包），逻辑稳定后再评估是否上移到顶层 `factor/indicator`（`AGENTS.md` 推荐包，当前未创建）。本设计采用 marketdata.analysis 子包，避免跨模块依赖与过早抽象。
- 读服务只读原始事实表 + 写衍生表 + 复用 `market_data_alert`；不写原始表。Scheduler 仅做衍生重算与提醒评估，不调用 provider。
- 与 `MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md` 的“行情资产三层（原始事实/可复用衍生统计/任务质量治理）”一致：本层属于“可复用衍生统计”，质量治理复用 `market_data_alert`。

## 4. 原始事实 / 衍生指标 / 提醒事件 三层模型

| 层 | 存储 | 读写边界 | 可变性 |
| --- | --- | --- | --- |
| 原始事实 | `market_sector_snapshot`、`market_sector_member_snapshot`、`market_sector_ranking_batch`、`market_sector_ranking_item`、`stock_daily_bar`、`stock_quote_snapshot` | 分析层**只读**这些原始事实表 | P1.7-A 起不可变、只追加；现有 watch 级联删除必须先迁移 |
| 数据治理 | V19+：稳定板块身份/分类版本、榜单完整性字段、计算运行及输入 manifest | 证明单位、范围、日历、来源集合与发布状态 | 可审计，不伪造完整性 |
| 衍生指标 | V19+ 新表：`sector_relative_strength_snapshot`、`sector_rotation_market_stability`、`sector_rotation_sector_persistence`、`sector_capital_flow_trend`、`sector_turnover_concentration`、`sector_volume_confirmation_snapshot` | 读取合格事实 → 计算运行 → 原子发布 | 可重算、可丢弃、可下线 |
| 提醒事件 | 复用 `market_data_alert`（V7），新增 `alert_type=SECTOR_*` | 提醒评估器读取衍生指标 + 原始快照 → 写 `market_data_alert` | INFO/WARN/HIGH，可 resolve |

> **禁止写回声明（强制边界）**：禁止写回 `market_sector_snapshot`、`market_sector_member_snapshot`、`market_sector_ranking_batch`、`market_sector_ranking_item`、`market_sector_watch`、`market_sector_ranking_config`、`stock_daily_bar`、`stock_quote_snapshot` 等原始事实表；衍生读服务只读原始事实表，不得写回、回写或覆盖原始事实，衍生结果只存新表。原始事实不可变，衍生结果可重算可下线。下线某衍生公式只需停掉对应计算与查询，不回滚也不污染原始事实。

字面 token 要求：本设计显式包含 `原始事实`、`衍生指标`、`提醒事件` 三类，并以“禁止写回/不得写回原始事实表”作为强约束。

## 5. 可解释指标契约

每个公式均给出五要素：输入（输入字段来源）、窗口（计算时间窗口）、基准（比较基准与配置）、样本（样本最小门槛）、失效（失效场景与降级）。所有公式为白盒可解释，不引入黑盒/ML 隐式评分。

### 5.1 相对强弱（N 日对数相对收益 / RS-rank 百分位）

- **输入与单位**：只读 `market_sector_ranking_batch/item` 的 `snapshot_type='CLOSE'`。当前适配器 fixture 同一响应项同时给出 `chg="0.0240"` 与 `value_data="2.40%"`，且客户端直接透传 `chg`，因此冻结为 **decimal ratio**；`sectorReturn(t)=change_rate(t)`，禁止再次 `/100`。P1.7-A 必须增加读取真实 fixture 的映射断言，不能仅在设计测试中硬编码数值。
- **身份**：唯一身份为 `(provider_code, market_code, provider_sector_id, taxonomy_version)`。`watch_id` 只是关注关系，永远不能参与历史身份、幂等键或跨表连接。
- **范围**：当前 LongPort 排行接口最大返回 100 条，且不提供独立总数或分页，因此 P1.7 MVP **只能**产 `RANKED_UNIVERSE`，不得产 `VERIFIED_FULL_MARKET`。后者是预留值，只有未来 provider 提供独立于当前响应的权威总数或可遍历分页，并证明 `actual_item_count=expected_item_count`、`is_truncated=false`、`coverage_rate=1` 时才能启用；禁止用当前返回条数反填 expected count。前端必须显示“排行样本，不代表全市场”。
- **窗口**：20/50/120 个权威市场交易日；任一应有 CLOSE 缺失，不前向填补，标 `INSUFFICIENT_SAMPLE`。
- **共同基准**：同一个 `(market, as_of_date, window, rank_scope, formula_version, parameter_hash)` 的横截面必须使用一个共同基准。MVP 固定使用同一 rank set 的每日等权收益。板块自己的 `tracking_symbol` 仅用于详情页 ETF/指数对照，不参与公共 RS 排名。
- **样本**：连续 N 日、每日相同 taxonomy、每日覆盖门禁通过、有效板块数不少于配置阈值；否则不产 RS-rank。
- **固定窗口 cohort**：先取窗口内每个合格 CLOSE 排行样本的稳定身份交集，形成该窗口唯一 cohort；每个交易日的等权基准、每个板块 N 日收益和最终横截面排名都只使用这个固定 cohort。中途进入/退出排行、任一日缺失或 cohort 低于阈值的板块不得补值；cohort 指纹进入 `parameter_hash/source_manifest_hash`。禁止每天使用不同 Top-100 集合计算一个 N 日共同基准。
- **失效**：单位未知、截断状态未知、交易日历不完整、taxonomy 变化、缺日、跨市场混算或 `1+return<=0` 均不得产 `OK`。

白盒公式：

```text
sectorReturn(t) = change_rate(t)
marketReturn(t) = mean(sectorReturn_i(t))       // 同一合格 rank set
sectorIndex(t)  = sectorIndex(t-1) * (1 + sectorReturn(t))
marketIndex(t)  = marketIndex(t-1) * (1 + marketReturn(t))
relativeReturn_N = ln(sectorIndex(t)/sectorIndex(t-N))
                 - ln(marketIndex(t)/marketIndex(t-N))
rsRankPercentile = (averageAscendingRank - 1) / (n - 1)
```

并列按 `relativeReturn_N` 数值计算平均秩，不能对现有唯一的 `rank_no` 伪造并列。原始 `0.0240` 必须端到端得到 `2.40%` 展示和 `0.0240` 计算输入。幂等业务键必须包含 `parameter_hash`，不同范围、窗口或基准不得互相覆盖。

### 5.2 轮动持续性（市场级 Spearman + 板块级位次指标，两层拆分）

本节明确拆分**市场级**（衡量整个市场板块位次稳定性）与**板块级**（衡量单板块位次序列特征）两层指标，分别落到独立表，互不混淆。

- **输入**：合格 CLOSE 批次的 `provider_sector_id` 与 `change_rate`。每日在相同 taxonomy 和合格 rank set 内根据 `change_rate` 重新计算平均秩；现有 `rank_no` 只用于展示和交叉校验，因为持久化代码按列表位置写入唯一序号，不能表达并列。
- **窗口**：默认 5 / 10 / 20 个交易日（短/中/长）。每个窗口独立计算持续性。
- **基准**：不引入外部指数。排行样本级以同一 `RANKED_UNIVERSE` 中由 `change_rate` 重算的平均秩向量为对象；板块级以单板块在窗口内的位次百分位序列为对象。
- **样本**：连续收盘交易日数 ≥ 窗口长度，每个相邻日交集数量 ≥ 5，且 `pair_coverage=min(intersection_count/left_count, intersection_count/right_count) >= 0.8`。低于门槛标 `INSUFFICIENT_SAMPLE`。
- **失效**：provider 口径变更（行业分类/provider 板块 ID 变更）→ 在变更点断档标记 `ORIGIN_CHANGED`；A 股集合竞价 `INTRADAY` 快照不得当作连续竞价 CLOSE 序列（必须只用 `snapshot_type=CLOSE`，集合竞价不计入连续位次）；板块集合不一致时按交集对齐并记录 `quality_reason`。

#### 5.2.1 市场级：sector_rotation_market_stability（Spearman ρ）

- **语义**：衡量当前 **`RANKED_UNIVERSE` 排行样本**的板块位次稳定性，不得称为全市场；不归属任何单板块。键为 `(market_code, trade_date, window, formula_version)`，**不按 sector identity 存储**（一行 = 一个市场排行样本、一个交易日、一个窗口）。
- **公式**：按稳定 `sector_identity` 连接相邻两个交易日，取交集后分别在交集内按 `change_rate` 重新计算平均秩（同值并列取平均秩），再计算两个平均秩向量的 Pearson 相关系数：

  ```
  ρ = Pearson(R_avg(t-1), R_avg(t))
    = Σ_i (R_i − R̄)(R'_i − R̄') / sqrt( Σ_i (R_i − R̄)² · Σ_i (R'_i − R̄')² )
  ```

  其中 `R_i`/`R'_i` 为板块 i 在两日的平均秩，`R̄`/`R̄'` 为两日平均秩向量的均值，`n` 为交集板块数。仅当两日所有板块位次均**无并列**时，才可使用无并列简化公式 `ρ = 1 − 6·Σd_i² / (n·(n²−1))`（`d_i` 为两日秩之差），该简化公式在并列下会错误高估/低估相关性。窗口汇总固定使用 `intersection_count` 加权均值，不使用未加权算术均值。
- **处理**：每个相邻日对落 `sector_rotation_pair_metric`，保存两日期、left/right/intersection count、pair coverage、ρ 和原因码；任一向量零方差则该日对无定义。窗口均值按有效日对的 `intersection_count` 加权，汇总表保存最小/平均覆盖率和有效日对数。
- **数值示例（GOLDEN-04）**：day1 全板块平均秩 `R1=[1,2,3,4,5]`，day2 原始值 `[2,2,4,4,5]` → 平均秩 `R2=[1.5,1.5,3.5,3.5,5]`。R2 存在并列，**必须用 Pearson 相关系数**：`R̄1=3`、`R̄2=3`，`Σ(x−x̄)(y−ȳ)=9`、`Σ(x−x̄)²=10`、`Σ(y−ȳ)²=9`，故 `ρ = 9/√(10·9) = 9/√90 ≈ 0.9486832981`。若误用无并列简化公式 `ρ = 1 − 6·1/(5·24) = 0.95`，会在并列下高估相关性，**不适用于本例**。该值归属 `market_code + trade_date + window`，存入市场级表 `sector_rotation_market_stability`，不重复存入任何 sector 记录。

#### 5.2.2 板块级：sector_rotation_sector_persistence（位次序列指标）

- **语义**：衡量**单板块**在窗口内位次序列的强度与稳定性。键为 `(sector_identity_id, as_of_date, window, formula_version)`。
- **指标（基于每日 `change_rate` 重排后的 `average_rank(t)`，`n_t` 为当日合格板块数）**：
  - `rank_percentile(t)=(average_rank(t)-1)/(n_t-1)`，升序平均秩，最强为 1.0；并列最高值都得到相同最高百分位。
  - `mean_rank_percentile`：窗口内每日 `rank_percentile(t)` 的算术均值。
  - `rank_percentile_std_dev`：上述位次百分位序列的总体标准差（除以样本数，非 n-1）。
  - `top_bucket_occupancy_rate`：窗口内 `rank_percentile(t) >= 0.8` 的交易日占比。
  - `consecutive_leading_days`：从窗口末尾向前数，`change_rate` 等于当日截面最大值的连续交易日数；并列领涨均计入。
  - `consecutive_lagging_days`：从窗口末尾向前数，`change_rate` 等于当日截面最小值的连续交易日数；并列领跌均计入。
  - `rank_percentile_change`：窗口末尾百分位减窗口起始百分位（正值表示相对位置上升）；不再存跨不同 `n_t` 不可比的原始 `rank_change`。
- **处理**：每日使用自己的 `n_t` 归一化；板块缺失日会中断连续领涨/领跌计数，不允许跳过缺日拼接；taxonomy 变化断档；端点缺失则不产变化值。
- **数值示例（GOLDEN-03）**：某板块 5 日窗口升序平均秩 `[3,4,4,5,5]`，每日 `n_t=5`：
  - 位次百分位序列 `[0.5, 0.75, 0.75, 1.0, 1.0]` → `mean_rank_percentile = 0.8`，`rank_percentile_std_dev = 0.18708286933869706`。
  - `top_bucket_occupancy_rate`（`rank_percentile >= 0.8`）= 2/5 = 0.4。
  - 假设末两日该板块的 `change_rate` 均等于当日截面最大值，则 `consecutive_leading_days=2`；平均秩为 5 不是独立判据，并列最大值同样计入。
  - 末日 `change_rate` 不等于当日截面最小值，故 `consecutive_lagging_days=0`。
  - `rank_percentile_change` = 1.0 - 0.5 = 0.5（相对位置上升 50 个百分点）。

幂等键：排行样本级 `(source_provider, market_code, trade_date, window, formula_version, parameter_hash)`；板块级 `(sector_identity_id, as_of_date, window, formula_version, parameter_hash)`。

### 5.3 资金趋势与交易集中度（MVP）/ 收益贡献（后置）

本节严格区分三类语义，表名、字段、接口、页面不得混用：

- **资金趋势（趋势）** = 板块净流入在时间轴上的方向、持续性和加速度，回答资金持续流入/流出还是反转。
- **交易集中度（集中度）** = 某子集金额 / 总金额，刻画“成交/资金有多集中在少数成分”，是一种占比，无量纲（0~1），与涨跌方向无关。
- **收益贡献（归因）** = `t-1 point-in-time weight · memberReturn`。该能力必须等待可信的历史成分和前一日自由流通市值，因此不属于 MVP。

不得把成交额占比称为“涨幅贡献/收益贡献”，不得把收益贡献称为“集中度”。

#### 5.3.1 资金趋势：sector_capital_flow_trend

- **输入**：合格 CLOSE 板块快照的 `total_net_inflow` 与 `total_turnover_amount`；字段必须先冻结币种、金额单位、是否日内累计及重置点。单位或累计口径未知时不得计算。
- **范围**：现有成分与净流入快照只为用户关注板块采集，因此固定 `flow_scope=WATCHED_SECTORS`，只能回答“我关注的板块中资金流向哪里”，不得称为全市场资金流向；API 同时返回 `watched_sector_count` 和实际有效数量。
- **窗口**：1/5/20 个交易日，历史窗口严格使用 `t-N+1..t`；趋势比较基准严格使用 `t-N..t-1`，不把当日同时放入自己的历史均值。
- **指标**：`net_inflow`、`flow_intensity=net_inflow/turnover_amount`、`cumulative_net_inflow_N`、`mean_flow_intensity_N`、`positive_flow_days_N/N`、`flow_intensity_change=flow_intensity(t)-mean(t-N..t-1)`。
- **样本**：连续 CLOSE、同币种、同累计口径，窗口完整；成交额为 0 时强度为空但净流入原值仍可展示。
- **失效**：盘中区间值不得和全日 CLOSE 比较；跨币种不得合并；缺日、单位未知、累计重置未识别均标 `INSUFFICIENT_RAW`。

#### 5.3.2 交易集中度：sector_turnover_concentration

- **输入**：`market_sector_member_snapshot` 的 `turnover_amount` / `net_inflow`（V14）与 `market_sector_snapshot` 的 `total_turnover_amount`（V14，板块聚合）。输入只读原始事实表。
- **窗口**：MVP 固定单个合格 CLOSE 快照，`window=1`。多日聚合涉及成分集合变化和分子/分母合并口径，后续另立公式版本，不在 MVP 暗含“5 日均值”。
- **基准**：成交额集中度的基准为板块 `total_turnover_amount`；净流入集中度的基准为成分绝对流量 `absSum`（不用可能为零/负的板块净流入总额）。
- **成交额集中度（占比，白盒）**：

  ```
  top_k_turnover_share = Σ top-K 成分 turnover_amount / 板块 total_turnover_amount
  ```

  top-K 为按 `turnover_amount` 降序的前 K 名（默认 K=3/5）。这是“集中度”（占比），**不是**“收益贡献/涨幅贡献”；审计明细单独保存为 `top_turnover_members_json`。
- **净流入方向占比与绝对流量集中度**：板块 `total_net_inflow` 可能为零或负，因此统一以**绝对流量** `absSum = Σ |member net_inflow|` 为分母。正/负两项只是方向构成占比，不得命名为集中度；只有 top-K 绝对流量占比表达集中程度：

  ```
  positiveFlowShare   = Σ 正净流入成分 net_inflow / absSum
  negativeFlowShare   = |Σ 负净流入成分 net_inflow| / absSum
  absoluteFlowConcentration(topK) = Σ top-K |member net_inflow| / absSum
  ```

  此处 top-K 独立按 `abs(net_inflow)` 降序，审计明细保存为 `top_absolute_flow_members_json`；不得与成交额 top-K 共用一个列表或含混的 `share` 字段。

  - **零分母处理（强制）**：当 `absSum == 0` 时不除零，标记 `quality_status=INSUFFICIENT_RAW`、`quality_reason_codes=['ZERO_NET_INFLOW']`，两个方向占比和绝对流量集中度均置空。
- **范围/样本**：现有成员快照仅来自关注板块，固定 `data_scope=WATCHED_SECTORS`，同时返回 watched/valid sector count；不得作为市场级排行。样本要求与 §5.3.1 同。
- **失效**：成分停牌/延迟排除并计 `excluded_member_count`；累计值跨日重置须先用相邻快照差分或仅用 CLOSE 快照对齐，避免虚假集中度。
- **数值示例（GOLDEN-06）**：4 个成分净流入 `net_inflows = [+100, +50, -30, +20]`：
  - `posSum = 170`，`negSum = -30`，`absSum = 200`，`total = 140`。
  - `positiveFlowShare = 170/200 = 0.85`，`negativeFlowShare = |-30|/200 = 0.15`；二者之和为 1，仅描述方向构成，不描述集中程度。
  - `absoluteFlowConcentration(top2) = (100+50)/200 = 0.75`。
  - 全零情形 `net_inflows = [0,0,0,0]` → `absSum=0` → 返回 `INSUFFICIENT_RAW`，不除零。

幂等键：资金趋势表和交易集中度表均包含 `(sector_identity_id, trade_date, window, formula_version, parameter_hash)`。

#### 5.3.3 收益贡献：P1.7-C 后置门禁

只有新增并验收 point-in-time 成分有效区间、`t-1` 自由流通股本/价格及成分集合指纹后，才能设计 `sector_member_return_contribution`。停牌成分应保留 `t-1` 权重并将当日收益按已冻结规则处理，不能简单剔除后重新归一。MVP 的接口、表和页面不得声称已提供真实收益贡献。

### 5.4 量价确认（六状态）

- **输入/范围**：`market_sector_snapshot` 的 `change_rate` 与 `total_turnover_amount/total_volume`。现有快照仅来自关注板块，因此固定 `data_scope=WATCHED_SECTORS` 并返回 watched/valid sector count，不得称为市场排行。区间增量须处理日内累计跨日重置；输入只读原始事实表。
- **窗口**：当日（量价是否同向）+ 近 5 日 CLOSE 均量（判断是否放量/缩量）。默认窗口为当日 + 近 5 日 CLOSE 均值。
- **基准**：基准为板块自身近期均量（近 5 日 `total_turnover_amount` CLOSE 均值），判断当日成交相对历史是放量还是缩量。
- **样本**：有可用成交数据且非全停牌；必须具备连续、权威交易日历对齐的 `t-5..t-1` 五个 CLOSE 成交快照。任一日缺失都不跳过、不缩短分母，标 `INSUFFICIENT_SAMPLE`（指标状态映射为 `INSUFFICIENT`）并降级。
- **失效**：累计值（成交额/成交量）跨日重置未处理（必须 CLOSE 或同桶差）；延迟行情（`is_delayed=true`）导致成交失真 → 保留状态、附 `quality_reason='DELAYED_QUOTE'`，不改变状态；小盘低成交板块（成交额绝对值过低）易产生虚假信号 → 降级展示，不产 HIGH 提醒。

公式（白盒，六状态判定）：

```
turnoverRatio = closeTurnover(t) / mean(closeTurnover(t-5..t-1))              （收盘量比）
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

- 收盘指标只允许 CLOSE 对 CLOSE，基准严格排除当日。盘中累计成交额只能与历史交易日**同一市场、同一交易阶段、同一时间桶**的累计值比较；相邻快照差分只是区间成交，不能冒充全日成交。
- 幂等键 `(sector_identity_id, trade_date, formula_version, parameter_hash)`。

### 5.5 异动提醒（Anomaly Alert）

- **输入**：相对强弱、轮动持续性、资金趋势、交易集中度、量价确认与合格原始快照。
- **窗口**：当日值与严格滞后的 `t-20..t-1` 比较；当日不得进入自身历史均值或标准差。
- **基准**：基准为板块自身历史分布（近 N 日该板块某指标的均值与标准差），不引入外部阈值集合。
- **样本**：历史样本 ≥ 20 个有效交易日时才计算 Z-score；MVP 的 Z-score 只作为解释证据，不单独触发或升级 severity。历史不足时不伪造 Z-score。
- **失效**：`STALE/ORIGIN_CHANGED/BLOCKED_AUTH/BLOCKED_PERMISSION/BACKOFF` 一律不产生新板块提醒，只保留旧提醒的历史展示。除量价规则自身固定阈值外，其他规则不允许 threshold-only fallback。

公式要点（白盒，阈值 + Z-score）：

- Z-score：`z=(x(t)-mean(x(t-20..t-1)))/stddev(x(t-20..t-1))`。标准差为 0 时无定义；MVP 仅把可用 Z-score 放入 evidence，不作为触发条件或 severity 条件。
- 阈值：涨跌幅字段是 decimal ratio，因此 3% 阈值写为 `0.03`，量比阈值如 `2.0`。
- 复用 `market_data_alert`（V7），新增 `alert_type=SECTOR_RS_REVERSAL` / `SECTOR_VOLUME_CONFIRMATION` / `SECTOR_TURNOVER_CONCENTRATION` / `SECTOR_RANK_JUMP` 等；`severity` 取 `INFO/WARN/HIGH`；`trigger_value_json` 存派生指标快照与上下文。

MVP 告警规则表（阈值全部进入 `parameter_hash`，缺任一必需指标不产该类告警）：

| alert_type | 方向与触发条件 | severity | 必需证据 |
| --- | --- | --- | --- |
| `SECTOR_RANK_JUMP` | `rank_percentile_change >= 0.30` 或 `<= -0.30` | WARN；绝对值 `>=0.50` 且质量无降级时 HIGH | 起止百分位、变化值、rank scope |
| `SECTOR_RS_REVERSAL` | 前一日 `rsPercentile<=0.2` 且当日 `>=0.8`（BULLISH），或前一日 `>=0.8` 且当日 `<=0.2`（BEARISH） | WARN | 前后 RS 百分位、regimeDirection、共同 cohort 指纹 |
| `SECTOR_VOLUME_CONFIRMATION` | `turnover_ratio >= 2.0` 且 `abs(change_rate) >= 0.03` | WARN；同时 `abs(change_rate)>=0.05` 时 HIGH | change rate、量比、五日基线 |
| `SECTOR_TURNOVER_CONCENTRATION` | `top_k_turnover_share >= 0.60` | INFO；`>=0.75` 且有效成分数达到门槛时 WARN | topK、分子、分母、有效/排除数 |

固定阈值 fallback 只允许 `SECTOR_VOLUME_CONFIRMATION`，且必须满足完整五日量比基线；`SECTOR_RS_REVERSAL` 与 `SECTOR_RANK_JUMP` 缺衍生历史时不 fallback。任一 `RANKED_UNIVERSE` 结果携带降级原因；降级状态不得产 HIGH。提醒解释只陈述观察证据，不做因果归因。

> **强制声明**：异动提醒是观察提醒，**不是买卖指令、不是投资建议**，不预测收益、不产生交易动作。前端必须在每条提醒旁标注“仅供参考，不构成投资建议”。

## 6. 数据模型（规划 V19+，独立新表，只读原始事实）

> 状态：**规划 V19+**，未实现。以下表为独立新表，绑定 Flyway V19+（V19、V20、...，具体由实现计划 ST-A1/ST-A2/ST-B1 拆分），不复用 V1-V18 既有版本号。所有衍生表只读原始事实表，不写回原始事实表。规划区域不含“已实现”描述。

### 6.1 数据治理前置表与统一血缘

P1.7-A 先建立 `market_sector_identity`：`id bigint` 作为内部/API `sectorId`，自然唯一键 `(provider_code, market_code, provider_sector_id, taxonomy_version)`；`valid_from date NOT NULL` 与 `valid_to date NULL` 使用左闭右开区间。另建 `market_sector_identity_lock(provider_code, market_code, provider_sector_id)` 唯一锚点；写事务在 `READ COMMITTED` 下先 `INSERT IGNORE` 锚点，再 `SELECT ... FOR UPDATE` 锁定锚点，之后检查所有 taxonomy version 的区间不重叠并写入。别名区间在同一锚点锁内校验。首次并发插入、跨 taxonomy version 和边界日期必须有集成测试。所有衍生表只使用 `sector_identity_id` 外键，API 只使用数值 `sectorId`。

P1.7-A 同时修复现有 watch 删除生命周期：给 `market_sector_snapshot/member_snapshot` 回填稳定 `sector_identity_id`，移除 watch 级联删除；DELETE watch 只归档关注关系，历史快照保留。迁移完成前 readiness 必须阻断分析。

另建 `sector_analytics_publication_batch` 作为跨公式发布单元。run 必须保存不可变 `provider_code/market_code/as_of_date`。member 冗余同三个范围字段，并用 `(batch_id, provider_code, market_code, as_of_date)` 复合 FK 指向 batch、用 `(calculation_run_id, formula_code, provider_code, market_code, as_of_date)` 复合 FK 指向 run，从数据库层拒绝跨市场/日期成员。member 对 `(batch_id, calculation_run_id)` 和 `(batch_id, formula_code)` 唯一。

`required_formula_set_hash=SHA256(sorted(formula_code + ':' + formula_version + ':' + parameter_hash))`；`source_manifest_group_hash=SHA256(sorted(formula_code + ':' + formula_version + ':' + parameter_hash + ':' + source_manifest_hash + ':' + calculation_run_id))`。batch 唯一键包含 provider/market/date/batch type 和两个 hash。发布事务必须重算两个 hash、验证成员集合与范围且所有 run READY，之后同时发布 batch 与成员 run。衍生结果行只保存 `calculation_run_id`；聚合查询通过成员表关联。

每一张衍生表都必须包含：

- `formula_code` varchar(64) — 公式标识（如 `RELATIVE_RETURN_LOG`、`ROTATION_SPEARMAN`、`CAPITAL_FLOW_TREND`、`TURNOVER_CONCENTRATION`、`VOLUME_CONFIRMATION`）。
- `formula_version` varchar(16) — 公式版本（如 `v1`、`v2`），幂等键必含。
- `parameter_hash` varchar(64) — 窗口、阈值、共同基准、范围和单位契约的内容哈希，并纳入唯一键。
- `calculation_run_id` bigint — 指向成功发布的计算运行。
- `source_provider` varchar(32) — 原始事实 provider。
- `source_date_range` varchar(64) — 来源日期区间。
- `source_manifest_hash` varchar(64) — 按日期和来源 ID 排序后的完整输入清单哈希；多日指标不得只记录一个 batch/snapshot ID。
- `calculated_at` datetime — 计算时间。
- `quality_status` varchar(32) — `OK/DEGRADED/NO_DERIVED_DATA/INSUFFICIENT_RAW/INSUFFICIENT_SAMPLE/STALE/ORIGIN_CHANGED/BLOCKED_AUTH/BLOCKED_PERMISSION/BACKOFF`。
- `quality_reason_codes` text — JSON 数组形式的结构化原因码，可同时记录上游鉴权、权限、截断、缺日、陈旧和口径变化。
- `valid_sample_size` int — 实际有效样本数。
- `published_at` datetime — 所属 calculation run 原子发布的时间。

发布策略（所有衍生表统一）：

- **重算**：单公式 run 以 `(formula_code, formula_version, parameter_hash, source_manifest_hash)` 唯一；结果以 `calculation_run_id + 结果业务维度` 唯一。输入变化生成新 run 和新结果，不覆盖历史证据。
- **原子发布**：每个公式 run 的候选结果先写不可见状态，单公式成功后可标 `READY`。每日高级总览所需的全部 run 都 READY 后，在同一事务中发布 batch 及成员 run。聚合查询以 batch 连接成员 run，不拼接不同批次；明细查询返回自己的 `calculationRunId`。薄切片不属于衍生发布批次并显式 `DERIVED_MODULES_NOT_PUBLISHED`。
- **并发**：按 `(provider, market, as_of_date, formula_version, parameter_hash)` DB claim；同一键只有一个运行者。
- **版本选择**：未传版本时使用端点冻结默认 `formulaVersion=v1`，服务端按请求窗口和默认阈值计算唯一 `parameterHash`；显式选择时 `formulaVersion/parameterHash` 必须同时传。不存在返回 404，不使用含混的“最新版本”。
- **废弃**：停掉对应计算与查询即可（旧行保留，不回滚也不污染原始事实）。
- **历史查询**：首屏解析并返回一个已发布 `calculationRunId`；后续页必须携带该 run ID，按允许的 `sortBy` 加 `sectorId ASC` 次序读取，防止并发发布导致跨页漂移。

### 6.2 sector_relative_strength_snapshot（规划 V19+）

用途：共同等权基准下的 N 日对数相对收益与 RS-rank。只消费通过完整性门禁的 CLOSE 榜单；范围为 `VERIFIED_FULL_MARKET` 或显式降级的 `RANKED_UNIVERSE`。tracking symbol 只作为详情对照，不参与公共排名。

核心字段：

- `id` bigint 主键 auto_increment
- `sector_identity_id` bigint — FK `market_sector_identity.id`；API 对外字段为 `sectorId`
- `market_code` varchar(8) — CN/HK/US
- `as_of_date` date — 基准交易日
- `window` smallint — 窗口长度（20/50/120）
- `benchmark_type` varchar(32) — MVP 固定 `RANK_SET_EQUAL_WEIGHT`
- `benchmark_symbol` varchar(32) — MVP 必须为空；仅详情对照响应可返回 tracking symbol
- `relative_return_n` decimal(20,10) — N 日对数相对收益（自然单位，前端 ×100 表百分比）
- `rs_rank_percentile` decimal(20,6) — 0~1（前端 ×100 表 0~100）
- `rank_scope` varchar(32) — `VERIFIED_FULL_MARKET` / `RANKED_UNIVERSE`
- 统一版本血缘列（见 §6.1）+ `created_at` / `updated_at` datetime

索引/幂等：

- unique `uk_sector_rs(calculation_run_id, sector_identity_id, as_of_date, window)`
- index `idx_sector_rs_market_date(market_code, as_of_date)`

### 6.3 sector_rotation_market_stability（规划 V19+，市场级）

用途：**排行样本级**轮动稳定性（相邻交易日按稳定身份对齐、以 `change_rate` 重算平均秩后的 Spearman ρ），键为 `(market_code, trade_date, window, formula_version)`，**不存 sector identity**，不重复存入任何板块记录。MVP 固定 `RANKED_UNIVERSE`，不得称为全市场。

核心字段：

- `id` / `market_code` / `trade_date` date / `window`(5/10/20) / `rank_scope` / `source_coverage_rate` / `is_truncated`
- `rank_spearman_mean` decimal(20,6) — 按 intersection count 加权的相邻日 ρ
- `min_pair_coverage` / `avg_pair_coverage` / `valid_pair_count` / `weighted_intersection_count` — 与来源完整性 coverage 分离
- 统一版本血缘列（见 §6.1）+ `created_at` / `updated_at`

索引/幂等：

- unique `uk_sector_rotation_market(calculation_run_id, source_provider, market_code, trade_date, window)`
- index `idx_sector_rotation_market_date(market_code, trade_date)`

`sector_rotation_pair_metric` 保存窗口内每个相邻日对：`calculation_run_id/market_code/left_trade_date/right_trade_date/left_count/right_count/intersection_count/pair_coverage/spearman_rho/quality_reason_codes`，唯一键 `(calculation_run_id, market_code, left_trade_date, right_trade_date)`，用于审计变化宇宙和重算窗口汇总。

### 6.4 sector_rotation_sector_persistence（规划 V19+，板块级）

用途：**板块级**位次序列指标，只读 CLOSE 排行事实并由 `change_rate` 重算平均秩和百分位；持久化 `rank_no` 仅作交叉校验。

核心字段：

- `id` / `sector_identity_id` FK / `market_code` / `as_of_date` / `window`(5/10/20) / `rank_scope`
- `mean_rank_percentile` decimal(20,6) — 窗口内位次百分位均值
- `rank_percentile_std_dev` decimal(20,6) — 位次百分位总体标准差
- `top_bucket_occupancy_rate` decimal(20,6) — `rank_percentile >= 0.8` 的占比
- `consecutive_leading_days` int — 末尾 `change_rate` 等于当日截面最大值的连续天数
- `consecutive_lagging_days` int — 末尾 `change_rate` 等于当日截面最小值的连续天数
- `rank_percentile_change` decimal(20,6) — 末日百分位减首日百分位
- 统一版本血缘列（见 §6.1）+ `created_at` / `updated_at`

索引/幂等：

- unique `uk_sector_rotation_sector(calculation_run_id, sector_identity_id, as_of_date, window)`
- index `idx_sector_rotation_sector_market_date(market_code, as_of_date)`

### 6.5 sector_capital_flow_trend（规划 V19+，资金趋势）

用途：保存板块净流入方向、持续性和变化速度，只读口径已冻结的 CLOSE 快照。

核心字段：`id`、`sector_identity_id`、`market_code`、`trade_date`、`window`(1/5/20)、`flow_scope='WATCHED_SECTORS'`、`currency_code`、`net_inflow`、`turnover_amount`、`flow_intensity`、`cumulative_net_inflow_n`、`mean_flow_intensity_n`、`positive_flow_days_rate`、`flow_intensity_change`、统一版本血缘列及时间列。

幂等键：unique `uk_sector_flow_trend(calculation_run_id, sector_identity_id, trade_date, window)`。

`sector_member_return_contribution` 不在 MVP 建表范围，待 P1.7-C point-in-time 数据门禁通过后另立 migration 与契约。

### 6.6 sector_turnover_concentration（规划 V19+，交易集中度）

用途：单个 CLOSE 快照的板块交易集中度（top-K 成交额占比 + 资金方向占比 + top-K 绝对流量集中度），只读 `market_sector_member_snapshot`/`market_sector_snapshot`，不写回。**禁止**把方向占比或成交额占比称为收益贡献。

核心字段：

- `id` / `sector_identity_id` FK / `market_code` / `trade_date` / `window`（MVP 固定 1）
- `top_k_turnover_share` decimal(20,6) — top-K 成分成交额 / 板块 total_turnover_amount
- `positive_flow_share` decimal(20,6) — 正净流入 / absSum（可空，方向占比）
- `negative_flow_share` decimal(20,6) — |负净流入| / absSum（可空，方向占比）
- `absolute_flow_concentration` decimal(20,6) — top-K |净流入| / absSum（可空）
- `top_k` int — 默认 3/5
- `top_turnover_members_json` text — 成交额 top-K（canonical_symbol、turnover_amount、turnover_share）
- `top_absolute_flow_members_json` text — 绝对净流入 top-K（canonical_symbol、net_inflow、absolute_flow_share）
- `excluded_member_count` int / `valid_member_count` int
- 统一版本血缘列（见 §6.1）+ `created_at` / `updated_at`

索引/幂等：

- unique `uk_sector_concentration(calculation_run_id, sector_identity_id, trade_date, window, top_k)`
- index `idx_sector_concentration_market_date(market_code, trade_date)`

### 6.7 sector_volume_confirmation_snapshot（规划 V19+，六状态）

用途：板块量价确认（六状态 + 量比），只读 `market_sector_snapshot`，不写回。

核心字段：

- `id` / `sector_identity_id` FK / `market_code` / `trade_date`
- `change_rate` decimal(20,8)
- `turnover_amount` decimal(30,6) — 当日板块成交额（已处理累计跨日重置）
- `turnover_ratio` decimal(20,6) — 当日 / 近 5 日 CLOSE 均值
- `confirmation_status` varchar(24) — `UP_CONFIRMED`/`UP_UNCONFIRMED`/`DOWN_CONFIRMED`/`DOWN_UNCONFIRMED`/`NEUTRAL`/`INSUFFICIENT`
- 统一版本血缘列（见 §6.1）+ `created_at` / `updated_at`

索引/幂等：

- unique `uk_sector_volume(calculation_run_id, sector_identity_id, trade_date)`
- index `idx_sector_volume_market_date(market_code, trade_date)`

### 6.8 扩展 market_data_alert（复用表，不新建第二套）

异动提醒复用 V7 表，但 V19+ 必须增加 `subject_type`、`sector_identity_id` nullable FK、`dedup_key`、`calculation_run_id`、`publication_batch_id`。`subject_type=SECTOR` 时 sector 与 publication batch 必填；若 calculation run 非空，`(publication_batch_id, calculation_run_id)` 必须以复合 FK 指向 publication member，数据库拒绝跨批次 run。同一板块、交易日、类型、batch、公式参数和证据只写一次；dedup key 覆盖这些字段。`canonical_symbol` 不承载板块 ID，`trigger_value_json` 不承担可查询主键。

### 6.9 MyBatis / Flyway 边界

- 新表走更高 Flyway 版本 V19+（V19、V20...），SQL 放在 `src/main/resources/db/migration/V19__*.sql`（具体由 ST-A1/ST-A2/ST-B1 决定）。
- MyBatis XML 放在 `src/main/resources/mapper/`（如 `SectorRelativeStrengthSnapshotMapper.xml`）。
- 主键统一 `id bigint auto_increment`；金额/价格 `decimal(20,6)`；时间 `created_at`/`updated_at`。
- 衍生读服务只读原始事实表，禁止 UPDATE/写回/回写/覆盖原始事实表。

## 7. API 设计（规划，未实现 — 详见 MARKET_DATA_API.md §5）

本节为概述；详细端点、请求/响应示例与错误码见 `docs/api/MARKET_DATA_API.md` §5（规划/未实现）。

- GET 板块相对强弱（按市场/窗口查询排行与单板块详情）
- GET 板块轮动持续性：市场级稳定性 + 板块级位次指标（分别端点）
- GET 板块资金趋势（按市场查询排行与单板块历史）
- GET 板块交易集中度（按市场查询排行与单板块集中度明细）
- GET 板块量价确认（按市场查询排行与单板块六状态）
- GET 板块异动提醒：扩展现有 `/api/v1/market-data/alerts?subjectType=SECTOR&alertTypePrefix=SECTOR_` 查询

每日总览冻结为 CLOSE 口径：显式日期无 CLOSE 时不回退，返回 `NO_DERIVED_DATA`；未传日期时选择该市场最新成功 CLOSE 并返回实际 `asOfDate`。Top/Bottom 默认各 5，按同一批次 `change_rate` 排序并保留并列。资金模块单独标 `WATCHED_SECTORS`，不能与排行范围混称。

ETF/指数对照只读取 `market_sector_watch.tracking_symbol` 对应的 `stock_daily_bar`，固定 `adjust_type=NONE`、同市场交易日、相同日期区间；响应返回 symbol、缺失日期、最新数据时间和质量状态。缺数据只降级详情对照，不改变公共 RS 基准。

单公式响应携带 `formulaCode/formulaVersion/parameterHash/calculationRunId/qualityStatus/qualityReasonCodes`；依赖排行时追加 scope/coverage。高级总览使用 `publicationBatchId` 并保留各模块自己的 run；薄切片明确没有衍生批次。参数非法返回 400；公式版本不存在返回专用 404；无数据、原始不足、陈旧和上游阻断均以统一 quality status + 原因码表达。所有端点仍为规划/未实现。

## 8. 前端页面与图表设计（规格 + mock 契约建议）

> 前端在**独立仓库**（`docs/FRONTEND_ARCHITECTURE.md`）。本仓库只给规格与 mock 契约建议，实际前端实现留作 ST-B3 子任务。

页面结构（MVP）：

- 首屏是“今日板块总览”，而不是五个平级复杂 Tab。按市场展示领涨/领跌、RS 百分位、较昨日排名变化、持续性、资金方向、量价状态、异动数和数据质量；一行即可进入板块详情。
- 第二层为“板块详情”：价格/相对强弱走势、资金趋势、轮动位置、交易集中度、量价确认、跟踪 ETF/指数对照和输入数据水位。
- 第三层为“数据与计算状态”：展示排行范围、覆盖率、截断状态、最近成功收盘批次、缺失日期、上游 `BLOCKED_AUTH/BLOCKED_PERMISSION/BACKOFF`、公式版本和计算 run。
- 收益贡献页面暂不实现，直到 P1.7-C 门禁通过。

图表建议：

- 总览优先使用可扫描表格、排序条和小型趋势线，不在 MVP 使用桑基图或 ridgeline。
- 相对强弱：RS 排名条形图和详情折线；范围不是 `VERIFIED_FULL_MARKET` 时显著显示降级标签。
- 资金趋势：净流入与强度双轴时序、连续流入天数和变化方向；不得把净流入等同于未来涨跌预测。
- 交易集中度：top-K 成交额占比条形图、正/负/绝对净流入集中度和成分明细。
- 量价确认：散点图（x=change_rate, y=turnover_ratio，颜色编码六状态 `UP_CONFIRMED`/`UP_UNCONFIRMED`/`DOWN_CONFIRMED`/`DOWN_UNCONFIRMED`/`NEUTRAL`/`INSUFFICIENT`）。
- 异动提醒：提醒流（按 severity 分色，INFO/WARN/HIGH），每条提醒含派生指标上下文与“不构成投资建议”标注。

提醒解释不是因果归因。固定证据模板依次展示：发生了什么（指标/阈值）、相对位置变化、关注板块资金趋势、量价确认、数据质量和失效警告；字段为 `summary/evidenceCodes/evidenceValues/qualityReasonCodes`。没有 point-in-time 收益贡献时不得使用“由某成分导致”等因果措辞。

mock 契约：

- 本仓库 `docs/mock/MOCK_REMOTE_CONTRACT.md` 是双模式权威说明。板块分析依赖真实 DB 血缘，mock 模式只能提供明确标记的 `LOCAL_DEMO` 页面样例，不得伪造 calculation run、全市场、资金流或提醒成功；remote 字段必须与后端 VO 对齐。

降级展示与用户动作：

- `DEGRADED`：可展示本批值但必须显示原因、scope、源时间；不得产 HIGH。
- `NO_DERIVED_DATA`：保留模块位置并显示“尚未计算”，不沿用旧值；用户可查看 readiness。
- `INSUFFICIENT_RAW/INSUFFICIENT_SAMPLE`：值置空，显示缺失日期/样本数和最低要求；用户动作是补采或等待样本积累。
- `STALE`：可展示最后值但必须同时显示 `asOfDate/sourceQuoteTime` 和“已过期”；不得与当日值混排。
- `ORIGIN_CHANGED`：在口径变更点断线，不跨 taxonomy 拼接；提示重新形成窗口。
- `BLOCKED_AUTH/BLOCKED_PERMISSION`：不自动重试轰炸，展示上游配置/权限阻断和最后成功时间。
- `BACKOFF`：展示下一次允许重试时间，保留最后成功值但明确标为历史值。
- 每个指标卡片需标注“不构成投资建议，仅作观察提示”。

## 9. 风险与失效边界

- **本系统是决策辅助，不构成投资建议**：所有指标与提醒仅为观察提示，不预测收益、不产生交易动作。前端必须在页面与每条提醒显式标注。
- 跨市场时区：严格按 P1.6 `ZoneId`（CN `Asia/Shanghai`、HK `Asia/Hong_Kong`、US `America/New_York`）对齐交易日，禁止跨市场混入同一序列。
- 权威交易日历：`market_calendar` 必须增加 `source_code/verification_status`；仅 `EXCHANGE_FILE` 或 `MANUAL_VERIFIED` 行可进入长窗口计算。HK/US 无验证日历时 fail closed 为 `INSUFFICIENT_RAW`，周末推断不得冒充权威交易日。
- 停牌与延迟是不同状态：延迟行情不得等同停牌。MVP 不做收益贡献；集中度只统计同一快照口径的有效成员并单独报告停牌数、延迟数和未知状态数。
- 缺失与样本不足：低于门槛标 `INSUFFICIENT_SAMPLE` 并降级展示，不产 HIGH 提醒。
- 口径变更：provider 行业分类/板块 ID 在窗口内变更 → 在变更点断档标 `ORIGIN_CHANGED`，不跨口径拼接。
- 累计值跨日重置：成交额/成交量/净流入若为日内累计，跨日比较必须先差分或仅用 CLOSE 快照对齐，避免虚假信号。

## 10. 统一缺失数据 / 停牌 / 跨市场时区 / 样本不足 / 失效处理

- 停牌：使用规范化交易状态；延迟单独使用 `is_delayed` 和 provider quote time。未知状态不得默认为正常。
- 样本不足：有效样本数低于门禁（CN≥8、HK/US≥5，或 < 预期成分 50%）时衍生结果标 `INSUFFICIENT_SAMPLE`，前端降级展示，且不产 HIGH 提醒（最多 INFO）。
- 跨市场时区：严格按 CN/HK/US 各自 ZoneId 对齐交易日，不混算同一序列；基准与窗口在同市场内成立。
- 数据陈旧：P1.7-A 必须补充 provider quote time；未提供 quote time 时标 `SOURCE_TIME_UNKNOWN`，不得仅用本地落库时间证明行情新鲜。
- 口径变更：provider 口径变更 → 断档标 `ORIGIN_CHANGED`，历史序列在变更点不跨口径拼接。

## 11. 验收边界（规划，由后续实现任务满足）

- P1.7-A 先通过：`0.0240 -> 2.40%` 单位契约、完整性/截断门禁、稳定身份、CLOSE 语义、权威交易日历、provider 时间/币种/累计口径、计算 run/manifest。
- P1.7-B 再通过：共同基准相对强弱、变化宇宙轮动、资金趋势、交易集中度、严格滞后量价和提醒，以及今日板块总览。
- `RANKED_UNIVERSE` 不得在 API 或页面显示为“全市场”；无法证明完整时不产 `VERIFIED_FULL_MARKET`。
- 收益贡献属于 P1.7-C，未具备 point-in-time 权重前不得建表、开放接口或显示结果。
- 原始事实零污染：所有衍生表只读原始事实表，禁止写回。
- 机器验收必须从真实原始字段走到最终指标，覆盖单位、截断、错序身份、变化集合、停牌/延迟、零方差、严格滞后、参数隔离、重复调度和原子发布；关键词测试只能作为补充。
