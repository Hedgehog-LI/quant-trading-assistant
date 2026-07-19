# 精确证券代码验证产品设计

> 状态：Implemented and accepted（2026-07-17）。该功能是行情采集计划的近期入口优化；全量证券目录和名称模糊搜索仍按 `SECURITY_DIRECTORY_SEARCH_DESIGN.md` 后续建设。

## 1. 用户目标

用户不再手工拼写 `SH.603308`、`HK.02498`、`US.NVDA`。创建采集计划时先选择市场、输入原始证券代码，点击“查询并验证”；系统从 LongPort 读取证券静态信息和当前可用报价，用户核对名称与价格后确认加入计划。

首批验收样本：

| 市场 | 用户输入 | LongPort symbol | 系统 canonical symbol | 预期名称 |
| --- | --- | --- | --- | --- |
| A 股 | `603308` | `603308.SH` | `SH.603308` | 应流股份 |
| 港股 | `2498` | `2498.HK` | `HK.02498` | 速腾聚创 |
| 美股 | `NVDA` | `NVDA.US` | `US.NVDA` | 英伟达 / NVIDIA |

## 2. 范围与非目标

本轮实现：

- A 股、港股、美股分段市场选择。
- 精确代码输入、格式归一化、静态信息验证和一次最新报价查询。
- 展示正式名称、统一代码、交易所、币种、每手股数、价格、报价时间、交易状态和行情延迟/权限说明。
- 用户显式确认后加入采集计划 scope；验证阶段不创建计划、不落行情事实表。
- 采集计划继续沿用现有结构化表单、执行引擎和 A 股分钟任务边界。

本轮不实现：

- 股票名称模糊搜索、拼音联想、全市场目录下载。
- 自动选择搜索结果或未经确认直接创建计划。
- 港美股分钟采集执行和跨市场 scheduler；精确验证成功不代表该任务类型已经支持执行。
- 自动交易、账户、订单或持仓接入。

## 3. 交互流程

1. 用户选择 `A 股 / 港股 / 美股`。
2. 输入证券代码；A 股仅接受 6 位数字，港股接受 1-5 位数字，美股接受合法 ticker 字符并转大写。
3. 点击“查询并验证”。输入变化后，旧验证结果立即失效。
4. 后端构造 canonical symbol，再调用 provider 的静态信息与最新报价能力。
5. 页面展示验证结果。用户可以核对名称、代码、价格、报价时间及延迟标签。
6. 点击“加入计划”后，该 canonical symbol 才进入当前计划的已选标的列表；用户仍需完成日期、粒度和触发方式并保存计划。

不得把“当前无报价”直接解释为“证券不存在”。静态信息成功时证券身份已验证，报价可能因闭市、权限、延迟或 provider 暂时异常不可用。

## 4. 状态语义

| 状态 | 含义 | 前端动作 |
| --- | --- | --- |
| `VERIFIED_QUOTE_AVAILABLE` | 静态信息与报价都可用 | 可加入计划，展示价格与时间 |
| `VERIFIED_DELAYED_QUOTE` | 静态信息与延迟报价可用 | 可加入计划，醒目标注延迟 |
| `VERIFIED_NO_QUOTE` | 静态信息有效，但没有可用报价 | 可加入计划，提示报价不可用 |
| `INVALID_SYMBOL` | 市场或代码格式错误、证券不存在 | 不可加入，允许修改重试 |
| `PROVIDER_UNAVAILABLE` | provider 未配置或暂不可达 | 不可加入，不把它解释成代码错误 |
| `NO_PERMISSION` | 当前账号无该市场行情权限 | 静态信息若已验证可加入，明确权限边界 |

## 5. 代码转换规则

- A 股：根据 6 位代码推断交易所。`92` 或 `4/8` 开头映射 BJ，`6/9` 开头映射 SH，`0/1/2/3` 映射 SZ；不明确的代码返回格式错误，不猜测。
- 港股：用户输入去前导零后发送给 LongPort，例如 `2498.HK`；系统 canonical symbol 固定补为 5 位 `HK.02498`。
- 美股：ticker 转大写并保留受支持的 `.` 等字符，例如 `BRK.B` -> `BRK.B.US` / `US.BRK.B`。
- provider symbol 与 canonical symbol 的互转必须复用 `LongPortSymbolMapper`，不得在 controller 或前端重复实现。

## 6. API 契约

规划接口：

```http
POST /api/v1/market-data/securities/verify
Content-Type: application/json

{"market":"CN","code":"603308"}
```

响应核心字段：

```json
{
  "canonicalSymbol": "SH.603308",
  "providerSymbol": "603308.SH",
  "displayName": "应流股份",
  "market": "CN",
  "exchange": "SSE",
  "currency": "CNY",
  "lotSize": 100,
  "verificationStatus": "VERIFIED_QUOTE_AVAILABLE",
  "quoteAvailable": true,
  "lastPrice": 0,
  "quoteTime": "2026-07-17T15:00:00+08:00",
  "tradeStatus": "NORMAL",
  "quoteDelay": "REAL_TIME",
  "provider": "LONGPORT"
}
```

接口是只读验证：不得写 `stock_basic`、`stock_quote_snapshot`、采集计划或任务表。成功响应允许 `lastPrice/quoteTime` 为空。

## 7. 后端边界

- controller 只负责校验请求和返回 VO；市场代码解析放 manager；provider 调用和状态编排放 service。
- `MarketDataProvider` 提供证券验证能力；LongPort 实现通过既有可选反射 SDK 通道读取 Static Info 和 Quote，Disabled/Fake 实现必须语义明确。
- Static Info 成功、Quote 失败时尽量返回 `VERIFIED_NO_QUOTE` 或 `NO_PERMISSION`，保留已确认的证券身份。
- 网络调用不进入数据库事务；日志不得打印凭据。
- API 错误沿用 `ErrorCodeEnum`，禁止用 HTTP 500 表达普通输入错误或 provider 未配置。

## 8. 前端边界

- 在采集计划 Drawer 中新增复用组件，不让页面自行拼 canonical symbol。
- 市场使用分段控件，查询和加入计划是两个独立命令；加载中禁止重复提交。
- 已选标的可移除；编辑已有计划时兼容旧 `scopeJson`，不能要求用户先重新验证才能保存未改动的老计划。
- 港美股证券可以完成身份验证，但当用户选择当前未支持的分钟任务时，继续展示现有明确阻断信息；日 K 任务不受该分钟边界影响。
- mock 模式不得伪装为 LongPort 实时查询；应返回明确的演示状态或提示切换后端模式。

## 9. 验收标准

- 后端单元/集成测试覆盖三市场代码转换、非法输入、Static Info 成功但 Quote 失败、provider disabled 和只读无落库。
- 前端测试覆盖查询成功、输入变化使旧结果失效、加入/移除、重复点击防护、错误/无报价/延迟报价展示，以及编辑旧计划兼容。
- Docker + curl 至少验证一个真实 A 股；若本机权限允许，再以 `HK.02498` 和 `US.NVDA` 做最小查询。外联次数保持最小，不做全市场扫描。
- typecheck、lint、目标测试、后端测试和 build/package 通过；浏览器 E2E 如跳过必须如实记录。

## 10. 后续演进

本轮精确验证闭环后，再推进本地证券目录、名称/拼音模糊搜索、目录同步和共享 `SecuritySelector`。精确验证 API 可作为目录未命中时的在线补全器，但不能替代本地目录。
