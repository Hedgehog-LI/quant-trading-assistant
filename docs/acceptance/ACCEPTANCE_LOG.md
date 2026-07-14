# Acceptance Log

> 按版本记录**实际执行过**的验收（测试数 / 构建 / Docker / curl / 浏览器）。**只记实际结果，不虚构**；新条目用 `../templates/ACCEPTANCE_TEMPLATE.md`。

---

## 2026-07-12 — P1.2 行情工作台 + P1.3 板块 单元测试验收（通过）

- **范围**：P1.2 行情工作台后端核心（V10 migration 6 表 + 分钟 K 质量/时段/采集计划/水位）+ P1.3 板块（V11 migration 2 表 + CRUD）+ 前端 2 个新页面。
- **后端门禁（全绿）**：
  - `./mvnw test`：**217 tests / 0 failures / 0 errors**（新增 29：MinuteBarQualityManagerTest 13 + TradingSessionManagerTest 8 + MarketDataWorkbenchServiceTest 8）。
  - `./mvnw -q compile` + `./mvnw -q -DskipTests package`：BUILD SUCCESS。
  - V10/V11 migration 在 H2 `MODE=MySQL` 下正常加载，未触发兼容性问题。
  - `git diff --check`：通过。
- **前端门禁（全绿）**：
  - `npm run typecheck`：通过。
  - `npm run lint`：通过（0 errors）。
  - `npm run test`：**221 tests passed**（新增 workbenchApi mock 5 tests）。
  - `npm run build`：通过。
- **单测覆盖重点**：
  - 分钟 K 质量：OHLC 非法 → REJECTED、volume 负 → REJECTED、turnoverRate 负 → SUSPECT、null → REJECTED、内容冲突检测。
  - 交易时段：空 DB 回退 A 股默认窗口、集合竞价开关、日历覆盖周末规则、幂等初始化。
  - 工作台 service：分钟 K 幂等（INSERTED/SKIPPED/CONFLICT/REJECTED）、水位 upsert、采集计划 CRUD/启停。
- **跳过项及原因**：
  - LongPort 真实外联验收：SKIPPED —— 无凭据/容器，本轮代码不涉及 LongPort 反射链路。
  - Docker `docker compose up` 联调：SKIPPED —— 代码侧编译+单测已覆盖。
  - 浏览器 Playwright 验收：SKIPPED —— 新页面 mock 模式测试已覆盖功能逻辑。
- **关联**：`development/DEVELOPMENT_LOG.md`（2026-07-12 P1.2/P1.3 条）、`ai/HANDOFF_2026-07-12_market_data_long_run.md`。

---

## 2026-07-12 — P1.2 行情工作台设计与建设看板同步验收（文档/看板）

- **范围**：产品架构设计文档、当前事实文档、前端建设看板数据和看板数据测试；未改后端业务代码、未改 DB migration。
- **设计验收**：
  - 新增 `docs/features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`，覆盖行情工作台、历史补档、盘中定时采集、分钟线资产、交易日历/交易时段、板块、异动大屏和多数据源策略。
  - `AI_HANDOFF.md`、`PRODUCT_BLUEPRINT.md`、`BUILD_CHECKLIST.md` 已把下一阶段从“直接 P2 指标/策略/回测”调整为“P1.2 行情工作台、采集任务与分钟线数据资产”。
  - `CURRENT_ARCHITECTURE_AND_MODULES.md` 已补 V7-V9 和 LongPort 行情当前事实。
- **看板验收**：
  - `longport-quote-snapshot`、`longport-history-sync` 已从 `IN_PROGRESS/M2/70` 更新为 `DONE/M4`。
  - 新增下一阶段节点：`longport-hardening`、`market-ops-workbench`、`market-collection-jobs`、`minute-bar-asset`、`market-movement-dashboard`、`multi-source-provider-research`。
  - 当前最优先 summary 改为 `P1.2 行情工作台与采集任务`。
- **实际执行检查**：
  - `git diff --check`（后端文档仓库）：通过。
  - `git -C /Users/joker/code/quant-trading-assistant-web diff --check`：通过。
  - 前端建设看板相关测试：通过。
