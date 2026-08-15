# SELF-CHECK — QTA-V2-MR0-CLOSEOUT-20260815-R1 / SLICE-01 / ROLE-RUN-IMP-S1-G1

- Dispatch ID: QTA-V2-MR0-CLOSEOUT-20260815-R1-IMP-S1-G1-D1
- Assigned AC: AC-01；verdict: `SELF_CHECKED`（切片级）
- Started: 2026-08-15T16:14:00Z / Finished: 2026-08-15T16:36:55Z

## Changed files（均在 SLICE-01 allowlist 内）

| 文件 | 变更 |
|---|---|
| `src/main/java/com/quant/trade/marketdata/poc/Mr0PocAnalysisMapper.java` | 删除 6 处 `@Select` 注解与 import；保留 @Mapper、6 个方法签名、语义 javadoc、5 个嵌套 row 类；偏离声明段落替换为 XML 归属说明（+3/−45） |
| `src/main/resources/mapper/Mr0PocAnalysisMapper.xml`（新建） | namespace 绑定接口 FQN；6 个 statement id 与方法名 1:1；SQL 语义逐字保持（`<if>` 条件、ORDER BY、`&gt;=`/`&lt;=` 转义）；resultType 二进制 `$` 引用嵌套 row 类（+53） |
| `src/test/java/com/quant/trade/marketdata/poc/Mr0PocMapperXmlArchitectureTest.java`（新建） | JUnit5 无 Spring 上下文：(a) poc 包递归扫描断言注解 SQL 计数 0；(b) XML statement id 集 == 接口反射方法名集（恰 6）+ namespace 绑定检查；模块根从工作目录向上解析；禁用外部 DTD 加载（+118） |

## Self-check commands

| 命令 | exit | 结果 |
|---|---|---|
| `./mvnw -q test -Dtest=Mr0PocMapperXmlArchitectureTest` | 0 | 2 tests, 0 failures/errors |
| `./mvnw -q test -Dtest=Mr0PocAnalysisServiceTest` | 0 | 7 tests, 0 failures/errors（文件未改、断言未改） |
| `./mvnw -q test -Dtest=Mr0PocQualityServiceTest` | 0 | 5 tests, 0 failures/errors（覆盖 countDailyBarsByProvider/countMarketCalendar） |

## Deviations（实施者披露）

- D-1: 首次架构测试运行因 JDK DOM 解析器外联获取 mybatis DTD 卡死；终止进程后一次定向修复（禁用 load-external-dtd）复跑全绿。waitCalls 6 / polls 4 超 packet 预算（诚实披露）。
- D-2: packet 基线写 2a7b451=HEAD，实际 HEAD 为 11fe460（父级 contract 提交，代码无差异）。
- D-3: control 文件为父级运行时修改的脏文件，实施者未触碰。
- D-4: selectMoneyFlows javadoc 删除一句描述已移除注解编码的过时句子。
- D-5: checkpoint 以文本载荷返回（任务工件为父级所有）。
- D-6: 额外运行 QualityServiceTest（packet 允许）以执行 6 条迁移语句中的 2 条。

## 角色运行元数据

- roleRunId ROLE-RUN-IMP-S1-G1；sessionId agent_7d8a3fa7-2635-46e2-8c0b-faeeb2c14fbf；executorType SUBAGENT；agentDefinition .zcode/agents/qta-implementer.md；sliceId SLICE-01；generation 1；capability READ_WRITE；executionOutcome COMPLETED；status CLOSED；enforcement ADVISORY；compensatingIsolation: 3-file allowlist、无 Git、无网络、无密钥；waitCalls 6（披露超额）；maxShellPollsForOneCommand 4（披露超额）；compactionCount 0。
