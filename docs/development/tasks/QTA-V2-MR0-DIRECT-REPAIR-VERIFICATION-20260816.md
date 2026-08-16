# MR-0 收口直接修复独立验收记录 — QTA-V2-MR0-DIRECT-REPAIR-20260816

- 日期：2026-08-16
- 范围：仅治理校验器时间门禁修复与回归测试；无生产代码、API、DB 变更。
- 实施方式：按用户指令直接实施（无 QTA orchestration、子代理或 Goal 模式）；由 Codex 独立验收。

## 1. 历史事实（不可改写）

- `QTA-V2-MR0-CLOSEOUT-20260815-R1` 的 CONTROL 以 `BLOCKED` 终结，保留为审计事实；证据与根因见 `QTA-V2-MR0-CLOSEOUT-20260815-R1-BLOCKED-CLOSURE.md`（其中 "562 tests" 与 "以 R2 新任务重开" 建议均为当时记录，保留原文）。
- R1 gen-3 候选 `4736a6c`（review G3 PASS）保留。

## 2. 直接修复内容（对应 BLOCKED-CLOSURE §4 R2 修复设计）

1. `reviewClearTransitionAt`（`scripts/check-ai-task-control.mjs`）：顺序扫描 `transitionHistory`，每次进入 `CANDIDATE_FROZEN` 候选代数递增，REVIEW_CLEAR 绑定当前代数；移除 `occurrences[generation-1]` 出现序启发式。
2. `validateVerifierDispatchOrdering`：已接受 FINAL_VERIFIER 缺少同代 REVIEW_CLEAR 时显式报错，不再静默跳过。
3. 回归测试（`scripts/tests/qta-role-ordering.test.mjs`）：新增三个确定性场景——真实多周期（gen-1 review FAIL → gen-2 CLEAR → gen-3 CLEAR，不反向误伤 gen-2 verifier）、跨代提前派发（gen-3 verifier 在 gen-3 REVIEW_CLEAR 前派发必须失败，即使 gen-2 CLEAR 已存在）、缺少同代 REVIEW_CLEAR 必须失败。

## 3. 验证结果（独立验收通过）

- 排序专项 `node --test scripts/tests/qta-role-ordering.test.mjs`：**10/10 PASS**。
- 治理组合 `ai-governance.test.mjs + qta-role-ordering.test.mjs`：**84/84 PASS**。
- 后端 `./mvnw test`：**564 tests / 0 failure / 0 error / 1 skipped**；`./mvnw -DskipTests package` PASS。
- `git diff --check` PASS。
- 真实 PoC `scripts/run-mr0-poc.sh`：**SUCCESS，213 秒**。
- 两次分析哈希一致：`1cb27099b8728b8ae029038886330bde6bd6ec33a47f07301cf078df86ca7e2a`。
- 二次导入 universe/membership/dailyBar/moneyFlow `inserted` 全部为 0（幂等实证）。
- universeSize=151、bar=3080、membership=101、moneyflow=3432；`failures=[]`。

## 4. 结论与边界

- **MR-0 代码与本直接修复验收通过，可以合并 main。**
- MR-0 仍只是样本级 PoC，**不等于 MR-1 全市场数据底座**；全市场逐股历史、PIT 申万成分、官方口径资金流的输入边界以 MR-0 POC-REPORT 四要素为准，保持不变。
- 下一阶段：**MR-1 市场全景 MVP**（基准走势、成交量、流动性/活跃度、市场广度、行业成交占比迁移）。
