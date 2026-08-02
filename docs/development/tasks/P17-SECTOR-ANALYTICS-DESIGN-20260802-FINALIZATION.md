# Delivery Finalization: P17-SECTOR-ANALYTICS-DESIGN-20260802

- Task ID: `P17-SECTOR-ANALYTICS-DESIGN-20260802`
- Lane: `L0`（设计 only）
- Status: `DELIVERY_READY`
- 独立验收：`qta-final-verifier` FV-RUN-1 `ACCEPTED`（functional=PASS, architecture=PASS, deliveryPermitted=true）
- 候选身份：`c8341df03ca656732cc85c42dcd779f066b835a5 / 7b4b016529915cfcc0031346b5e6855b276b776f`（generation 1, SNAPSHOT, 无漂移）
- Baseline：`563e84a573426800b3f6aa8e4e0525bc5314b3a8` on `codex/p17-sector-analytics-design-20260802`

> 本任务为 **P1.7 板块分析层可开发设计**，仅产出设计文档与任务拆分 artifact，不写业务代码。
> 按契约 §Out Of Scope，**不修改** `BUILD_CHECKLIST.md`、`AI_HANDOFF.md`、`docs/development/DEVELOPMENT_LOG.md`、`docs/acceptance/ACCEPTANCE_LOG.md` —— 这些统一留给后续集成/交付任务（即 `P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md` 中 ST-1..ST-4 完成后的收口）。
> 因此本 finalization 仅记录本设计任务的交付范围、证据与推送边界，不触达全局建设看板与交接。

## 交付物（本任务新增/追加的文件）

| 文件 | 类型 | 说明 |
|---|---|---|
| `docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md` | 新建主设计 | 板块相对强弱/轮动持续性/龙头贡献/成交量确认/异动提醒五大可解释公式 + 四视角 + 三层分层 + 数据模型(V19+) + MyBatis/Flyway 边界 + API/前端设计 + 风险失效边界 |
| `docs/api/MARKET_DATA_API.md`（追加 §5） | 追加 | §5 板块分析（规划/未实现）规划端点 + `ApiResponse<T>` 示例 + 错误码；§1-§4 未改 |
| `docs/DATABASE_DESIGN.md`（追加板块分析规划表块） | 追加 | V19+ 规划衍生表 + 复用 `market_data_alert`；V1-V18 未改 |
| `docs/development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md` | 新建实现计划 | ST-1..ST-4 四个可并行子任务（独占写路径/依赖/AC/测试/合并顺序 + 并行串行 DAG） |
| `scripts/tests/p17-sector-analytics-design-structure.test.mjs` | 新建静态校验脚本 | 19 项断言覆盖 AC-01/02/03（结构/交叉引用/污染探测） |
| `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-*.md` | 治理 artifact | CONTRACT / TEST-DESIGN / IMPLEMENTER / REVIEW / VERIFICATION / FINALIZATION |
| `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-CONTROL.json` | 机器控制 | schemaVersion 3，DELIVERY_READY |
| `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-CANDIDATE.json` | SNAPSHOT manifest | 9 entries |
| `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-BASELINE-CANDIDATE.patch` | 冻结 diff | baseline→candidate |
| `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-ARCH-REPORT.json` | 架构门禁报告 | errorCount=0, warningCount=0, 绑定冻结候选 |
| `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-EVIDENCE-TEST-*.json` | 证据回执 | STATIC + 治理 |

## 验收证据摘要

- AC-01/02/03：静态结构脚本 19/19 断言通过，evidence runner 观察到 selector `P17-SECTOR-ANALYTICS-AC01/AC02/AC03`，exit 0，候选不变。
- 架构门禁：`check-ai-architecture.mjs` exit 0，errorCount=0，warningCount=0，报告 SHA `d7c75c131436e1d4b4d9a71358d93c42d36d85ae0128fe247515131a76ada69f` 绑定冻结候选（CR-1 关闭）。
- 治理门禁：`validate-ai-governance.mjs` + `run-ai-governance-gates.mjs`（58/58）exit 0。
- 候选无漂移：manifest manifestSha256 `3fdb49dd...` / entrySetSha256 `001cc821...` 匹配；冻结 diff SHA `80140284...` 匹配。
- append-only 基线：API/DB 候选新增 0 删除行；V1-V18 表与 §1-§4 未改；新规划区无 `已实现`。
- 禁止项：无买卖指令/无自动交易/无黑盒 ML/无券商/密钥；分析结果不写回原始事实表（仅含否定形式声明）。

## 设计关键决策（交付摘要）

1. 板块分析层分三层：**原始事实（不可变）→ 衍生指标（V19+ 新表，只读原始事实，幂等可重算）→ 提醒事件（复用 `market_data_alert` 新增 `SECTOR_*` 类型）**。衍生结果绝不写回 `market_sector_*`/`stock_*`/`ranking_*` 原始事实表。
2. 五大可解释白盒公式：相对强弱（Mansfield + RS-rank）、轮动持续性（Spearman 位次相关 + 连续领涨/跌天数）、龙头贡献（成交额加权为主+净流入加权为辅）、成交量确认（方向一致性）、异动提醒（阈值 + Z-score）。无黑盒/ML 隐式评分。
3. 基准/窗口/缺失/停牌/跨市场：默认收盘快照序列为主；基准=用户 `tracking_symbol`，缺则同市场板块等权均值并显式标记；停牌按 `trade_status`/`is_delayed` 排除并计 `excluded_member_count`；样本不足标 `INSUFFICIENT_SAMPLE` 降级；跨市场严格按 ZoneId（CN/HK/US）对齐交易日，不混算。
4. 后续实现拆为 ST-1（数据模型+衍生计算服务+DTO/Mapper）→ ST-2（分析 REST API）+ ST-3（异动评估器+`market_data_alert`+Scheduler）并行 → ST-4（前端页面+图表+mock 契约，前端在独立仓库）。每个子任务列独占写路径、依赖、AC、测试与合并顺序。

## Git 与推送

- Git automation: `DELIVERY_PUSH`（用户明确授权）。
- 任务分支: `codex/p17-sector-analytics-design-20260802`。
- 推送目标: `origin/codex/p17-sector-analytics-design-20260802`。
- 禁止: 合并或推送 `main`、force push、推送其他分支。
- 提交阶段: contract（`d3d2613`）→ candidate（`c8341df`）→ candidate-identity（`54c809e`）→ finalization（本次）。
- 推送条件: 候选不变 + finalization 完成 + delivery-ready 门禁通过。

## 未触达（留给后续集成任务）

- `BUILD_CHECKLIST.md`、`AI_HANDOFF.md`、`docs/development/DEVELOPMENT_LOG.md`、`docs/acceptance/ACCEPTANCE_LOG.md` 不在本设计任务范围（契约 §Out Of Scope），由 ST-1..ST-4 完成后的收口任务统一同步。
- 任何业务代码（Java/MyBatis XML/Flyway SQL/React/TS）—— 留待 ST-1..ST-4。
- 运行时/部署验证 —— RUNTIME/DEPLOYMENT 为 NOT_REQUIRED（设计任务，契约禁止 Maven/npm/Docker/外联）。

## 非阻塞遗留

- CR-2（RS 公式 Mansfield 标准化步骤以省略号写出）—— 非阻塞 NIT，可在 ST-1 实现相对强弱计算时补闭式表达，不影响本设计交付。
