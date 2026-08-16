# QTA-V2-DATA-FOUNDATION-V21 运行时验证记录（2026-08-16，AC-08）

> 环境：Docker Compose（qta-mysql MySQL 8.4 + qta-server），`docker compose up -d --build` 全量重建；前端 Vite dev `remote` 模式 + proxy `http://127.0.0.1:8080`。所有输出为真实 curl/浏览器响应摘录。

## 1. 基础

- `GET /actuator/health` → `{"status":"UP","groups":["liveness","readiness"]}`
- Flyway 真库迁移：`SELECT MAX(CAST(version AS DECIMAL)) FROM flyway_schema_history WHERE success=1` → **24**（V24 mdf_* 10 表落 MySQL）。
- **运行时暴露并修复的真实缺陷**：`MdfDatasetVersionMapper.selectMaxVersionSeq` 使用 `CAST(... AS INT)`（H2 方言）在 MySQL 8 报 SQLSyntaxErrorException → 修复为 `CAST(... AS SIGNED)`（H2/MySQL 双兼容，先例 SyncScopeLockMapper 注释），重建容器后复验通过。修复已随 commit 提交，相关测试复跑全绿。

## 2. CSV 导入链路（无凭据通道）

| 步骤 | 请求 | 结果 |
| --- | --- | --- |
| 建数据集 | POST `/datasets`（CN_DAILY_IMPORT_FIXTURE，provider=IMPORT_CSV_DAILY，adjust=NONE） | id=1，success=true |
| 导入日历 | POST `/imports?kind=TRADING_CALENDAR`（3 交易日） | batch id=1，inserted=3 |
| 导入日 K（2026-07 窗口，MR-0 真实探针值换算：手×100/万元×10000） | POST `/imports?kind=DAILY_BAR` | batch id=2，inserted=3 |
| **幂等重放**（同文件再次导入） | 同上 | **返回同一批次 id=2、createdAt 不变**（kind+file_hash 幂等实证） |
| 导入 2021 干净窗口日历+日 K | 同上 | batch id=3/id=4，inserted 各 3 |

## 3. 质量门禁与发布（真实检出）

- 7 月版本 v1（IMPORT_CSV_DAILY，2026-07-01..03）：`POST /dataset-versions/1/quality-check` → 13 族结果中 **PROVIDER_ADJUST_MIXING FAIL（409 foreign rows，既有 TENCENT_PUBLIC PoC 数据同窗共存）+ UNIT_ANOMALY FAIL（1 行，MR-0 已知 LONGPORT SH.600519 手/股脏数据落入窗口）** → 版本 **REJECTED**；`POST .../publish` → 拒绝 `DATA_FOUNDATION_QUALITY_GATE_FAILED`（"版本状态为 REJECTED，只有 QUALIFIED 版本可发布"）。
- 2021 版本 v2（2021-01-04..06）：13 族 11 OK + 2 WARN（UNIVERSE_COVERAGE/池空）→ **QUALIFIED** → publish → **RELEASED**；`GET /datasets/{code}/released` 返回 v2（isCurrentReleased=true）；`GET /dataset-versions/2/coverage` → SH.600519 covered 3/3、coverageRatio=1.0。

## 4. 极小真实回补（TENCENT_PUBLIC 实验源真实外联）

- 建数据集 CN_DAILY_BAR（TENCENT_PUBLIC/NONE）→ POST `/backfill-tasks`（SH.600519 × 2026-07-01..03 × 1D，chunkSize=50）→ task id=1，planned=1，totalChunks=1。
- `POST /backfill-tasks/1/run` → **SUCCEEDED**（1/1/0/0，inserted=0/updated=3——MR-0 既有 TENCENT_PUBLIC 行被 ODKU 原值刷新，幂等语义实证；真实数据落 `stock_daily_bar(data_source=TENCENT_PUBLIC)`）。
- `GET /backfill-tasks/1/chunks` → chunk 0 SUCCEEDED，attempts=1。
- 终态重跑 → 拒绝（"回补任务不可执行（正在执行或状态不允许）"）。

## 5. 前端 remote 模式（浏览器，1440×900）

- `/data-foundation` 三 Tab 真实数据渲染：回补任务表（id=1 CN_DAILY_BAR SUCCEEDED 1/1/0/0、0/3）；数据集与版本（v2 RELEASED 发布按钮禁用、v1 REJECTED 发布按钮禁用、当前发布版本 v2、选中 v2 行显示覆盖率 100%/质量结果）；导入（批次 4 条：DAILY_BAR×2 + TRADING_CALENDAR×2，计数 3/0/0/0，错误报告 `--`）。
- 截图：`quant-trading-assistant-web/docs/development/screenshots/data-foundation-{backfill,versions-quality,imports}.png`。

## 6. 边界与未验证项（如实）

- 全市场/长窗口真实回补未执行（本轮仅证明执行能力，1 标的×3 日）；TENCENT_PUBLIC 容器外联在本环境可达且成功（NOT_VERIFIED→本窗口 RUNTIME 证据如上），但公共源稳定性不构成生产承诺（ADR-0015 EXPERIMENTAL）。
- TUSHARE/LONGBRIDGE 维持 NOT_VERIFIED/NOT_RETESTED（无凭据/外部鉴权故障），资金流正式表 BLOCKED。
- 服务器部署 NOT_DEPLOYED。
