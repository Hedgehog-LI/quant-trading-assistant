# Development Log

> 按版本追加开发记录。每条：目标 / 范围 / 前后端改动 / 接口变化 / 测试结果 / 产品决策 / 遗留问题 / 关联文档。**不粘贴命令流水和聊天全文。** 新条目用 `docs/templates/DEVELOPMENT_LOG_TEMPLATE.md`。

---

## 2026-07-12 — LongPort SDK 安装 + 域名覆盖 + 真实外联验收

- **目标**：完成 P1.1 LongPort 单股票手动同步真实外联的最后一公里 —— 安装官方 Java SDK、解决 SDK 默认域名废弃问题、单 symbol 单日真实落库验收。
- **范围**：后端代码（配置 + 反射 adapter）+ 配置文件透传 + `.env`/`.env.example` + docker-compose + 文档；不新增 DB migration；不接交易、账户、订单、真实持仓能力；不保存密钥。
- **关键发现 1：官方 SDK artifact 早已可用，之前 groupId 查错**：
  - 之前所有"Maven Central 查不到"的结论根因是官方源码 `java/javasrc/pom.xml` 里 groupId `io.github.longport` 缺 `app` 后缀。
  - 正确坐标 `io.github.longportapp:openapi-sdk:4.3.3`（`<release>=4.3.3`，`versionCount=68`，`lastUpdated=20260701095601`）。
  - `openapi-sdk-4.3.3.jar`（约 35MB）内置全平台 native（linux/osx/windows × 64/arm64），含本项目反射 adapter 需要的全部 `com.longport.*` 类。一个 jar 同时覆盖本机 osx_arm64 与服务器 linux_64，无需源码构建。
- **关键发现 2：SDK 默认域名已废弃，需切换到 Longbridge 新域名**：
  - native lib 硬编码默认域名 `https://openapi.longport.cn`（HTTP）+ `wss://openapi-quote.longport.cn/v2`（quote ws）已废弃，DNS 解析失败（长桥已更名 Longbridge）。
  - 可用同源域名：`https://openapi.longbridge.cn` + `wss://openapi-quote.longbridge.cn/v2`（解析到阿里云国内节点）。
- **后端改动**：
  - `LongPortProperties` 新增可选 `httpUrl`、`quoteWebsocketUrl` 字段及 getter/setter/hasXxx，由 `LONGPORT_HTTP_URL` / `LONGPORT_QUOTE_WEBSOCKET_URL` 环境变量驱动，默认空（不影响默认行为）。
  - `ReflectiveLongPortQuoteClient.createConfig` 在创建 `Config` 后，若配了上述字段则反射调用 `Config.httpUrl(...)` / `Config.quoteWebsocketUrl(...)` 覆盖默认域名。
  - `application.properties` 增加 `qta.market-data.longport.http-url` / `quote-websocket-url` 占位符绑定。
  - `docker-compose.yml` app 服务：透传 `LONGPORT_HTTP_URL` / `LONGPORT_QUOTE_WEBSOCKET_URL`；新增 `dns`（默认 `223.5.5.5` / `119.29.29.29`，由 `QTA_DNS_SERVER_1/2` 覆盖）保证容器内 native resolver 解析外部域名。
  - `.env.example` 增加 `LONGPORT_HTTP_URL=` / `LONGPORT_QUOTE_WEBSOCKET_URL=`。
- **凭据管理**：本地只读凭据放在独立的 `.env.longport`（被 `.gitignore` 的 `.env.*` 规则忽略），运行前 `set -a; source .env.longport; set +a` 注入；`.env` 只保留本地基础配置 + 非密开关。两文件均不入 Git。
- **测试结果**：
  - `./mvnw test` 187 tests / 0 failures / 0 errors。
  - `inspect-longport-runtime-libs.sh` 对 osx_arm64 与 linux_64 均通过。
  - `check-longport-readiness.sh` errors=0。
  - `verify-longport-real-sync.sh`（SH.600519 / 2026-07-10 / NONE）全绿：provider `configured=true / reachable=true`；latest quote 写入 `stock_quote_snapshot`（dataSource=LONGPORT）；daily bar 写入 `stock_daily_bar`（data_source=LONGPORT）；sync task `SUCCEEDED / insertedCount=1`。