- **结论**：P1.2 设计与建设看板同步通过；后续可以进入业务代码开发，但必须先实现数据资产和采集治理，再推进指标/策略/回测。

---

## 2026-07-12 — LongPort 单股票同步真实外联验收（通过）

- **范围**：P1.1 LongPort 单股票手动同步真实外联最小验收。1 个 A 股 symbol、单日、不复权、不做全量扫描。
- **前置条件（实测）**：
  - 官方 SDK artifact 坐标 `io.github.longportapp:openapi-sdk:4.3.3`（Maven Central `<release>`），jar 内置全平台 native。
  - `runtime-libs/` 已装 `openapi-sdk-4.3.3.jar` + `gson-2.10.1.jar` + `native-lib-loader-2.4.0.jar`。
  - SDK 默认域名 `openapi.longport.cn` / `openapi-quote.longport.cn` 已废弃（DNS 解析失败）；切换到 `https://openapi.longbridge.cn` + `wss://openapi-quote.longbridge.cn/v2`。
  - 只读凭据由 `.env.longport`（gitignored）`source` 注入；脚本不打印密钥。
- **实际执行命令**：
  - `set -a; source .env.longport; set +a`
  - `scripts/check-longport-readiness.sh` → errors=0 / warnings=2（可选检查跳过）。
  - `QTA_VERIFY_SYMBOL=SH.600519 QTA_VERIFY_START_DATE=2026-07-10 QTA_VERIFY_END_DATE=2026-07-10 scripts/verify-longport-real-sync.sh`。
- **验收结果（全部通过）**：
  - 后端 `./mvnw test`：187 tests / 0 failures / 0 errors。
  - `inspect-longport-runtime-libs.sh`：osx_arm64 与 linux_64 均通过。
  - Docker app 容器重建启动，`/actuator/health` = UP。
  - provider status：`configured=true / reachable=true / lastError=null`。
  - latest quote：`POST /quotes/latest`（persist=true）成功，`stock_quote_snapshot` 写入 1 条 `dataSource=LONGPORT`（SH.600519 贵州茅台，2026-07-10 收盘区数据，currentPrice=1204.98）。
  - daily bar sync：`POST /sync-tasks/daily-bars` 成功，`stock_daily_bar` 写入 1 条 `data_source=LONGPORT`（SH.600519 / 2026-07-10 / NONE）。
  - sync task 留痕：`status=SUCCEEDED / totalCount=1 / insertedCount=1 / failCount=0`。
- **不构成投资建议**：本次仅验证只读行情链路联通与落库；价格数据为单次小流量抓取，不作为任何交易依据。
- **关联**：`development/DEVELOPMENT_LOG.md`（2026-07-12 条）、`AI_HANDOFF.md`、`docs/development/LONGPORT_SDK_RUNTIME_INSTALLATION.md`。

### 2026-07-12 追加：收口复核（文档口径统一 + 域名覆盖补测试 + 全门禁 + 只读复核）

- **背景**：用户复核确认真实外联链路跑通后，要求做最终收口沉淀，便于后续 Codex 接手读取完整过程。
- **文档口径统一**：修正 7 个入口文档残留的"SDK 待安装 / Maven 查不到 / 外联未完成"旧口径，统一为"SDK 已装（`io.github.longportapp:openapi-sdk:4.3.3`，runtime-libs gitignored）+ 真实外联已验收 + 域名覆盖必配（`LONGPORT_HTTP_URL` / `LONGPORT_QUOTE_WEBSOCKET_URL`）"。历史日志原文保留。详见 `DEVELOPMENT_LOG.md` 2026-07-12 收口追加条。
- **补测试**：
  - fake SDK `Config` 加链式 `httpUrl` / `quoteWebsocketUrl`。
  - `ReflectiveLongPortQuoteClientTest` 新增 `reflectiveSdkPathHonoursDomainOverrides`（配置两个域名覆盖后 healthCheck + quote + daily bar 全成功）。
  - `check-longport-official-java-contract.sh` 加 `Config.httpUrl` / `Config.quoteWebsocketUrl` 合约断言。
