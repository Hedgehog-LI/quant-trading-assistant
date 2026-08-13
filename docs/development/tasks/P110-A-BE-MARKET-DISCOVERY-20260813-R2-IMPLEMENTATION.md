# P110-A Backend Market Discovery R2 Implementation

> 日期：2026-08-13  
> 状态：`FULL_STACK_CANDIDATE / AUTOMATION_PASS / MOCK_BROWSER_PASS / RUNTIME_NOT_VERIFIED`  
> 范围：P1.10-A 后端研究引擎与前端市场雷达/板块详情；不包含 Docker/服务器部署、真实资金流、P1.10-B/C。

## 1. 恢复背景

原治理任务 `P110-A-BE-MARKET-DISCOVERY-20260812-R1` 因多切片状态机缺陷在只完成首个 slice 后进入终态 `BLOCKED`。该账本是事故证据，保持不变。本轮按用户明确要求由当前 Codex 上下文直接恢复业务实现，没有重写 R1、伪造 R2 control、候选 commit 或独立 Agent 回执。

因此，本文件记录的是**代码实现和客观自动化证据**，不是治理状态机的独立交付裁决。

## 2. 已实现范围

### 2.1 原始事实身份和数据门禁

- 排行批次新增 `provider_quote_time`，由 LongPort HTTP 响应 `Date` 头解析；语义是 provider 响应时间，不是交易所逐笔时间。
- 排行项、关注板块快照和成分快照绑定 `market_sector_identity.id`，名称/外部代码不再承担主身份。
- readiness 固定声明 `RANKED_UNIVERSE`、期望样本量 100、实际数量、覆盖率、截断、来源时间、质量状态和原因码。
- 来源时间未知、cohort 小于 5、交易日中间缺口或 HK/US 长窗口缺少足量权威交易日历时 fail closed。

### 2.2 可重算衍生引擎

- `RELATIVE_STRENGTH v1`：固定 cohort、每日等权基准、对数相对收益、并列平均名次、百分位排名；所有比例为 decimal ratio。
- `ROTATION_PERSISTENCE v1`：固定 5 日动量，计算平均名次百分位、总体标准差、头部占用率、连续领涨/落后天数和首尾位次变化。
- 四象限状态：`LEADING/IMPROVING/WEAKENING/LAGGING/INSUFFICIENT_DATA`；仅作观察，不是买卖信号。
- 支持强度窗口 `5/10/20/50`，每个发布固定配套 5 日动量。

### 2.3 可审计原子发布

- 计算 run 绑定 provider、market、as-of、formula、version、window、参数哈希、源批次 manifest/hash、质量和样本数。
- 发布批次同时绑定强度 run 和动量 run；scope、market、as-of、强度/动量窗口、公式集合和源批次集合共同构成幂等身份。
- DB 行锁阻止同 scope 重复计算；同输入重跑复用发布批次。
- 发布成员使用复合外键和同 scope `INSERT SELECT`，阻止把 HK/US run 混入 CN 发布。
- 事务失败不会向查询端暴露半成品；查询只读取 `PUBLISHED` 批次。

### 2.4 API 与自动触发

- `GET /api/v1/market-research/readiness`
- `POST /api/v1/market-research/calculations`
- `GET /api/v1/market-research/radar`
- `GET /api/v1/market-research/sectors/ranking-history`
- `GET /api/v1/market-research/sectors/{sectorId}`
- CLOSE 排行批次成功保存后，scheduler 尝试计算 5/10/20/50 强度窗口；分析不足只记录可解释跳过，不反向污染原始采集成功状态。

详细契约见 `docs/api/MARKET_RESEARCH_API.md`。

## 3. 数据库变更

- V19：稳定板块身份和 readiness 基础（本轮接续的已有候选）。
- V20：排行稳定身份/source time、计算 run、发布批次和发布成员。
- V21：相对强弱、轮动持续性衍生结果。
- V22：双窗口发布身份、跨市场复合约束和查询索引。

V22 采用新 migration 补强 V20/V21，没有修改已可能执行过的 migration。

## 4. 自动化证据

| 门禁 | 实际结果 |
| --- | --- |
| 全量后端测试 | 515 tests，0 failures，0 errors，1 skipped |
| Maven package | PASS |
| H2 Flyway | V1-V22 全部成功迁移 |
| 真实格式 provider fixture | `chg=0.0240` / `value_data=2.40%` 经生产 JSON 解析进入计算和百分比格式化 |
| 端到端集成测试 | CLOSE facts → calculation → atomic publication → radar/history/detail；含幂等复跑、20+5 双窗口、跨市场 FK 拒绝 |
| 失败门禁测试 | 无发布空态、来源时间缺失拒绝、HK 长窗口缺权威日历拒绝 |
| 架构守卫 | 分析包不得依赖 provider、不得向原始表写 SQL；PASS |
| 架构脚本 | 0 errors；仅现有/复杂度 warning |
| AI 治理套件 | 70/70 PASS |
| `git diff --check` | PASS |
| 前端自动化 | typecheck、lint、51 files / 396 tests、production build PASS |
| 前端浏览器 | mock 桌面 1280px、窄屏 390px、雷达进入板块详情 PASS；无水平页面溢出 |

## 5. 未验证和剩余边界

- `RUNTIME_NOT_VERIFIED`：未拉 Docker、未在 MySQL 执行 V19-V22、未用真实 LongPort CLOSE 数据计算。
- `DEPLOYMENT_NOT_VERIFIED`：未部署服务器、未跑 Nginx/curl、未做浏览器验收。
- `REMOTE_UI_NOT_VERIFIED`：前端 remote adapter 已接入，但未连接本次新后端在真实数据上验收。
- `INDEPENDENT_ACCEPTANCE_NOT_RUN`：当前实现者完成了测试和自审，但没有使用干净、未参与实现的最终核验上下文；不得写成完整验收通过。
- 真实资金流、成交集中度、量价确认、异动提醒、候选扫描和个股决策台不在本轮。

## 6. 开发期间事故说明

一次集成测试最初缺少 `@ActiveProfiles("test")`，误连本机开发 MySQL，并执行了当时待应用的 V20/V21 后清理本机板块排行/分析测试数据。问题已立即修复：集成测试强制 test profile，此后全部使用 H2。服务器未受影响，源码和 migration 未丢失；本机板块排行/分析数据需要通过采集重新生成。该事故不影响自动化结果，但部署前必须按正常备份和 migration 流程操作。

## 7. 下一动作

1. 本地 Docker/MySQL 备份后重建，确认 V19-V22 和真实 CLOSE 数据计算。
2. 运行 readiness → calculation → radar → history → detail 的最小 curl 验收，再做 remote 桌面/窄屏浏览器验收。
3. 独立核验通过后，才把 P1.10-A 从“全栈候选”升级为“完整验收”。