- **遗留问题 / 部署注意**：
  - 服务器部署必须配 `LONGPORT_HTTP_URL=https://openapi.longbridge.cn` + `LONGPORT_QUOTE_WEBSOCKET_URL=wss://openapi-quote.longbridge.cn/v2`，否则 SDK 默认域名解析失败。详见 `docs/development/LONGPORT_SDK_RUNTIME_INSTALLATION.md`。
  - `docker-compose.yml` 的 `dns` 默认值是国内公共 DNS；海外部署如需可经 `QTA_DNS_SERVER_1/2` 覆盖。
- **关联文档**：`docs/development/LONGPORT_SDK_RUNTIME_INSTALLATION.md`、`api/MARKET_DATA_API.md`、`BUILD_CHECKLIST.md`、`acceptance/ACCEPTANCE_LOG.md`。

### 2026-07-12 追加：文档口径统一收口 + 域名覆盖补测试 + 全门禁复核

- **目标**：用户复核确认真实外联链路跑通后，做最终收口 —— 统一所有当前事实文档的旧口径（"SDK 待安装 / Maven 查不到 / 外联未完成"）、为域名覆盖逻辑补最小单测、跑全量前后端质量门禁、只读复核真实外联状态。
- **文档口径修正（7 个入口文档 + 1 个合约文档）**：
  - `docs/api/MARKET_DATA_API.md`：头注 + §2 标题 + §2 实现状态 + §3 安全约束，统一改为"真实外联已验收 + 正确坐标 `io.github.longportapp:openapi-sdk:4.3.3` + runtime-libs gitignored + 域名覆盖必配"。
  - `docs/PRODUCT_BLUEPRINT.md`：P1.1 从"部分实现"改为"已完成 + 真实外联已验收 + 域名覆盖必配"。
  - `docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`：当前实现事实从"SDK 包安装和凭据联调待完成"改为"已装 + 真实外联已验收"。
  - `docs/features/LONGPORT_SINGLE_SYMBOL_SYNC_ENGINE_DESIGN.md`：§2.5"仍未完成/外部阻塞"整段改为"外部阻塞已全部解除 + 验收通过 + 域名覆盖必配"。
  - `docs/prompts/ZCODE_LONGPORT_RESUME_PROMPT_2026-07-12.md`：顶部加"历史状态(归档)"说明，标注后续读 `AI_HANDOFF.md` 获取当前事实，原 prompt 块保留作历史归档。
  - `docs/development/LONGPORT_OFFICIAL_JAVA_CONTRACT.md`：合约表补 `Config.httpUrl(String)` / `Config.quoteWebsocketUrl(String)` 两行。
  - `docs/AI_HANDOFF.md`、`docs/BUILD_CHECKLIST.md`：前几轮已更新为最新口径，本轮无需改。
  - 原则：历史日志（DEVELOPMENT_LOG/ACCEPTANCE_LOG 历史条目、resume prompt 原文）保留不动，只改"当前入口文档"避免误导新会话。
- **补测试（域名覆盖逻辑的最小单测）**：
  - `src/test/java/com/longport/Config.java`（fake SDK）：新增链式 `httpUrl(String)` / `quoteWebsocketUrl(String)` 方法 + getter，对齐官方 SDK fluent 风格。
  - `src/test/java/com/quant/trade/marketdata/provider/ReflectiveLongPortQuoteClientTest.java`：新增 `reflectiveSdkPathHonoursDomainOverrides` 测试 —— 配置 httpUrl + quoteWebsocketUrl 后验证 healthCheck configured/reachable + quote + daily bar 全成功。
  - `scripts/check-longport-official-java-contract.sh`：新增 `Config.httpUrl(String)` / `Config.quoteWebsocketUrl(String)` 合约断言，防止 SDK 升级破坏域名覆盖。
