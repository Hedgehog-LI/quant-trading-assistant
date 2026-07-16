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
- **P1.2 行情工作台 + 分钟线资产（2026-07-16 第六轮 Codex 收口完成）**：V10/V11/V12 migration、工作台、采集计划 CRUD、分钟 K 存储/质量/水位、板块后端和前端页面已完成。后端 **250 tests**、前端 **261 tests** 全绿。`TaskItemsDrawer` 按 `lastTaskId` 重建内部状态，内部使用单一加载 effect + request-id/active guard；从第二页切换任务只请求新任务 page=1，旧响应和卸载后的异步回调不会覆盖当前数据；startedAt/finishedAt 使用统一日期工具展示。板块创建/删除失败、添加/移除 pending 与任务明细 7 类交互均真实触发并断言 API。**未完成（下一阶段）：分钟 K LongPort 批量 adapter（getMinuteBars）+ 盘中 scheduler（@Scheduled）+ MINUTE_BAR_BACKFILL/INTRADAY 执行链路**。
- **多轮交付总览**：2026-07-12 至 2026-07-16 的功能、六轮质量收口、最终门禁和未完成边界见 `development/MULTI_ROUND_DELIVERY_2026-07-16.md`。
- **P1.1 LongPort 单股票手动同步**：后端已实现 `LongPortProperties`、`LongPortMarketDataProvider`、`LongPortQuoteClient`、`ReflectiveLongPortQuoteClient`、Docker/env 透传和单元测试；默认仍安全 disabled。Docker `runtime-libs/` 外部 jar 通道已用 fake SDK jar 实测可加载。**官方 Java SDK artifact 已在 Maven Central 找到并安装（2026-07-12）**：之前查询失败是因为用了官方源码 `java/javasrc/pom.xml` 里错误的 groupId `io.github.longport`（缺 `app` 后缀），正确坐标是 `io.github.longportapp:openapi-sdk:4.3.3`（`<release>=4.3.3`，`versionCount=68`）。`openapi-sdk-4.3.3.jar`（约 35MB）内置全平台 native（linux/osx/windows × 64/arm64），已连同 `gson-2.10.1`、`native-lib-loader-2.4.0` 放入 `runtime-libs/`（gitignored），`inspect-longport-runtime-libs.sh` 对 osx_arm64 与 linux_64 均通过。**真实外联已于 2026-07-12 验证通过**（见下条）。
- **P1.1 真实外联验收通过（2026-07-12）**：SDK 默认域名 `openapi.longport.cn` / `openapi-quote.longport.cn` 已废弃（DNS 解析失败，长桥已更名 Longbridge）。本轮新增 `LONGPORT_HTTP_URL` / `LONGPORT_QUOTE_WEBSOCKET_URL` 可选覆盖（`Config.httpUrl(...)` / `Config.quoteWebsocketUrl(...)` 反射调用），切换到 `https://openapi.longbridge.cn` + `wss://openapi-quote.longbridge.cn/v2`；docker-compose app 服务加 `dns`（默认 223.5.5.5/119.29.29.29）保证容器 native resolver 解析。`verify-longport-real-sync.sh`（SH.600519 / 2026-07-10 单日 / NONE）全绿：provider `configured=true / reachable=true`、latest quote 写入 `stock_quote_snapshot(dataSource=LONGPORT)`、daily bar 写入 `stock_daily_bar(data_source=LONGPORT)`、sync task `SUCCEEDED/inserted=1`。后端 `./mvnw test` 187 tests 通过。凭据通过 `.env.longport`（gitignored）`source` 注入，不进 Git/文档/日志/前端。
- **P1.1 最新验收（2026-07-12）**：latest quote 请求已补齐 HTTP Bean Validation + service 层校验；`./mvnw -q test` 通过 187 tests / 0 failures / 0 errors，`./mvnw -q -DskipTests package` 通过，两个 LongPort 验收脚本 `bash -n` 通过，`git diff --check` 通过。
- **P1.1 前端联调防呆（2026-07-12）**：前端行情页已补齐 canonical symbol 格式校验、latest quote 单次 500 个上限、同步日期起止校验、HF 禁用、点击拉取/同步前 provider status 预检查与状态提示。前端 `npm run typecheck` / `lint` / `test`（214 tests）/ `build` 已通过。
- **P1.1 SDK 源码构建备选路径（2026-07-12）**：新增 `scripts/build-longport-java-sdk-from-source.sh`，在 Maven artifact 继续不可用时，可从官方 `longportapp/openapi` tag 构建当前平台或 `QTA_LONGPORT_RUST_TARGET` 指定平台的 JNI + Java jar，并复制 SDK jar/runtime deps 到 `runtime-libs/`。该脚本未执行真实构建，仅通过 `bash -n`。
- **P1.1 SDK 离线检查（2026-07-12）**：新增 `scripts/inspect-longport-runtime-libs.sh`，用于在真实外联前离线检查 `runtime-libs/` 中 SDK jar、目标平台 native、`gson`、`native-lib-loader` 是否齐全；当前空 `runtime-libs` 下会明确提示需先构建/下载 SDK。
- **P1.1 官方 SDK 合约检查（2026-07-12）**：新增 `scripts/check-longport-official-java-contract.sh` 和 `docs/development/LONGPORT_OFFICIAL_JAVA_CONTRACT.md`，用于核对 `ReflectiveLongPortQuoteClient` 依赖的官方类、方法、getter、枚举常量。`v4.3.3` 源码缓存检查通过；当前在线 raw GitHub 检查受代理/DNS 影响不可达。
- **P1.1 真实外联预检（2026-07-12）**：新增 `scripts/check-longport-readiness.sh`，在真实外联前集中检查只读凭据是否存在、`QTA_LONGPORT_ENABLED`、`runtime-libs` SDK/native/dependency 结构、可选官方源码合约和可选 provider status。该脚本不会打印 LongPort 密钥；`scripts/verify-longport-real-sync.sh` 也已支持 `QTA_VERIFY_RUNTIME_LIB_INSPECTION=auto|true|false`。
- **P1.1 暂停归档（2026-07-12）**：当前暂停点与 ZCode 接手清单已沉淀到 `docs/ai/HANDOFF_2026-07-12_longport_zcode_resume.md`；完整可复制 ZCode 提示词在 `docs/prompts/ZCODE_LONGPORT_RESUME_PROMPT_2026-07-12.md`。后续接手优先读这两个文件，避免重放长聊天。
- **v0.1.1 验收**：后端 121 测试通过、前端 179 测试通过、Docker 冷构建 + curl 端到端 + 浏览器（4 页面控制台 `DEPRECATED_WARNINGS=0`）全绿。详见 `acceptance/ACCEPTANCE_LOG.md`。
- **2026-07-11 轻量接手（历史）**：上一轮 Claude/GLM 在修复 `SyncScopeLockMapper.xml` MySQL 兼容问题时触发 5 小时用量上限，历史说明保留在 `docs/ai/ZCODE_HANDOFF_2026-07-11.md`。当前新会话优先读本文件和 `docs/development/DEVELOPMENT_LOG.md` 最新条目，不再按 2026-07-11 历史任务接手。

