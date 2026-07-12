# LongPort Java SDK Runtime Installation

> 状态：官方 Java SDK artifact 已在 Maven Central 确认可用（正确 groupId 为 `io.github.longportapp`），SDK jar/native libs 已安装到 `runtime-libs/`，离线检查通过。真实外联仅待只读凭据配置。本文只记录可执行事实，不保存任何 LongPort 密钥。

## 1. 当前结论

- 官方 Java README/Javadoc 给出 SDK 类和方法形状，当前后端 `ReflectiveLongPortQuoteClient` 已按这些类名做运行时反射。
- LongPort `v4.3.3` release workflow 存在 `publish-java-sdk` 任务，会构建 JNI 并发布到 Maven Central。
- 2026-07-12 复查 Maven Central 确认 artifact 可用，**但官方 `java/javasrc/pom.xml` 写的坐标 `io.github.longport:openapi-sdk` 是错的**（源码 pom 里 groupId 缺少 `app` 后缀），之前按源码 pom 推导的查询因此全部落空。正确坐标如下：
  - `groupId`：`io.github.longportapp`（不是 `io.github.longport`）。
  - `artifactId`：`openapi-sdk`。
  - `<latest>` / `<release>`：`4.3.3`（`maven-metadata.xml` 的 `lastUpdated=20260701095601`）。
  - metadata：`https://repo.maven.apache.org/maven2/io/github/longportapp/openapi-sdk/maven-metadata.xml` 返回 200。
  - `search.maven.org` 查询 `g:"io.github.longportapp"` 返回 `numFound=1`，`latestVersion=4.3.3`，`versionCount=68`，`p=jar`。
- 实测 `openapi-sdk-4.3.3.jar`（约 35MB）：
  - 包含本项目反射 adapter 需要的全部类：`com/longport/Config.class`、`com/longport/quote/QuoteContext.class`、`Period`、`AdjustType`、`TradeSessions`、`SecurityQuote`、`Candlestick`。
  - jar 内置全平台 native：`natives/{linux_64,linux_arm64,osx_64,osx_arm64,windows_64}/` 各含对应 `liblongport_java.{so,dylib,dll}`。即同一个 jar 同时覆盖本机 osx_arm64 与服务器 linux_64，无需按平台分别构建。
  - POM 声明 runtime 依赖 `org.scijava:native-lib-loader:2.4.0` 和 `com.google.code.gson:gson:2.10.1`，与本项目 `runtime-libs/` 离线检查脚本要求一致。
- 因此本项目目前走 runtime-libs 外部 jar 通道（路径 A 的“jar 放入 runtime-libs”分支），不需要源码构建。`io.github.longport:openapi-sdk`（无 `app`）这个错误坐标绝不能写入 `pom.xml`，正确坐标 `io.github.longportapp:openapi-sdk` 若未来改为编译期依赖也只应使用正确坐标。

## 2. 本项目的运行时方案

后端采用“编译期不依赖、运行时检测”的方式：

- 未启用 LongPort：继续使用 `DisabledMarketDataProvider`。
- 启用 LongPort 但 SDK 不在 classpath：应用仍启动，`/providers/LONGPORT/status` 返回 `configured=false` 和可读错误。
- SDK + 凭据都可用：`LongPortMarketDataProvider` 通过反射调用官方 Java SDK。

Docker 已支持外部运行时 jar：