- **质量门禁结果（全绿）**：
  - 后端：`bash -n scripts/*.sh`（6 脚本）通过；`git diff --check` 通过；`./mvnw test` **188 tests / 0 failures**（较上轮 187 +1 新测试）；`./mvnw -DskipTests package` BUILD SUCCESS。
  - 前端：`git diff --check` 通过；`npm run typecheck` 通过；`npm run lint` 通过；`npm run test` **214 tests passed**；`npm run build` 通过。
- **真实外联只读复核（HTTP 200，容器未重建）**：
  - provider `configured=true / reachable=true / lastError=null`。
  - quote-snapshots：SH.600519 贵州茅台 1 条 LONGPORT 数据（price=1204.98 / vol=52212）。
  - daily-bars：8 条 LONGPORT 日 K（7/1-7/10 跳过周末，OHLC 合理，7/10 收盘 1204.98 与快照一致）。
- **安全复核**：`.env` / `.env.longport` / runtime-libs jar 均不在 git status（gitignored）；所有 tracked 改动无 LongPort 凭据明文；无交易/订单/账户/持仓能力接入；未 commit/push。
- **遗留风险**：(1) 域名漂移 —— 已靠合约检查脚本兜底；(2) 仅单 symbol 验收，多 symbol 并发/边界日期/QF 复权未压测（BUILD_CHECKLIST 已记）；(3) volume 同日差 1 是实时快照 vs 历史 K 线两个 API 的正常口径差异，非 bug。
- **关联**：`acceptance/ACCEPTANCE_LOG.md`（2026-07-12 收口追加）、`AI_HANDOFF.md`、`LONGPORT_SDK_RUNTIME_INSTALLATION.md`。

---

## 2026-07-11 — LongPort 单股票同步后端 adapter

- **目标**：把 LongPort 单股票手动同步从“接口壳 + DB 留痕”推进到后端 provider adapter 可运行状态，同时避免不可用 SDK 坐标拖垮构建。
- **范围**：后端代码 + 配置 + 测试 + 文档；不新增 DB migration；不接交易、账户、订单、真实持仓能力；不保存密钥。
- **后端改动**：
  - 新增 `LongPortProperties`：统一绑定 LongPort enabled、legacy API key、timeout、quote time zone。
  - 新增 `LongPortQuoteClient` 与 `ReflectiveLongPortQuoteClient`：运行时反射调用官方 Java SDK，只读调用 `getQuote` 与 `getHistoryCandlesticksByDate`。
  - 新增 `LongPortMarketDataProvider`：负责 canonical symbol 转换、`NONE/QF` 复权映射、`HF` 明确拒绝、provider 状态。
  - 调整 `MarketDataConfig` / `DisabledMarketDataProvider`：默认 disabled，`qta.market-data.longport.enabled=true` 时切换 LongPort provider。
  - 调整 `MarketQuoteService`：provider 不可用时返回具体原因（未启用 / SDK 缺失 / 凭据缺失），并写入 alert/task。
  - `.env.example`、`docker-compose.yml` 增加 LongPort 环境变量透传。
- **外部调研结论**：
  - 官方 Java README/Javadoc 存在，API 方法形状已确认。
  - Maven Central 当前查询不到 `io.github.longport:openapi-sdk` artifact；GitHub `v4.3.3` release 当前未提供 Java jar。
  - 因此本轮采用反射式 adapter，等待 SDK jar/native libs 可安装后做真实小调用验收。
- **测试结果**：
  - `./mvnw -q -Dtest=LongPortMarketDataProviderTest,ReflectiveLongPortQuoteClientTest,LongPortSymbolMapperTest,MarketQuoteServiceTest test` 通过。
  - `./mvnw -q -Dtest=LongPortEnabledWithoutSdkContextTest,LongPortMarketDataProviderTest,ReflectiveLongPortQuoteClientTest,MarketQuoteServiceTest test` 通过。
- **遗留问题**：
  - 前端 `/market-data` 小改已补：状态页展示 SDK/凭据未就绪，历史同步禁用 `HF`。
  - 未执行 Docker 重构建与真实 LongPort 外联；真实外联取决于 SDK jar/native libs 安装。
