# QTA-V2-DATA-FOUNDATION-V21 修复收口 R1 运行时验证（2026-08-16，AC 对应 Repair §九）

> 环境：Docker Compose 重建（qta-mysql + qta-server 含 R1 全部改动）。全部为真实 curl 响应摘录。实施状态 SELF_CHECKED（待 Codex 独立验收）。

## 1. 部署与基线

- `GET /actuator/health` → `{"status":"UP"}`。
- Flyway 真库迁移：`MAX(version)=25`（V25 mdf_backfill_task_symbol / mdf_dataset_version_manifest / 版本血缘列 / 导入批次版本关联 / queued_at 全部落 MySQL）。
- **默认数据集幂等初始化（Repair §八.1）**：容器启动后 `mdf_dataset` 自动含 `CN_DAILY_BAR`（无人工 curl）。
- 架构门禁：`node scripts/check-ai-architecture.mjs --base main --architecture-review-count 2` → **errors=0**（计数=G1 审查 + 本次 R1 主会话边界自检，如实记录；候选 4070 行超 3000 阈值触发双计数要求）。修复过程消除了 VersionLineageService 的 file-protocol 误聚合。

## 2. 二维分片 + 真实小回补（跨两个日期窗口）

- 创建任务 id=2：SH.600519 × 2021-01-01..2022-12-31（腾讯安全窗 365 天 → **totalChunks=2**）。
- `POST /backfill-tasks/2/run` → **0.106s 返回 QUEUED**（异步，不等待执行）。
- worker 自动认领执行 → 轮询 `GET /backfill-tasks/2` → **SUCCEEDED**：chunk0=2021-01-01..2021-12-31（243 行）、chunk1=2022-01-01..2022-12-31（242 行），**inserted=485=两窗完整交易日数（无 640 截断）**；chunk.start/end=实际请求区间。
- 版本血缘：`mdf_dataset_version_manifest(dataset_version_id=4)` = **485 行**（回补事实全部入 manifest，row_hash 由 Java 冻结公式计算）。

## 3. 暂停/继续（Repair §九）

- 任务 id=4（SH.600000 × 2024-01-01..06-30）：run → QUEUED → **pause 成功（QUEUED 允许暂停）→ PAUSED**（worker 不认领已暂停任务）→ 再次 run → QUEUED → worker → **SUCCEEDED，inserted=117**（2024 上半年交易日）。
- 终态任务 pause 正确拒绝（`DATA_FOUNDATION_BACKFILL_STATE_INVALID`）。

## 4. 边界与假设（如实）

- 全市场真实回补未执行（本轮仅 1 标的×小窗口验证执行能力）。
- TENCENT_PUBLIC 为实验性公共源（ADR-0015）；本次两窗外联成功不代表生产稳定性承诺。
- 服务器部署 NOT_DEPLOYED；未 push、未 merge。
- 恢复/双 worker 认领/严格门禁/漂移检测的自动化证明见测试套件（QueuedExecutionAndRecoveryTest / StrictGateCoexistAndLineageTest / BackfillScaleChunkingTest）。
