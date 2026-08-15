# TEST-DESIGN Artifact — QTA-V2-MR0-CLOSEOUT-20260815-R1 / ROLE-RUN-TD-G1

- Dispatch ID: QTA-V2-MR0-CLOSEOUT-20260815-R1-TD-G1-D1
- Role run: ROLE-RUN-TD-G1（TEST_DESIGNER, generation 0, FRESH, ADVISORY）
- Started: 2026-08-15T16:00:30Z / Finished: 2026-08-15T16:07:30Z
- Verdict: `AMENDMENTS_REQUIRED`（AMD-001/002/003 阻塞修正案；合并后可冻结，无需重派）
- 挑战对象: contract v1.0 draft（sha256 86c03024b602b443ddb40303fb7af2ace5b24e43b1333a894172fefe626745f9）

## 1. 阻塞修正案（全部被父协调者 ACCEPTED 并冻结进 contract v1.1）

### AMD-001（AC-03）冻结边界数值

- `sampleSize ∈ [1, 500]`（含端点）；0、负数、>500 一律 400。
- `analysisStart <= analysisEnd`（相等合法，单日窗口）；`warmupStart <= analysisStart`（相等合法）。
- 跨度上限 `analysisEnd − analysisStart <= 365 天`（365 合法，366 起 400）。
- 校验同时作用于 GET /analyze、GET /report（start/end 规则）与 POST /ingest（全规则），Controller+Service 双层；参数被拒时零外联。

### AMD-002（AC-05）时间锚点绑定可观察工件 + 不得放松既有校验

强制检查仅依赖 control 文件 + `.json` dispatch 回执：
- (a) 同代 `verifier.startedAt >= reviewer.finishedAt`（相等合法）；
- (b) verifier dispatch 回执 `observedAt >= transitionHistory 中 to=REVIEW_CLEAR 的 at`；
- (c) 任何 `verifier.startedAt < reviewer.finishedAt` 判失败。
- reviewer `.outcome.json` 若含时间戳字段则做一致性交叉校验，缺失时降级 warning（不得为 error；Hook 脚本不在本任务可改范围）。
- 护栏：`node --test scripts/tests/ai-governance.test.mjs` 既有用例全部保留（共享 fixture 时间戳改为严格有序值），删除任一既有断言视为 repair 缺陷。

### AMD-003（AC-06/TEST-08）PoC 运行次数语义 + artifact-restore 模式

- 实现阶段（SLICE-05）恰成功执行一次 `bash scripts/run-mr0-poc.sh` exit 0；最终 verifier 按冻结 inventory 重跑 TEST-08 一次，两次均须 exit 0（exit 2 公共源失败各允许重试一次）；全程合计成功运行恰两次。
- artifact-restore 为 verifier 侧流程（不新增仓库脚本）：运行前备份 `QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-EVIDENCE.json` 与 `...-POC-REPORT.md`，命令以 `bash scripts/run-mr0-poc.sh` 原样记录进 receipt，结束后按字节恢复候选冻结版本，`candidateUnchanged=true` 必须成立。

## 2. 建议（父级采纳情况）

- REC-01（AC-02）采纳：`getActiveProfiles()` 集合恰为 `{"local"}`；组合/空/其他 profile 全部拒绝；同用 `BUSINESS_RULE_VIOLATION` 400 envelope；测试用 MockEnvironment。
- REC-02（AC-01）采纳：架构测试断言 poc 包全量注解 SQL=0（含 Mr0PocMapper）+ XML statement id 集 == 接口方法名集。
- REC-03（AC-04）采纳：零噪声断言收敛进 TEST-05 node 测试（fs 扫描 9 文件）；`POC-REPORT.md` 的 selector 注释随 SLICE-05 重跑再生消除，验收以重跑产物为准。
- REC-04（AC-08/TEST-09）采纳：selector 改为四标记复合命令串（superseded 声明、19:53:16/19:56:00 时间证据、data quality boundary 延续、MR-0 ≠ MR-1 声明）；9 份文档枚举绑定 finalization.changedPaths；MARKET_RESEARCH_API.md §7 示例同步划入父级 finalization 职责。
- REC-05（AC-03）采纳：畸形日期（2026-13-01）用例必须 400 非 500；非法参数时 PublicMarketDataClient 零交互断言。
- REC-06（AC-05）采纳：补相等边界与多代 repair 用例（TD-05-04/05）。
- REC-07（TEST-07）知晓：sourcePath 指主类，selector 跑三个类（receipt 备注）。
- REC-08（AC-06）采纳：UNIT_ANOMALY 族存在且 affectedCount 与基线一致写入 CLOSEOUT-REPORT 引用。

