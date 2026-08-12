# P1.7 板块分析实施计划（专家复审修订）

> 状态：规划/未实现。设计基线：`docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md` v1.1。
> 原则：先证明数据可用，再计算指标；先做每日决策总览，再做高级归因。P1.7-A 未通过时禁止进入 P1.7-B。
> 产品落点：P1.7 是 `docs/features/MARKET_RESEARCH_DECISION_CENTER_DESIGN.md` 的衍生研究引擎；前端成果必须落入 P1.10-A 市场雷达/板块详情，不创建平行的“板块分析”产品孤岛。

## 1. 冻结范围

MVP 包含：数据就绪、每日总览、共同基准相对强弱、轮动持续性、资金趋势、交易集中度、严格滞后的量价确认、板块提醒和前端详情。

MVP 不包含：成分收益贡献、自动交易、预测收益、provider 新外联。收益贡献属于 P1.7-C，必须先具备 point-in-time 成分与 `t-1` 权重。

## 2. 阶段门禁

### P1.7-A 数据就绪门禁

必须全部满足：

1. `change_rate` 契约固定为 decimal ratio，真实 fixture `0.0240` 端到端计算为 `0.0240`、显示为 `2.40%`。
2. 当前 LongPort 无独立总数/分页且上限 100，MVP 固定 `RANKED_UNIVERSE`；不得用返回条数伪造 expected count。`VERIFIED_FULL_MARKET` 仅未来具备权威分母后启用。
3. `market_sector_identity.id` 为内部/API `sectorId`；使用 identity lock 锚点处理首次并发插入和跨 taxonomy 区间，禁止 watch_id；衍生表使用 FK。
4. 关注/排行收盘数据具有明确 `trade_date/snapshot_type/provider_quote_time`；AUTO/MANUAL 不能冒充 CLOSE。
5. `market_calendar` 增加 source/verification；只有 EXCHANGE_FILE/MANUAL_VERIFIED 可进入长窗口，HK/US 缺失时 fail closed。
6. 金额字段冻结 currency/unit/cumulative-period/reset 语义；延迟和停牌分别建模。
7. 单公式 calculation run + source manifest + `parameter_hash` + DB claim 可用；跨公式 `publication_batch` 作为高级总览的一致发布单元。

任一门禁失败：readiness 返回明确状态，分析调度不运行，页面显示阻断原因。

### P1.7-B 分析与页面门禁

只有 P1.7-A 独立验收 PASS 后启动。所有指标只消费合格 CLOSE 和已发布 calculation run。

## 3. 子任务与独占写路径

### ST-A1：事实契约、身份、范围和薄切片总览

写路径：

- 新 Flyway migration（V19+）
- `marketdata/analysis/readiness/**`
- 现有板块 ranking/watch 的 DO、Mapper/XML、service 中与完整性和 CLOSE 语义直接相关的文件
- provider 字段单位契约测试

产出：稳定身份与 identity-lock 锚点表（READ COMMITTED 下先建锚点再锁定）；快照回填 sectorId、移除 watch 级联删除并改为归档；排行范围字段；provider 时间、币种、累计语义；字段完整的 readiness；CLOSE 薄切片总览。

验收：真实 fixture 单位；rank_limit 固定 `RANKED_UNIVERSE`；首次并发插入/跨 taxonomy 区间无重叠；删除/重建 watch 不改变 sectorId 且不删除快照；AUTO/MANUAL 不命中 CLOSE；readiness 必需 gate 任一阻断即禁止计算。

### ST-A2：计算运行、血缘和原子发布

依赖：ST-A1。

写路径：

- calculation run / manifest migration、DO、Mapper/XML
- `marketdata/analysis/run/**`
- DB claim 与发布事务测试

产出：calculation run、publication batch/member、完整 manifest/hash、候选/READY/发布状态和失败恢复；结果只存 run ID，batch 通过成员表关联并用复合 FK/hash 校验。

验收：重复运行幂等；参数变化不覆盖；并发 claim；错误 batch/run 组合受 FK/事务拒绝；group hash 可复算；任一指标失败时 batch 不发布。

### ST-B1：MVP 衍生指标

依赖：P1.7-A 独立 PASS。

写路径：

- 衍生表 migration 与 Mapper/XML
- `marketdata/analysis/derived/**`
- `marketdata/analysis/model/**`（共享模型唯一所有者）

