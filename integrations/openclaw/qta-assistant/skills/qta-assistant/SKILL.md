---
name: qta-assistant
description: QTA 量化交易助手远程只读查询工具。通过此 Skill 可以查询系统健康、行情采集状态、持仓盈亏、今日待办和单证券行情。
---

# QTA 远程只读助手

## 何时使用

- 用户问"系统正常吗"→ `qta_system_health`
- 用户问"今天有什么待办"→ `qta_today_overview`
- 用户问"持仓怎么样"→ `qta_portfolio_summary`
- 用户问"行情采集到几点了"→ `qta_collection_overview`
- 用户问"哪些任务失败了"→ `qta_collection_failures`
- 用户问"有没有数据质量异常"→ `qta_data_quality_alerts`
- 用户问"今天板块涨跌"→ `qta_sector_ranking_summary`
- 用户问"某只股票数据"→ `qta_security_market_summary`

## 边界

- 所有工具均为只读查询，不能修改数据。
- 投资相关回复必须追加"不构成投资建议"。
- 工具返回的数据包含新鲜度和证据，需如实传达。
- 禁止根据查询结果生成买卖指令。
