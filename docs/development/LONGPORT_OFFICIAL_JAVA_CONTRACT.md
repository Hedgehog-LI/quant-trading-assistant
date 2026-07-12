# LongPort Official Java SDK Contract

> 目的：记录本项目 `ReflectiveLongPortQuoteClient` 依赖的官方 Java SDK 类、方法和枚举常量。升级 LongPort SDK tag 前，先运行 `scripts/check-longport-official-java-contract.sh`。

## 1. 官方来源

- Tag：`v4.3.3`
- Base URL：`https://raw.githubusercontent.com/longportapp/openapi/v4.3.3/java/javasrc/src/main/java`
- 关键文件：
  - `com/longport/Config.java`
  - `com/longport/quote/QuoteContext.java`
  - `com/longport/quote/SecurityQuote.java`
  - `com/longport/quote/Candlestick.java`
  - `com/longport/quote/Period.java`
  - `com/longport/quote/AdjustType.java`
  - `com/longport/quote/TradeSessions.java`

## 2. Adapter 依赖点

| 本项目反射点 | 官方 SDK 合约 |
| --- | --- |
| `com.longport.Config` | 类存在，且实现 `AutoCloseable` |
| `Config.fromApikey(String, String, String)` | 显式 app key / secret / access token |
| `Config.fromApikeyEnv()` | 从环境变量和 `.env` 读取 Legacy API Key |
| `Config.httpUrl(String)` | 链式覆盖 HTTP API 基址（SDK 默认 `openapi.longport.cn` 已废弃，切 `https://openapi.longbridge.cn`） |
| `Config.quoteWebsocketUrl(String)` | 链式覆盖 Quote WebSocket 基址（SDK 默认 `openapi-quote.longport.cn` 已废弃，切 `wss://openapi-quote.longbridge.cn/v2`） |
| `com.longport.quote.QuoteContext` | 类存在，且实现 `AutoCloseable` |
| `QuoteContext.create(Config)` | 创建只读行情上下文 |
| `QuoteContext.getQuoteLevel()` | 返回 `CompletableFuture<String>` |
| `QuoteContext.getQuote(String[])` | 返回 `CompletableFuture<SecurityQuote[]>` |
| `QuoteContext.getHistoryCandlesticksByDate(String, Period, AdjustType, LocalDate, LocalDate, TradeSessions)` | 返回 `CompletableFuture<Candlestick[]>` |
| `SecurityQuote` getters | `getSymbol`、`getLastDone`、`getPrevClose`、`getOpen`、`getHigh`、`getLow`、`getTimestamp`、`getVolume`、`getTurnover`、`getTradeStatus` |
| `Candlestick` getters | `getClose`、`getOpen`、`getLow`、`getHigh`、`getVolume`、`getTurnover`、`getTimestamp` |
| `Period.Day` | 日 K 周期 |
| `AdjustType.NoAdjust` | 不复权 |
| `AdjustType.ForwardAdjust` | 前复权 |
| `TradeSessions.All` | 全交易时段 |

## 3. 检查命令

在线检查官方 tag：

```bash
scripts/check-longport-official-java-contract.sh
```

指定 tag：

```bash
QTA_LONGPORT_OPENAPI_TAG=v4.3.3 \
scripts/check-longport-official-java-contract.sh
```

当前机器如果代理或 DNS 无法访问 GitHub raw，可把官方源码放到本地目录并使用 `file://`：

```bash
QTA_LONGPORT_OPENAPI_RAW_BASE_URL=file:///path/to/java/javasrc/src/main/java \
scripts/check-longport-official-java-contract.sh
```

## 4. 2026-07-12 复核结果

- 已拉取官方 `v4.3.3` 关键 Java 源码并逐项核对。
- 本项目当前 `ReflectiveLongPortQuoteClient` 使用的类、方法、getter 和枚举常量均与官方源码一致。
- 当前环境在线脚本直连 GitHub raw 受代理/DNS 影响会失败；使用本地官方源码缓存通过合约检查。
