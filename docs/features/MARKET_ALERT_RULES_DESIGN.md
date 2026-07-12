# Feature Design: 行情异常与量价观察规则

> 版本：v0.1.2 · 状态：基础数据质量提醒部分实现 / 量价观察规则待实现 · 关联：`LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md`、`MARKET_DATA_FOUNDATION_DESIGN.md`

## 1. 用户目标

行情接入后，系统需要帮助用户从自选股、持仓股和计划股中发现值得观察的异常变化，同时避免把观察提醒包装成买卖建议。

## 2. 设计原则

- 先做数据质量提醒，再做量价观察。
- 所有提醒必须可解释：触发规则、触发值、阈值、数据来源、数据时间。
- 所有提醒只进入工作台/行情页/复盘线索，不自动修改计划、交易、持仓或账本。
- 数据质量异常时，禁止生成策略信号，只生成数据质量提醒。
- 提醒文案统一使用“观察 / 风险 / 数据质量”，不用“买入 / 卖出 / 必涨 / 必跌”。

## 3. 第一阶段规则

| 规则 code | 名称 | 输入数据 | 建议阈值 | 等级 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `PROVIDER_NOT_CONFIGURED` | provider 未配置 | provider status | LongPort 未启用或无凭据 | WARN | 阻断真实行情刷新 |
| `PROVIDER_PERMISSION_DENIED` | 行情权限不足 | provider error | 权限/额度错误 | HIGH | 提示用户检查 LongPort 行情权限 |
| `QUOTE_STALE` | 行情时间过旧 | quote_time/fetched_at | A 股交易时段内超过 5 分钟 | WARN | 避免把延迟行情当实时 |
| `DAILY_BAR_EMPTY` | 日 K 返回为空 | sync task | 指定范围无数据 | WARN | 可能是停牌、非交易日、无权限或代码映射错 |
| `SYNC_PARTIAL_FAILED` | 同步部分失败 | sync task | failed_count > 0 | WARN | 展示错误摘要 |
| `PRICE_GAP` | 跳空异常 | open/pre_close | 绝对涨跌幅 >= 2% | WARN | 盘前/开盘纪律提醒 |
| `VOLUME_BREAKOUT` | 放量突破 | price + volume | 突破观察价且成交量 > 20 日均量 * 1.5 | INFO | 只提示观察趋势，不提示买入 |
| `VOLUME_BREAKDOWN` | 放量跌破 | price + volume | 跌破止损/昨日低点且成交量 > 20 日均量 * 1.5 | HIGH | 持仓风险优先 |
| `INTRADAY_REVERSAL` | 冲高回落 | high/last/pre_close/volume | 日内回撤超过涨幅 50% 且放量 | WARN | 防追高与做 T 复盘线索 |
| `LOW_VOLUME_REBOUND` | 缩量反弹 | close/pre_close/volume | 上涨但成交量 < 20 日均量 * 0.7 | INFO | 提醒反弹质量不足 |
| `HIGH_AMPLITUDE` | 高振幅 | high/low/pre_close | 振幅 >= 5% 或 > ATR20 * 1.5 | WARN | 提醒降低主观追单冲动 |

## 4. 交易员口径补充

盘中量比不应简单使用 `当前成交量 / 20日均量`。更合理的第一版公式：

```text
时间进度量比 = 当前累计成交量 / (20日均量 * 已过交易分钟 / 全天交易分钟)
```

原因：

- 上午直接和全天均量比会误判缩量。
- 尾盘直接和全天均量比会误判放量。
- A 股中午休市需要排除非交易分钟。

第一版若无法精确处理交易日历，可以先标记为“估算量比”，并在提醒详情中展示计算假设。

## 5. 数据模型要求

每条 `market_data_alert` 至少保存：

- `alert_type`
- `severity`
- `canonical_symbol`
- `provider`
- `quote_time` 或 `trade_date`
- `message`
- `trigger_value_json`
- `task_id`
- `resolved`
- `created_at`

`trigger_value_json` 示例：

```json
{
  "lastPrice": "18.520000",
  "referencePrice": "19.000000",
  "changePct": "-0.025263",
  "volume": 12000000,
  "volumeMa20": 7000000,
  "threshold": "volume > ma20 * 1.5"
}
```

## 6. 前端展示

- 行情页「异常提醒」Tab：按 severity、symbol、resolved 筛选。
- 工作台后续可聚合 HIGH/WARN 未处理提醒，入口跳 `/market-data`。
- 提醒详情必须展示数据来源和时间，避免用户误以为是实时交易指令。

## 7. 不做事项

- 不做自动买卖。
- 不做高频盘口策略。
- 不做全市场扫描。
- 不基于单条提醒直接生成“交易建议”。
- 不在数据质量异常时继续生成量价观察。

## 8. 验收标准

- [ ] 数据质量异常可单独触发，并阻断策略类提醒。
- [ ] 量价观察提醒有触发值、阈值、来源和时间。
- [ ] 提醒可标记 resolved，不物理删除。
- [ ] mock 模式可构造每类主要提醒用于页面验收。
- [ ] 页面文案含“不构成投资建议”。