- **后端门禁（全绿）**：`bash -n scripts/*.sh` 通过；`git diff --check` 通过；`./mvnw test` **188 tests / 0 failures**（+1 新测试）；`./mvnw -DskipTests package` BUILD SUCCESS。
- **前端门禁（全绿）**：`git diff --check` 通过；`npm run typecheck` 通过；`npm run lint` 通过；`npm run test` **214 tests passed**；`npm run build` 通过。
- **真实外联只读复核（容器未重建，HTTP 200）**：
  - `GET /providers/LONGPORT/status` → `configured=true / reachable=true / lastError=null`。
  - `GET /quote-snapshots?canonicalSymbol=SH.600519` → 1 条 LONGPORT（price=1204.98 / vol=52212 / quoteTime=2026-07-10T15:00:01）。
  - `GET /daily-bars?canonicalSymbol=SH.600519&startDate=2026-07-10&endDate=2026-07-20&adjustType=NONE` → 8 条 LONGPORT 日 K（7/1-7/10 跳过周末，OHLC 合理，7/10 C=1204.98 与快照一致）。
- **安全复核**：`.env` / `.env.longport` / runtime-libs jar 均 gitignored 不在 git status；tracked 改动无凭据明文；无交易能力接入；未 commit/push。
- **可否进入提交阶段**：可以。建议提交时显式 `git add` 选择文件，不用 `git add -A`。
- **遗留风险**：域名漂移（合约脚本兜底）；仅单 symbol 验收（多 symbol 并发/边界/QF 未压测，BUILD_CHECKLIST 已记）；volume 同日差 1 是快照 vs 历史 K 线 API 正常口径差异，非 bug。

---

## 2026-07-11 — LongPort 单股票同步后端 adapter 验收

- **后端实现**：
  - 新增 `LongPortProperties`，支持 `QTA_LONGPORT_ENABLED`、`LONGPORT_APP_KEY`、`LONGPORT_APP_SECRET`、`LONGPORT_ACCESS_TOKEN`、`QTA_LONGPORT_TIMEOUT_SECONDS`、`QTA_LONGPORT_QUOTE_TIME_ZONE`。
  - 新增 `LongPortMarketDataProvider`，负责 `SH.600519` ↔ `600519.SH` 转换、`NONE/QF` 复权映射、`HF` 明确拒绝。
  - 新增 `LongPortQuoteClient` 与 `ReflectiveLongPortQuoteClient`，运行时反射调用官方 Java SDK 的 `QuoteContext#getQuote` 和 `getHistoryCandlesticksByDate`；编译期不引入不可解析 Maven 依赖。
  - `DisabledMarketDataProvider` 仍是默认安全兜底；`enabled=true` 时切换 LongPort provider。
  - `.env.example` 与 `docker-compose.yml` 已增加 LongPort 环境变量透传，不提交密钥。
- **官方 SDK 调研结论**：
  - 官方 README/Javadoc 存在 Java SDK 与只读 quote/history 方法。
  - Maven Central 当前查询不到 `io.github.longport:openapi-sdk` artifact；`repo.maven.apache.org` metadata / `0.0.1` POM 返回 404。
  - GitHub `v4.3.3` release 当前未提供 Java jar，只看到 Node native assets。
  - 因此外联小调用未执行；真实调用需先解决 SDK jar/native libs 运行时安装。
- **已执行测试**：
  - `./mvnw -q -Dtest=LongPortMarketDataProviderTest,ReflectiveLongPortQuoteClientTest,LongPortSymbolMapperTest,MarketQuoteServiceTest test` 通过。
  - `./mvnw -q -Dtest=LongPortEnabledWithoutSdkContextTest,LongPortMarketDataProviderTest,ReflectiveLongPortQuoteClientTest,MarketQuoteServiceTest test` 通过。
  - `./mvnw -q -DskipTests package` 通过。
  - `./mvnw -q test` 通过；Surefire 报告合计 182 tests，Failures 0，Errors 0。
