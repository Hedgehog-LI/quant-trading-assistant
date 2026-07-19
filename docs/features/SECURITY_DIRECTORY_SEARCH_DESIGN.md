# 证券目录与智能检索产品设计

> 状态：Planned（后续阶段）。近期先实现 `EXACT_SECURITY_VERIFICATION_DESIGN.md` 定义的“市场 + 精确代码 + 静态信息/报价确认”；本文保留全量目录与名称模糊搜索的完整演进方案。

## 0. 分阶段决策

- **P1.4a 当前实施**：用户选择市场、输入精确代码，通过 LongPort Static Info + Quote 验证并确认加入采集计划。无需下载全量目录，接口和验收见 `EXACT_SECURITY_VERIFICATION_DESIGN.md`。
- **P1.4b 后续实施**：扩展 `stock_basic`、导入全市场证券目录、支持名称/拼音/别名模糊检索和共享选择器。
- P1.4a 不建设平行主数据，也不妨碍 P1.4b；它将成为目录未命中时的在线精确补全通道。

## 1. 背景与目标

当前行情、采集计划、板块成员等页面主要依赖用户手工填写统一证券代码。用户往往只记得“应流股份”“速腾聚创”“Apple”，不知道 `SH.603308`、`HK.02498`、`US.AAPL`，容易输错市场、代码位数或证券类型。

本功能建设一个本地优先的证券目录，并提供可复用的智能检索组件：

- 支持中文名、英文名、代码、统一证券代码、简称和拼音检索。
- 搜索结果明确展示市场、交易所、证券类型和上市状态，同名证券不猜测、不静默选错。
- 用户确认结果后自动填充 `canonicalSymbol`、名称、市场等字段。
- 搜索与行情拉取解耦：输入关键词时不拉报价、不创建采集任务、不写价格事实表。
- A 股、港股、美股共用统一模型，为后续板块、指标、策略和回测提供稳定标识。

## 2. 产品原则

1. **本地目录优先**：证券名称检索必须在外部 provider 不可用时仍可工作。
2. **目录全量，行情按需**：本地保存全市场证券元数据，不全量保存所有证券的价格历史；报价和 K 线仍只为用户选择的标的拉取。
3. **选择必须明确**：检索只给候选，必须由用户确认；同名、跨市场或退市证券不得自动选择。
4. **权威事实与用户别名分离**：外部目录维护证券正式信息；用户自定义名称只作为别名，不能覆盖权威名称。
5. **复用现有模型**：扩展 `stock_basic`，禁止再建含义重复的 `security_master`。
6. **只读行情边界不变**：不接交易、账户、订单和券商持仓能力。

## 3. 用户场景

### 3.1 创建行情采集任务

用户输入“应流股份”或“603308”，选择“应流股份 / SH.603308 / 上交所”，系统自动填入任务 scope。用户再配置日期、粒度和触发方式。

### 3.2 查询港美股

用户输入“速腾聚创”“2498”或“RoboSense”，选择 `HK.02498`；输入“Apple”或“AAPL”，选择 `US.AAPL`。市场标签始终可见。

### 3.3 目录暂未收录

系统保留手工输入统一证券代码的后备路径。用户可触发“在线校验”，由已配置的 metadata enricher 校验并生成待确认信息；不得因外部校验失败阻塞已有本地目录检索。

## 4. 信息架构与交互

前端建设共享 `SecuritySelector`，而不是在各页面分别实现搜索框。

### 4.1 输入与结果

- 中文最少 1 个字符，拉丁字母或数字最少 2 个字符后搜索。
- 输入去首尾空格，代码和英文统一大写；250ms debounce，并取消或忽略过期请求。
- 默认返回前 20 条，结果展示：显示名、统一证券代码、市场/交易所、证券类型、上市状态。
- 支持键盘上下选择、Enter 确认、Esc 关闭，并包含 loading、空结果、失败与重试状态。
- 已选择后再编辑文本，必须清除旧选择，避免“页面显示新名称但提交旧代码”。
- 退市证券默认隐藏，可通过高级选项显式包含。

