# Feature Design: LongPort 单股票手动同步最小闭环

> 版本：v0.1.3 · 状态：真实外联已验收通过（2026-07-12）；后续扩展见 `MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md` · 关联：`LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md`、`MARKET_DATA_FOUNDATION_DESIGN.md`、`../api/MARKET_DATA_API.md`、`../BUILD_CHECKLIST.md`、`../development/LONGPORT_SDK_RUNTIME_INSTALLATION.md`

## 1. 用户目标

用户当前不需要全市场或全量自动拉取，只需要在页面输入一个本系统统一证券代码，例如 `SH.600519`，就能手动从长桥 LongPort 获取该股票的行情并同步入库：

- 拉取最新价，写入 `stock_quote_snapshot`。
- 拉取指定日期范围的日 K，写入 `stock_daily_bar`，`data_source=LONGPORT`。
- 返回本次 inserted / updated / skipped / failed。
- 同步任务、失败原因和数据质量提醒可查。

## 2. 当前事实

已完成：

- `MarketDataProvider` 只读抽象。
- `DisabledMarketDataProvider` 未配置兜底。
- `FakeMarketDataProvider` 测试用模拟 provider。
- `LongPortSymbolMapper` 内部 `SH.600519` 与 provider `600519.SH` 的双向映射。
- `stock_quote_snapshot`、`market_data_sync_task`、`market_data_alert`、`market_data_sync_scope_lock` 表。
- provider 状态、最新价快照、历史同步任务、任务查询、提醒查询 API。
- 前端 `/market-data` 6 Tab 页面。

2026-07-11 本轮新增：

- `LongPortProperties` 配置绑定。
- `LongPortMarketDataProvider`：canonical symbol / LongPort symbol 转换、复权类型转换、只读 provider 状态。
- `LongPortQuoteClient` + `ReflectiveLongPortQuoteClient`：运行时反射调用官方 Java SDK 的 `QuoteContext#getQuote` 与 `getHistoryCandlesticksByDate`。
- 默认 disabled provider 保持安全兜底；`qta.market-data.longport.enabled=true` 时切换到 LongPort provider。
- `.env.example` 与 Docker Compose 已增加 LongPort 环境变量透传。
- Dockerfile / Compose 已支持 `runtime-libs/` 外部只读 jar classpath，后续拿到官方 SDK jar 后不需要提交进 Git。
- 测试覆盖：SDK 缺失安全降级、enabled=true 上下文可启动、provider quote / daily bar 转换、HF 后复权拒绝。

外部阻塞已于 2026-07-12 全部解除，真实外联验收通过：

- 官方 Java SDK Maven 坐标实测可用：正确坐标 `io.github.longportapp:openapi-sdk:4.3.3`（注意 groupId 含 `app`；官方源码 pom 里写的 `io.github.longport` 是错的，曾导致查询失败）。`openapi-sdk-4.3.3.jar` 内置全平台 native，已装入 `runtime-libs/`（vendor jar 被 gitignore，不入 Git），详见 §4 与 `../development/LONGPORT_SDK_RUNTIME_INSTALLATION.md`。
- **部署必须配置域名覆盖**（SDK 默认域名 `openapi.longport.cn` / `openapi-quote.longport.cn` 已废弃）：`LONGPORT_HTTP_URL=https://openapi.longbridge.cn` + `LONGPORT_QUOTE_WEBSOCKET_URL=wss://openapi-quote.longbridge.cn/v2`。
- 真实外联已验收（SH.600519 单 symbol 多日）：provider `configured=true / reachable=true`，latest quote 写入 `stock_quote_snapshot(dataSource=LONGPORT)`，daily bar 写入 `stock_daily_bar(data_source=LONGPORT)`，sync task `SUCCEEDED`。
- 前端页面小改已完成：状态页展示 provider configured/reachable；历史同步禁用 `HF`。

## 3. 范围

做：

- 只接入 LongPort 只读行情能力。
- 后端已增加 provider adapter：配置完整且 enabled 时注入 `LongPortMarketDataProvider`，否则继续注入 disabled provider。
- 支持单股票最新价拉取：复用 `POST /api/v1/market-data/quotes/latest`，后端遵循 LongPort quote 单次最多 500 个标的限制。
- 支持单股票日 K 同步：复用 `POST /api/v1/market-data/sync-tasks/daily-bars`。
- 将 LongPort 错误转换为系统业务错误码和脱敏提示。
- 更新 `.env.example`、Docker Compose 环境变量示例和部署说明。
- 前端在未配置 provider 时展示“需要配置长桥只读行情凭据”，不要把预期 400 做成吓人的系统错误。

不做：

- 不全市场扫描。
- 不定时任务。
- 不自动选择股票池批量同步。
- 不接 LongPort 交易、订单、账户、真实持仓、改单、撤单。
- 不把 LongPort 行情覆盖 `portfolio_price_snapshot` 手工当前价。
- 不在前端、数据库、日志、Git 中保存密钥明文。