- **前端补充验收**：
  - `/market-data` 状态页已补 SDK 未安装 / 凭据未配置 / provider 不可达的明确提示。
  - 历史同步已禁用 `HF` 后复权，并在提交前做提示拦截。
  - 前端 `npm run typecheck` 通过。
  - 前端 `npm run lint` 通过。
  - 前端 `npm run test` 通过：28 files / 214 tests passed。
  - 前端 `npm run build` 通过。
- **未完成项**：
  - 未跑 Docker 重构建；本轮先用轻量 Java 测试控制成本。
- **结论**：后端“单股票手动同步发动机”的 adapter 边界、配置、错误降级和转换逻辑已落地；真实行情外联卡在官方 SDK 包获取/安装，不是业务层缺失。

### 追加验收：SDK 分发与 Docker runtime classpath

- **官方分发复核**：
  - LongPort `v4.3.3` release workflow 存在 `build-java-jni` 与 `publish-java-sdk`。
  - `repo.maven.apache.org` metadata 实测返回 404。
  - `search.maven.org` 精确查询 `io.github.longport:openapi-sdk` 实测 `numFound=0`。
  - GitHub `v4.3.3` release assets 实测仅有 Node `.node` 文件，未看到 Java jar。
- **运行时准备**：
  - Dockerfile 已改为 `PropertiesLauncher`，`loader.path` 指向 `/app/libs`。
  - Compose 已将项目 `runtime-libs/` 只读挂载到容器 `/app/libs`。
  - `.gitignore` 已防止真实 vendor jar/native 包进入 Git。
  - `ReflectiveLongPortQuoteClientTest` 使用 test-only fake SDK 覆盖 quote 与 daily bar 反射调用路径，并用隔离 ClassLoader 保留 SDK 缺失降级测试。
- **已执行检查**：
  - `git diff --check` 通过。
  - `./mvnw -q clean test` 通过；Surefire 合计 183 tests，Failures 0，Errors 0，Skipped 0。
  - `./mvnw -q -DskipTests package` 通过。
  - `docker compose config` 通过，确认 `runtime-libs` 只读挂载。
  - `docker compose up -d --build app` 通过，镜像构建并启动成功。
  - `curl http://localhost:8080/actuator/health` 返回 HTTP 200 + `status=UP`。
  - `curl http://localhost:8080/api/v1/market-data/providers/LONGPORT/status` 返回 HTTP 200 + `configured=false` + `LongPort provider 未启用`。
- **runtime-libs 外部 jar 通道实测（2026-07-12）**：
  - 临时用 test-only fake LongPort SDK classes 打成 `runtime-libs/qta-fake-longport-sdk.jar`，不提交 Git。
  - 用 `QTA_LONGPORT_ENABLED=true`、fake app key/secret/token 重建 app 容器。
  - `GET /api/v1/market-data/providers/LONGPORT/status` 返回 HTTP 200 + `configured=true` + `reachable=true`。
  - `POST /api/v1/market-data/quotes/latest`，请求 `{"canonicalSymbols":["SH.600519"],"persist":false}`，返回 HTTP 200 + `data[0].canonicalSymbol=SH.600519` + `dataSource=LONGPORT`。
  - 验证后删除 fake jar，重建默认容器；`runtime-libs/` 仅剩 `.gitkeep`，status 恢复为 HTTP 200 + `LongPort provider 未启用`。
  - 已沉淀并执行 `scripts/verify-longport-runtime-libs.sh`；脚本自动完成 fake jar 生成、容器重启、status/quote 断言、fake jar 清理和默认容器恢复。
- **真实外联验收脚本**：
  - 已新增 `scripts/verify-longport-real-sync.sh`。
  - 脚本用于官方 SDK jar/native libs 与真实只读凭据到位后验证：provider status、最新价落库并查询、日 K 同步任务并查询。
  - 脚本会在启动前检查 `LONGPORT_APP_KEY`、`LONGPORT_APP_SECRET`、`LONGPORT_ACCESS_TOKEN`；默认保留 LongPort enabled 容器用于继续联调，可用 `QTA_VERIFY_RESTORE_APP_AFTER_RUN=true` 在退出时恢复默认 disabled 容器。
  - 本轮未执行该脚本的真实外联路径，因为仍缺官方 SDK jar/native libs 与用户只读凭据。