- **关联文档**：`features/LONGPORT_SINGLE_SYMBOL_SYNC_ENGINE_DESIGN.md`、`api/MARKET_DATA_API.md`、`BUILD_CHECKLIST.md`、`acceptance/ACCEPTANCE_LOG.md`、`ai/HANDOFF_2026-07-11_longport_single_symbol_engine.md`。

### 2026-07-11 追加：运行时 classpath 与 SDK 分发复核

- **目标**：避免后续拿到 LongPort SDK jar 后仍因 Docker `java -jar` 启动方式无法加载外部 jar。
- **改动**：
  - `Dockerfile` 改为通过 Spring Boot `PropertiesLauncher` 启动，`loader.path` 指向 `/app/libs`。
  - `docker-compose.yml` 将项目 `runtime-libs/` 只读挂载到容器 `/app/libs`。
  - `.gitignore` 忽略 `runtime-libs/*`，仅保留 `.gitkeep`，防止 vendor jar/native 包误提交。
  - 新增 `development/LONGPORT_SDK_RUNTIME_INSTALLATION.md`，沉淀官方 artifact 查询结论、推荐安装路径和最小真实外联验收命令。
  - `ReflectiveLongPortQuoteClient` 支持注入 ClassLoader 便于测试；等待 SDK Future 时补中断处理；错误信息会脱敏显式 LongPort 凭据。
  - 增加 test-only fake LongPort SDK 类，覆盖 `QuoteContext#create`、`getQuote`、`getHistoryCandlesticksByDate` 的反射调用路径。
- **外部复核结论**：
  - LongPort `v4.3.3` release workflow 存在 `build-java-jni` 和 `publish-java-sdk`，理论上会把 JNI 打入 Java jar 并 deploy 到 Maven Central。
  - 但 `repo.maven.apache.org` metadata 当前仍 404，`search.maven.org` 精确查询仍为 0，GitHub release 仍未挂 Java jar。
