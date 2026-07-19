# 行情采集执行引擎最终交付报告（2026-07-17）

## 1. 交付结论

行情采集执行引擎已完成代码、产品语义、自动化测试、Docker MySQL curl 联动和 A 股真实 LongPort 分钟 K 最小外联验收。

- `MINUTE_BAR_BACKFILL`：支持手工执行、多标的明细、分钟 K 幂等落库和水位更新。
- `INTRADAY_MINUTE_REFRESH`：支持 A 股交易日/交易时段扫描、DB claim 防重入和重启恢复；港股/美股自动盘中任务明确拒绝。
- 历史非法计划：保留原数据并标记“需要修正”，不静默执行、不批量篡改。
- LongPort：只使用 Quote SDK 4.3.3，不接账户、订单、交易或真实持仓。
- 浏览器验收：用户在最终阶段明确要求停止页面验收并改用 curl，因此本报告不宣称完成浏览器全流程；该项按用户最新指令跳过。

## 2. 自动化与静态门禁

实际执行命令：

```bash
cd /Users/joker/code/quant-trading-assistant
./mvnw test
./mvnw package
git diff --check

cd /Users/joker/code/quant-trading-assistant-web
npm run typecheck
npm run lint
npm run test
npm run build
git diff --check
```

结果：

| 仓库 | 门禁 | 结果 |
| --- | --- | --- |
| 后端 | test | 270 tests，0 failures，0 errors，0 skipped |
| 后端 | package | BUILD SUCCESS；再次执行 270 tests |
| 前端 | typecheck | 通过 |
| 前端 | lint | 通过 |
| 前端 | test | 34 files / 267 tests passed |
| 前端 | build | production build 通过 |
| 两仓库 | `git diff --check` | 通过，无空白错误 |

前端首次全量测试发现建设看板仍断言旧优先级“行情工作台”；产品现状已经进入“异动观察”，修正断言后全量通过。

## 3. Docker 与 MySQL curl 证据

Docker 由用户手动执行不清库重建：

```bash
QTA_MARKET_DATA_FAKE_ENABLED=true \
QTA_MARKET_DATA_FAKE_FAILURE_SYMBOL= \
QTA_LONGPORT_ENABLED=false \
QTA_MARKET_DATA_SCHEDULER_ENABLED=true \
docker compose up -d --build --force-recreate
```

健康与 migration：

```bash
curl --fail http://127.0.0.1:8080/actuator/health
docker compose ps
docker compose logs app
```

精简结果：

```json
{"status":"UP","groups":["liveness","readiness"]}
```

- `qta-mysql`：healthy。
- `qta-server`：持续运行。
- Flyway：成功校验 13 migrations，schema version=13，无待执行 migration。
- 启动及验收期间无 `ERROR` / `Exception`；存在 Flyway 对 MySQL 8.4 的版本支持提示，不影响启动。

### 3.1 创建并首次执行分钟补档

关键请求：

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"planName":"codex-post-rebuild-20260717-2115","taskType":"MINUTE_BAR_BACKFILL","provider":"FAKE","scopeJson":"{\"symbols\":[\"SH.603986\",\"SZ.003035\"],\"startDate\":\"2026-07-10\",\"endDate\":\"2026-07-10\"}","intervalType":"5M","adjustType":"NONE","triggerType":"MANUAL","includeAuction":false}' \
  http://127.0.0.1:8080/api/v1/market-data/sync-plans

