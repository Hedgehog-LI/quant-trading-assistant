# QTA-V2-DATA-FOUNDATION-V21 代码审查 G1（REVIEW_CLEAR）

> 审查对象:双仓分支 `codex/qta-v2-data-foundation-v21` 未提交候选(后端基线 `main@b2400e4`,前端 `main@40f0f68`)。
> 审查执行:**主协调会话**(按用户指令的一次产品/数据/架构边界检查;因子代理派发门禁要求先建 CONTROL 工件,本次审查在主会话完成并如实标注——最终独立验收仍归 Codex)。
> 结论:**REVIEW_CLEAR**(0 BLOCKER / 0 MAJOR;6 MINOR/NOTE)。架构审查计数 1(候选 2614 行),门禁以 `--architecture-review-count 1` 复核通过。
> 证据基线:后端 `./mvnw test` **627 run / 0 failures / 0 errors / 1 skipped**(重构后复跑);前端 typecheck/lint/test(**55 files / 441 tests**)/build 全绿。

## 审查范围与方法

契约 `QTA-V2-DATA-FOUNDATION-V21-CONTRACT.md` AC-01..AC-07;ADR-0015;重点:数据红线/单位口径、并发与幂等、事务边界、发布门禁、分层、安全、前后端契约、节流退避。方法:diff+关键文件通读(DataBackfillService/SnapshotImportService/CsvSnapshotParser/DataQualityService/DatasetPublicationService/DataFoundationController/V24/前端 types+api+hooks 抽查)。

## 发现清单

| # | 级别 | 位置 | 说明 |
| --- | --- | --- | --- |
| 1 | NOTE | `HistoricalBarProviderRegistry.require` | Provider 缺失抛 `MARKET_DATA_PLAN_INVALID`(语义借用);首期无实际第二种场景,留待专用错误码 |
| 2 | NOTE | `DataQualityService` | 检查族硬编码 `SINA_INDUSTRY`/`CN`;首期范围冻结,多市场扩展时参数化 |
| 3 | MINOR | `mdf_backfill_task` scope 唯一键改普通索引 | 决策正确(硬唯一会阻断"终态后同 scope 重建重跑"的文档化幂等工作流);活跃重复由 `countActiveByScope` 服务防线覆盖,并发双创建存在窄竞态但结果为两条独立任务+版本,无数据损坏 |
| 4 | NOTE | `TencentPublicHistoricalBarProvider` 节流 | AtomicLong getAndSet 近似节流,单机部署假设已在契约 ASSUMPTION 冻结 |
| 5 | NOTE | `SnapshotImportService.upsertCalendar` | 逐行 update-then-insert(日历量级小,可接受);批量优化留待后续 |
| 6 | NOTE | WARN 项留档 | DataBackfillService 426 行/22 方法、DataQualityService 26 方法、SnapshotImportService 11 依赖——均为 REVIEW 级,后续任务拆分,不阻断本候选 |

## 边界检查结论(用户指令的一次产品/数据/架构检查)

- **数据红线**:两处单位换算一致(Tencent 手×100/万元×10000/%÷100;CSV schema 冻结元/股/小数);无价量冒充资金流、无 SINA_INDUSTRY 冒充申万、无当前成分冒充 PIT、无 Top-N 冒充全市场的代码或文案;null 均未当 0(前端 `--`、后端 null 断点)。
- **并发/幂等**:tryClaim 条件更新+finally 释放;pause 释放 claim 且循环逐片检查状态;断点续跑跳过终态分片;重试后任务计数从分片表确定性重算;日 K ODKU(uk 含 data_source)重跑 inserted=0 有测试锁定(T04)。
- **事务边界**:短事务+网络调用在事务外(MarketDataPlanExecutionService 先例纪律);质量结果+覆盖+版本状态单事务持久化;recordBatch 并发唯一键兜底。
- **发布门禁**:QUALIFIED 才可发布;FAIL/空数据 REJECTED;旧 RELEASED→RETIRED+指针同事务切换;REJECTED 无成为默认版本路径(T12 锁定)。
- **分层/架构**:SQL 全部 XML;Controller 零 Mapper 零业务;importer 纯解析无持久化依赖;V24 纯新增(仅本任务未提交 migration 被编辑过,无历史 migration 改动);FK/唯一键完整。
- **安全**:无密钥/.env 读取;错误信息只携带截断片段;上传限制 50MB/20 万行/错误报告 50 条。
- **前后端契约**:抽查 BackfillTaskVO/DatasetVersionVO/QualityResultVO/ImportBatchVO 字段逐一对齐;`DATA_FOUNDATION_*` 错误码路径透传展示,无伪造成功。

## 遗留(移交最终验收/Codex)

- RUNTIME/DEPLOYMENT 未验证(AC-08 由主会话 Docker 验证,结果见后续记录);真实 TENCENT_PUBLIC 外联回补 NOT_VERIFIED(设计上 stub 测试锁定逻辑,真实外联依赖网络)。
- WARN 项(#6)列入后续技术债。
