# Backend Today MVP Implementation Manual

本文是给 Claude Code 执行后端开发用的手册。目标不是一次做完整量化平台，而是先把周一可用的 6 个实用工具对应的后端 API、数据模型和分层架子设计清楚。

Claude Code 实现前必须先阅读：

1. `AGENTS.md`
2. `CLAUDE.md`
3. `docs/ARCHITECTURE.md`
4. `docs/DATABASE_DESIGN.md`
5. `docs/RISK_RULES.md`
6. 本文件

## 1. 边界

本阶段只做交易辅助系统后端，不做任何自动交易能力。

禁止实现：

- 自动下单。
- 券商账户连接。
- 真实券商 API Key、交易密码、资金账号读取或保存。
- “必涨”“稳赚”“无风险收益”等确定性交易结论。
- 后端根据计算结果直接触发真实交易动作。

所有交易相关输出必须表达为：

```text
辅助记录 / 辅助计算 / 风险提示 / 人工确认
```

## 2. 本阶段 6 个功能

今天围绕这 6 个功能设计后端：

1. `Dashboard` 今日工作台。
2. `Watchlist` 自选股。
3. `Trade Plan` 盘前计划。
4. `Risk Calculator` 风控计算器。
5. `Trade Journal` 交易记录。
6. `Review` 盘后复盘。

后端第一版要支持前端从 localStorage 平滑切到 REST API。不要急着做日 K、指标、信号、复杂回测。

## 3. 推荐分层

遵循 Spring Boot 单体分层，不做微服务，不做复杂插件框架。

```text
com.quant.trade
├── common
│   ├── api              # ApiResponse, PageResponse, ResultCode
│   ├── exception        # BusinessException, GlobalExceptionHandler
│   ├── validation       # 通用校验工具或注解
│   └── enums            # 通用枚举
├── web
│   ├── dashboard        # Controller + Request/Response DTO
│   ├── watchlist
│   ├── tradeplan
│   ├── risk
│   ├── journal
│   └── review
├── dashboard            # DashboardService, aggregation model
├── watchlist            # WatchlistService, business rules
├── tradeplan            # TradePlanService, plan rules
├── risk                 # RiskCalculatorService, risk domain logic
├── journal              # TradeJournalService
├── review               # ReviewService
└── storage
    ├── entity           # JPA Entity
    └── repository       # Spring Data JPA Repository
```

分层职责：

| 层 | 职责 | 禁止事项 |
| --- | --- | --- |
| `web` | 参数校验、REST 路由、请求响应 DTO 转换 | 不写业务规则，不直接拼 SQL |
| `service` | 编排业务流程、事务边界、调用 repository | 不暴露 Entity 给 Controller |
| `risk` domain | 计算仓位、亏损金额、风险等级、阻断原因 | 不输出确定性买卖建议 |
| `storage` | Entity、Repository、数据库访问 | 不写 Controller 逻辑 |
| `common` | 统一响应、异常、枚举、工具 | 不依赖具体业务模块 |

## 4. Java 编码规范

参考 Alibaba Java 编码规约的风格，落地到本项目时按以下规则执行：

- 类名使用 UpperCamelCase，例如 `WatchlistService`。
- 方法名、变量名使用 lowerCamelCase。
- 常量使用 UPPER_UNDERSCORE_CASE。
- 表名和字段名使用 snake_case。
- Controller 不返回 JPA Entity。
- Request/Response DTO 命名清晰，例如 `CreateWatchlistRequest`、`WatchlistResponse`。
- 金额、价格、比例使用 `BigDecimal`，不要使用 `double`/`float`。
- 交易日期使用 `LocalDate`，创建更新时间使用 `LocalDateTime`。
- Service 层负责 `@Transactional`。
- 参数校验优先使用 Spring Validation 注解。
- 不吞异常。业务错误抛 `BusinessException`，由全局异常处理转成统一响应。
- 不使用魔法值。交易方向、交易风格、计划状态、风险等级等必须定义枚举。
- 测试中不要依赖真实 MySQL，可继续使用 H2 测试环境。

DTO 是否使用 Java `record` 由 Claude 根据项目现有风格决定。如果项目没有明确风格，Request/Response DTO 可以用 `record`，JPA Entity 不允许用 `record`。

## 5. 统一响应与错误

后端 REST API 建议统一返回：

```json
{
  "success": true,
  "code": "OK",
  "message": null,
  "data": {},
  "timestamp": "2026-06-07T20:00:00"
}
```