- **遗留问题**：SDK 获取本身仍未解决；真实 quote/candlestick 小调用等待 SDK artifact 或源码构建产物 + 用户只读凭据。
- **验证**：`./mvnw -q clean test` 通过（183 tests，0 failures/errors）；`./mvnw -q -DskipTests package` 通过；`docker compose up -d --build app` 成功；`curl /actuator/health` 200 UP；`curl /api/v1/market-data/providers/LONGPORT/status` 200 + provider 未启用。
- **runtime-libs 外部 jar 验证**：2026-07-12 临时将 test-only fake LongPort SDK 打成 jar 放入 `runtime-libs/`，使用 fake 凭据重建容器；status 返回 `configured=true/reachable=true`，`POST /quotes/latest` + `persist=false` 返回 `SH.600519` fake quote。验证后已删除 fake jar 并恢复默认 disabled 容器。
- **可重复脚本**：新增并执行 `scripts/verify-longport-runtime-libs.sh`，后续可一键复验 runtime-libs 外部 jar 加载链路；脚本会自动清理 fake jar 并恢复默认 disabled 容器。
- **真实外联验收脚本**：新增 `scripts/verify-longport-real-sync.sh`，用于官方 SDK 和真实只读凭据到位后，一键验证 provider status、最新价落库和日 K 同步任务；默认 symbol/date 为 `SH.600519` / `2026-07-10`，可用环境变量覆盖。脚本启动前会预检 LongPort 三项凭据，默认保留 enabled 容器用于继续联调，可用 `QTA_VERIFY_RESTORE_APP_AFTER_RUN=true` 退出时恢复默认 disabled 容器。
- **收尾复验**：2026-07-12 10:36 重新执行 `./mvnw -q test`，Surefire 汇总 183 tests / 0 failures / 0 errors；`bash -n` 两个 LongPort 验收脚本通过；`git diff --check` 通过；默认容器 status 确认为 HTTP 200 + provider 未启用。
- **latest quote 请求校验补强**：2026-07-12 继续补齐 `FetchQuotesRequestDTO` Bean Validation、controller `@Valid`、service 直接调用校验；空 `canonicalSymbols`、超过 500 个标的、空代码、非法 canonical symbol 会在 provider 调用前返回参数/代码格式错误。同步修正 `MARKET_DATA_API.md` 中 `quote-snapshots` 与 `sync-tasks` 查询参数说明。
- **latest quote 补强后复验**：`./mvnw -q -Dtest=MarketQuoteControllerValidationTest,MarketQuoteServiceTest,LongPortMarketDataProviderTest,ReflectiveLongPortQuoteClientTest test` 通过；`./mvnw -q test` 通过，Surefire 汇总 187 tests / 0 failures / 0 errors；`./mvnw -q -DskipTests package` 通过；两个 LongPort 验收脚本 `bash -n` 通过；`git diff --check` 通过。
- **前端联调防呆补强**：行情页 latest quote 支持 canonical symbol 格式校验、去重、单次 500 个上限；历史日 K 同步支持 canonical symbol、日期范围、HF 禁用校验；两个写操作在请求前先检查 LongPort provider status，未配置/不可达时只提示用户，不制造失败同步任务。Provider 状态页补充面向 SDK 缺失/凭据缺失/不可达的 Alert。前端 `npm run typecheck` / `lint` / `test`（214 tests）/ `build` 通过。
- **SDK 源码构建脚本**：新增 `scripts/build-longport-java-sdk-from-source.sh`，根据官方 release workflow 的 `build-java-jni` / `publish-java-sdk` 步骤，支持从官方 `longportapp/openapi` tag 构建当前平台或 `QTA_LONGPORT_RUST_TARGET` 指定平台的 JNI，执行 Java Maven package，并把 SDK jar 与 runtime 依赖复制到 `runtime-libs/`。脚本默认不覆盖已有 jar，不删除已有 build 目录，不读取任何 LongPort 凭据；本轮仅执行 `bash -n`，未做真实源码构建。
- **SDK 离线检查脚本**：新增 `scripts/inspect-longport-runtime-libs.sh`，用于真实外联前离线检查 `runtime-libs/` 中 SDK jar、目标平台 native、`gson`、`native-lib-loader` 是否齐全，并拒绝 fake SDK jar。当前空 `runtime-libs/` 下脚本会明确提示需要先 build/download SDK。已用临时 fake SDK/dependency jars 验证正向路径、缺 native 失败路径、fake SDK jar 残留失败路径。
- **官方 SDK 合约检查脚本**：新增 `scripts/check-longport-official-java-contract.sh` 和 `docs/development/LONGPORT_OFFICIAL_JAVA_CONTRACT.md`，用于升级 SDK tag 前检查官方 Java 源码中的类、方法、getter、枚举常量是否仍匹配 `ReflectiveLongPortQuoteClient`。本轮对 `v4.3.3` 本地官方源码缓存检查通过；在线 GitHub raw 检查受当前代理/DNS 影响失败。
- **真实外联预检脚本**：新增 `scripts/check-longport-readiness.sh`，用于在真实外联前集中检查 LongPort 三项只读凭据、`QTA_LONGPORT_ENABLED`、`runtime-libs` SDK/native/dependency 结构、可选官方源码合约和可选 provider status；脚本不会打印密钥。`scripts/verify-longport-real-sync.sh` 增加 `QTA_VERIFY_RUNTIME_LIB_INSPECTION=auto|true|false`，默认在 `runtime-libs` 有 jar 时先做离线结构检查。
- **遗留真实外联**：未执行真实 LongPort 外联；2026-07-12 复查 Maven Central metadata 仍 404，`search.maven.org` 仍 `numFound=0`，仍缺官方 SDK jar/native libs 与用户只读凭据。

---

## 2026-07-10 — LongPort 只读行情源产品与架构设计

- **目标**：研究 LongPort/长桥 OpenAPI 是否适合接入 A 股行情，并沉淀下一轮前后端开发设计。
- **范围**：只做产品/架构/文档设计，不改业务实现代码，不接真实交易能力。
- **发现**：
  - 代码事实已包含 `marketdata` 模块、V5/V6、`stock_basic`、`stock_daily_bar`、CSV 日 K 导入和 `/api/v1/market-data/*` 基础接口。
  - 部分文档仍把行情基础标为规划，已在本轮同步。
  - LongPort 能力覆盖实时行情、历史 K 线、MCP/SDK，但 MCP 也暴露交易/账户能力，必须通过 ADR 限定 quote-only。
