# SELF-CHECK (REPAIR-1) — QTA-V2-MR0-CLOSEOUT-20260815-R1 / ROLE-RUN-IMP-R1-G2

- Dispatch ID: QTA-V2-MR0-CLOSEOUT-20260815-R1-IMP-R1-G2-D1
- Assigned AC: AC-03, AC-04（findings CR-G1-1/CR-G1-2/CR-G1-6）；verdict: `SELF_CHECKED`
- Started: 2026-08-15T17:38:00Z / Finished: 2026-08-15T17:44:00Z

## Changed files（3 个，均在 REPAIR-1 allowlist 内）

| 文件 | 变更 |
|---|---|
| `Mr0PocController.java` | 新增局部 `@ExceptionHandler(HttpMessageNotReadableException.class)` → 400 `VALIDATION_ERROR` envelope（不透出 Jackson 细节）；import + javadoc 同步（净 +11） |
| `Mr0PocParamBoundaryTest.java` | 新增 `ingestMalformedBodyReturns400Not500`（畸形日期 + 数值文本两路 body，400 + envelope + 全 mock 零交互）；CR-G1-6 注释口径修正 |
| `scripts/tests/ai-governance.test.mjs` | 新增 TD-04-03 用例：恰 9 个冻结路径逐文件 readFile，缺文件即 fail，断言 `frozen-selector` 0 命中 |

## Self-check commands

| 命令 | exit | 结果 |
|---|---|---|
| `./mvnw -q test -Dtest=Mr0PocParamBoundaryTest` | 0 | 14/0/0（原 13+1） |
| `node --test scripts/tests/ai-governance.test.mjs` | 0 | 74/74/0（原 73+1） |
| `./mvnw -q test -Dtest=Mr0PocIngestGateTest,Mr0PocAnalysisServiceTest,Mr0PocQualityServiceTest` | 0 | 9+7+5 全绿 |
| `node scripts/check-ai-architecture.mjs --base 2a7b451 --architecture-review-count 0` | 0 | errors=0, warnings=1（ARCH-W-001 已处置） |

## findingResolution

- CR-G1-1（P1）解决：ingest 畸形 body 反序列化失败不再落 catch-all 500，controller 局部 400 VALIDATION_ERROR；新用例修复前必 500，具判别力。
- CR-G1-2（P2）解决：TD-04-03 恰 9 文件 fs 扫描落地，未来重嵌 selector 注释 TEST-05 变红。
- CR-G1-6（P3）解决：注释口径修正（2026 非闰年含端点 365 日历日）。

## Deviations

- checkpoint 以文本载荷交付（allowlist 限制）；「非法枚举文本」以数值字段文本等价覆盖；额外只读架构自检；packet HEAD 元数据偏差（34ed2c8 = checkpoint 提交，树内容同候选 + control 工件）。

## 角色运行元数据

- roleRunId ROLE-RUN-IMP-R1-G2；dispatch QTA-V2-MR0-CLOSEOUT-20260815-R1-IMP-R1-G2-D1；sessionId agent_6822614b-745a-4060-a813-4e39c9e81379；executorType SUBAGENT；agentDefinition .zcode/agents/qta-implementer.md；sliceId REPAIR-1；generation 2；capability READ_WRITE；executionOutcome COMPLETED；status CLOSED；enforcement ADVISORY；compensatingIsolation: 3-file allowlist、无 Git、无网络；waitCalls 0；maxShellPollsForOneCommand 1；compactionCount 0。
