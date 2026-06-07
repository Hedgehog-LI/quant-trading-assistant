# Strategy Plugin Design

## 设计目标

策略框架要做到：

- 同一个策略既能用于历史回测，也能用于生成今日信号。
- 策略规则可以配置化。
- 指标、信号、风控解耦。
- v0.1 不追求动态 jar 插件，先用 Spring Bean 插件模式。

## 核心接口草案

```java
public interface Strategy {
    String code();

    StrategySignal generate(StrategyContext context);
}
```

```java
public interface IndicatorService {
    TechnicalIndicator calculateDaily(String symbol, LocalDate tradeDate);
}
```

```java
public interface RiskManager {
    RiskDecision check(SignalCandidate signal, AccountSnapshot account);
}
```

```java
public interface PositionSizer {
    PositionSize calculate(SignalCandidate signal, RiskDecision riskDecision, AccountSnapshot account);
}
```

```java
public interface BacktestEngine {
    BacktestResult run(BacktestTask task);
}
```

## 策略上下文

```java
public class StrategyContext {
    private String symbol;
    private LocalDate tradeDate;
    private List<DailyBar> bars;
    private TechnicalIndicator indicator;
    private MarketEnvironment marketEnvironment;
    private StrategyConfig config;
}
```

## 信号表达

信号不要只保存 `BUY` / `SELL`，还要保存原因：

```java
public class StrategySignal {
    private String symbol;
    private LocalDate tradeDate;
    private String strategyCode;
    private SignalType signalType; // BUY, SELL, HOLD
    private BigDecimal strength;   // 0-100
    private String reason;
    private RiskLevel riskLevel;
    private String riskNote;
}
```

## 配置示例

```json
{
  "strategyCode": "ma_volume_trend_v1",
  "strategyName": "均线趋势成交量策略",
  "parameters": {
    "shortMa": 5,
    "middleMa": 20,
    "longMa": 60,
    "volumeMultiplier": 1.5,
    "maxSinglePositionRatio": 0.2,
    "stopLossRatio": 0.05
  }
}
```

## 示例策略：均线趋势 + 成交量

规则：

- `close > ma20`
- `ma5 > ma10`
- `ma20 > ma60`
- `volume > volume_ma20 * 1.5`
- 市场环境不为 bearish
- 单票仓位不超过上限

伪代码：

```java
if (close > ma20
        && ma5 > ma10
        && ma20 > ma60
        && volume > volumeMa20 * volumeMultiplier
        && marketTrend != BEARISH) {
    return BUY;
}

if (close < ma20 || ma5 < ma10) {
    return SELL;
}

return HOLD;
```

## 新增策略流程

1. 新建一个类实现 `Strategy`。
2. 提供唯一 `strategyCode`。
3. 从 `StrategyContext` 读取 K 线、指标和配置。
4. 输出 `SignalCandidate`。
5. 交给 `RiskManager` 过滤。
6. 写入 `strategy_signal`。
7. 在回测引擎中复用同一策略。

## v0.1 不做的复杂能力

- 不做运行时加载外部 jar。
- 不做复杂 DSL。
- 不做机器学习模型编排。
- 不做自动实盘 order routing。
