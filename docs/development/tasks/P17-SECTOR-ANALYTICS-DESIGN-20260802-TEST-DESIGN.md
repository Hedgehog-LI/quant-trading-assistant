# Test Design: P17-SECTOR-ANALYTICS-DESIGN-20260802

- Task ID: `P17-SECTOR-ANALYTICS-DESIGN-20260802`
- Lane: `L0`（静态证据 only；RUNTIME/DEPLOYMENT = NOT_REQUIRED）
- Role: TEST_DESIGNER / TD-RUN-1 / DISPATCH-TD-1
- Contract hash asserted: `9138c98e5b09a7d3e28bf491a4abfe0c052200c944498d1e492f5dae67810dc6`
- Verdict: `READY_TO_FREEZE`（0 blocking amendments，在 L0 cap=0 之内）
- Baseline: `563e84a573426800b3f6aa8e4e0525bc5314b3a8` on `codex/p17-sector-analytics-design-20260802`（干净）

本文件为独立 test-designer 在干净只读上下文返回的 artifact，由父上下文持久化。

## Amendments（父上下文 disposition 记录）

| ID | Type | Disposition |
|---|---|---|
| AMEND-REC-01 | RECOMMENDATION | 采纳：将 `scripts/tests/p17-sector-analytics-design-structure.test.mjs` 显式加入契约 §Authority 与 SLICE-01 `allowedWritePaths`（与 SLICE-01 已列一致，消除歧义）。改 §Authority 即需重算 contract_hash。 |
| AMEND-REC-02 | RECOMMENDATION | 采纳：契约明确说明治理角色 artifact（`*-TEST-DESIGN.md`/`*-IMPLEMENTER.md`/`*-REVIEW.md`/`*-VERIFICATION.md`/`*-FINALIZATION.md`/`*-CONTROL.json`）不计入 file/line cap；文档行数不计入 500 行 cap，仅 `.mjs` 静态脚本的代码行计入。产品文件数=5（4 docs+1 script）。不改变 AC 语义，无需重算 hash。 |
| AMEND-REC-03 | RECOMMENDATION | 采纳：实现计划文档须使用固定结构词——每个子任务以 `### 子任务 N`（或 `### ST-N`）开头，含字面字段标签 `写路径：`/`依赖：`/`AC：`/`测试：`/`合并顺序：`；并含一个标题同时包含 `并行` 与 `串行`（或 `DAG`）的章节。冻结的测试断言据此实现。不改变 AC 语义，无需重算 hash。 |

## Resolved Ambiguities（保守决策，记入静态断言）

1. 成交量确认公式名：静态检查接受 `成交量确认` 或 `量价确认`（正则 `(成交量确认|量价确认)`）；其余四个公式名固定 `相对强弱`/`轮动持续性`/`龙头贡献`/`异动提醒`。
2. 每个公式的五要素：设计文档按 `##`/`###` 切分，标题命中公式名之一的 ≥5 段，每段正文须同时含 `输入`/`窗口`/`基准`/`样本`/`失效` 五个关键词。
3. 新增 `## 5.` API 段与新增 DB 规划表块内**不得**出现 `已实现` 字面子串（只能用 `规划`/`未实现`/`规划 V19+`）；既有事实表对照段落可保留 `已实现`。
4. 污染探测：候选写回动词 `UPDATE/写回/回写/覆盖` 与原始事实表名 `market_sector_/stock_/ranking` 共现，且**不含**否定标记 `禁止|不得|严禁|不可|勿|不|未|prohibit|never|read-only|只读` 的行即为违规；含否定标记的合规声明不算违规。
5. §1-§4 / V1-V18 不变：以基线锚点字符串原样保留作为静态断言（非 git diff）。锚点：API `## 1. 当前已实现接口`、`## 2. LongPort 只读行情接口（真实外联已验收）`、`## 3. 行情工作台、采集计划、分钟 K、水位（P1.2）`、`## 4. 安全约束`；DB `### market_sector_watch / market_sector_snapshot / market_sector_member_snapshot`、`状态：已实现（V14，V15 扩展）`、`### market_sector_ranking_batch / market_sector_ranking_item`、`### security_directory_sync_state`、`状态：已实现（V18 migration）`。
6. 预存在 DB 头部 `当前已发布 V1-V17` 已过期（V18 已实现）：静态检查不依赖该字符串，也不把其修正为 `V1-V18` 当作回归。
7. V19+ 绑定：新分析表块须引用 `V19`（或 `V19+`/`V2[0-9]`/`V3[0-9]`）且**不含** `已实现（V1[0-8]`。

