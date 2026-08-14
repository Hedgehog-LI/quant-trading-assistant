# Verification: QTA-V2-MR0-DATA-SEMANTICS-POC-20260815

> Role run: `ROLE-RUN-FV-G1`（FINAL_VERIFIER，fresh，dispatch ...-FV-G1-D1，一次性 worktree /tmp/qta-mr0-verify @ 05eece1）
> Candidate: COMMIT generation-2 `05eece11a8bbfa92c79341efac0a3f5ef818fc74`（tree cfa2232…，patch sha 7c3eeb73…）
> **最终结论：ACCEPTED（FUNCTIONAL: PASS / ARCHITECTURE: PASS）**

## 维度结论

| 维度 | 结果 | 证据 |
| --- | --- | --- |
| STATIC | PASS | TEST-01/02/03/08 冻结选择器逐字执行，4/4 exit 0 |
| AUTOMATION | PASS | TEST-04 6/6、TEST-05 7/7、TEST-06 5/5（surefire XML 权威计数）；TEST-FULL `./mvnw -q test && ./mvnw -q -DskipTests package` exit 0（538 tests / 0 fail / 0 err / 1 既有 skip；jar 构建） |
| RUNTIME | PASS（附 F-1 注意事项） | TEST-07 独立重跑 exit 0：真实公共源+本地 MySQL、双哈希一致（4a0a73c8…）、四表二次导入 inserted=0、universeSize=151、tradingDays=23、0 失败 |
| DEPLOYMENT | NOT_REQUIRED | 契约声明；qta-server 8080 未触碰 |

## AC 结论（8/8 PASS）

- AC-01 字典：23 指标 13 属性；AMD-3 冻结值逐项核验（adLine 种子、ε=1e-6、覆盖域、ddof=1 不年化）。
- AC-02 矩阵：五源状态词齐；TUSHARE NOT_VERIFIED+IMPLEMENTATION_GATE；实测端点引用≥4。
- AC-03 盘点：九节精确标题+可核验引用。
- AC-04 导入：六冻结方法；ODKU×5、零违禁方言；V23 三表结构核验。
- AC-05 分析：七冻结方法（含 warmup 边界两侧、重读存储）。
- AC-06 质量/控制器：五冻结方法（含嵌套零外联）；控制器无 HTTP 客户端。
- AC-07 真实 PoC：独立重跑全链 exit 0，八项数值互洽（370=23×150−3080、1127=49×23、31=31 重算）。
- AC-08 边界：BND-A/B/C/D 四标记在已提交与重生成报告上均存在。

## Findings（核验者）

- **F-1 MEDIUM（流程）**：TEST-07 回执机械标记 result=FAIL 仅因脚本按设计覆盖自身两个被追踪工件（candidateUnchanged=false）；实质 PASS（exit 0、断言全过、HEAD/tree 不变、src/scripts/pom 零变更）。处置：本文件记录注意事项；MR-1 泄漏项——PoC 运行工件移出追踪路径或为 runner 加写路径例外。
- **F-2 LOW**：P3 CR2-1（字典 §3 指数行豁免注记——finalization 处理）、CR2-2（ingest Top-N tie-break）、CR2-4（ingest VWAP 负例/skipped 观测）、CR2-3（control contract.version 元数据注记）——路由 finalization/MR-1。
- **F-3 INFO**：全部披露例外复核确认（预算例外 1495 行、注解 Mapper、基准免个股 VWAP 自检、exit-3 扩展语义、UNIT_ANOMALY FAIL(8)=本地既有 LONGPORT 脏数据真实检出、COVERAGE WARN(0.673)=SINA 公共源真实缺口）。
- **F-4 INFO**：TEST-06 .txt 摘要显示 0（@Nested 统计怪癖），XML 权威 5/5；跨日 membership updated 101→100（inserted=0 保持）。

## 回执（9 个，已取回主仓 docs/development/tasks/）

| testId | exit | result | receipt sha256（前 16） |
| --- | --- | --- | --- |
| TEST-01 | 0 | PASS | f108cc440dc30ef1 |
| TEST-02 | 0 | PASS | 04275d6c2323d9af |
| TEST-03 | 0 | PASS | 1206aa765c72d571 |
| TEST-04 | 0 | PASS | 016f79fb72f8d7c8 |
| TEST-05 | 0 | PASS | cc327e49a9fe111a |
| TEST-06 | 0 | PASS | bd41b16e34671978 |
| TEST-07 | 0 | PASS*（F-1 注意事项） | 5bf1cf6b1efb913a |
| TEST-08 | 0 | PASS | 2e98a0131b77a2ac |
| TEST-FULL | 0 | PASS | 592485f6e904c94e |

## MR-1 泄漏项（核验者路由）

1. 复用前清理本地 LONGPORT SH.600519 手/股脏数据（8 行，2026-07-01..10）。
2. PoC 运行工件移出追踪路径或扩展 runner 未变更检查例外。
3. CR2-2 ingest tie-break、CR2-4 负例观测。

## 角色遥测

start 2026-08-14T19:20:13Z / finish 2026-08-14T19:31:28Z；每门禁恰好执行一次；0 压缩；主仓 Git 未触碰；worktree 已由父级移除。