- **SDK artifact 复查（2026-07-12）**：
  - `repo.maven.apache.org` metadata 仍返回 404。
  - `search.maven.org` 精确查询 `io.github.longport:openapi-sdk` 仍为 `numFound=0`。
- **本轮收尾复验（2026-07-12 10:36 Asia/Shanghai）**：
  - `./mvnw -q test` 通过，Surefire 汇总为 183 tests，0 failures，0 errors。
  - `bash -n scripts/verify-longport-runtime-libs.sh scripts/verify-longport-real-sync.sh` 通过。
  - `git diff --check` 通过。
  - 默认本地容器 LongPort status 为 HTTP 200 + `configured=false` + `LongPort provider 未启用`。
- **latest quote 输入校验补强（2026-07-12）**：
  - `POST /api/v1/market-data/quotes/latest` 已补齐 HTTP Bean Validation 和 service 层直接调用校验。
  - 空 `canonicalSymbols`、空代码、非法 canonical symbol、超过 500 个标的会在 provider 调用前被拒绝。
  - API 文档已同步修正 `quote-snapshots`、`sync-tasks` 的实际查询参数。
- **latest quote 补强后复验（2026-07-12 10:41 Asia/Shanghai）**：
  - `./mvnw -q -Dtest=MarketQuoteControllerValidationTest,MarketQuoteServiceTest,LongPortMarketDataProviderTest,ReflectiveLongPortQuoteClientTest test` 通过。
  - `./mvnw -q test` 通过，Surefire 汇总为 187 tests，0 failures，0 errors。
  - `./mvnw -q -DskipTests package` 通过。
  - `bash -n scripts/verify-longport-runtime-libs.sh scripts/verify-longport-real-sync.sh` 通过。
  - `git diff --check` 通过。
- **前端联调防呆验收（2026-07-12 10:46 Asia/Shanghai）**：
  - `src/pages/market-data.tsx` 已补齐 latest quote / daily bar sync 前端入参校验。
  - latest quote：空输入、非法 canonical symbol、超过 500 个标的在前端拦截；请求前先检查 provider status，未就绪时不发起拉取。
  - daily bar sync：非法 canonical symbol、起始日期晚于截止日期、HF 后复权在前端拦截；请求前先检查 provider status，未就绪时不创建同步任务。
  - Provider 状态页根据 SDK 缺失、凭据缺失、不可达等场景显示更具体的 Alert。
  - 前端 `npm run typecheck`、`npm run lint`、`npm run test`（28 files / 214 tests）、`npm run build` 均通过。
- **SDK 源码构建备选路径（2026-07-12）**：
  - 已新增 `scripts/build-longport-java-sdk-from-source.sh`。
  - 脚本按官方 release workflow 固化当前平台或 `QTA_LONGPORT_RUST_TARGET` 指定平台的 JNI + Java jar 构建路径，并将 SDK jar/runtime dependencies 安装到 `runtime-libs/`。
  - 已执行 `bash -n scripts/build-longport-java-sdk-from-source.sh scripts/verify-longport-runtime-libs.sh scripts/verify-longport-real-sync.sh`，语法通过。
  - 未执行真实源码构建，原因是该步骤会克隆官方仓库、下载 Maven/Cargo 依赖、编译 native 代码，耗时和外部状态不可控；真实外联验收仍以后续 SDK 产物 + 只读凭据为准。