## 3. 测试矩阵（黑盒用例，绑定 TEST-ID）

### AC-01（TEST-01/02/07）
- TD-01-01 poc 包注解 SQL 计数=0；TD-01-02 XML statement id 集==方法名集（恰 6）；TD-01-03 既有 AnalysisServiceTest 7 用例不改断言全绿；TD-01-04 QualityServiceTest 走 XML 路径全绿；TD-01-05 空库 analyze EMPTY_VALID_UNIVERSE 无 500。

### AC-02（TEST-03）
- TD-02-01 ["local"]+true 放行；TD-02-02 ["test"]+true 400；TD-02-03 ["local","test"]+true 400；TD-02-04 []+true 400；TD-02-05 ["local"]+false 400；TD-02-06 拒绝/只读路径 client 零交互；TD-02-07 TEST-08 真实运行 local 放行实证。

### AC-03（TEST-04）
- TD-03-01 start>end 400 双层；TD-03-02 warmup>start 400（相等放行）；TD-03-03 sampleSize 0/-1/501 400，1/500 放行；TD-03-04 跨度 366 放行、367 400、start==end 放行；TD-03-05 GET analyze/report 同规则 400；TD-03-06 畸形日期 400 非 500；TD-03-07 envelope code ∈ ErrorCodeEnum + 零外联。

### AC-04（TEST-05）
- TD-04-01 sourcePath 存在但不含 selector 串 → 无 error（F-005 修复正例）；TD-04-02 sourcePath 缺失仍 error；TD-04-03 恰 9 文件 frozen-selector 扫描 0 命中；TD-04-04 receipt observedSelectors/身份不匹配仍 error。

### AC-05（TEST-06）
- TD-05-01 重叠失败；TD-05-02 提前 dispatch（observedAt < REVIEW_CLEAR.at）失败；TD-05-03 正常串行通过；TD-05-04 相等边界通过；TD-05-05 多代仅同代比较；TD-05-06 旧任务形态（19:56:00/19:53:16）必须被拦截；TD-05-07 既有断言全集保留通过。

### AC-06（TEST-07/FULL/08）
- TD-06-01 聚焦测试 exit 0；TD-06-02 全量+package exit 0；TD-06-03 PoC 证据键齐全+双哈希一致+二次导入 inserted=0；TD-06-04 exit 2 允许重试一次；TD-06-05 timezone/startedAt 真实值；TD-06-06 UNIT_ANOMALY affectedCount 与基线一致。

### AC-07（RUNTIME 账本）
- TD-07-01 最终 control VERIFIED 通过 AMD-002 (a)(b)(c)；TD-07-02 dispatch observedAt >= REVIEW_CLEAR.at。

### AC-08（TEST-09）
- TD-08-01 四标记复合 grep 计数各 >=1；TD-08-02 finalization.changedPaths 覆盖枚举 9 份文档。

## 4. 环境/fixture

- Java 测试全程 H2 + Mock client，零网络零 Docker；治理测试 node:test 临时目录 + 微型 git repo；TEST-08 本地 MySQL 127.0.0.1:3306 + 18080 空闲 + 公共源可达 + verifier artifact-restore。

## 5. 角色运行元数据

- role_run_id ROLE-RUN-TD-G1；dispatch QTA-V2-MR0-CLOSEOUT-20260815-R1-TD-G1-D1；executorType SUBAGENT；agentDefinition .zcode/agents/qta-test-designer.md；capability READ_ONLY；executionOutcome COMPLETED；status CLOSED；waitCalls 0；maxShellPollsForOneCommand 0；compactionCount 0；enforcement ADVISORY；compensatingIsolation: 只读角色，无 Bash/Git/网络，allowlist 内读取。