错误响应示例：

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "stopLossPrice must be lower than buyPrice",
  "data": null,
  "timestamp": "2026-06-07T20:00:00"
}
```

建议错误码：

| code | 场景 |
| --- | --- |
| `OK` | 成功 |
| `VALIDATION_ERROR` | 参数校验失败 |
| `RESOURCE_NOT_FOUND` | 资源不存在 |
| `DUPLICATE_RESOURCE` | 唯一键冲突 |
| `BUSINESS_RULE_VIOLATION` | 违反业务规则 |
| `INTERNAL_ERROR` | 未预期错误 |

## 6. 数据库表

新增 Flyway migration：

```text
src/main/resources/db/migration/V2__create_today_mvp_tables.sql
```

如果当前仓库已经存在 V2，则使用下一个版本号，不要覆盖旧 migration。

### 6.1 watchlist

用途：自选股和关注理由。

字段：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint auto_increment | 主键 |
| `symbol` | varchar(32) not null | 股票代码 |
| `name` | varchar(128) not null | 股票名称 |
| `market` | varchar(32) | 市场，例如 A_SHARE/HK/US |
| `group_name` | varchar(64) | 分组 |
| `watch_reason` | varchar(1024) | 关注理由 |
| `trade_style` | varchar(32) | SHORT_TERM/DO_T/SWING/OBSERVE |
| `attention_level` | varchar(32) | HIGH/MEDIUM/LOW |
| `support_price` | decimal(20,6) | 支撑位 |
| `resistance_price` | decimal(20,6) | 压力位 |
| `stop_loss_price` | decimal(20,6) | 默认止损位 |
| `risk_note` | varchar(1024) | 风险备注 |
| `enabled` | boolean not null default true | 是否启用 |
| `created_at` | datetime not null | 创建时间 |
| `updated_at` | datetime not null | 更新时间 |

索引：

- `uk_watchlist_symbol` unique (`symbol`)
- `idx_watchlist_enabled` (`enabled`)
- `idx_watchlist_trade_style` (`trade_style`)

### 6.2 trade_plan

用途：盘前计划和是否允许交易。

字段：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint auto_increment | 主键 |
| `plan_date` | date not null | 计划日期 |
| `symbol` | varchar(32) not null | 股票代码 |
| `name` | varchar(128) | 股票名称 |
| `plan_status` | varchar(32) not null | DRAFT/ACTIVE/DONE/CANCELLED |
| `buy_condition` | varchar(1024) | 买入条件 |
| `sell_condition` | varchar(1024) | 卖出条件 |
| `stop_loss_price` | decimal(20,6) | 止损价 |
| `take_profit_price` | decimal(20,6) | 止盈价 |
| `planned_position_ratio` | decimal(10,6) | 计划仓位比例，0 到 1 |
| `max_loss_amount` | decimal(20,6) | 本计划最大可承受亏损 |
| `allowed_to_trade` | boolean not null default false | 今日是否允许交易 |
| `risk_note` | varchar(1024) | 风险备注 |
| `notes` | varchar(2048) | 备注 |
| `created_at` | datetime not null | 创建时间 |
| `updated_at` | datetime not null | 更新时间 |

索引：

- `uk_trade_plan_symbol_date` unique (`symbol`, `plan_date`)
- `idx_trade_plan_date` (`plan_date`)
- `idx_trade_plan_status` (`plan_status`)

### 6.3 trade_journal

用途：手工记录真实或模拟交易。不得自动读取券商成交。

字段：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint auto_increment | 主键 |
| `trade_date` | date not null | 交易日期 |
| `trade_time` | datetime | 交易时间 |
| `symbol` | varchar(32) not null | 股票代码 |
| `name` | varchar(128) | 股票名称 |
| `side` | varchar(16) not null | BUY/SELL |
| `price` | decimal(20,6) not null | 成交价 |
| `quantity` | bigint not null | 数量 |
| `amount` | decimal(20,6) | 成交金额 |
| `position_ratio` | decimal(10,6) | 仓位比例，0 到 1 |
| `plan_id` | bigint | 关联交易计划 |
| `reason` | varchar(2048) | 交易理由 |
| `plan_stop_loss` | decimal(20,6) | 计划止损 |
| `plan_take_profit` | decimal(20,6) | 计划止盈 |
| `followed_plan` | boolean | 是否按计划执行 |
| `emotion_tags` | varchar(512) | 情绪标签，API 层暴露为列表 |
| `mistake_tags` | varchar(512) | 错误标签，API 层暴露为列表 |
| `actual_result` | varchar(1024) | 实际结果 |
| `review_status` | varchar(32) not null | PENDING/REVIEWED |
| `created_at` | datetime not null | 创建时间 |
| `updated_at` | datetime not null | 更新时间 |

索引：

- `idx_trade_journal_symbol_date` (`symbol`, `trade_date`)
- `idx_trade_journal_date` (`trade_date`)
- `idx_trade_journal_review_status` (`review_status`)

### 6.4 review_note

用途：盘后复盘。

字段：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | bigint auto_increment | 主键 |
| `review_date` | date not null | 复盘日期 |
| `symbol` | varchar(32) | 股票代码，可为空表示每日总复盘 |
| `title` | varchar(128) not null | 标题 |
| `market_context` | varchar(2048) | 市场环境 |
| `plan_summary` | varchar(2048) | 原计划 |
| `action_summary` | varchar(2048) | 实际操作 |
| `right_things` | varchar(2048) | 做对了什么 |
| `wrong_things` | varchar(2048) | 做错了什么 |
| `rule_changes` | varchar(2048) | 规则修正 |
| `next_actions` | varchar(2048) | 下一步 |
| `linked_journal_ids` | varchar(512) | 关联交易记录 ID 列表，API 层暴露为列表 |
| `created_at` | datetime not null | 创建时间 |
| `updated_at` | datetime not null | 更新时间 |

索引：

- `idx_review_note_date` (`review_date`)
- `idx_review_note_symbol_date` (`symbol`, `review_date`)

### 6.5 risk_calculation_log 可选

第一版风控计算器可以只做纯计算 API，不落库。若实现保存计算历史，可增加 `risk_calculation_log`，但不要阻塞 MVP。

建议第一版不建该表，避免过早复杂化。

## 7. REST API

基础路径：

```text
/api/v1
```

### 7.1 Dashboard

```text
GET /api/v1/dashboard/today?date=2026-06-08
```

返回内容：

- 当前日期。
- 启用自选股数量。
- 今日有效交易计划数量。
- 今日交易记录数量。
- 待复盘交易数量。
- 今日复盘数量。
- 风险提醒摘要，例如无止损计划数量、超仓计划数量。
- 快捷列表：高关注自选股、今日计划、待复盘交易。

Dashboard 不建表，只聚合其他模块。

### 7.2 Watchlist

```text
GET    /api/v1/watchlist?enabled=true&keyword=宁德&tradeStyle=DO_T
POST   /api/v1/watchlist
GET    /api/v1/watchlist/{id}
PUT    /api/v1/watchlist/{id}
PATCH  /api/v1/watchlist/{id}/enabled
DELETE /api/v1/watchlist/{id}
```

删除建议第一版做软删除：`enabled=false`。如果实现 `DELETE`，内部也只停用，不物理删除。

新增请求关键校验：

- `symbol` 必填，最长 32。
- `name` 必填，最长 128。
- `supportPrice`、`resistancePrice`、`stopLossPrice` 大于 0。
- 如果同时填写支撑位、压力位，压力位应大于支撑位。

### 7.3 Trade Plan

```text
GET    /api/v1/trade-plans?date=2026-06-08&symbol=300750
POST   /api/v1/trade-plans
GET    /api/v1/trade-plans/{id}
PUT    /api/v1/trade-plans/{id}
PATCH  /api/v1/trade-plans/{id}/status
```

业务规则：

- 同一 `symbol + planDate` 只能有一条计划。
- `plannedPositionRatio` 必须在 0 到 1 之间。
- 如果 `allowedToTrade=true`，必须填写 `buyCondition` 和 `stopLossPrice`。
- 如果填写 `takeProfitPrice`，应大于 `stopLossPrice`。
- 计划不是交易建议，只是用户的盘前纪律记录。

### 7.4 Risk Calculator

```text
POST /api/v1/risk/calculations/position-size
```

请求字段：

| 字段 | 说明 |
| --- | --- |
| `totalCapital` | 总资金 |
| `riskPercent` | 单笔风险比例，例如 0.005 |
| `buyPrice` | 计划买入价 |
| `stopLossPrice` | 止损价 |
| `maxPositionRatio` | 单票最大仓位比例 |
| `lotSize` | 最小交易单位，A 股默认 100 |

核心公式：

```text
riskAmount = totalCapital * riskPercent
perShareRisk = buyPrice - stopLossPrice
riskBasedQuantity = floor(riskAmount / perShareRisk)
positionCapQuantity = floor(totalCapital * maxPositionRatio / buyPrice)
finalQuantity = min(riskBasedQuantity, positionCapQuantity)
finalQuantity = floor(finalQuantity / lotSize) * lotSize
estimatedLoss = finalQuantity * perShareRisk
positionAmount = finalQuantity * buyPrice
positionRatio = positionAmount / totalCapital
```

校验和提示：

- `stopLossPrice >= buyPrice` 时返回业务错误。
- `riskPercent <= 0` 或过大时返回校验错误。
- `finalQuantity <= 0` 时返回 `riskLevel=HIGH`，提示资金或止损距离不满足交易条件。
- 返回 `warnings`，例如无止损、仓位过高、每股风险过大。

返回必须包含：

- `riskAmount`
- `perShareRisk`
- `riskBasedQuantity`
- `positionCapQuantity`
- `finalQuantity`
- `estimatedLoss`
- `positionAmount`
- `positionRatio`
- `riskLevel`
- `warnings`
- `disclaimer`

### 7.5 Trade Journal

```text
GET    /api/v1/trade-journals?date=2026-06-08&symbol=300750&reviewStatus=PENDING
POST   /api/v1/trade-journals
GET    /api/v1/trade-journals/{id}
PUT    /api/v1/trade-journals/{id}
PATCH  /api/v1/trade-journals/{id}/review-status
```

业务规则：

- `side` 只能是 BUY 或 SELL。
- `price > 0`。
- `quantity > 0`。
- 如果 `followedPlan=false`，建议但不强制填写原因。
- 如果 `side=BUY` 且未填写 `planStopLoss`，返回 warning，不阻断保存。
- 交易记录是手工录入，不允许接券商自动同步。

### 7.6 Review

```text
GET    /api/v1/reviews?date=2026-06-08&symbol=300750
POST   /api/v1/reviews
GET    /api/v1/reviews/{id}
PUT    /api/v1/reviews/{id}
```

业务规则：

- `reviewDate` 必填。
- `title` 必填。
- 每日总复盘允许 `symbol` 为空。
- 个股复盘建议关联 `linkedJournalIds`，但第一版不强制。
- 创建 review 后，如果关联了 journal，可将对应交易记录标记为 `REVIEWED`。

## 8. 枚举

建议枚举：

```text
MarketType: A_SHARE, HK, US, ETF, OTHER
TradeStyle: SHORT_TERM, DO_T, SWING, OBSERVE
AttentionLevel: HIGH, MEDIUM, LOW
PlanStatus: DRAFT, ACTIVE, DONE, CANCELLED
TradeSide: BUY, SELL
ReviewStatus: PENDING, REVIEWED
RiskLevel: LOW, MEDIUM, HIGH
EmotionTag: CALM, FOMO, FEAR, REVENGE, HESITATION
MistakeTag: CHASE_HIGH, PANIC_SELL, NO_STOP_LOSS, OVERSIZED_POSITION, NO_PLAN, BROKE_RULE
```

API 可以先用字符串传输，Service 层转 enum 校验。

## 9. 测试要求

Claude Code 实现后必须至少补以下测试：

1. 应用上下文启动测试继续通过。
2. Repository 能保存和查询 `watchlist`、`trade_plan`、`trade_journal`、`review_note`。
3. Watchlist Service：
   - 新增成功。
   - 重复 symbol 报业务错误。
   - 停用成功。
4. TradePlan Service：
   - `allowedToTrade=true` 但无止损时报业务错误。
   - 同一 symbol/date 重复时报业务错误。
5. RiskCalculator Service：
   - 正常计算仓位。
   - 止损价不低于买入价时报错。
   - 交易单位向下取整。
6. TradeJournal Service：
   - 新增 BUY/SELL 成功。
   - 无止损 BUY 返回 warning 或保存时带风险提示。
7. Review Service：
   - 新增每日复盘。
   - 关联 journal 后标记 REVIEWED。

最后必须运行：

```bash
./mvnw test
```

## 10. 实施顺序

Claude Code 应按以下顺序实现，不要一上来做全部量化模块：

1. 新增 migration。
2. 新增 Entity 和 Repository。
3. 新增 common 统一响应、异常和枚举。
4. 实现 Watchlist。
5. 实现 TradePlan。
6. 实现 RiskCalculator。
7. 实现 TradeJournal。
8. 实现 Review。
9. 实现 Dashboard 聚合接口。
10. 补测试。
11. 运行 `./mvnw test`。
12. 更新 API 示例文档。

## 11. API 示例文档要求

实现后更新或新增：

```text
docs/API_TODAY_MVP.md
```

至少包含：

- 每个接口路径。
- 请求 JSON 示例。
- 响应 JSON 示例。
- 风控计算器公式说明。
- 明确说明系统只做辅助记录和复盘，不自动交易。

## 12. Claude Code 执行完成后的汇报格式

Claude Code 完成后，应输出：

```text
已实现：
- ...

修改文件：
- ...

验证：
- ./mvnw test 通过 / 失败原因

注意：
- 没有实现自动交易
- 没有接券商
- 没有保存真实密钥
```
