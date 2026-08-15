# SELF-CHECK — QTA-V2-MR0-CLOSEOUT-20260815-R1 / SLICE-05 / ROLE-RUN-IMP-S5-G1

- Dispatch ID: QTA-V2-MR0-CLOSEOUT-20260815-R1-IMP-S5-G1-D1
- Assigned AC: AC-06, AC-07, AC-08；verdict: `SELF_CHECKED`（切片级）
- Started: 2026-08-15T17:15:00Z / Finished: 2026-08-15T17:20:39Z

## Changed files（3 个，均在 allowlist 内）

| 文件 | 变更 |
|---|---|
| `QTA-V2-MR0-CLOSEOUT-20260815-R1-CLOSEOUT-REPORT.md` | 新建：四要素标记、三项门禁精确结果、五切片摘要、AC 状态表、AMD-003 计数账 |
| `QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-EVIDENCE.json` | PoC 重跑再生（+6/−6） |
| `QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-REPORT.md` | PoC 重跑再生（成功路径全量生成，0 frozen-selector 残留） |

## Self-check commands

| 命令 | exit | 结果 |
|---|---|---|
| `./mvnw -q test -Dtest=Mr0PocIngestServiceTest,Mr0PocQualityServiceTest,Mr0PocAnalysisServiceTest` | 0 | 18 tests / 0F / 0E |
| `./mvnw -q test && ./mvnw -q -DskipTests package` | 0 | **562 tests / 0 failures / 0 errors / 1 skipped**（基线 538→+24；skipped 为既有条件跳过）+ package OK |
| `bash scripts/run-mr0-poc.sh` | 0 | 首次即成功（182s，CST）；实现阶段成功运行恰 1 次，无 exit-2 重试 |
| `jq -e <证据断言>` | 0 | EVIDENCE_ASSERTIONS=PASS |
| TEST-09 冻结 selector（4×grep -c） | 0 | 计数 1/1/1/2 |

## PoC evidence

- status=SUCCESS、exitCode=0、failures=[]、pocReportWritten=true；窗口 2026-07-01..07-31 / warmup 2026-04-01 / sampleSize 150。
- 双哈希一致：run1==run2==1cb27099b8728b8ae029038886330bde6bd6bd6ec33a47f07301cf078df86ca7e2a（sha256）。
- 二次导入四表 inserted=0（universe/membership/dailyBar/moneyFlow；updated 5543/101/11199/3432 ODKU 原地刷新）。
- tradingDays=23、universeSize=151（样本 150+基准）、bar=3080、membership=101、moneyflow=3432。
- **UNIT_ANOMALY affectedCount=8（与基线一致，SH.600519 volume 未×100，保留未清理）**、coverageGap=49/150、marketCalendarCnRows=0、TIME_POINT_LOOKAHEAD 显式时点假设。

## Deviations

1. checkpoint 以文本载荷交付（任务工件父级所有）。
2. 全量计数以 surefire XML 为准（.txt 摘要漏计嵌套类 5 例：557→562）。
3. TEST-09 首跑 'data quality boundary' 计数 0（标题误大写），一次定向修复后通过。
4. PoC 报告 universe as_of=2026-08-16（本地 CST 已跨日，脚本取当前日期，预期行为）。
5. packet 基线写 2a7b451=HEAD，实际 HEAD=11fe460（父级 contract 提交）。

## 角色运行元数据

- roleRunId ROLE-RUN-IMP-S5-G1；dispatch QTA-V2-MR0-CLOSEOUT-20260815-R1-IMP-S5-G1-D1；sessionId agent_daee91c8-48f0-49f8-be63-9e71a75d3b42；executorType SUBAGENT；agentDefinition .zcode/agents/qta-implementer.md；sliceId SLICE-05；generation 1；capability READ_WRITE；executionOutcome COMPLETED；status CLOSED；enforcement ADVISORY；compensatingIsolation: 3 路径 allowlist、无 Git、无手工 SQL/数据修改、外联仅经冻结脚本；waitCalls 0；maxShellPollsForOneCommand 1；compactionCount 0。
