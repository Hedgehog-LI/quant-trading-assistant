# QTA-V2-DATA-FOUNDATION-V21 Repair Addendum R1（后端修复收口，2026-08-16）

> 对既有契约 `QTA-V2-DATA-FOUNDATION-V21-CONTRACT.md` 的定点修复冻结；不重规划 V2-1，不改前端仓库，不改 V24。实施状态：**SELF_CHECKED（等待 Codex 独立验收）**。
> 用户指令冲突裁决：任务头要求专家团并行、任务体要求"不要创建专家团或递归子代理"——以任务体为准在主会话直接实施（治理派发门禁亦要求 CONTROL 工件，成本高于收益）。

## R1 范围冻结（对应修复指令一~十）

1. **二维分片**：V25 新表 `mdf_backfill_task_symbol`（uk task_id+canonical_symbol）承载任务证券范围，symbols_json 不再存全量；`HistoricalBarProvider.safeRequestWindowDays()`（腾讯=365 天安全窗，<640 条上限）；chunk=(证券组×日期窗) 二维拆分，chunk.start/end=实际请求区间；MAX_TASK_SYMBOLS 提升至 10000（全 A ≥6000），新增 MAX_TOTAL_CHUNKS 输入保护；5000+ 证券×2021 至今纯 stub 分片测试。
2. **持久化后台执行**：状态机加 QUEUED；`POST run` 仅做条件状态转换（PENDING/PAUSED/PARTIAL_FAILED/FAILED→QUEUED）立即返回 BackfillTaskVO；后台 worker（`@Scheduled` 轮询+可配置线程池，DB 为事实源）条件 UPDATE 认领 QUEUED→RUNNING；每证券执行单元检查暂停；QUEUED/RUNNING 均可暂停；终态与计数从 chunk 事实确定性汇总（残留 RUNNING chunk 不得判 SUCCEEDED→重新入队）；Controller 零线程创建；轮询周期/并发配置化。
3. **崩溃恢复**：启动+定时恢复：claimed_at 超时的 RUNNING 任务→QUEUED，其 RUNNING chunk→PENDING（attempts 保留、错误追加 RECOVERED 标记）；重复恢复幂等；双 worker 认领仅一成功。
4. **严格质量门禁**：发布覆盖率阈值 0.90（`qta.data-foundation.publish-coverage-threshold` 可配）；计算日期覆盖/股票池覆盖/日 K 总体覆盖/窗口首尾边界覆盖；空数据 FAIL（既有）；总体覆盖<阈值 FAIL；首/末日边界覆盖<阈值 FAIL（截断必拒）；期望行基于 stock_basic 上市日/状态+日历（缺失时显式假设记录）；质量结果真正控制 QUALIFIED/RELEASED；"不完整六年不得发布"测试。
5. **Provider 混用修复**：多 Provider 行在 stock_daily_bar 合法共存；质量检查基于版本 manifest（只检本版本归属行）；同窗其他 Provider 事实不致 FAIL；版本内 source/adjust 混入才 FAIL；腾讯+Longbridge+CSV 共存测试。
6. **版本血缘**：V25 表 `mdf_dataset_version_manifest`（版本+bar_id+业务键+row_hash+来源 task/batch+纳入时间；双唯一键）；`mdf_dataset_version` 加 content_hash/manifest_row_count/lineage_status；发布前冻结内容哈希；漂移检测（manifest↔当前事实重算）FAIL 并阻断发布；released VO 返回 contentHash/manifestRowCount/lineageStatus；不新建权威日 K 表。
7. **CSV×版本打通**：`POST /imports` 加可选 datasetVersionId；DAILY_BAR 必须绑定导入类版本（provider/adjust/market 不符拒绝）；导入行入版本 manifest（来源=IMPORT_BATCH）；其余 kind 可不绑定但保留批次血缘（import batch 加 dataset_version_id）；重复文件幂等不变。
8. **默认数据集与兼容**：启动幂等初始化 CN_DAILY_BAR；接口路径不变；BackfillTaskVO 兼容 QUEUED；DatasetVersionVO 新字段向后兼容；run 返回 QUEUED/已认领 RUNNING；GET task/chunks 供轮询。
9. **验证**：`./mvnw test`/`package`/`git diff --check` + 指定测试族；自动化全绿后一次小型 Docker 验证（1 证券×跨≥2 日期片段、run 快速返回、轮询 QUEUED/RUNNING→终态、暂停/继续各一次、不做全市场真实回补）。
10. **收口**：四阶段 commit（契约+V25+状态机 → 二维分片+worker+恢复 → 质量+manifest+导入血缘 → 测试+文档）；不 push/merge；只写 SELF_CHECKED。

## 验收矩阵（Repair AC）

- R-AC1：6000+ 证券不拒；二维分片 chunk 数=⌈N/chunkSize⌉×窗数；chunk 日期=实际窗（T-Scale）。
- R-AC2：run 快速返回 QUEUED；worker 认领执行到终态；暂停/继续（T-Async）。
- R-AC3：stale RUNNING 恢复+幂等+双 worker 单认领（T-Recovery）。
- R-AC4：覆盖<0.90 / 截断 / 空 / 不完整六年 → FAIL 不可发布；完整数据 QUALIFIED→RELEASED（T-StrictGate）。
- R-AC5：多 Provider 同窗共存不 FAIL（T-Coexist）。
- R-AC6：manifest 血缘+漂移检测阻断发布+released 字段（T-Lineage）。
- R-AC7：CSV 绑定版本+manifest 血缘+不一致拒绝+幂等（T-ImportLink）。
- R-AC8：默认数据集幂等初始化（T-DefaultInit）；Docker 小验证记录（RUNTIME-VERIFICATION-R1）。
