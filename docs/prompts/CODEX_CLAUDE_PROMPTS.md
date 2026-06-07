# Codex / Claude Code Prompts

这些提示词可以直接复制到新的 Codex 或 Claude Code 对话中。

## 0. 接手项目

```text
你现在接手 quant-trading-assistant 项目。

请先阅读：
- AGENTS.md
- CLAUDE.md
- docs/AI_HANDOFF.md
- docs/ARCHITECTURE.md
- docs/DEVELOPMENT_ROADMAP.md

然后总结：
1. 当前项目定位；
2. 当前技术栈；
3. 当前禁止事项；
4. 下一步最应该开发什么；
5. 你准备修改哪些文件。

不要连接券商账户，不要处理任何真实 API Key，不要实现自动下单。
```

## 1. 开发第一版数据库表

```text
请基于 docs/DATABASE_DESIGN.md，为 quant-trading-assistant 开发 v0.1 核心数据库 migration。

要求：
1. 使用 Flyway，新建 V2__create_core_tables.sql；
2. 至少包含 stock_basic、watchlist、stock_daily_bar、technical_indicator_daily、strategy_signal、trade_journal、risk_alert、review_note；
3. 字段类型适合 MySQL 8.4；
4. 添加必要唯一索引和普通索引；
5. 不要删除已有 V1；
6. 实现后运行 ./mvnw test。
```

## 2. 开发自选股模块

```text
请为 quant-trading-assistant 开发 watchlist 自选股模块。

要求：
1. 新建 entity、repository、service、controller；
2. 支持新增、查询列表、启用/停用、更新关注理由；
3. 使用 Spring Validation；
4. REST API 返回清晰 JSON；
5. 不引入自动交易能力；
6. 添加基础测试；
7. 运行 ./mvnw test。
```

## 3. 开发日 K CSV 导入

```text
请开发日 K CSV 导入功能。

要求：
1. 支持导入字段：symbol, tradeDate, open, high, low, close, preClose, volume, amount, turnoverRate；
2. 同一 symbol + tradeDate 幂等更新；
3. 校验价格和成交量不能为负；
4. 写入 stock_daily_bar；
5. 提供 REST API 或 service 测试入口；
6. 添加测试；
7. 运行 ./mvnw test。
```

## 4. 开发技术指标模块

```text
请开发 indicator 模块，先实现日线 MA、MACD、RSI、BOLL。

要求：
1. 读取 stock_daily_bar；
2. 计算 MA5、MA10、MA20、MA60；
3. 计算 MACD DIF/DEA/HIST；
4. 计算 RSI6/RSI12；
5. 计算 BOLL MID/UPPER/LOWER；
6. 写入 technical_indicator_daily；
7. 注意缺少足够历史数据时的处理；
8. 添加单元测试；
9. 运行 ./mvnw test。
```

## 5. 开发第一个策略

```text
请开发第一个策略：均线趋势 + 成交量过滤。

规则：
- close > ma20
- ma5 > ma10
- ma20 > ma60
- volume > volume_ma20 * 1.5
- 如果 close < ma20 或 ma5 < ma10，生成 SELL；
- 其他情况 HOLD。

要求：
1. 参考 docs/STRATEGY_PLUGIN_DESIGN.md；
2. 新建 Strategy 接口和 MaVolumeTrendStrategy；
3. 信号必须包含 reason、riskLevel、riskNote；
4. 信号写入 strategy_signal；
5. 不输出确定性赚钱话术；
6. 添加测试；
7. 运行 ./mvnw test。
```

## 6. 开发简化回测引擎

```text
请基于 docs/BACKTEST_ENGINE_DESIGN.md 开发 v0.1 简化回测引擎。

要求：
1. 使用日线数据；
2. 支持初始资金、手续费率、滑点率；
3. 支持 BUY/SELL/HOLD 信号；
4. 模拟持仓、现金、权益曲线；
5. 输出 totalReturn、maxDrawdown、winRate、profitLossRatio、tradeCount；
6. 第一版可以只支持单只股票；
7. 明确记录 T+1 和涨跌停暂未完整模拟或已如何模拟；
8. 添加测试；
9. 运行 ./mvnw test。
```

## 7. 开发复盘模块

```text
请开发 review/trade_journal 模块。

要求：
1. 支持记录交易计划、真实买卖、交易理由、止损止盈、结果；
2. 支持盘后复盘 note；
3. 支持按 symbol 和日期查询；
4. 输出字段要适合短线做 T 复盘；
5. 不连接券商，不自动读取真实成交；
6. 添加测试；
7. 运行 ./mvnw test。
```

## 8. 代码审查提示词

```text
请以资深 Java 后端工程师 + 量化系统架构师的身份审查当前 quant-trading-assistant 代码。

重点检查：
1. 是否违反“不自动交易、不保存真实密钥”的边界；
2. 数据库表设计是否支持回测和复盘；
3. 是否存在未来函数或回测失真风险；
4. 策略、风控、回测是否解耦；
5. Java 分层是否清晰；
6. 是否有必要测试；
7. 哪些地方应保持简单，不要过度设计。

请按严重程度输出问题和建议。
```
