# Verification: QTA-V2-MR0-DATA-SEMANTICS-POC-20260815（gen-3 权威结论）

> **权威核验：ROLE-RUN-FV-G2**（FINAL_VERIFIER，fresh，dispatch ...-FV-G2-D1，一次性 worktree /tmp/qta-mr0-verify3 @ 981cd47，2026-08-14T19:53:16Z→20:04:10Z）
> Candidate: COMMIT generation-3 `981cd47ff56e60a871a53c5c572f4fe484e306e8`（tree 68025bcdf67a23376867730da32b206641831ae2，patch sha e95e8f898d8155c7d0dcea7669140f1c467edf118453dfa4cef7eafbdccf49b9）
> **最终结论：ACCEPTED（FUNCTIONAL: PASS / ARCHITECTURE: PASS）**

## 维度结论（机器回执绑定，全部 candidateUnchanged=true、selector 逐字 9/9）

| 维度 | 结果 | 证据（RECEIPT2-*.json） |
| --- | --- | --- |
| STATIC | PASS | TEST-01/02/03/08 |
| AUTOMATION | PASS | TEST-04（6/6 方法名经 surefire XML 核对）、TEST-05（7/7）、TEST-06（5/5 含嵌套）、TEST-FULL（538/0/0 + package） |
| RUNTIME | PASS | TEST-07 真实公共源→本地 MySQL 全链 exit 0（恢复包装保证 candidateUnchanged；运行本身真实）；已提交 POC-EVIDENCE 双哈希一致+四表 inserted=0 |
| DEPLOYMENT | NOT_REQUIRED | 契约声明；L2 本地优先 PoC |

## AC 结论（8/8 PASS）

AC-01 字典（23 指标 13 属性+AMD-3 冻结值）；AC-02 矩阵（五源+TUSHARE NOT_VERIFIED+实测端点）；AC-03 盘点（九节）；AC-04 导入（V23 三表/ODKU/单位换算/六方法）；AC-05 分析（七方法/公式/守卫）；AC-06 质量（八族/五方法/受控 ingest 默认关）；AC-07 真实交易月（exit 0/双哈希/幂等 inserted=0/universeSize 151）；AC-08 边界（BND-A/B/C/D）。

## 核验者发现（均 info 级）

- O-1：control contract.version="1.0" vs 契约 v1.1（sha 绑定不受影响）——finalization 元数据修正。
- O-2：pom.xml（+1 selector 注释）与 application.properties（+4 默认关闭 ingest 门禁）在字面 slice 允许列表外——已披露、经 G1-G3 审查。
- O-3：候选提交内 CONTROL 快照含 gate 窗口中间态——权威 control 为父级 checkpoint 副本，selector 逐字一致。
- O-4：surefire .txt 对 @Nested 计数怪癖，XML 权威 5/5。
- 披露例外全部复核确认（预算例外 1495 行、注解 Mapper、基准免个股 VWAP 自检、exit-3 扩展语义、UNIT_ANOMALY FAIL(8)=既有 LONGPORT 脏数据真实检出、COVERAGE WARN=公共源真实缺口、TEST-07 恢复包装目的）。

## 前轮记录（历史，非本轮绑定）

- FV-G1（gen-2 `05eece1`，2026-08-14T19:20-19:31Z）：实质 ACCEPTED（9/9 门禁 exit 0），但 TEST-07 回执 candidateUnchanged=false（脚本按设计覆盖自身工件）+ 台账 selector-源绑定缺陷，无法机绑——驱动修复轮 2（F-005）。其回执（RECEIPT-TEST-*.json）保留为历史证据。

## 回执（9 个 RECEIPT2-*，已取回主仓 docs/development/tasks/）

| testId | exit | result | receipt sha256（前 16） |
| --- | --- | --- | --- |
| TEST-01 | 0 | PASS | c6ae762039bb11b2 |
| TEST-02 | 0 | PASS | 72137da0cf019d51 |
| TEST-03 | 0 | PASS | 43a05ba1cf1bada6 |
| TEST-04 | 0 | PASS | 3a259934c3c1b3c6 |
| TEST-05 | 0 | PASS | 83243930ec3a5960 |
| TEST-06 | 0 | PASS | 80f3c3a78a092bb5 |
| TEST-07 | 0 | PASS | 4d7a4807031e3a3f |
| TEST-08 | 0 | PASS | 5d518c15ab20a424 |
| TEST-FULL | 0 | PASS | 25c7cb784197b91c |

## MR-1 泄漏项

1. 复用前清理本地 LONGPORT SH.600519 手/股脏数据（8 行）。
2. PoC 运行工件移出追踪路径或 runner 加写路径例外（TEST-07 恢复包装为本轮补偿）。
3. CR2-2 ingest tie-break、CR2-4 负例观测、CR2-1 字典 §3 指数豁免注记（finalization 处理）。
