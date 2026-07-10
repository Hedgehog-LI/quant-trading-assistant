# ClaudeCode Prompt: LongPort 只读行情源 P1.1 前后端开发

你是 ClaudeCode，当前任务是在 Quant Trading Assistant 项目中开发 `P1.1 LongPort 只读行情源`。请以资深后端架构师、前端架构师、交易员产品经理、测试工程师组成的专家团方式工作；先进入 plan 模式做任务拆解，再按计划自主执行。用户不在乎 token 和时间消耗，需要完整闭环。若你当前运行在 `claude --dangerously-skip-permissions`，请不要反复询问常规文件读写、测试、构建权限；但涉及真实密钥、远程部署、删除数据、下单/交易/账户类能力时必须停止并说明风险。

## 0. 必读上下文

后端仓库：

```text
/Users/joker/code/quant-trading-assistant
```

前端仓库：

```text
/Users/joker/code/quant-trading-assistant-web
```

先读取：

```text
/Users/joker/code/quant-trading-assistant/AGENTS.md
/Users/joker/code/quant-trading-assistant/docs/AI_DEVELOPMENT_INDEX.md
/Users/joker/code/quant-trading-assistant/docs/AI_HANDOFF.md
/Users/joker/code/quant-trading-assistant/docs/PRODUCT_BLUEPRINT.md
/Users/joker/code/quant-trading-assistant/docs/BUILD_CHECKLIST.md
/Users/joker/code/quant-trading-assistant/docs/CURRENT_ARCHITECTURE_AND_MODULES.md
/Users/joker/code/quant-trading-assistant/docs/DATABASE_DESIGN.md
/Users/joker/code/quant-trading-assistant/docs/features/MARKET_DATA_FOUNDATION_DESIGN.md
/Users/joker/code/quant-trading-assistant/docs/features/LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md
/Users/joker/code/quant-trading-assistant/docs/features/MARKET_ALERT_RULES_DESIGN.md
/Users/joker/code/quant-trading-assistant/docs/api/MARKET_DATA_API.md
/Users/joker/code/quant-trading-assistant/docs/decisions/ADR-0008-longport-quote-only-provider.md
/Users/joker/code/quant-trading-assistant/docs/development/2026-07-10-longport-market-data-research.md
```

再检查现有代码事实：

```text
src/main/java/com/quant/trade/marketdata
src/main/resources/db/migration/V5__add_market_data_tables.sql
src/main/resources/db/migration/V6__add_fetched_at_to_daily_bar.sql
src/main/resources/mapper/Stock*.xml
/Users/joker/code/quant-trading-assistant-web/src/pages/market-data.tsx
/Users/joker/code/quant-trading-assistant-web/src/features/market-data
/Users/joker/code/quant-trading-assistant-web/src/features/build-status/api/buildStatusData.ts
```

## 1. 不可突破的安全边界

- LongPort 只能作为只读行情 provider。
- 禁止调用、封装、测试或暴露 LongPort 下单、改单、撤单、账户资金、真实持仓、订单、成交、保证金、最大可买数量等能力。
- 禁止在前端、DB、日志、文档样例、测试快照中保存真实 token/app secret。
- 未配置 LongPort 凭据时，后端必须能启动，provider status 返回 `configured=false`。
- 外部行情不得自动覆盖 `portfolio_price_snapshot` 手工当前价。
- 提醒只能表达观察/数据质量/风险，不得输出买入、卖出、必涨、稳赚等建议。

## 2. 本轮后端目标

在现有 `com.quant.trade.marketdata` 模块内增量开发，不另起平行体系。

必须实现：

1. Provider 抽象与 LongPort quote-only provider 边界。
   - `MarketDataProvider`
   - provider domain model
   - `LongPortSymbolMapper`：内部 `SH.600519` <-> LongPort `600519.SH`
   - `Disabled/Fake provider`：用于未配置和测试
   - 若能确认 LongPort Java SDK API，则接入 `io.github.longport:openapi-sdk:4.0.5`；若不能确认，不要猜 SDK 方法导致编译失败，先保留可编译的 provider facade，并在文档中标注真实 SDK 适配待验证。

2. 新增 Flyway migration（V7 或更高，不修改 V1-V6）：
   - `stock_quote_snapshot`
   - `market_data_sync_task`
   - `market_data_alert`
   - 字段和索引参考 `docs/features/LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md` 与 `docs/DATABASE_DESIGN.md`

3. 新增 MyBatis Mapper + XML：
   - quote snapshot upsert/query
   - sync task create/update/query
   - market alert create/query/resolve

