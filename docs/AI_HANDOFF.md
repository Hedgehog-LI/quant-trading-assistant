# AI Handoff

> 本文件只记录**当前接手所需事实**。历史开发细节见 `development/DEVELOPMENT_LOG.md`；验收记录见 `acceptance/ACCEPTANCE_LOG.md`。若与代码冲突，以 migration、测试、`BUILD_CHECKLIST.md`、`CURRENT_ARCHITECTURE_AND_MODULES.md` 为准（优先级见 `AI_DEVELOPMENT_INDEX.md §2`）。

## 项目定位

Quant Trading Assistant：个人交易辅助系统（自选股 / 计划 / 交易 / 账本 / 持仓快照 / 复盘 / 风控 / 工作台）。**不自动交易、不连券商、不存密钥、不承诺收益。**

## 仓库与技术栈

| 仓库 | 路径 | 技术栈 |
| --- | --- | --- |
| 后端 + 文档 | `/Users/joker/code/quant-trading-assistant` | Java 17、Spring Boot 3.5、MyBatis XML、MapStruct、Flyway、MySQL 8.4、H2 test、Docker Compose |
| 前端 | `/Users/joker/code/quant-trading-assistant-web` | React 19、Vite、TypeScript、Ant Design 6、mock/remote 双模式 |

## 当前状态（2026-07）

- **v0.1.0** Today MVP + 交易账本 + 持仓快照：已完成。
- **v0.1.1** 基础交易闭环优化（计划关联 + 复盘一致性 + 快照对比 + FIFO 对账 + 工作台待办 + 连接防呆）及多轮质量收尾：**已完成并验收**。范围与改动见 `development/DEVELOPMENT_LOG.md`。
- **P1.0 行情基础**：`marketdata` 模块已存在，V5/V6 已实现 `stock_basic`、`stock_daily_bar`、CSV 日 K 导入和 `fetched_at`。
- **v0.1.1 验收**：后端 121 测试通过、前端 179 测试通过、Docker 冷构建 + curl 端到端 + 浏览器（4 页面控制台 `DEPRECATED_WARNINGS=0`）全绿。详见 `acceptance/ACCEPTANCE_LOG.md`。

## 下一阶段

P1.0 证券主数据和 CSV 日 K 基础已由 `marketdata` 模块实现（V5/V6）。P1.1 LongPort provider facade + V7-V9 migration + 9 API + 6 Tab 前端已实现；真实 LongPort SDK 凭据联调待完成。

## 接手顺序（新会话）

1. 启用 skill `.claude/skills/qta-context-bootstrap`（分阶段加载上下文）。
2. `AGENTS.md` → `CLAUDE.md` → `docs/AI_DEVELOPMENT_INDEX.md` → 本文件。
3. 按任务类型路由（`AI_DEVELOPMENT_INDEX.md §4`）只读必要文档；Historical 文档（§6）不必读。

## 开发完成定义

- 后端 `./mvnw test` + `./mvnw package` 通过；前端 typecheck / lint / test / build 通过。
- 新增 DB 结构只通过更高版本 Flyway migration；MyBatis SQL 在 XML；分层清晰。
- 开发结束按 `docs/DEVELOPMENT_WORKFLOW.md §2` 执行文档同步检查。
- 未经用户明确要求，**不自动 commit / push / 部署远程**。