- **SDK runtime-libs 离线检查（2026-07-12）**：
  - 已新增 `scripts/inspect-longport-runtime-libs.sh`。
  - 脚本检查 SDK jar、目标平台 native、`gson`、`native-lib-loader`，并拒绝 test-only fake SDK jar。
  - 当前 `runtime-libs/` 只有 `.gitkeep`，执行脚本会返回明确提示：`no jar files found ... build/download LongPort Java SDK first`。
  - 使用临时目录构造最小 fake SDK/dependency jars，指定 `QTA_LONGPORT_EXPECTED_RUST_TARGET=x86_64-unknown-linux-gnu`，脚本成功通过，证明正向路径可用。
  - 使用临时目录构造缺少 native 的 SDK jar，脚本返回 `SDK jar is missing native library ...`，证明目标平台 native 缺失会被拦截。
  - 使用临时目录放置 `qta-fake-longport-sdk.jar`，脚本返回 test-only fake SDK jar 残留提示，证明真实验收前能阻止误用测试包。
  - `bash -n scripts/inspect-longport-runtime-libs.sh scripts/build-longport-java-sdk-from-source.sh scripts/verify-longport-runtime-libs.sh scripts/verify-longport-real-sync.sh scripts/check-longport-readiness.sh` 通过。
  - `scripts/check-longport-readiness.sh` 已新增并执行轻量自检路径：注入测试凭据、跳过 runtime/provider 检查时返回 `errors=0`，证明脚本不会打印凭据且可按参数渐进开启检查。
- **官方 Java SDK 合约复核（2026-07-12）**：
  - 已新增 `scripts/check-longport-official-java-contract.sh` 和 `docs/development/LONGPORT_OFFICIAL_JAVA_CONTRACT.md`。
  - 对照官方 `v4.3.3` Java 源码确认：`Config.fromApikey`、`Config.fromApikeyEnv`、`QuoteContext.create`、`getQuoteLevel`、`getQuote`、`getHistoryCandlesticksByDate`、`SecurityQuote` / `Candlestick` getters、`Period.Day`、`AdjustType.NoAdjust`、`AdjustType.ForwardAdjust`、`TradeSessions.All` 均与本项目反射 adapter 匹配。
  - 当前在线 GitHub raw 检查因本机代理/DNS 失败；使用本地官方源码缓存设置 `QTA_LONGPORT_OPENAPI_RAW_BASE_URL=file://...` 检查通过。
- **未执行**：
  - 未执行真实 LongPort 外联；仍缺官方 SDK jar/native libs 与用户只读凭据。

---

## 2026-07-11 — P1.1 行情 provider facade 验收与真实 LongPort adapter 缺口确认

- **后端单测/构建**：
  - `./mvnw -q -Dtest=SyncScopeLockMapperTest,MarketQuoteServiceTest test` 通过。
  - `./mvnw -q test` 通过（本机 Oracle JDK 17 需 `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker=mock-maker-subclass` 避免 Mockito inline self-attach 失败）。
  - `./mvnw -q -DskipTests package` 通过。
  - `git diff --check` 通过。
- **Docker**：
  - 用户执行 `docker compose up -d --build` 成功：`qta-mysql` healthy，`qta-server` started。
  - `/actuator/health`：HTTP 200，`status=UP`。
- **后端 curl（Docker 本地 8080）**：
  - `GET /api/v1/market-data/providers/LONGPORT/status`：HTTP 200，`configured=false`。
  - `GET /api/v1/market-data/stocks`：HTTP 200，已有 `SH.600519` 样例数据。
  - `GET /api/v1/market-data/daily-bars`：HTTP 200，已有 `CSV` 样例日 K。
  - `GET /api/v1/market-data/quote-snapshots`：HTTP 200，空列表。
  - `GET /api/v1/market-data/sync-tasks`：HTTP 200，可查询失败任务留痕。
  - `GET /api/v1/market-data/alerts`：HTTP 200，可查询 `PROVIDER_NOT_CONFIGURED` 提醒。
  - `POST /api/v1/market-data/quotes/latest`：HTTP 400 + `BUSINESS_RULE_VIOLATION`（未配置/未实现真实 provider，预期业务拦截，不是 500）。
  - `POST /api/v1/market-data/sync-tasks/daily-bars`：HTTP 400 + `BUSINESS_RULE_VIOLATION`（预期业务拦截，不是 500）。
- **前端质量门禁**：
  - `npm run typecheck` 通过。
  - `npm run lint` 通过。
  - `npm run test`：28 files / 214 tests passed。
  - `npm run build` 通过。
