# Task Contract: MARKET-DATA-ASSET-INGESTION-LOOP-P19D 行情采集与资产查看闭环

## Contract Identity

- Status: `FROZEN`
- Contract version: `1.0`
- Frozen at: `2026-08-13`
- Lane: `L2`
- Product/architecture owner: Codex

## Objective

修复“采集计划已建立或行情已入库，但行情数据详情无法发现资产、固定快捷入口反而返回 404”的断链，交付以下最小闭环：

```text
精确代码验证 -> 创建采集计划 -> 幂等登记证券主数据 -> 执行采集并落 K 线
-> 已入库资产目录 -> 数据详情 -> 数据不足时回到行情工作台补充采集
```

## Current Facts

- `/api/v1/market-data/assets/{symbol}/availability` 以 `stock_basic` 为证券入口；证券未登记时返回 `STOCK_NOT_FOUND`。
- 云端 `stock_basic` 为空时，现有前端仍展示贵州茅台等固定快捷入口，因此会稳定制造误导性的 404。
- 日 K 与分钟 K 已分别落在 `stock_daily_bar`、`stock_minute_bar`，无需新增事实表。
- 行情数据详情是数据资产检查与追溯页，不承担市场发现和选股职责。

## Scope

### In Scope

- 创建或修改个股采集计划时，根据冻结并校验后的 canonical symbols 幂等补齐最小 `stock_basic` 记录。
- 新增只读、分页的“已入库行情资产目录”API，只返回至少存在一条日 K 或分钟 K 的证券。
- 行情数据详情首页展示真实已入库资产，支持关键词和市场筛选。
- 区分“证券未登记”“证券已登记但无 K 线”“范围无记录”“系统错误”。
- 资产页与行情工作台双向导航；删除远程模式固定真实证券快捷入口和多余演示水印/免责声明。
- 补充聚焦测试、API 文档、开发日志、验收记录和能力矩阵状态。

### Out Of Scope

- 不新增 provider、交易日历、行情表或 migration。
- 不在本任务持久化证券验证结果的全部元数据；自动登记只保证 canonical symbol 可追溯，名称可由证券目录后续补全。
- 不实现板块轮动、候选选股、策略信号、MACD、回测或 IM 提醒。
- 不修改现有 K 线计算、覆盖率和采集调度语义。

### Prohibited

- 远程接口失败后回退 mock 或合成真实上市公司行情。
- 为没有 K 线的证券伪造资产目录记录。
- 把 `STOCK_NOT_FOUND` 继续显示为 Axios 原始错误文本。
- 创建新表、写 provider 旁路、自动交易或接入券商账户。

## Acceptance Criteria

| AC-ID | Observable behavior | Expected result | Evidence |
|---|---|---|---|
| AC-01 | 创建/修改含合法 symbol 的采集计划 | `stock_basic` 缺失时补最小记录，已存在时不覆盖名称等元数据 | backend unit/integration tests |
| AC-02 | 查询资产目录 | 只返回存在日 K 或分钟 K 的证券，分页/关键词/市场筛选稳定 | backend controller + H2 tests |
| AC-03 | 远程模式未选择证券 | 不展示固定真实证券；展示真实资产目录或可操作空态 | frontend tests |
| AC-04 | 访问未登记证券 | 显示“尚未建立行情资产”，可回资产列表或去行情工作台，不泄漏 Axios 404 文本 | frontend tests |
| AC-05 | 证券已登记但没有 bars | 显示“尚未采集”，不视为系统错误，可去创建计划 | backend/frontend tests |
| AC-06 | 已入库证券进入详情 | availability、series、质量与相关任务沿用已验收口径 | regression tests/build |
| AC-07 | 文档与看板 | API、设计、开发日志、验收记录、能力矩阵反映实际完成度 | static review |

## Verification Plan

1. 后端聚焦测试：计划登记、资产目录过滤/分页、404 与空组合语义。
2. 前端聚焦测试：目录列表、空目录、未登记、已登记无数据、详情回归。
3. 后端：`./mvnw test`、`./mvnw package -DskipTests`。
4. 前端：`npm run typecheck`、`npm run lint`、`npm run test -- --run`、`npm run build`。
5. 静态：两仓 `git diff --check`，确认无固定真实证券 mock 入口、无 provider/DB migration 越界。

## Stop Conditions

- 同一失败指纹修复两轮仍不通过时记录证据并停止扩张范围。
- 外部 LongPort、服务器或真实凭据不作为本轮自动化前置条件。
- 本轮实现者只能报告自检结果，不以文档代替测试结果。
