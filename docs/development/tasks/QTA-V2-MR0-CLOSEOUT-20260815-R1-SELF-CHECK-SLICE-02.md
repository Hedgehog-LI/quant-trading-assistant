# SELF-CHECK — QTA-V2-MR0-CLOSEOUT-20260815-R1 / SLICE-02 / ROLE-RUN-IMP-S2-G1

- Dispatch ID: QTA-V2-MR0-CLOSEOUT-20260815-R1-IMP-S2-G1-D1
- Assigned AC: AC-02, AC-03；verdict: `SELF_CHECKED`（切片级）
- Started: 2026-08-15T16:40:00Z / Finished: 2026-08-15T16:50:05Z

## Changed files（8 个，均在 SLICE-02 allowlist 内；生产行增量 176 ≤ 400）

| 文件 | 变更 |
|---|---|
| `Mr0PocIngestGate.java`（新） | AC-02 门禁组件：开关 true 且 `getActiveProfiles()` 恰为 `{"local"}` 才放行；拒绝 `BusinessException(BUSINESS_RULE_VIOLATION)` |
| `Mr0PocParamValidator.java`（新） | AMD-001 冻结边界：start<=end、warmup<=start、sampleSize∈[1,500]、跨度 diff<=365；非法 `VALIDATION_ERROR` |
| `Mr0PocController.java` | `@Value` 改注入 gate；ingest gate+全量校验；analyze/report 窗口校验；controller 局部 `MethodArgumentTypeMismatchException`→400 |
| `Mr0PocIngestService.java` | `ingest()` 入口 gate 再校验+全量校验（+12） |
| `Mr0PocAnalysisService.java` | `analyze()` 入口窗口+sampleSize 校验（+6） |
| `Mr0PocIngestGateTest.java`（新） | TEST-03：9 用例 |
| `Mr0PocParamBoundaryTest.java`（新） | TEST-04：13 用例 |
| `Mr0PocIngestServiceTest.java` | 仅 StubConfig 增加 @Primary gate 桩（MockEnvironment 恰 local+true），断言零改动 |

## Self-check commands

| 命令 | exit | 结果 |
|---|---|---|
| `./mvnw -q test -Dtest=Mr0PocIngestGateTest` | 0 | 9/0/0 |
| `./mvnw -q test -Dtest=Mr0PocParamBoundaryTest` | 0 | 13/0/0 |
| `./mvnw -q test -Dtest=Mr0PocIngestServiceTest,Mr0PocAnalysisServiceTest,Mr0PocQualityServiceTest` | 0 | 18/0/0 |
| `node scripts/check-ai-architecture.mjs --files <5 变更生产文件>` | 0 | errors=0, warnings=1（Mr0PocIngestService deps 11→12，见 D5） |

## Deviations（实施者披露）

1. Mr0PocIngestServiceTest @Primary gate 桩（test profile 无法切 local；gate 真实行为由 TEST-03 覆盖）。
2. 跨度语义按合同差值规则：diff 365 放行（366 日历日）、diff 366 拒绝（367 日历日）；两种表述均被测试满足。
3. analyze() service 入口额外校验 sampleSize（AnalysisCommand 携带该字段；HTTP 行为不变，仅防直调）。
4. 畸形日期由 controller 局部 handler 处理（沿用 MarketDataAssetController 先例），未动 common 包。
5. 架构 WARN：Mr0PocIngestService direct dependencies 基线 11→12（AC-02 service 层防御所致），需父级架构门禁处置。
6. checkpoint 以文本载荷交付（任务工件为父级所有）。

## 角色运行元数据

- roleRunId ROLE-RUN-IMP-S2-G1；dispatch QTA-V2-MR0-CLOSEOUT-20260815-R1-IMP-S2-G1-D1；sessionId agent_864eeb47-047c-48f4-90dd-0ae4f8e76da0；executorType SUBAGENT；agentDefinition .zcode/agents/qta-implementer.md；sliceId SLICE-02；generation 1；capability READ_WRITE；executionOutcome COMPLETED；status CLOSED；enforcement ADVISORY；compensatingIsolation: 两目录 allowlist、无 Git、无网络、无密钥；waitCalls 0；maxShellPollsForOneCommand 0；compactionCount 0。