产出：相对强弱、市场/板块轮动、资金趋势、交易集中度和量价确认。不得创建收益贡献表。

强制公式：

- RS 窗口先冻结每日排行稳定身份交集 cohort；共同基准、板块收益和最终排名都只使用该 cohort。
- Spearman 按稳定身份连接，在交集内根据 change_rate 重排；零方差无定义。
- 板块位次使用每日 `n_t` 百分位，缺日中断连续性。
- 资金趋势冻结 `flowScope=WATCHED_SECTORS`、币种和累计口径；集中度 MVP 固定单个 CLOSE，正/负资金字段只称方向占比。
- CLOSE 量比使用 `t-5..t-1`；盘中只比较历史同时间桶。

验收：从原始字段到最终指标的端到端单元测试，覆盖单位、截断、错序身份、并列、变化宇宙、缺日、零方差、严格滞后、零分母和参数隔离。

### ST-B2：查询 API、每日总览和提醒

依赖：ST-B1。

写路径：

- `SectorAnalyticsController`、VO、查询 service
- `MarketQuoteController`、`MarketDataAlertMapper.java/xml` 及告警迁移
- `marketdata/analysis/alert/**`、`marketdata/scheduler/SectorAnalyticsScheduler.java`
- `ErrorCodeEnum` 与 `GlobalExceptionHandler` 的必要映射
- `docs/api/MARKET_DATA_API.md`

产出：readiness 固定 gate schema；daily-overview 固定 THIN/ADVANCED 选择规则、PageData 和 batch 冲突校验；单公式列表固定 v1/参数哈希、calculationRunId 分页锚点和次级排序；tracking symbol 未复权价格收益对照；提醒绑定 publication batch 并按冻结阈值返回非因果 evidence。

验收：MockMvc 覆盖 400/404/200 降级语义、分页上限、日期范围、公式/参数选择、scope/血缘完整性、板块过滤、跨批次拒绝、告警阈值边界和重复调度。分析接口不得反向调用 provider。

### ST-B3：P1.10-A 市场雷达与板块详情前端

依赖：ST-B2 API 契约冻结。

前端独立仓库写路径：feature、API adapter、types、router/navigation、page/component、tests 和 mock contract。不得只写后端仓库的 mock 建议代替前端实现。

产出：按 P1.10 上位线框实现市场雷达首屏、板块详情和数据与计算状态；显式展示 `RANKED_UNIVERSE`、覆盖率、上游阻断、公式版本和“不构成投资建议”。热力图、轮动矩阵和排行表使用同一发布批次。候选扫描只保留入口/空态，不在本子任务自行发明候选规则；MVP 不显示收益贡献。

验收：typecheck/lint/test/build；关键页面在桌面和移动端验证无溢出；空数据、阻断、降级、加载、错误、正常状态均有测试。

## 4. DAG 与提交

```text
ST-A1 -> 可部署薄切片总览
  |
  +--> ST-A2 -> 独立验证 P1.7-A -> ST-B1 -> ST-B2 -> ST-B3 -> 独立验证 P1.7-B
```

每个子任务独立 candidate commit。实现者只可 SELF_CHECKED；代码审查和最终验证使用干净角色。P1.7-A 未 PASS 时不得以“先写后补”为理由进入 B。

## 5. 机器验收清单

- 后端：定向测试 + `./mvnw test` + `./mvnw package`。
- 前端：typecheck + lint + test + build。
- DB：Flyway 从空库迁移、旧 V18 数据升级、唯一键/索引/回滚失败场景。
- 架构：无原始事实写回、无 provider 反向调用、无收益贡献 MVP 文件、无 watch_id 身份。
- 契约：OpenAPI/接口文档与 Controller/VO/错误映射一致。
- 数据：真实 fixture 单位、截断、固定 cohort、排名方向、CLOSE、日历、币种、累计、血缘、幂等、单公式 run 与跨公式 batch 发布均有反例测试。

结构关键词测试只能证明文档存在，不能作为功能或设计通过证据。

## 6. 停止条件

- 单位、完整性、CLOSE、身份或交易日历任一未冻结。
- 需要真实 provider 权限但环境不可用：记录阻塞，不伪造 PASS；其余纯本地任务可继续。
- 候选越过非目标（自动交易、真实下单、收益贡献 MVP）。
- 独立 verifier 给出 REJECT：回到对应子任务修复，不更新建设看板为完成。