- **产品决策**：
  - LongPort 只作为只读行情 provider。
  - 最新价进入 `stock_quote_snapshot`，历史日 K 进入 `stock_daily_bar(data_source=LONGPORT)`。
  - 外部行情不得覆盖 `portfolio_price_snapshot` 手工当前价。
  - 异常提醒先做数据质量，再做量价观察，不输出买卖建议。
- **新增文档**：
  - `features/LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md`
  - `features/MARKET_ALERT_RULES_DESIGN.md`
  - `development/2026-07-10-longport-market-data-research.md`
  - `decisions/ADR-0008-longport-quote-only-provider.md`
  - `api/MARKET_DATA_API.md`
  - `prompts/LONGPORT_MARKET_DATA_CLAUDE_PROMPT.md`
- **同步文档**：`PRODUCT_BLUEPRINT.md`、`BUILD_CHECKLIST.md`、`CURRENT_ARCHITECTURE_AND_MODULES.md`、`DATABASE_DESIGN.md`、`api/API_INDEX.md`、`AI_HANDOFF.md`、`decisions/ADR_INDEX.md`。

---

## 2026-07-06 — 生产环境实测验证

- **目标**：验证生产 Nginx → 后端 → MySQL 链路，确认 production-data-mode 真实状态。
- **验证（只读 GET）**：
  - `http://129.204.169.155:18080/` 首页 → HTTP 200。
  - `/api/v1/watchlist` → `success=true, data=[]`。
  - `/api/v1/trade-plans` → `success=true, data=[]`。
  - `/api/v1/dashboard/today` → `success=true`，含完整 date/todos/pendingReviewJournals 数据（1 条 AAPL PENDING 交易）。
- **结论**：生产同源 /api/v1 + Nginx 反代 + Docker qta-server + MySQL 链路**实测通过**。production-data-mode 升级为 DONE/M4。
- **关联**：`acceptance/ACCEPTANCE_LOG.md`、前端 `buildStatusData.ts`。

---

## 2026-07 — 建设看板状态同步与发布收口

- **目标**：让建设看板与 v0.1.1 已验收事实、BUILD_CHECKLIST、PRODUCT_BLUEPRINT 完全一致。
- **范围**：前端看板数据 + 同步机制，不改业务代码/DB。
- **改动**：
  - `buildStatusData.ts` 重写：修正 6 类过期节点（pnl-explainability target、portfolio-pnl IN_PROGRESS→DONE、production-data-mode RISK→DONE、ai-collaboration "已推送"→"已沉淀"、trade-loop/position-snapshot nextActions）；新增 `market-data-foundation` P1 一级节点（stock-basic/daily-bar-import/market-data-provider）；`ai-input` P1→P2；`daily-bar-import` 从 quant-analysis 移入行情基础。
  - `pages/build-status.tsx` 加看板基线提示（v0.1.1 / 2026-07-06 / 与 BUILD_CHECKLIST 同步）。
  - `useBuildStatus` selectedId 初始 `null`（进入/刷新不默认打开抽屉）。
  - `production-data-mode` currentEvidence 分两条（同源 /api/v1 + curl 链路 / mock 4 页面 Playwright）。
  - 同步机制：`DEVELOPMENT_WORKFLOW` + `qta-context-bootstrap` 加 buildStatusData 同步规则；`BUILD_STATUS_BOARD_DESIGN` 标初始基线。
  - 口径统一：BUILD_CHECKLIST/PRODUCT_BLUEPRINT/buildStatusData "证券主数据**与**行情基础"。
- **测试**：`buildStatusData.test.ts` 重写（v0.1.1 DONE/M4、snapshot-comparison 100%、market-data-foundation P1、ai-input 非 P1、无过期下一步、一级分类含行情基础）；新增 `useBuildStatus.test.ts`（初始未选中/选择/关闭）。
- **验收**：后端 121、前端 191 测试通过；浏览器 /build-status 控制台 0 deprecated/error；基线 + P1 行情基础 + 节点显示；production-data-mode 降级 RISK/M3（生产 Nginx 反代未实测，不与"已验证"矛盾）。
- **关联文档**：`BUILD_CHECKLIST.md`、`PRODUCT_BLUEPRINT.md`、`acceptance/ACCEPTANCE_LOG.md`。

