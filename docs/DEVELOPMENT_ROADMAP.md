# Development Roadmap

## v0.1 目标

完成一个能在本机跑通的最小交易辅助闭环：

```text
自选股 -> 日 K 数据导入 -> 指标计算 -> 策略信号 -> 简单回测 -> 风险提示 -> 复盘记录
```

前端可以作为独立项目 `quant-trading-assistant-web` 并行开发。今天要跑雏形时，前端优先使用 mock/localStorage，先让自选股、交易记录和复盘可用，后续再逐步接后端 REST API。

## v0.1 功能清单

1. 自选股管理
   - 新增股票到 watchlist。
   - 查询自选股。
   - 标记关注理由、交易风格、风险备注。

2. 日 K 数据导入
   - 支持 CSV 导入。
   - 字段包含 open/high/low/close/volume/amount。
   - 同一股票同一交易日幂等写入。

3. 技术指标
   - MA5、MA10、MA20、MA60。
   - MACD。
   - RSI。
   - BOLL。
   - 成交量均线和量比。

4. 策略信号
   - 均线趋势信号。
   - 放量突破信号。
   - BOLL 均值回归信号。
   - 信号必须保存触发原因和指标快照。

5. 简化回测
   - 初期使用日线。
   - 支持手续费、滑点。
   - 支持 A 股 T+1 约束的简化模拟。
   - 输出收益率、最大回撤、胜率、盈亏比。

6. 风控
   - 单票最大仓位。
   - 单笔最大风险。
   - 固定止损和结构止损。
   - 连续亏损后的降仓/暂停提示。

7. 复盘
   - 记录交易计划。
   - 记录真实买卖点。
   - 记录错误原因和改进动作。

## v0.2 目标

- 接入 AKShare 或其他公开数据源。
- 支持分钟线数据。
- 支持板块和指数环境判断。
- 增加策略参数管理。
- 增加多策略对比报告。
- 增加前端页面。

## v1.0 目标

- 稳定的数据采集和调度。
- 策略插件化。
- 风险预算模型。
- 回测、复盘、报告一体化。
- 支持服务器 Docker 部署。
- 支持 Python 量化服务作为外部分析引擎。

## 推荐迭代顺序

### Iteration 1: 数据库和基础 API

- 建 migration：
  - `stock_basic`
  - `watchlist`
  - `stock_daily_bar`
  - `technical_indicator_daily`
  - `strategy_signal`
  - `trade_journal`
  - `risk_alert`
- 建基础 Entity/Repository。
- 建 watchlist REST API。
- 跑通 `./mvnw test`。

前端并行任务：

- 创建 Vite + React + TypeScript 项目。
- 实现 AppLayout、Dashboard、Watchlist、TradeJournal、Review。
- 使用 localStorage 保存记录。
- 后端 API 完成后再切换为 remote 模式。

### Iteration 2: CSV 导入和指标计算

- 实现日 K CSV 导入。
- 实现指标计算服务。
- 对单只股票生成指标。
- 指标结果写入数据库。

### Iteration 3: 信号和风控

- 实现 Strategy 接口。
- 实现均线策略。
- 实现 SignalGenerator。
- 实现 RiskManager。
- 生成信号并保存风险说明。

### Iteration 4: 简化回测

- 实现 BacktestEngine。
- 模拟买卖、手续费、滑点。
- 输出收益曲线和指标。

### Iteration 5: 复盘和报告

- 实现交易日志。
- 实现复盘记录。
- 输出 JSON 报告。

## 每次开发验收标准

- 代码能编译。
- `./mvnw test` 通过。
- 数据库迁移能在空库执行。
- REST API 有基本请求/响应示例。
- 涉及交易信号时必须有风险提示。