4. 新增分层代码：
   - controller / service / manager / dao / model / dto / vo / convert
   - 常量进入 `marketdata.constant` 或 `common.constant`
   - 错误码进入 `ErrorCodeEnum`
   - DTO/VO 字段要有清晰命名和必要注释
   - 转换使用 MapStruct，避免手写重复 get/set 转换

5. API：
   - `GET /api/v1/market-data/providers/LONGPORT/status`
   - `POST /api/v1/market-data/providers/LONGPORT/health-check`
   - `POST /api/v1/market-data/quotes/latest`
   - `GET /api/v1/market-data/quote-snapshots`
   - `POST /api/v1/market-data/sync-tasks/daily-bars`
   - `GET /api/v1/market-data/sync-tasks`
   - `GET /api/v1/market-data/sync-tasks/{id}`
   - `GET /api/v1/market-data/alerts`
   - `PATCH /api/v1/market-data/alerts/{id}/resolve`

6. 规则：
   - 实时快照唯一键：`canonical_symbol + data_source + quote_time`
   - 日 K 同步复用 `stock_daily_bar`，`data_source=LONGPORT`
   - 同步任务要记录 inserted/updated/skipped/failed
   - 先实现同步执行即可，后续再做异步 scheduler
   - 数据质量异常生成 `market_data_alert`

7. 后端测试：
   - provider 未配置状态
   - symbol 映射
   - quote snapshot 幂等
   - daily bar sync 幂等
   - sync task 状态流转
   - alert 查询/resolve
   - 禁止出现 trade/order/account/position 相关 LongPort 调用

## 3. 本轮前端目标

在现有 `/market-data` 页面增量扩展，不另起独立菜单。

必须实现：

1. `marketDataApi.ts` 增加 mock/remote 双模式：
   - provider status
   - health check
   - latest quotes
   - quote snapshots
   - sync tasks
   - market alerts
   - alert resolve

2. `/market-data` 页面改为 5 个 Tab：
   - 行情状态
   - 证券主数据
   - 最新价快照
   - 历史数据同步
   - 异常提醒

3. UI 要求：
   - 显示 LongPort 只读行情源提示
   - 显示“不构成投资建议”
   - mock 模式明确提示为浏览器模拟数据
   - 不提供 LongPort token 输入框
   - latest quote 刷新前提示“不覆盖手工当前价”
   - 任务部分展示状态、进度、inserted/updated/skipped/failed、错误摘要
   - 异常提醒支持 severity/resolved/symbol 筛选和 resolve

4. 建设看板必须同步：
   - 更新 `src/features/build-status/api/buildStatusData.ts`
   - 新增或更新节点：`longport-config-health`、`provider-symbol-mapping`、`longport-quote-snapshot`、`longport-history-sync`、`market-data-alerts`
   - `market-data-foundation` 显示 P1.0 已完成、P1.1 进行中
   - 增加/更新 build status 测试

5. 前端测试：
   - mock API 数据契约
   - 页面关键空态/错误/成功态
   - 建设看板节点存在并状态正确

## 4. 文档同步

开发完成后必须更新：

```text
docs/api/API_INDEX.md
docs/api/MARKET_DATA_API.md
docs/DATABASE_DESIGN.md
docs/CURRENT_ARCHITECTURE_AND_MODULES.md
docs/BUILD_CHECKLIST.md
docs/PRODUCT_BLUEPRINT.md
docs/mock/MOCK_REMOTE_CONTRACT.md
docs/FRONTEND_ARCHITECTURE.md
docs/development/DEVELOPMENT_LOG.md
docs/acceptance/ACCEPTANCE_LOG.md
docs/AI_HANDOFF.md
```

如果实现与设计有差异，必须写清楚原因，不要让文档停留在旧事实。

## 5. 验收命令

后端执行：

```bash
cd /Users/joker/code/quant-trading-assistant
./mvnw test
./mvnw package
```

前端执行：

```bash
cd /Users/joker/code/quant-trading-assistant-web
npm run typecheck
npm run lint
npm run test
npm run build
```

可选联调：

```bash
docker compose up -d --build
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/market-data/providers/LONGPORT/status
```

## 6. 最终汇报格式

请输出：

1. 开发了哪些功能。
2. 后端新增表、接口、测试。
3. 前端新增页面/Tab/mock/remote/建设看板节点。
4. 哪些文档已更新。
5. 验收命令结果。
6. 未完成项或需要用户提供的 LongPort 真实凭据/权限验证事项。