curl -X POST http://127.0.0.1:8080/api/v1/market-data/sync-plans/11/run
curl http://127.0.0.1:8080/api/v1/market-data/sync-tasks/51
curl 'http://127.0.0.1:8080/api/v1/market-data/sync-tasks/51/items?page=1&size=10'
```

精简响应：

```json
{
  "planId": 11,
  "lastTaskId": 51,
  "taskStatus": "SUCCEEDED",
  "totalCount": 4,
  "successCount": 4,
  "failCount": 0,
  "insertedCount": 4,
  "updatedCount": 0,
  "skippedCount": 0,
  "items": [
    {"symbol":"SH.603986","status":"SUCCEEDED","rowCount":2,"insertedCount":2},
    {"symbol":"SZ.003035","status":"SUCCEEDED","rowCount":2,"insertedCount":2}
  ]
}
```

分钟 K 与水位查询：

```bash
curl 'http://127.0.0.1:8080/api/v1/market-data/minute-bars?canonicalSymbol=SH.603986&intervalType=5M&tradeDate=2026-07-10&page=1&size=20'
curl 'http://127.0.0.1:8080/api/v1/market-data/watermarks?canonicalSymbol=SH.603986&dataSource=FAKE&intervalType=5M&page=1&size=10'
```

精简响应：

```json
{
  "minuteBarTotal": 2,
  "barTimes": ["2026-07-10T10:00:00", "2026-07-10T10:05:00"],
  "qualityStatus": "VALID",
  "watermark": {"lastBarTime":"2026-07-10T10:05:00","totalRows":2}
}
```

### 3.2 相同范围幂等复跑

```bash
curl -X POST http://127.0.0.1:8080/api/v1/market-data/sync-plans/11/run
curl http://127.0.0.1:8080/api/v1/market-data/sync-tasks/52
curl -X POST http://127.0.0.1:8080/api/v1/market-data/sync-tasks/52/reconcile
```

精简响应：

```json
{
  "taskId": 52,
  "status": "SUCCEEDED",
  "totalCount": 4,
  "successCount": 4,
  "insertedCount": 0,
  "updatedCount": 0,
  "skippedCount": 4,
  "minuteBarTotalAfterRerun": 2,
  "watermarkTotalRowsAfterRerun": 2
}
```

重复 reconcile 后状态和六类计数不变，验证终态收敛幂等。

### 3.3 受控失败留痕

受控失败在重建前执行，重建后再次通过 API 查询持久化结果：

```bash
curl http://127.0.0.1:8080/api/v1/market-data/sync-tasks/48
curl 'http://127.0.0.1:8080/api/v1/market-data/sync-tasks/48/items?page=1&size=10'
```

精简响应：

```json
{
  "taskId": 48,
  "status": "PARTIAL_FAILED",
  "totalCount": 3,
  "successCount": 2,
  "failCount": 1,
  "insertedCount": 2,
  "items": [
    {"symbol":"SH.600036","status":"SUCCEEDED","insertedCount":2},
    {"symbol":"SZ.000858","status":"FAILED","errorCode":"MARKET_DATA_PROVIDER_TIMEOUT"}
  ]
}
```

### 3.4 配置语义与 scheduler

- 缺少 `startDate/endDate` 的分钟补档创建返回 `MARKET_DATA_PLAN_INVALID`，错误信息逐项指出缺失字段。
- 合法盘中计划返回 `automaticallyRunnable=true`、`manuallyRunnable=false`。
- 对盘中计划调用 `/run` 返回 `BUSINESS_RULE_VIOLATION`，说明只能由交易时段 scheduler 触发。
- 计划 12 于 21:16 创建，21:19 查询仍为 `lastRunAt=null / lastTaskId=null`，验证非交易时段不制造空任务。
- 自动化测试使用可注入 `Clock` 覆盖交易时段触发、非交易日、午休、收盘后、防重入和异常后继续扫描，不依赖真实等待。

## 4. 真实 LongPort 最小外联

使用本机 gitignored 安全配置和 runtime SDK 4.3.3；未打印、写库或提交任何凭据。

查询链路：

```bash
curl http://127.0.0.1:8080/api/v1/market-data/sync-tasks/50
curl 'http://127.0.0.1:8080/api/v1/market-data/sync-tasks/50/items?page=1&size=10'
curl 'http://127.0.0.1:8080/api/v1/market-data/minute-bars?canonicalSymbol=SH.601318&intervalType=5M&tradeDate=2026-07-10&page=1&size=100'
curl 'http://127.0.0.1:8080/api/v1/market-data/watermarks?canonicalSymbol=SH.601318&dataSource=LONGPORT&intervalType=5M&page=1&size=10'
```

精简结果：

```json
{
  "symbol": "SH.601318",
  "tradeDate": "2026-07-10",
  "intervalType": "5M",
  "taskId": 50,
  "status": "SUCCEEDED",
  "providerReturned": 49,
  "insertedCount": 48,
  "skippedCount": 1,
  "storedRows": 48,
  "lastStoredBarTime": "2026-07-10T14:55:00",
  "watermarkLastBarTime": "2026-07-10T14:55:00"
}
```

LongPort 返回的 15:00 边界 bar 按 A 股会话规则计入 skipped，不再落为 `SUSPECT`。数据库里早期 task 49 产生的历史 `SH.600519 15:00` SUSPECT 记录未擅自删除。

## 5. 主要改动文件

后端执行与校验：

- `src/main/java/com/quant/trade/marketdata/manager/SyncPlanValidationManager.java`
- `src/main/java/com/quant/trade/marketdata/service/MarketDataPlanExecutionService.java`
- `src/main/java/com/quant/trade/marketdata/service/MinuteBarIngestService.java`
- `src/main/java/com/quant/trade/marketdata/service/MarketDataIntradayScheduler.java`
- `src/main/java/com/quant/trade/marketdata/service/MarketDataWorkbenchService.java`
- `src/main/java/com/quant/trade/marketdata/manager/MinuteBarQualityManager.java`
- `src/main/java/com/quant/trade/marketdata/util/CanonicalSymbolUtils.java`

Provider 与配置：

- `MarketDataProvider.java`、`FakeMarketDataProvider.java`、`DisabledMarketDataProvider.java`
- `LongPortMarketDataProvider.java`、`LongPortQuoteClient.java`、`ReflectiveLongPortQuoteClient.java`
- `MarketDataConfig.java`、`MarketDataConstants.java`、`WorkbenchConstants.java`
- `.env.example`、`application.properties`、`application-test.properties`、`docker-compose.yml`

DB / MyBatis / API 模型：

- `src/main/resources/db/migration/V13__add_sync_plan_run_claim.sql`
- `MarketDataSyncPlanMapper.java/.xml`
- `MarketDataSyncTaskMapper.java/.xml`
- `MarketDataSyncTaskItemMapper.java/.xml`
- `MarketDataSyncPlanDO.java`、`UpdateSyncPlanDTO.java`、`MarketDataSyncPlanVO.java`
- `ErrorCodeEnum.java`

后端测试：

- `SyncPlanValidationManagerTest.java`
- `MarketDataIntradaySchedulerTest.java`
- `MinutePlanExecutionIntegrationTest.java`
- LongPort provider/client、Workbench、Quote、StockData 相关测试及测试 SDK `Period.java`

前端：

- `src/features/market-data/utils/syncPlanForm.ts`
- `src/features/market-data/utils/syncPlanForm.test.ts`
- `src/features/market-data/api/workbenchApi.ts`
- `src/pages/market-workspace.tsx`
- `src/shared/types/domain.ts`
- `src/features/build-status/api/buildStatusData.ts`
- `src/features/build-status/api/buildStatusData.test.ts`

文档：

- API：`docs/api/API_INDEX.md`、`docs/api/MARKET_DATA_API.md`
- DB/架构：`docs/DATABASE_DESIGN.md`、`docs/CURRENT_ARCHITECTURE_AND_MODULES.md`
- 产品/路线：`docs/BUILD_CHECKLIST.md`、`docs/PRODUCT_BLUEPRINT.md`、`docs/DEVELOPMENT_ROADMAP.md`
- 设计/契约：行情 foundation、workbench、LongPort provider 设计及 `docs/mock/MOCK_REMOTE_CONTRACT.md`
- 状态记录：`docs/AI_HANDOFF.md`、开发日志、验收日志、本轮轻量 handoff 和本报告。

工作区还包含证券目录设计等用户/其他任务的未提交文件。本轮没有 reset、checkout 或覆盖这些改动；提交时应按任务范围选择性 stage。

## 6. 未完成边界与后续风险

- 浏览器全流程验收按用户最新要求跳过；不将其伪装为已通过。
- 港股/美股分钟自动采集仍禁用，直到各市场交易日历、时区和交易时段闭环。
- 异动观察页面、量价衍生统计和多 provider 尚未实现，是后续阶段，不属于本轮。
- MySQL 8.4 启动时有 Flyway 版本支持提示；当前 migration 验证和运行正常，后续依赖升级时应复核兼容性。
- 验收计划 11/12、任务 51/52 保留在本地数据库，未擅自删除。
- 未 commit、未 push、未远程部署、未清理用户数据。

## 7. 关联记录

- `../acceptance/ACCEPTANCE_LOG.md`
- `DEVELOPMENT_LOG.md`
- `../ai/HANDOFF_2026-07-17_market_data_execution_engine.md`
- `../AI_HANDOFF.md`