## 4. 外部接口调研摘要

LongPort 官方文档当前给出的关键事实：

- 官方 Java SDK Maven 坐标（2026-07-12 实测纠正）：正确坐标是 `io.github.longportapp:openapi-sdk:4.3.3`。注意官方源码 `java/javasrc/pom.xml` 里 groupId 写的是 `io.github.longport`（缺 `app` 后缀），那个是错的，曾导致 Maven Central 查询返回 404 / 搜索 0 结果。
- `openapi-sdk-4.3.3.jar`（约 35MB）内置全平台 native（linux/osx/windows × 64/arm64），含本系统反射 adapter 需要的全部 `com.longport.*` 类。已装入 `runtime-libs/`。
- **SDK 4.3.3 默认域名已废弃**：native lib 硬编码默认 `https://openapi.longport.cn`（HTTP）+ `wss://openapi-quote.longport.cn/v2`（quote ws），长桥更名 Longbridge 后这些域名 DNS 解析失败。本系统通过 `Config.httpUrl(...)` / `Config.quoteWebsocketUrl(...)` 反射覆盖为 `https://openapi.longbridge.cn` + `wss://openapi-quote.longbridge.cn/v2`（环境变量 `LONGPORT_HTTP_URL` / `LONGPORT_QUOTE_WEBSOCKET_URL`，默认空）。
- 因此本系统采用“运行时反射 adapter”：编译期不依赖 LongPort SDK；运行时 classpath 中存在官方 SDK + native libs 时，才会真实调用。
- 实时行情接口参数使用 `ticker.region` 格式，例如 `700.HK`；单次 quote 请求最多 500 个标的。
- 历史 K 线接口同样使用 `ticker.region`，支持按日期区间查询，日期格式为 `YYYYMMDD`。
- 历史 K 线返回字段包含 open / high / low / close / volume / turnover / timestamp。
- A 股日/周/月/年 K 线官方说明历史区间为 1999-11-1 至今。
- 历史 K 线频率限制：每 30 秒最多 60 次。

因此本系统内部仍使用 `SH.600519` / `SZ.000001` / `BJ.430047`，provider adapter 内部转换为 `600519.SH` / `000001.SZ` / `430047.BJ`。

## 5. 后端设计

### 5.1 包结构

```text
com.quant.trade.marketdata
├── config
│   ├── MarketDataConfig
│   └── LongPortProperties
├── provider
│   ├── MarketDataProvider
│   ├── DisabledMarketDataProvider
│   ├── FakeMarketDataProvider
│   ├── LongPortMarketDataProvider
│   ├── LongPortSymbolMapper
│   └── longport
│       ├── LongPortQuoteClient
│       └── ReflectiveLongPortQuoteClient
```

### 5.2 配置

```properties
qta.market-data.longport.enabled=${QTA_LONGPORT_ENABLED:false}
qta.market-data.longport.app-key=${LONGPORT_APP_KEY:}
qta.market-data.longport.app-secret=${LONGPORT_APP_SECRET:}
qta.market-data.longport.access-token=${LONGPORT_ACCESS_TOKEN:}
qta.market-data.longport.timeout-seconds=${QTA_LONGPORT_TIMEOUT_SECONDS:10}
qta.market-data.longport.quote-time-zone=${QTA_LONGPORT_QUOTE_TIME_ZONE:Asia/Shanghai}
```

判定 configured：

- `enabled=true`
- 官方 Java SDK 进入运行时 classpath
- `app-key` 非空
- `app-secret` 非空
- `access-token` 非空

### 5.3 Provider 行为

`LongPortMarketDataProvider#getLatestQuotes`：

- 校验 symbols 非空且不超过 `LONGPORT_MAX_QUOTE_SYMBOLS`（500）。
- `SH.600519` 转 `600519.SH`。
- 调用 LongPort quote。
- 字段映射：
  - `last_done` -> `currentPrice`
  - `prev_close` -> `preClosePrice`
  - `open/high/low` -> 对应价格
  - `timestamp` -> `quoteTime`
  - `volume` -> `volume`
  - `turnover` -> `amount`
  - `trade_status` -> `tradeStatus`

`LongPortMarketDataProvider#getDailyBars`：

- 第一阶段只实现日线：`Period.Day`。
- `NONE` 映射 `AdjustType.NoAdjust`。
- `QF` 映射 `AdjustType.ForwardAdjust`。
- `HF` 当前明确拒绝：官方 Java SDK 当前仅有 `NoAdjust` / `ForwardAdjust`，未提供后复权枚举。
- 使用日期区间查询。
- LongPort timestamp 转本地交易日 `tradeDate`。
- 写入仍由 `MarketQuoteService` 负责，provider 不直接写 DB。

