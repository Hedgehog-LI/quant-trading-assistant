# Self-Check (slice 2): QTA-V2-MR0-DATA-SEMANTICS-POC-20260815

> SLICE-02 local self-check receipt persisted by parent. Slice-local only.

## SLICE-02（ROLE-RUN-IMP-S2-G1，dispatch ...-IMP-S2-G1-D1）

- 状态：`SELF_CHECKED`（父级复测 Mr0PocIngestServiceTest exit 0，6/6）
- 变更（7 文件）：V23__add_mr0_poc_tables.sql（55 行，3 张 mr0_ 表）、Mr0PocMapper.java（40）、Mr0PocMapper.xml（81，幂等统一 ON DUPLICATE KEY UPDATE，H2 MODE=MySQL 兼容实测通过）、PublicMarketDataClient.java（226，腾讯/新浪 5 组只读方法，无凭据）、Mr0PocIngestService.java（322，Top-N∪基准→universe→成分→日K换算→资金流→最小身份回填，单 symbol 失败隔离）、Mr0PocIngestServiceTest.java（341，6 冻结方法）、mr0-public-probe-fixtures.json（53，真实值+provenance 声明）。
- 附加自检：SecurityDirectoryMigrationTest 冒烟确认 Flyway 迁移至 v23；切片交接全量 ./mvnw -q test 526/0/0；早期架构门禁 dry-run exit 0（additions=484，.md 不计）。
- 父级裁决（预算口径）：原始行数公式 sql+xml+java=724>500；采纳架构门禁机器口径（生产源显著行 additions=484≤500，REC-9 已确认门禁为行数权威）。精确 inserted/updated 计数保留——AMD-1 冻结 TEST-07 需要"二次导入 inserted=0"证据，削减会违反冻结契约。两个数字均如实记录。
- 遗留关注点（移交 reviewer）：Mr0PocIngestService 直接依赖 11>10 warning（编排层嵌套模型类抬高，已处置接受）。
- 实施中修正：批量 upsert 子句选择 VALUES(col)（MyBatis foreach 变量作用域限制），H2 实测幂等通过；inserted/updated 计数改由窗口预查推导（ODKU 受影响行数语义 H2/MySQL 不一致）。无同指纹重复失败。
