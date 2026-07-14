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
- **P1.2 行情工作台 + 分钟线资产（2026-07-12/13 后端核心 + 手动执行闭环 + 前端已完成）**：V10 migration 新增 `stock_minute_bar`、`market_trading_session`、`market_calendar`、`market_data_sync_plan`、`market_data_sync_task_item`、`market_data_watermark`（6 表）；V11 新增 `market_segment`、`market_segment_member`（2 表）。后端实现分钟 K 质量校验（OHLC/非负/交易日/时段 VALID-SUSPECT-REJECTED）、交易时段启动幂等初始化、采集计划 CRUD/启停/`POST /sync-plans/{id}/run` 手动执行（生成 task+item+lastRun，接入 daily bar 写入链路）、分钟 K 幂等写入+水位更新、工作台 overview 接 DAO 真实查询、板块 CRUD+成员管理；21 个新 API。前端新增 `/market-workspace`（4 Tab）+ `/market-segments`（板块管理）。后端 219 tests / 前端 221 tests 全绿；Docker curl smoke test 通过。**盘中自动调度 + 分钟 K 批量拉取 LongPort adapter 待下一轮补**。详见 `docs/ai/HANDOFF_2026-07-12_market_data_long_run.md`。
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

当前下一阶段不是直接做指标/策略/回测，而是先做 **P1.2 行情工作台、采集任务与分钟线数据资产**，设计入口为 `docs/features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`。下一轮优先级：

1. 行情工作台 MVP：工作台下聚合 provider 状态、重点标的、最近同步、失败任务、未处理提醒。
2. 采集任务配置中心：支持历史补档（例如 `SH.600519`，2025-01 到最近交易日，30M）和盘中定时采集（交易时段内按配置频率落库）。
3. 分钟线数据资产：新增 `stock_minute_bar` 及必要的交易日历、交易时段、任务明细、水位模型。
4. 异动大屏和量价观察：先围绕持仓股、自选股、计划股和自定义板块，不做全市场扫描。
5. 多源研究：LongPort 继续作为主线，同时保留 Tushare、AKShare、BaoStock、专业数据源的 provider/导入桥扩展位。

## 接手顺序（新会话）

1. 启用 skill `.claude/skills/qta-context-bootstrap`（分阶段加载上下文）。
2. `AGENTS.md` → `CLAUDE.md` → `docs/AI_DEVELOPMENT_INDEX.md` → 本文件。
3. 按任务类型路由（`AI_DEVELOPMENT_INDEX.md §4`）只读必要文档；Historical 文档（§6）不必读。

## 开发完成定义

- 后端 `./mvnw test` + `./mvnw package` 通过；前端 typecheck / lint / test / build 通过。
- 新增 DB 结构只通过更高版本 Flyway migration；MyBatis SQL 在 XML；分层清晰。
- 开发结束按 `docs/DEVELOPMENT_WORKFLOW.md §2` 执行文档同步检查。
- 未经用户明确要求，**不自动 commit / push / 部署远程**。
