# P1.5a 市场板块目录交接

## Task

- Goal: 市场行业排行/层级发现与自定义分组拆分，ETF 衔接现有行情采集。
- Repo: 后端与文档 `quant-trading-assistant`；前端 `quant-trading-assistant-web`。
- Started from commit: 后端 `cede098`，前端 `13baa33`；两仓库开始时已有大量未提交行情发动机/P1.4a 改动。

## Changes Made

- 后端新增 `MarketSectorProvider`、LongPort/Disabled 实现、行业排行/层级 API 和测试。
- LongPort 反射 client 支持 Fundamental 行业 DTO 映射，并将 native/Java 不兼容转换为明确业务错误。
- `CN + 512480` 可识别为 `SH.512480`。
- 前端板块页拆为市场板块与自定义分组；mock 数据显式标 `LOCAL_DEMO`。
- API、Mock、产品、架构、建设清单、建设看板、开发与验收日志均已同步。
- 无 migration、无新表、无行业数据落库。

## Verification

- `./mvnw test`: 280 tests，0 failure/error。
- 前端 `typecheck`、`lint`、`test`（36 files / 273 tests）、`build`: 全部通过。
- `Docker/curl/真实 LongPort/浏览器`: 按用户要求未执行。

## Decisions

- P1.5a 只做行业发现，不把临时 provider 结果写进自定义分组表。
- P1.5b 才做板块主数据、排行快照、成分关系、关注和低频刷新。
- 当前官方 SDK jar 的 Java 类含行业方法，但其 macOS/Linux native 缺 JNI 符号；不得声称真实行业接口已通过。

## Remaining Work

1. 升级或从同版本源码重建匹配的 LongPort SDK native，执行 CN/HK/US 最小真实行业排行/层级验收。
2. 设计并迁移 provider 板块、排行快照、成分有效期和用户关注表。
3. 增加低频刷新任务、板块宽度/成交额/相对强弱统计；资金流字段必须有来源和口径。

## Resume Prompt

```text
先读 docs/ai/HANDOFF_2026-07-17_market_sector_catalog.md 和 docs/features/MARKET_SECTOR_CATALOG_DESIGN.md，不读取全量 docs。
目标：执行 P1.5b，先解决 LongPort 行业 JNI 合约并做最小真实验收，再设计落库；若真实接口仍不可用，不得伪造通过或写入空快照。
```