---

## 2026-07 — 文档体系治理与上下文加载 Skill

- **目标**：建立可自洽的文档体系，让任意 AI 新会话不依赖历史聊天即可继续开发。
- **范围**：纯文档 + 项目级 skill，**不改业务代码、不改 DB migration**。
- **改动**：
  - 新建：`AI_DEVELOPMENT_INDEX`（路由型）、`DEVELOPMENT_WORKFLOW`、`api/API_INDEX`、`mock/MOCK_REMOTE_CONTRACT`、`development/DEVELOPMENT_LOG`、`decisions/ADR_INDEX`+7 ADR、`acceptance/ACCEPTANCE_LOG`、`templates/` 5 模板、`.claude/skills/qta-context-bootstrap`。
  - 治理：`CLAUDE.md` 删旧必读清单 + Today MVP 指令；`AGENTS.md` 下一阶段优先级重写（删早期建表/指标/策略计划）；`DEVELOPMENT_ROADMAP` 重写（v0.1.1 已完成 + 下一阶段证券主数据，删 Entity/Repository/创建前端）；`FRONTEND_ARCHITECTURE` 按实际 React 项目重写（删 `/api/risk-alerts` 等不存在接口）；`CONVERSATION_HANDOFF` 精简为 Historical；10 个早期文档加 Historical 标记。
  - 契约修正：`MOCK_REMOTE_CONTRACT` 物理 key 带 `qta:` 前缀 + Risk Calculator 前端纯函数（未接 remote adapter）；`API_INDEX` Portfolio 完整路径 `/api/v1/portfolio/positions` 等；`PRODUCT_BLUEPRINT` v0.1.1 "待开发"→"已完成"。
  - 信息真实性优先级写入 `AI_DEVELOPMENT_INDEX §2` + `DEVELOPMENT_WORKFLOW`。
- **测试结果**：后端 `./mvnw test` 121 通过；前端 typecheck/lint/test/build 全绿；`git diff --check` 两仓库干净；grep 主流程无 JPA/Repository 冲突、无旧测试数残留、Controller 路径与 API_INDEX 一致、localStorageClient `qta:` 前缀与 MOCK_REMOTE_CONTRACT 一致。
- **产品决策**：Historical 文档原文保留（不删），仅顶部标记 + 主索引降级；单一事实来源（API_INDEX/MOCK_REMOTE_CONTRACT/ADR/DEVELOPMENT_LOG/ACCEPTANCE_LOG）。
- **关联文档**：`AI_DEVELOPMENT_INDEX.md`、`DEVELOPMENT_WORKFLOW.md`、`acceptance/ACCEPTANCE_LOG.md`。

---

## v0.1.1 — 基础交易闭环优化（含两轮质量收尾 + 最终交付）

**目标**：把计划 / 交易 / 账本 / 快照 / 复盘 / 工作台串成可信、可追溯闭环。

**范围**：6 大功能 + 两轮收尾 + 最终交付（路由 / 解绑 / 历史日期 / FIFO 对齐 / Antd deprecated / 文案 / 文档治理）。

**后端改动**：
- 新增 `PositionSnapshotComparisonManager`（纯计算）、`PositionSnapshotReconciliationManager`（FIFO 对账，复用 `FifoCalculatorManager`）、`DashboardTodoVO` + `DashboardTodoCodeEnum` / `DashboardTodoLevelEnum`、`SnapshotChangeTypeEnum` / `ReconciliationStatusEnum`、6 个对比/对账 VO。
- `TradeJournalManager`：`planId` 关联校验 + `unlinkPlan` 三态；`recalculateReviewStatus`。
- `ReviewManager`：扫全表解析 `linked_journal_ids`（CSV，容忍脏数据 + 去重）；删除保护。
- `DashboardManager`：`buildTodos(date)`（6 类待办，历史日期口径 `trade_date<=date`，STALE 用 `getLatestConfirmedUpTo`）。
- `TradeJournalMapper`：`selectAllOrderedUpTo`（截止时点 FIFO）、`selectByReviewStatusUpTo` / `countByReviewStatusUpTo`（历史日期）。
- `PositionSnapshotMapper`：`selectLatestConfirmedUpTo`。
- 5 个新错误码 + `MessageConstants` 文案。
- **未新增表，未修改 V1-V4 migration。**