## 下一阶段

P1.0 证券主数据和 CSV 日 K 基础已由 `marketdata` 模块实现（V5/V6）。P1.1 LongPort provider facade + V7-V9 migration + 9 API + 6 Tab 前端已实现；后端反射式 SDK adapter 已实现。**P1.1 单股票手动同步真实外联已于 2026-07-12 全流程验收通过**（SDK 安装 + 域名覆盖 + 凭据 + 单 symbol 落库）。

P1.2 的工作台、计划配置、分钟线表结构、任务明细、水位和板块管理已经完成。当前下一阶段是补齐 **P1.2 行情采集执行引擎**，再进入指标/策略/回测。设计入口为 `docs/features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`。下一轮优先级：

1. 实现 LongPort 分钟 K adapter（`getMinuteBars`），明确粒度、复权、分页和限流边界。
2. 打通 `MINUTE_BAR_BACKFILL` / `INTRADAY_MINUTE_REFRESH` 从计划到子任务、落库、水位和质量结果的执行链路。
3. 增加交易时段约束下的 scheduler，非交易时段不做无效轮询，并保留手工触发入口。
4. 用单标的、小时间范围完成 Docker/curl/页面端到端验收，确认幂等、失败留痕和任务收敛。
5. 执行引擎稳定后再建设异动大屏、量价指标和多数据源扩展，不做全市场扫描。

## 接手顺序（新会话）

1. 启用 skill `.claude/skills/qta-context-bootstrap`（分阶段加载上下文）。
2. `AGENTS.md` → `CLAUDE.md` → `docs/AI_DEVELOPMENT_INDEX.md` → 本文件。
3. 按任务类型路由（`AI_DEVELOPMENT_INDEX.md §4`）只读必要文档；Historical 文档（§6）不必读。

## 开发完成定义

- 后端 `./mvnw test` + `./mvnw package` 通过；前端 typecheck / lint / test / build 通过。
- 新增 DB 结构只通过更高版本 Flyway migration；MyBatis SQL 在 XML；分层清晰。
- 开发结束按 `docs/DEVELOPMENT_WORKFLOW.md §2` 执行文档同步检查。
- 未经用户明确要求，**不自动 commit / push / 部署远程**。