```text
runtime-libs/  ->  /app/libs:ro
java -Dloader.path=/app/libs -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

`runtime-libs/` 只提交 `.gitkeep`，真实 jar/native 包不得提交到 Git。

2026-07-12 已用 test-only fake SDK jar 验证该通道：

- 将 fake `com.longport.*` jar 临时放入 `runtime-libs/`。
- 使用 `QTA_LONGPORT_ENABLED=true` 和 fake 凭据重建 app 容器。
- `/providers/LONGPORT/status` 返回 `configured=true`、`reachable=true`。
- `POST /quotes/latest` + `persist=false` 返回 fake quote，证明 REST → provider → 反射 SDK → response 链路打通。
- 验证后已删除 fake jar，并恢复默认 `QTA_LONGPORT_ENABLED=false` 容器。

注意：fake SDK 仅用于验证加载通道，不代表真实 LongPort 外联通过。生产必须替换为官方 Java SDK jar/native libs。

可重复执行的验证脚本：

```bash
scripts/verify-longport-runtime-libs.sh
```

脚本行为：

- 编译 `src/test/java/com/longport/**` test-only fake SDK。
- 临时生成 `runtime-libs/qta-fake-longport-sdk.jar`。
- 用 fake 凭据重建 app 容器。
- 校验 provider status 为 `configured=true` / `reachable=true`。
- 校验 `POST /quotes/latest` + `persist=false` 能返回 `SH.600519` fake quote。
- 删除 fake jar，并恢复默认 `QTA_LONGPORT_ENABLED=false` app 容器。

脚本会重启本地 `qta-server` 容器；不要在生产环境高峰期执行。

## 3. 推荐安装路径

### 路径 A：Maven Central 直接下载官方 jar（当前已采用，2026-07-12）

官方 artifact 已在 Maven Central 可用，**正确 groupId 是 `io.github.longportapp`**（官方源码 `java/javasrc/pom.xml` 里写的 `io.github.longport` 是错的，缺 `app` 后缀，曾导致查询全部落空）。

先在服务器或本机确认 artifact 已可解析（注意是 `longportapp`）：

```bash
curl -fsSL https://repo.maven.apache.org/maven2/io/github/longportapp/openapi-sdk/maven-metadata.xml
curl -fsSL 'https://search.maven.org/solrsearch/select?q=g:%22io.github.longportapp%22%20AND%20a:%22openapi-sdk%22&rows=20&wt=json'
```

本项目当前采用 runtime-libs 外部 jar 分支（不修改 `pom.xml`，保持编译期零依赖、默认 disabled 安全兜底）。把以下三个 jar 放入 `runtime-libs/`（同一 `openapi-sdk-4.3.3.jar` 已内置全平台 native，本机与服务器各放一份即可）：

```bash
# 版本以 maven-metadata.xml 的 <release> 为准；当前为 4.3.3
BASE=https://repo.maven.apache.org/maven2
curl -fsSL -o runtime-libs/openapi-sdk-4.3.3.jar       $BASE/io/github/longportapp/openapi-sdk/4.3.3/openapi-sdk-4.3.3.jar
curl -fsSL -o runtime-libs/gson-2.10.1.jar             $BASE/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
curl -fsSL -o runtime-libs/native-lib-loader-2.4.0.jar $BASE/org/scijava/native-lib-loader/2.4.0/native-lib-loader-2.4.0.jar
```

`runtime-libs/*` 已被 `.gitignore`（仅保留 `.gitkeep`），真实 jar 不会进 Git。

放好后做离线结构检查（不需要凭据，不会请求外部行情）：

```bash
scripts/inspect-longport-runtime-libs.sh
# 服务器目标是 Linux x86_64 时：
QTA_LONGPORT_EXPECTED_RUST_TARGET=x86_64-unknown-linux-gnu scripts/inspect-longport-runtime-libs.sh
```

预期：`LongPort runtime libs inspection passed`。2026-07-12 本机与 linux_64 目标均通过。

也可改为把正确坐标 `io.github.longportapp:openapi-sdk` 加入 `pom.xml` 让 fat jar 直接打包，但这会让编译期依赖 SDK、打破当前“默认 disabled、SDK 可选”的安全兜底，暂不采用。

### 路径 B：从官方源码自行构建 SDK（备选，当前不需要）

路径 A 已经可用并验收通过，路径 B 仅在路径 A 再次失效（例如未来某版本 Maven Central 撤包）时才尝试。这一路径只根据官方 workflow 推导，尚未在本项目内完成真实验收。

官方 workflow 的关键事实：

- `build-java-jni` 会针对 Linux/macOS/Windows 构建 `longport_java` JNI native 库。
- `publish-java-sdk` 会把 JNI 文件复制到 `java/javasrc/target/natives/*`。
- `java/javasrc/pom.xml` 会把 `target/natives` 打进 jar 的 `natives` 目录。

自行构建时需要 Git、JDK、Maven、Rust/Cargo；Linux 跨平台构建可能还需要 `cross`。本项目已提供辅助脚本：

```bash
scripts/build-longport-java-sdk-from-source.sh
```

脚本默认行为：

- 从 `https://github.com/longportapp/openapi.git` 克隆/更新 `v4.3.3`。
- 自动识别当前平台并构建当前平台所需的 `longport-java` JNI。
- 如需为服务器交叉构建，可用 `QTA_LONGPORT_RUST_TARGET` 显式指定目标平台。
- 将 JNI 文件放入官方 `java/javasrc/target/natives/<platform>/` 目录。
- 根据官方根 `Cargo.toml` 推导版本，执行 Maven package。
- 将 `openapi-sdk-*.jar` 以及 runtime 依赖复制到本项目 `runtime-libs/`。
- 不读取、不打印、不保存任何 LongPort 凭据。

可覆盖的环境变量：

```bash
QTA_LONGPORT_OPENAPI_TAG=v4.3.3
QTA_LONGPORT_OPENAPI_BUILD_DIR=/tmp/qta-longport-openapi
QTA_LONGPORT_SDK_INSTALL_DIR=/Users/joker/code/quant-trading-assistant/runtime-libs
QTA_LONGPORT_JNI_BUILD_TOOL=cargo    # Linux 需要 cross 时改为 cross
QTA_LONGPORT_RUST_TARGET=            # 为空时自动识别当前平台；如 x86_64-unknown-linux-gnu
QTA_LONGPORT_PACKAGE_VERSION=        # 为空时从 Cargo.toml 推导
QTA_LONGPORT_SDK_OVERWRITE=false     # 已存在 SDK/dependency jar 时默认拒绝覆盖；不会删除 build 目录
```

常见目标：

```text
macOS Apple Silicon: aarch64-apple-darwin
macOS Intel:         x86_64-apple-darwin
Linux x86_64:        x86_64-unknown-linux-gnu
Linux arm64:         aarch64-unknown-linux-gnu
Windows x86_64:      x86_64-pc-windows-msvc
```

脚本完成后，确认 jar 已进入：

```text
/Users/joker/code/quant-trading-assistant/runtime-libs/
```

先做离线结构检查（不需要 LongPort 凭据，不会请求外部行情）：

```bash
scripts/inspect-longport-runtime-libs.sh
```

如果要检查服务器目标平台的 native 是否存在，可显式指定：

```bash
QTA_LONGPORT_EXPECTED_RUST_TARGET=x86_64-unknown-linux-gnu \
scripts/inspect-longport-runtime-libs.sh
```

检查内容：

- `runtime-libs/` 中存在且只存在一个 LongPort SDK jar。
- SDK jar 中存在 `Config`、`QuoteContext`、`Period`、`AdjustType`、`TradeSessions` 等本项目反射 adapter 需要的类。
- SDK jar 中存在目标平台的 `longport_java` native 文件。
- `runtime-libs/` 中能找到 `gson` 和 `native-lib-loader` runtime dependency。
- 不允许残留 test-only `qta-fake-longport-sdk.jar`。

2026-07-12 已使用临时目录构造最小 fake SDK/dependency jars 验证该检查脚本：

- 结构完整时，检查通过。
- 缺少目标平台 native 时，检查失败并指出缺少的 native 路径。
- 存在 `qta-fake-longport-sdk.jar` 时，检查失败并阻止进入真实验收。

服务器部署时放入项目部署目录下的：

```text
runtime-libs/
```

然后重建并启动：

```bash
docker compose up -d --build
```

然后执行真实外联最小验收：

```bash
scripts/inspect-longport-runtime-libs.sh
scripts/verify-longport-real-sync.sh
```

注意：本脚本只是把官方源码构建路径固化成可执行流程；是否能在本机一次构建成功仍取决于本机 Rust target、Maven 网络依赖、native toolchain 和官方仓库状态。

## 4. 环境变量

`.env` 或服务器环境中配置：

```bash
QTA_LONGPORT_ENABLED=true
LONGPORT_APP_KEY=***
LONGPORT_APP_SECRET=***
LONGPORT_ACCESS_TOKEN=***
QTA_LONGPORT_TIMEOUT_SECONDS=10
QTA_LONGPORT_QUOTE_TIME_ZONE=Asia/Shanghai
```

禁止把真实值写入 `.env.example`、文档、日志、Git、前端代码。

### 4.1 凭据分层（推荐）

真实只读凭据与本地基础配置建议分文件存放，敏感度分层：

- `.env`（被 `.gitignore` 忽略）：本地 MySQL + LongPort 非密开关（`QTA_LONGPORT_ENABLED` 等）。Docker Compose 自动加载。
- `.env.longport`（被 `.gitignore` 的 `.env.*` 忽略）：只放 `LONGPORT_APP_KEY` / `LONGPORT_APP_SECRET` / `LONGPORT_ACCESS_TOKEN`。本地手动同步前 `set -a; source .env.longport; set +a` 注入。

### 4.2 SDK 默认域名废弃 —— 必须配置域名覆盖（2026-07-12 实测）

SDK 4.3.3 的 native lib 默认域名是长桥旧品牌域名，**已废弃、DNS 解析失败**：

- HTTP 默认：`https://openapi.longport.cn`（废弃）
- Quote WebSocket 默认：`wss://openapi-quote.longport.cn/v2`（废弃）

长桥已更名 Longbridge，可用同源域名（解析到阿里云国内节点）：

```bash
LONGPORT_HTTP_URL=https://openapi.longbridge.cn
LONGPORT_QUOTE_WEBSOCKET_URL=wss://openapi-quote.longbridge.cn/v2
```

这两个变量通过 `Config.httpUrl(...)` / `Config.quoteWebsocketUrl(...)` 反射覆盖默认域名（`ReflectiveLongPortQuoteClient.createConfig`）。默认空时仍用 SDK 自带域名，不影响默认行为。

**部署必须同时配这两个变量**，否则 provider 会报 `failed to lookup address information: Name or service not known`（HTTP 域名）或 quote 连接失败（ws 域名）。

### 4.3 Docker 容器 DNS

`docker-compose.yml` 的 app 服务已加 `dns`（默认 `223.5.5.5` / `119.29.29.29`，由 `QTA_DNS_SERVER_1` / `QTA_DNS_SERVER_2` 覆盖），保证容器内 native resolver 能解析外部域名。海外部署如需可覆盖为当地公共 DNS。

## 5. 最小真实外联验收

### 5.0 真实外联前预检

在安装官方 SDK jar/native libs、配置只读凭据之后，先执行一键预检：

```bash
scripts/check-longport-readiness.sh
```

默认检查内容：

- `LONGPORT_APP_KEY`、`LONGPORT_APP_SECRET`、`LONGPORT_ACCESS_TOKEN` 是否存在且不是占位符；脚本不会打印真实值。
- `QTA_LONGPORT_ENABLED` 是否为 `true`。
- `runtime-libs/` 中是否存在 LongPort SDK jar、目标平台 native、`gson`、`native-lib-loader`。
- 如果本地后端正在运行，检查 `/api/v1/market-data/providers/LONGPORT/status`。

常用参数：

```bash
# 服务器目标是 Linux x86_64，但当前在 Mac 上检查 runtime-libs 结构时使用
QTA_LONGPORT_EXPECTED_RUST_TARGET=x86_64-unknown-linux-gnu \
scripts/check-longport-readiness.sh

# 要求后端已启动，且 provider 必须 configured=true / reachable=true
QTA_LONGPORT_REQUIRE_RUNNING_APP=true \
QTA_LONGPORT_REQUIRE_PROVIDER_READY=true \
scripts/check-longport-readiness.sh

# 如需额外复核官方源码合约，开启该项；网络/代理异常时可能失败
QTA_LONGPORT_READINESS_CHECK_OFFICIAL_CONTRACT=true \
scripts/check-longport-readiness.sh
```

如果只是想做离线准备检查，不碰正在运行的后端：

```bash
QTA_LONGPORT_READINESS_CHECK_PROVIDER_STATUS=false \
scripts/check-longport-readiness.sh
```

### 5.1 SDK 未安装或凭据未配置

```bash
curl -s -w '\nHTTP:%{http_code}\n' \
  http://localhost:8080/api/v1/market-data/providers/LONGPORT/status
```

预期：HTTP 200，`configured=false`，`lastError` 能解释是 SDK 未安装、未启用或凭据缺失。

### 5.2 SDK + 凭据可用后：推荐脚本

推荐直接执行：

```bash
scripts/verify-longport-real-sync.sh
```

脚本会：

- 使用 `QTA_LONGPORT_ENABLED=true` 重建/启动 app 容器。
- 读取 shell env 或 `.env` 中的 `LONGPORT_APP_KEY`、`LONGPORT_APP_SECRET`、`LONGPORT_ACCESS_TOKEN`。
- 启动前先做凭据预检；缺少任意一个凭据会直接退出，不会发起外部请求。
- 校验 `/providers/LONGPORT/status` 为 `configured=true` / `reachable=true`。
- 拉取最新价并默认 `persist=true`，再查询 `quote-snapshots` 确认写入 `stock_quote_snapshot`。
- 创建一个日 K 同步任务，再查询 `daily-bars` 确认写入 `stock_daily_bar(data_source=LONGPORT)`。
- 不打印任何凭据。
- 默认保留 LongPort enabled 容器，方便继续联调；如需脚本结束后恢复默认 disabled 容器，可设置 `QTA_VERIFY_RESTORE_APP_AFTER_RUN=true`。

默认参数：

```bash
QTA_VERIFY_SYMBOL=SH.600519
QTA_VERIFY_START_DATE=2026-07-10
QTA_VERIFY_END_DATE=2026-07-10
QTA_VERIFY_ADJUST_TYPE=NONE
QTA_VERIFY_QUOTE_PERSIST=true
QTA_VERIFY_RUN_DAILY_BAR_SYNC=true
QTA_VERIFY_REBUILD_APP=true
QTA_VERIFY_RESTORE_APP_AFTER_RUN=false
QTA_VERIFY_RUNTIME_LIB_INSPECTION=auto
```

`QTA_VERIFY_RUNTIME_LIB_INSPECTION` 说明：

- `auto`：默认值；`runtime-libs/` 下存在 jar 时自动执行 `scripts/inspect-longport-runtime-libs.sh`，无 jar 时仅提示，兼容未来 SDK 被打入 app jar 的路径。
- `true`：强制检查 `runtime-libs/`，适合当前 Docker 外挂 jar 方案。
- `false`：跳过检查，只在明确知道 SDK 已通过其他方式进入 classpath 时使用。

示例：

```bash
QTA_VERIFY_SYMBOL=SH.600519 \
QTA_VERIFY_START_DATE=2026-07-10 \
QTA_VERIFY_END_DATE=2026-07-10 \
scripts/verify-longport-real-sync.sh
```

如果只想做不写库的 latest quote 烟测：

```bash
QTA_VERIFY_QUOTE_PERSIST=false \
QTA_VERIFY_RUN_DAILY_BAR_SYNC=false \
scripts/verify-longport-real-sync.sh
```

### 5.3 SDK + 凭据可用后：手工 curl

只用一个 A 股标的小流量验证，不做全量扫描：

```bash
curl -s -w '\nHTTP:%{http_code}\n' \
  -H 'Content-Type: application/json' \
  -X POST http://localhost:8080/api/v1/market-data/quotes/latest \
  -d '{"canonicalSymbols":["SH.600519"],"persist":true}'
```

```bash
curl -s -w '\nHTTP:%{http_code}\n' \
  -H 'Content-Type: application/json' \
  -X POST http://localhost:8080/api/v1/market-data/sync-tasks/daily-bars \
  -d '{"taskType":"DAILY_BAR_SYNC","provider":"LONGPORT","canonicalSymbol":"SH.600519","startDate":"2026-07-10","endDate":"2026-07-10","adjustType":"NONE"}'
```

验收标准：

- 不返回 500。
- 最新价写入 `stock_quote_snapshot`，`dataSource=LONGPORT`。
- 日 K 写入 `stock_daily_bar`，`data_source=LONGPORT`。
- 同一日期范围重复同步能解释 inserted / updated / skipped。
- 失败时 `market_data_sync_task` 和 `market_data_alert` 有脱敏留痕。

## 6. 后续维护规则

- 升级 LongPort SDK tag 或改动 `ReflectiveLongPortQuoteClient` 前，先运行 `scripts/check-longport-official-java-contract.sh`，合约说明见 `LONGPORT_OFFICIAL_JAVA_CONTRACT.md`。
- 真实外联前优先运行 `scripts/check-longport-readiness.sh`；只有预检通过后，再运行 `scripts/verify-longport-real-sync.sh`。
- 若官方 Maven artifact 可用，再考虑是否把反射 adapter 简化为编译期依赖 adapter。
- 未完成真实外联前，不把建设看板标成 DONE/M4。
- 不接 LongPort 交易、订单、账户、真实持仓能力。
- 不因行情源失败影响 Today MVP、交易账本、持仓快照等本地核心功能。