## Frozen Test Inventory（单 Node 静态脚本）

脚本：`scripts/tests/p17-sector-analytics-design-structure.test.mjs`（`node:test`+`node:assert/strict`+`fs/promises`，无 shell/网络；与 `scripts/tests/ai-governance.test.mjs` 同构）。每 AC 全部断言通过时打印该 AC 的 selector token，否则非零退出并诊断。

| Test ID | AC IDs | Kind | Required | Source path | Exact selector | Receipt path |
|---|---|---|---|---|---|---|
| TEST-01 | AC-01 | STATIC | YES | `scripts/tests/p17-sector-analytics-design-structure.test.mjs` | `P17-SECTOR-ANALYTICS-AC01`（脚本在该 AC 全部 A01-* 通过时打印；命令 `node scripts/tests/p17-sector-analytics-design-structure.test.mjs`） | `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-EVIDENCE-TEST-01.json` |
| TEST-02 | AC-02 | STATIC | YES | 同上脚本 | `P17-SECTOR-ANALYTICS-AC02` | `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-EVIDENCE-TEST-02.json` |
| TEST-03 | AC-03 | STATIC | YES | 同上脚本 | `P17-SECTOR-ANALYTICS-AC03` | `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-EVIDENCE-TEST-03.json` |
| TEST-GOV-01 | ALL | STATIC | YES | `scripts/validate-ai-governance.mjs` | `node scripts/validate-ai-governance.mjs`（exit 0） | `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-EVIDENCE-TEST-GOV-01.json` |
| TEST-GOV-02 | ALL | STATIC | YES | `scripts/run-ai-governance-gates.mjs` | `node scripts/run-ai-governance-gates.mjs`（exit 0） | `docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-EVIDENCE-TEST-GOV-02.json` |

### TEST-01（AC-01）断言

- A01-01 `docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md` 存在且非空。
- A01-02 按 `^##`/`^###` 切分，标题命中五公式名之一的段 ≥5。
- A01-03 上述每段正文同时含 `输入`/`窗口`/`基准`/`样本`/`失效`。
- A01-04 文档同时含 `原始事实`/`衍生指标`/`提醒事件` 与禁止写回声明（`禁止写回|不得写回|严禁写回|不可写回|不写回|只读.{0,12}原始事实`）。
- A01-05 API 文档含 `^## 5.` 段，其标题或后 5 行含 `板块分析` 与 `规划|未实现`。
- A01-06 API 基线锚点 `## 1.`/`## 2.`/`## 3.`/`## 4.` 原样保留。
- A01-07 DB 含新增 `板块分析`+`规划`+`V19` 块；且基线锚点（market_sector_watch/snapshot/member_snapshot、`V14，V15 扩展`、ranking_batch/item、security_directory_sync_state、`V18 migration`）原样保留。
- A01-08 新 `## 5.` 段与新增 DB 规划块内不含 `已实现`。

### TEST-02（AC-02）断言

- A02-01 扫描四个 artifact，候选写回行（`(?i)(UPDATE\s+(market_sector|stock_|market_sector_ranking)|(写回|回写|覆盖).{0,40}(market_sector|stock_|ranking)|(market_sector|stock_|ranking).{0,40}(写回|回写|覆盖))`）扣除含否定标记行后 = 0。
- A02-02 每个新分析表块引用 `V19+` 且不含 `已实现（V1[0-8]`。
- A02-03 设计或 DB 规划块含只读原始事实声明（`衍生.{0,12}只读|只读.{0,12}(原始事实|market_sector|stock_)|读.{0,8}原始事实`）。

### TEST-03（AC-03）断言

- A03-01 `docs/development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md` 存在且非空。
- A03-02 含 `^###\s+(子任务|ST-|TASK-)` 块 ≥4。
- A03-03 每块均含 `写路径`/`依赖`/`AC`/`测试`/`合并顺序`。
- A03-04 各子任务 `写路径` 后的路径值（匹配 `^(src|docs|scripts)/`）两两不前缀重叠。
- A03-05 含一个标题/段同时含 `并行` 与 `串行`（或 `DAG`）。

## Falsifiability

AC-01/02/03 各有可在纯静态证据下返回 PASS/FAIL 的二值机器断言；不依赖“测试通过”计数或实现者自述。L0 可证伪门槛满足。

## Escalations

无。0 blocking amendments，在 L0 cap=0 之内。

（独立角色 TD-RUN-1 已终止；未编辑任何文件、未调用 git/shell/npm/maven/docker、未召唤子代理、未压缩。）