需要区分三个空态：目录尚未初始化、目录正常但无匹配、外部精确校验不可用。目录陈旧时展示最近成功同步时间，但不能阻断已有本地结果；不支持的证券类型要明确标识，不能按普通股票静默保存。

### 4.2 排序规则

服务端给出确定性排序，避免前端自行拼装：

| 匹配类型 | 建议基础分 |
| --- | ---: |
| 统一证券代码完全匹配 | 100 |
| 原始代码完全匹配 | 95 |
| 当前正式名称完全匹配 | 90 |
| 当前名称前缀匹配 | 80 |
| 别名完全/前缀匹配 | 75 |
| 拼音首字母或全拼前缀匹配 | 70 |
| 名称或别名包含匹配 | 50 |

同分时依次按：正常上市优先、用户指定市场优先、名称、`canonical_symbol` 排序。MVP 不使用不可解释的模型排序。

### 4.3 首批接入位置

1. 行情页最新价查询。
2. 历史日 K 同步。
3. 行情工作台采集计划 scope 配置，逐步替换原始 JSON 输入。
4. 板块成员添加。

第二批再接入自选股、交易计划、交易记录、风控计算和持仓快照，避免一次改动所有业务表单。

## 5. 数据模型

### 5.1 扩展 `stock_basic`

保留现有 `name` 作为兼容显示名，规划新增：

| 字段 | 说明 |
| --- | --- |
| `name_cn` / `name_hk` / `name_en` | 多语言正式名称，可空 |
| `short_name` | 市场常用简称 |
| `pinyin_full` / `pinyin_abbr` | 中文名称全拼与首字母，供本地检索 |
| `exchange` | 交易所代码，区别于市场 |
| `currency` | 交易币种 |
| `security_type` | STOCK / ETF / INDEX / REIT 等 |
| `list_status` | LISTED / DELISTED / UNKNOWN；停牌属于时点行情状态，不放入生命周期 |
| `data_source` | 当前主信息来源 |
| `source_updated_at` | 来源数据更新时间 |
| `source_hash` | 用于增量比对和幂等更新 |

`stock_basic.id` 是不可变内部身份，`canonical_symbol` 是当前跨模块业务代码，A/H/US 规则沿用现有约定。代码发生变化时不得新造重复证券；旧代码应保留为别名/历史标识。退市证券不物理删除。

### 5.2 新增 `stock_alias`

用于旧名称、英文名、繁体名、简称和用户别名：

- `id`
- `stock_basic_id`
- `alias`
- `normalized_alias`
- `alias_type`：FORMER_NAME / OLD_TICKER / SHORT_NAME / ENGLISH / TRADITIONAL / USER
- `language`
- `data_source`
- `effective_from` / `effective_to`
- `created_at` / `updated_at`

建议唯一键：`stock_basic_id + normalized_alias + alias_type`；索引覆盖 `normalized_alias`。权威名称变更时，旧名称转入 `FORMER_NAME`，保证历史搜索可用。

## 6. Provider 与同步设计

证券目录属于元数据域，不复用只负责报价/K 线的 `MarketDataProvider`：

```text
SecurityDirectoryProvider
  -> 拉取证券目录快照/增量
  -> 标准化市场、交易所、类型、名称
  -> upsert stock_basic / stock_alias

SecurityMetadataEnricher
  -> 已知 canonical symbol 后按需校验和补全
  -> LongPort Static Info 可作为一种实现
```

MVP 首选可审计的 CSV/官方目录快照导入；后续可配置 LongPort Security List、Tushare 等目录 provider，并在接入前核实覆盖率、额度、许可证和再分发边界。LongPort Static Info 需要先知道 symbol，因此只作为按需补全器，不能成为名称联想的唯一依赖。外部精确查找只在本地无结果且用户明确触发时执行，不在每次键入时外联。

同步策略：