**前端改动**：
- `TradeJournalForm` 计划选择器 + 自动带入；`PositionSnapshotInspectionDrawer`（对比 + 对账 + 成本列 + 横向滚动）；`DashboardTodos`（ul/li，无 List）；`dashboardApi`（remote 用后端聚合，mock 同口径）；`settingsApi`（localhost 防误配 + 测试连接）；`positionSnapshotReconciliation`（FIFO 含 totalFee + 稳定排序 + 超卖停止）；`DataManagement`（动态文案 + 导出范围说明）。
- Antd 6.4 deprecated 全清理：`Alert message→title`、`Spin tip→description`、`Space direction→orientation`、`Drawer width→size`。

**接口变化**：
- 新增 `GET /position-snapshots/comparison`、`GET /position-snapshots/{id}/reconciliation`。
- `GET /dashboard/today` 响应增 `todos`（旧字段保留）；待办 `targetPath` 全 `/journal*` 或 `/position-snapshots`（复数）。
- `PUT /trade-journals/{id}` 增 `unlinkPlan`（三态）；响应增 `planDate/planStatus`。
- `reviews` 新增/编辑/删除后回算 reviewStatus；`trade-journals/{id}` 删除前引用保护。

**测试结果**：后端 `./mvnw test` = 121 通过；前端 `typecheck/lint/test/build` = 179 测试通过；Docker 冷构建 + curl 端到端 + Playwright 4 页面控制台 `DEPRECATED_WARNINGS=0, CONSOLE_ERRORS=0`。详见 `../acceptance/ACCEPTANCE_LOG.md`。

**产品决策**：对账只读不改流水；TRADE_AGAINST_PLAN 含 `followedPlan=false`；历史日期统一 `trade_date<=date`；超卖视为 QUANTITY_MISMATCH；mock FIFO 必须复刻后端（含 totalFee）；JSON 导出仅 localStorage 不含 MySQL。

**遗留问题**：浏览器自动化目视仍建议手动复核（Playwright 已验证控制台）；联调测试数据未清理（`TEST01/TEST01C/TEST02/UNLINK1/CMP1/HISTFX1/FUTFX1/OVERFX1` 等）。

**关联文档**：`../features/TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md`、`../api/POSITION_SNAPSHOT_API.md`、`../api/API_INDEX.md`、`../mock/MOCK_REMOTE_CONTRACT.md`、`../BUILD_CHECKLIST.md`、`../acceptance/ACCEPTANCE_LOG.md`。

---

## v0.1.0 — Today MVP + 交易账本 + 持仓快照

**目标**：本地运行的基础交易记录工具。

**范围**：Dashboard / Watchlist / Trade Plan / Risk / Trade Journal / Review + Portfolio FIFO 账本 + Position Snapshot。

**后端**：Spring Boot 3.5 + MyBatis XML + MapStruct + Flyway V1-V4 + MySQL 8.4 + H2 test；分层 controller/service/manager/dao/model/dto/vo/convert；`ApiResponse` + `ErrorCodeEnum` + `BusinessException`。

**前端**：React 19 + Vite + TypeScript + Ant Design 6 + feature-based + mock/remote 双模式 + `shared/api/client` 动态 baseURL。

**接口**：见 `../api/API_INDEX.md`。

**测试**：后端基础测试 + 前端基础测试（数量低于 v0.1.1，已被覆盖）。

**关联文档**：`../API_TODAY_MVP.md`、`../api/PORTFOLIO_API.md`、`../api/POSITION_SNAPSHOT_API.md`、`../DATABASE_DESIGN.md`、`../CURRENT_ARCHITECTURE_AND_MODULES.md`。