### 5.4 错误映射

| LongPort 场景 | 系统错误码 | 用户提示 |
| --- | --- | --- |
| 未配置 | `BUSINESS_RULE_VIOLATION` | 行情 provider 未配置 |
| 无权限 / 行情权限不足 | `BUSINESS_RULE_VIOLATION` | 长桥行情权限不足，请检查行情权限 |
| 标的无行情 | `RESOURCE_NOT_FOUND` 或 `BUSINESS_RULE_VIOLATION` | 标的无行情数据 |
| 限流 | `BUSINESS_RULE_VIOLATION` | 长桥接口限流，请稍后再试 |
| 参数非法 | `PARAM_ERROR` | 标的代码或日期参数不合法 |
| SDK 未安装 | `BUSINESS_RULE_VIOLATION` | LongPort Java SDK 未安装或未进入运行时 classpath |
| SDK / 网络异常 | `BUSINESS_RULE_VIOLATION` | 获取 LongPort 行情失败，已记录任务失败 |

错误响应和日志不得打印 app secret / access token / 完整原始响应。

## 6. 前端设计

### 6.1 行情状态

- configured=false 时展示明确状态：`长桥只读行情未配置，当前只能使用 CSV 导入和本地已有数据`。
- `健康检查` 按钮继续可用，但未配置时显示 warning，不作为系统错误。

### 6.2 最新价快照

- 输入框提示：`输入 SH.600519，拉取最新价并保存为行情快照`。
- provider 未配置时，不弹“系统异常”，改为 warning：`请先在服务器配置 LongPort 只读行情凭据`。

### 6.3 历史数据同步

- 第一阶段主操作就是单股票同步：
  - canonicalSymbol
  - startDate
  - endDate
  - adjustType
- 提交后展示任务结果：
  - 成功：inserted / updated / skipped。
  - 失败：lastErrorCode + 用户可读 message。
- 本地开发时若页面出现 502，优先检查 Vite `VITE_DEV_PROXY_TARGET` 是否指向真实后端端口。

## 7. 验收标准

- [x] 未配置 LongPort 时：
  - `/actuator/health` UP。
  - `/providers/LONGPORT/status` 返回 200 + configured=false。
  - `/quotes/latest` 和 `/sync-tasks/daily-bars` 返回业务 400，不返回 500。
- [x] 启用 LongPort 但 SDK 未安装时：
  - Spring 上下文可启动。
  - `/providers/LONGPORT/status` 返回 200 + configured=false + `LongPort Java SDK 未安装或未进入运行时 classpath`。
- [x] SDK jar/native libs 可用并配置 LongPort 凭据后：
  - `/providers/LONGPORT/status` 返回 configured=true。
  - 输入 `SH.600519` 可拉取最新价并写入 `stock_quote_snapshot`。
  - 输入 `SH.600519` + 日期区间可同步日 K 并写入 `stock_daily_bar(data_source=LONGPORT)`。
  - 重复同步同一日期范围可解释 inserted / updated / skipped，不重复造脏数据。
  - 同 scope 并发请求不会创建 sibling retry 冲突（基础锁和重试留痕已实现；多 symbol/多日压测仍可作为 P1.2 hardening）。
- [x] 前端：
  - `npm run typecheck` 通过。
  - `npm run lint` 通过。
  - `npm run test` 通过。
  - `npm run build` 通过。
  - `/market-data` 页面无 502、无控制台 error。
  - 状态页清晰展示 SDK 未安装 / 凭据未配置。
  - 历史同步禁用 `HF` 后复权。
- [x] Docker：
  - `docker compose up -d --build` 成功。
  - `curl /actuator/health` UP。
  - 长桥凭据通过环境变量注入，不进入 Git。
  - 外部 SDK jar 放入 `runtime-libs/` 后能够被容器加载。

## 8. 下轮开发提示词摘要

开发目标：本单股票最小闭环已完成。后续不要继续重复做 SDK 获取和单 symbol 验收；下一步读取 `MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`，建设行情工作台、采集任务、分钟线资产和异动大屏；仍然不接交易 API、不做全市场扫描。

必读：

- `AGENTS.md`
- `docs/AI_DEVELOPMENT_INDEX.md`
- `docs/AI_HANDOFF.md`
- 本文档
- `docs/features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`
- `docs/api/MARKET_DATA_API.md`
- `docs/features/LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md`

已改：

- 后端 config / provider adapter / reflection client / tests。
- `.env.example` 和 Docker 环境变量示例。

待改：

- 建设看板下一阶段节点保持同步。
- 多 symbol / 多日范围 / 并发边界可作为 P1.2 hardening。

禁止：

- 不接交易 API。
- 不把密钥放前端。
- 不改旧 migration。
- 不开启全量 docs 读取。