- **浏览器 `/market-data` 实测**：
  - 本地 Vite `http://localhost:5173/market-data` 首屏复现 `Request failed with status code 502`。
  - 根因：前端 `.env.local` 中 `VITE_DEV_PROXY_TARGET=http://localhost:18081`，但当前 Docker 后端暴露在 `http://localhost:8080`；直接访问 8080 API 返回 200。
  - 结论：页面 502 是本地前端代理端口配置问题，不是后端 Docker/API 崩溃。
- **产品/研发结论**：
  - “发动机之外”的 DB、API 壳、失败留痕、提醒、并发锁、前端页面和质量门禁基本完成。
  - 真实 `LongPortMarketDataProvider` / Java SDK adapter 尚未实现，不能真实从长桥拉行情。
  - 下一轮开发入口：`docs/features/LONGPORT_SINGLE_SYMBOL_SYNC_ENGINE_DESIGN.md`。

---

## 2026-07 — 文档体系治理验收

- **后端** `./mvnw test`：121 通过（业务代码未改，回归通过）。
- **前端** typecheck/lint/test/build：0 error / 179 测试通过 / build 成功（业务代码未改）。
- **文档一致性**：主流程无 JPA/Repository 冲突、无 `v0.1.1 当前/待开发/Iteration 0.5` 残留、无不存在接口（`/api/risk-alerts`/`/api/review-notes`/`/api/backtests`）；Controller 路径与 `api/API_INDEX.md` 一致；`localStorageClient` `qta:` 前缀与 `mock/MOCK_REMOTE_CONTRACT.md` 一致；`API_INDEX` Portfolio 完整路径 `/api/v1/portfolio/positions`。
- **git diff --check**：两仓库 clean。
- **结论**：文档治理通过；**未改业务代码、未改 DB migration、未 commit/push**。

---

## 2026-07-06 — 生产环境实测验证

- **地址**：`http://129.204.169.155:18080`（只读 GET，未新增/修改/删除任何数据）。
- **首页**：HTTP 200。
- **/api/v1/watchlist**：`success=true, data=[]`。
- **/api/v1/trade-plans**：`success=true, data=[]`。
- **/api/v1/dashboard/today**：`success=true`，含 date/todos/pendingReviewJournals 完整数据。
- **结论**：生产 Nginx → Docker qta-server → MySQL 链路实测通过。**production-data-mode 升级 DONE/M4**。

---

## 2026-07 — 建设看板状态同步验收

- **后端** `./mvnw test`：121 通过（业务代码未改，回归）。
- **前端** typecheck/lint/test/build：0 error / **191 测试通过**（+8 buildStatusData + 3 useBuildStatus + 1 production RISK 矛盾检测）/ build 成功。
- **production-data-mode 口径**：降级 RISK/M3（生产 Nginx /api 反代未实测）；currentEvidence 不含"已验证"与"待部署"同时出现的矛盾；测试断言保护。
- **浏览器 /build-status**（Playwright，mock 模式）：桌面 + 窄屏；看板基线提示（v0.1.1 / 2026-07-06）显示；`证券主数据与行情基础` P1 节点 + `快照对比与账本对账` DONE 节点显示；`useBuildStatus` 初始 `null`（默认无抽屉，需点击节点打开）；控制台 `DEPRECATED_WARNINGS=0, CONSOLE_ERRORS=0`。
- **口径一致**：BUILD_CHECKLIST / PRODUCT_BLUEPRINT / buildStatusData 三处统一为"证券主数据**与**行情基础"。
- **git diff --check**：两仓库 clean。
- **结论**：看板同步通过；**未改业务代码/DB/未 commit/push**。

---

## 2026-07 — 文档治理最终收尾验收

在文档治理基础上修复 6 项残留：① `AGENTS.md` 删除"读取 CONVERSATION_HANDOFF"旧指令 + "前端可以后续生成"改为"前后端均已存在"；② `BUILD_CHECKLIST.md` "当前 P0.5" 改为 "已完成并验收"；③ `api/API_INDEX.md` 全部路径改完整 `/api/v1/...`（27 条，含 Position Snapshot 的 `/api/v1/position-snapshots/{id}/reconciliation` 等）；④ `qta-context-bootstrap` + `DEVELOPMENT_WORKFLOW` 删除"每轮总是追加 DEVELOPMENT_LOG"（改为"仅产品/架构/功能/缺陷/契约/治理重要变更才追加"）；⑤ `FRONTEND_ARCHITECTURE.md` 部署改为"宿主机 Nginx 托管 dist + Docker qta-server/qta-mysql，前端不容器化"（删除虚构的 qta-frontend 容器）；⑥ 日志规则修正（不声称未执行的检查通过）。

