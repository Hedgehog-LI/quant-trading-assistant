# REVIEW-G1 — QTA-V2-MR0-CLOSEOUT-20260815-R1 / ROLE-RUN-CR-G1

- Dispatch ID: QTA-V2-MR0-CLOSEOUT-20260815-R1-CR-G1-D1
- Candidate: 8c260f325747158a879e548f6902b2dbc42c7559（gen-1, COMMIT）
- Started: 2026-08-15T17:30:00Z / Finished: 2026-08-15T17:31:45Z
- Verdict: `FUNCTIONAL: FAIL` / `ARCHITECTURE: PASS`（REVIEW_CLEAR 未授予；建议 repair round 1）

## Findings

### CR-G1-1 — P1 — AC-03 合同违约：POST /ingest 畸形日期返回 500 而非 400

- `POST /api/v1/market-research/mr0-poc/ingest` 携带 `{"analysisStart":"2026-13-01"}`（或非法 JSON 数字越界）→ Jackson 抛 `HttpMessageNotReadableException` → 被 `GlobalExceptionHandler.java:66-71` 的 catch-all `Exception` handler 拦截 → 500 INTERNAL_ERROR。AC-03 点名输入类别违反「不出现 500」。
- 位置：`Mr0PocController.java:50-57`（ingest 入口）、`:83-88`（局部 handler 仅覆盖 MethodArgumentTypeMismatchException）。
- 现有测试未覆盖 POST body 畸形日期（`Mr0PocParamBoundaryTest.malformedDateParametersReturn400Not500` 仅 GET query 路径）。
- 最小修复：controller 局部增加 `@ExceptionHandler(HttpMessageNotReadableException.class)` → 400 VALIDATION_ERROR envelope；TEST-04 补 POST 畸形日期用例（含零外联断言）。

### CR-G1-2 — P2 — AC-04 承诺的「fs 扫描恰 9 文件」断言未落地 TEST-05（TD-04-03 缺失）

- AC-04 期望明文「fs 扫描恰 9 文件断言」；当前 ai-governance.test.mjs 仅 TD-04-01/02/04 三用例；清理成果无机器防回归（未来重新嵌入 frozen-selector 注释 TEST-05 仍全绿）。
- 当前状态本身合格：reviewer 独立 grep 全工作树业务源 0 命中；10 条注释/9 文件删除在 diff 逐条核实。
- 最小修复：ai-governance.test.mjs 增加 9 个冻结路径 fs 扫描用例断言 0 命中（POC-REPORT 为脚本再生文件，「存在即扫」语义）。

### CR-G1-3 — P3 — packet 元数据偏差：候选规模写 30 文件，冻结 diff 实际 32 个 `diff --git` 条目（父级元数据更正，非候选缺陷）

### CR-G1-4 — P3 — `reviewClearTransitionAt` 缺失/畸形时静默跳过检查(b) 且无 warning（REVIEW_CLEAR 中间态短窗口盲区；VERIFIED 结构绑定最终拦截；建议后续治理轮补降级 warning，本任务不改）

### CR-G1-5 — P3 — CLOSEOUT-REPORT §2 门禁时间（17:13:43Z–17:15:08Z）早于 SLICE-05 角色 startedAt 17:15:00Z 约 75 秒（实现期门禁不进 control testEvidence，无机器违规；verifier 侧注释澄清）

### CR-G1-6 — P3 — `Mr0PocParamBoundaryTest` 注释算术口径小误（2026 非闰年，含端点 365 个日历日而非 366；行为与断言正确）

## 逐 AC 核对结论（摘要）

- AC-01 通过（注解 SQL 与 XML 逐字等价、resultType 二进制名正确、mapper-locations/驼峰映射确认、架构测试断言面真实）。
- AC-02 通过（恰 local 单元素语义、双层、400 映射实测、run-mr0-poc.sh 兼容、analyze/report 零外联有测试实证）。
- AC-03 除 CR-G1-1 外通过（边界与 AMD-001 一致、默认命令不被误杀）。
- AC-04 主体通过、断言面缺口见 CR-G1-2（F-005 修复未放松既有校验，正反用例实证）。
- AC-05 通过（AMD-002 (a)(b)(c) 全吻合、outcome 缺失降级 warning、既有断言保留、7 用例含旧任务形态拦截）。
- AC-06/07/08 证据链通过（双哈希一致、二次导入 inserted=0、UNIT_ANOMALY=8 在案、四标记齐备、切片串行无重叠）。

## regressionCheck

既有 538 测试语义未削弱。仅两处触及既有测试：SLICE-02 @Primary gate 桩（断言零改动）、SLICE-04 fixture FINAL_VERIFIER 时间戳调整（无断言删除，TD-05-07 子进程护栏）。其余为删除 frozen-selector 注释行。

## scopeCheck

32 个 diff 文件全部落入切片 allowlist 或父级专属工件范围。无越权、无 Prohibited 项。

## ARCHITECTURE_REVIEW: PASS（8.5/10）

- 机器门禁 PASS/0 errors；ARCH-W-001 ACCEPT（基线 11→12 为 AC-02 强制 gate 注入，合同冻结设计，无需 ADR 例外）。
- 职责图与依赖方向清晰；MyBatis SQL 仅 XML 机器强制。

## 角色运行元数据

- roleRunId ROLE-RUN-CR-G1；dispatch QTA-V2-MR0-CLOSEOUT-20260815-R1-CR-G1-D1；sessionId agent_5ad61cab-2d80-4abc-a5ae-8e1c719ef60a；executorType SUBAGENT；agentDefinition .zcode/agents/qta-code-reviewer.md；sliceId ""；generation 1；capability READ_ONLY；executionOutcome COMPLETED；status CLOSED；enforcement ADVISORY；compensatingIsolation: 只读、无 Bash/Git/网络/子代理、冻结 diff 工件经 Read 读取；waitCalls 0；maxShellPollsForOneCommand 0；compactionCount 0。