- 首次导入全量快照。
- 每日增量同步上市、退市、改名和状态变化。
- 每周做全量 checksum 对账。
- 复用现有 `market_data_sync_task` 及已预留的 `SECURITY_MASTER_SYNC` 任务类型记录执行过程；目录最新成功时间可使用独立轻量状态表，禁止从任意 JSON 反查。
- 按 `canonical_symbol` 幂等 upsert，记录来源、来源时间和 hash。
- 来源失败保留最后一次成功目录；失败要留任务状态和错误摘要，不清空本地数据。
- 单次目录缺失不得直接判定退市；至少连续成功批次缺失并经第二来源或人工确认。
- 全量同步先进入 staging/候选集，完成数量波动、唯一性和必填字段门禁后再发布，禁止把异常空结果覆盖为正式目录。
- 来源优先级可配置；权威目录字段不被用户别名覆盖。

## 7. 规划 API

以下接口尚未实现，因此暂不加入“已实现 API”索引：

```http
GET /api/v1/market-data/securities/search?q=应流股份&markets=SH,SZ&types=STOCK&includeDelisted=false&limit=20
GET /api/v1/market-data/securities/{canonicalSymbol}
POST /api/v1/market-data/security-directory/sync
GET /api/v1/market-data/security-directory/status
POST /api/v1/market-data/securities/{canonicalSymbol}/verify
```

搜索响应至少包含 `canonicalSymbol`、`symbol`、`displayName`、`market`、`exchange`、`currency`、`securityType`、`listStatus`、`matchedBy`；响应元数据包含 `catalogUpdatedAt`、`stale`、`degraded`。同步与在线校验接口必须鉴权/限流能力预留，且不得返回 provider 密钥。

## 8. 非功能要求

- 目标规模为 A/H/US 数万到十余万证券元数据；先使用规范化列和 B-Tree 前缀索引，实测不足再评估 MySQL ngram/fulltext。
- 避免把 `%keyword%` 作为唯一查询路径；完全匹配和前缀匹配应命中索引。
- 目标数据集下搜索接口 P95 小于 300ms。
- 正常 autocomplete 不产生任何外部网络请求。
- 搜索日志不得记录用户密钥；关键词日志按运维需要采样，不保存交易敏感表单内容。
- mock/remote 两种模式的选择值和提交流程必须一致。

## 9. 验收标准

- 搜索“应流股份”“603308”“SH.603308”“ylgf”均能定位 `SH.603308`。
- 搜索“速腾聚创”“2498”能定位 `HK.02498`；搜索“Apple”“AAPL”能定位 `US.AAPL`。
- 同名证券同时展示市场，不自动替用户选择。
- 退市证券默认不出现，开启筛选后可查且状态明显。
- provider 不可达时，本地目录搜索仍成功。
- 快速连续输入时，旧请求响应不能覆盖新关键词结果。
- 选择证券后自动填充；再次编辑后旧选择失效。
- 输入搜索不触发报价、K 线同步或任务创建。

## 10. 不在本阶段

- 全市场实时行情扫描。
- 自动推荐或预测用户想买的证券。
- ETF、指数、期权、期货、权证和 OTC 的完整业务支持；目录可保存其类型，但 MVP 表单只允许普通股。
- 用大模型替代确定性证券标识匹配。
- 自动创建自选股、采集计划或交易记录。
- 自动交易、券商账户或订单能力。

## 11. 关联文档

- `docs/decisions/ADR-0009-local-first-security-directory.md`
- `docs/development/SECURITY_DIRECTORY_SEARCH_IMPLEMENTATION_PLAN.md`
- `docs/development/SECURITY_DIRECTORY_SEARCH_EXPERT_REVIEW.md`
- `docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`
- `docs/features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`
- `docs/api/MARKET_DATA_API.md`

## 12. 外部资料基线

- Longbridge Security List：支持按市场列出可用证券，但官方提示名单会随 eligibility 变化，适合作为目录发现来源而非永久权威快照：<https://open.longbridge.com/docs/cli/market-data/security-list>
- Longbridge Static Info：用于已知证券代码的基础信息补全：<https://open.longbridge.com/docs/quote/pull/static>
- Tushare `stock_basic`：官方建议基础信息拉取后保存在本地使用，可作为 A 股可选目录来源；权限、额度和许可需部署前确认：<https://tushare.pro/document/1?doc_id=25>