**验证（真实执行）**：
- 后端 `./mvnw test`：121 通过（业务代码未改，回归通过）。
- 前端 typecheck/lint/test/build：0 error / 179 测试通过 / build 成功（业务代码未改）。
- 残留复查：上一轮声明 `当前 P0.5`、AGENTS `CONVERSATION_HANDOFF`、`总是追加`、`qta-frontend`、`前端可以后续生成` 为 none，但**实际漏检** `PRODUCT_BLUEPRINT.md:87`、`CLAUDE.md:37`、`BUILD_CHECKLIST.md:51` 三处（"当前 P0.5"、"前端可以后续生成"、"下一阶段 P0: 建设看板"）。本轮已修复这三处并重新精确搜索，确认上述关键词在 `docs/`、`CLAUDE.md`、`AGENTS.md` 中**真正 none**（`ACCEPTANCE_LOG` 内的引用为历史记录说明，不计为残留）。
- Controller 方法级路径（`/{id}`、`/today`、`/comparison`、`/{snapshotId}/reconciliation`、`/calculations/position-size`、`/positions`、`/closed-trades`、`/summary`、`/symbol/{symbol}`、`/prices`、`/latest`、`/{id}/confirm`、`/{id}/cancel`、`/{id}/enabled`、`/{id}/review-status`、`/{id}/status`）全部出现在 API_INDEX 完整路径中。
- `git diff --check`：两仓库 clean。

**结论**：文档治理最终收尾通过；**未改业务代码、未改 DB migration、未 commit/push**。

---

## v0.1.1 — 基础交易闭环优化（最终交付，2026-07）

- **后端测试**：`./mvnw test` → `Tests run: 121, Failures: 0, Errors: 0`，BUILD SUCCESS。
- **后端打包**：`./mvnw package` → BUILD SUCCESS，`target/quant-trading-assistant-0.0.1-SNAPSHOT.jar` 31MB。
- **前端**：`npm run typecheck` 0 error；`npm run lint` 0 error/0 warning；`npm run test` = Test Files 26 / Tests 179 passed；`npm run build` 成功。
- **Docker**：`docker compose up -d --build` → `qta-mysql` healthy + `qta-server` `/actuator/health` UP，Flyway V1-V4 应用成功。
- **curl 端到端**：
  - `/actuator/health` UP。
  - `/dashboard/today` todos `targetPath` 全 `/journal*` 或 `/position-snapshots`（复数，无 404）。
  - `/dashboard/today?date=2026-06-27` `pendingReviewCount=1`，不含未来交易日。
  - `/position-snapshots/comparison` 正向 `INCREASED`，反向 `POSITION_SNAPSHOT_COMPARISON_INVALID`。
  - `/position-snapshots/{id}/reconciliation` 纯超卖（空快照+只卖出）→ `QUANTITY_MISMATCH` + 超卖 warning。
  - `PUT /trade-journals/{id}` `unlinkPlan=true` → `planId=null`（解绑成功）。
  - `/reviews` 含历史脏数据（空段/非法/重复）查询 → `SUCCESS`（不 500）。
- **浏览器（Playwright chromium）**：mock 模式访问 `/dashboard`、`/journal`、`/position-snapshots`、`/settings`，控制台 `DEPRECATED_WARNINGS=0, CONSOLE_ERRORS=0`。
- **结论**：v0.1.1 验收通过。

---

## v0.1.0 — Today MVP + 交易账本 + 持仓快照

- 后端基础测试 + 前端基础测试通过（具体数量低于 v0.1.1，已被后续版本覆盖）。
- Docker 冷构建 + 联调通过。
- 详细结果见 `../BUILD_CHECKLIST.md` 第 2-5 节勾选项。
