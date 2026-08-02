# Task Contract: FRONTEND-GOVERNANCE-BOOTSTRAP-20260802 前端仓库 AI 治理接入 + D1/D2/D3 事实修正

## Contract Identity

- Status: `FROZEN`
- Contract version: `2` (test-designer amendments BLOCK-01/02/03 accepted: smoke promoted to L1; AC-02 byte-allowlist enumerated; AC-03 tracked/untracked split; AC-07 L1 + diff-name-set; AC-06 negative-grep; file-cap is declarative)
- Frozen at: 2026-08-02T15:00:00Z
- Frozen by parent run: zcode-parent-FRONTEND-GOVERNANCE-BOOTSTRAP-20260802
- Lane: `L2`

## Objective

让 QTA 前端仓库（`frontend-governance-web`）可以独立启动 `/qta-run`，使用与后端一致的固定角色、TaskPacket、Hook、独立验收和 delivery-ready 门禁；建立可维护的规范源/同步方案；并修正后端控制仓库中 D1/D2/D3（已进入 main）/D4（尚未实施）的当前事实文档。最后用一次不修改业务代码的真实 L0 微任务冒烟，验证前端仓库治理闭环。

## Authority

- Product/design: 用户请求（/qta-run 自主执行），本契约
- API/data contract: 后端治理规范源 = 控制仓库 `frontend-governance-control` @ `563e84a`
- Baseline commit (control): `563e84a573426800b3f6aa8e4e0525bc5314b3a8`
- Baseline commit (web): `0cf382fec889bbecb567fd27064040b3901b9c27`
- Baseline branch: `codex/frontend-governance-bootstrap-20260802`（两仓均与各自 main 同点起点）
- Pre-existing dirty paths: 无（两仓工作树均干净）
- Allowed write paths (control): `docs/development/tasks/FRONTEND-GOVERNANCE-BOOTSTRAP-20260802-*`, `docs/AI_HANDOFF.md`, `docs/BUILD_CHECKLIST.md`, `docs/CURRENT_ARCHITECTURE_AND_MODULES.md`, `docs/PRODUCT_BLUEPRINT.md`, `docs/features/SECURITY_DIRECTORY_SEARCH_DESIGN.md`, `docs/development/SECURITY_DIRECTORY_SEARCH_IMPLEMENTATION_PLAN.md`, `docs/ai/HANDOFF_2026-07-17_security_directory_search.md`, `docs/acceptance/ACCEPTANCE_LOG.md`
- Allowed write paths (web): `.agents/**`, `.zcode/**`, `.claude/**`, `scripts/**`, `docs/**`, `AGENTS.md`, `CLAUDE.md`, `GOVERNANCE_SOURCE.md`, `.gitignore`

## Facts And Decisions

| Type | Item |
|---|---|
| FACT | D1 已进入控制仓库 main（`8e4447e`，候选 `f3ba475`）；D3 已进入控制仓库 main（`62a7270` feat + `ff393bc` repair2）；D2 前端 `SecuritySelector` + 目录 API + 四流接入已进入 web 仓库 main（`0cf382f`）并已进入控制仓库 main 的治理/契约文档链。D4（及可选 D3-LongPort enricher）尚未实施。依据 §2 优先级：migration+代码+main commit 为最高事实来源。 |
| FACT | 前端仓库当前无任何治理脚手架（无 `.agents/.zcode/.claude/scripts/AGENTS.md`）。`main` = 候选分支 = `0cf382f`。 |
| FACT | 治理脚本（hook/task-control/delivery-ready/architecture/manifest/evidence/validator/evaluator/sync-skills）经探索确认为 repo-agnostic；`check-ai-architecture.mjs` 与 `tests/ai-governance.test.mjs` 对 `.ts/.tsx` 有效，Spring/Java 分支在前端为 inert。无硬编码绝对路径或仓库名假设。 |
| FACT | `validate-ai-governance.mjs` 强制要求：10 个 skill 目录（canonical+镜像）、4 个 agent 模板、`.zcode/config.json` hook 配置、7 个 active docs、所有 `` `docs/...` `` 引用路径必须存在；前端仓库必须满足这些结构校验。 |
| FACT | 前端仓库 `.env`/`.env.production` 当前被追踪提交，`.claude/settings.local.json` 未被忽略。本任务禁止读取/提交 `.env*` 与任何凭据；仅通过 `.gitignore` 增补 `.claude/settings.local.json` 与 `.env*` 规则，不改变已追踪文件。 |
| DECISION | 单一规范源同步模型：控制仓库为治理规范源；前端仓库 byte-identical 复制 repo-agnostic 治理资产，附 `scripts/sync-governance-from-source.mjs` 与 `GOVERNANCE_SOURCE.md` provenance 标记，禁止本地漂移编辑。防漂移：相同 `validate-ai-governance.mjs` + 字节相等校验。 |
| DECISION | 精简前端 scoped 文档：7 个 active docs 写为前端适用最小版（非复制后端 50+ 文档）；skill 引用的 feature doc 以 pointer stub 存在。 |
| DECISION | 冒烟测试范围：前端仓库真实 **L1** 微任务（采纳 test-designer Option B；L0 强制 `review.omitted=true` 与"含 reviewer 会话"自相矛盾），仅编辑 `GOVERNANCE_SOURCE.md` 一处（governance 元数据，非业务代码），完整 lifecycle（implementer→reviewer→verifier）。 |
| DECISION | 可逆方案自主采纳推荐项，governed run 期间不调用 AskUserQuestion。 |
| ASSUMPTION | 治理脚本在前端 Node v26 下可正常运行（与后端相同 Node 脚本，仅依赖 node:内置模块）。 |
| OPEN_QUESTION | 无。 |

## Scope

### In Scope

1. 前端仓库字节级移植治理资产：`scripts/`（11 个 .mjs + tests）、`.agents/`（skills/schema/manifest/evals）、`.zcode/`（4 agents + qta-run + config）、`.claude/skills/`（镜像）。
2. 新增 `scripts/sync-governance-from-source.mjs` + `GOVERNANCE_SOURCE.md` provenance。
3. 前端 `.gitignore` 增补 `.claude/settings.local.json`、`.env`、`.env.*`、`!.env.example`。
4. 前端精简 `AGENTS.md`/`CLAUDE.md`/`docs/`（7 active docs + feature stubs）。
5. 控制仓库修正 16 行 D1/D2/D3/D4 过时事实（7 文件，不动 ADR/冻结任务工件）。
6. 前端真实 L1 冒烟微任务（仅改 `GOVERNANCE_SOURCE.md`，governance 元数据非业务代码）完整 lifecycle（implementer→reviewer→verifier→finalization）至 `DELIVERY_READY`。

### Out Of Scope

- 前端业务代码、依赖（`package.json`/lock）、页面、路由、API client、产品行为。
- 后端业务代码、`./mvnw` 业务构建。
- OpenClaw/行情/板块/策略/回测业务功能。

### Prohibited

- 复制、读取或提交 `.env`/`.env.local`/`.env.production`/`.claude/settings.local.json`/任何凭据。
- 编辑 ADR（`docs/decisions/`）或冻结任务工件（`docs/development/tasks/SECURITY-DIRECTORY-D2/D3-*`）。
- 合并、修改、推送或 force-push `main`；任何 main 上的 stage/commit/merge/cherry-pick/revert/tag。
- 同因失败两次后无限重试（须 `BLOCKED`）。

## Acceptance Criteria

| AC-ID | Observable behavior | Preconditions/input | Expected result | Evidence | Dimension | Owner | Status |
|---|---|---|---|---|---|---|---|
| AC-01 | 前端仓库 `node scripts/run-ai-governance-gates.mjs` 全绿 | 移植完成后在 web 仓库执行 | skills 测试 + trigger 评估 + validator 0 errors 全部通过 | `run-ai-governance-gates.mjs` 退出码 0 收据 | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-02 | 字节相等集 = {`.agents/skills/**`, `.agents/schemas/**`, `.agents/skill-manifest.json`, `.agents/skill-evals/trigger-cases.json`, `.zcode/**`, `.claude/skills/**`, `scripts/**`} 减去 {`scripts/sync-governance-from-source.mjs`}；与控制仓库 `563e84a` 文件对文件、sha256 对 sha256 一致。明确排除集 = {`AGENTS.md`, `CLAUDE.md`, `docs/**`, `GOVERNANCE_SOURCE.md`, `.gitignore`}（前端 hand-authored，非复制） | `node scripts/sync-governance-from-source.mjs --check --source <control-path> --baseline 563e84a` | 退出码 0 + stdout `0 byte diffs`（命名 allowlist，显式排除集） | sync-check 收据 | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-03 | `.gitignore` 增补 `.env`/`.env.*`/`!.env.example`/`.claude/settings.local.json`；`git check-ignore .claude/settings.local.json` 命中且 `git ls-files --error-unmatch .claude/settings.local.json` 失败（真未追踪）；`.env`/`.env.production` 保持已追踪（不做 `git rm --cached`）；`git diff main --name-only` 不含任何 `.env*`/凭据路径（无新密钥 stage） | 多命令组合校验 | 上述四项全部满足 | `.gitignore` + check-ignore + ls-files + diff 证据 | STATIC | IMPLEMENTER | NOT_STARTED |
| AC-04 | 前端 validator 的 active-docs（AGENTS/CLAUDE/docs/AI_DEVELOPMENT_INDEX/docs/AI_HANDOFF/docs/DEVELOPMENT_WORKFLOW/docs/ai/PROGRESSIVE_DISCLOSURE_PROTOCOL/docs/ai/SKILL_AND_AGENT_GOVERNANCE）与 skill 引用的 `docs/...` 路径均存在且校验通过 | 移植后执行 validator | 上述 7 文档存在；skill 引用路径存在；validator 0 errors | validator 收据 | STATIC | IMPLEMENTER | NOT_STARTED |
| AC-05 | 前端业务零回归 | 移植后执行 `npm run typecheck && npm run lint && npm run test && npm run build` | 四者全绿，`git diff main --stat` 仅含治理/文档/.gitignore | npm 四门收据 + git diff --stat | AUTOMATION | FINAL_VERIFIER | NOT_STARTED |
| AC-06 | 控制仓库 16 行 D1/D2/D3/D4 过时事实已修正为"D1/D2/D3 已进 main；D4 尚未实施"，且保留 runtime/NOT_VERIFIED 警示与冻结候选身份 | 编辑 7 文件后 grep 校验 | 每行修正，不含 "条件验收"/"D2/D3 仍未实现" 过时措辞；ADR 与冻结任务工件零改动 | git diff + grep 证据 | STATIC | IMPLEMENTER | NOT_STARTED |
| AC-07 | 前端真实 **L1** lifecycle 到达 `DELIVERY_READY`，含独立 implementer/reviewer/verifier 角色会话与双向 dispatch 收据；`git diff <smoke-baseline>..HEAD --name-only` 恰为允许集（业务影响变更仅 `GOVERNANCE_SOURCE.md`，加声明的 task-control/evidence 元数据） | 在 web 仓库跑完整 L1 lifecycle | `node scripts/check-ai-delivery-ready.mjs <smoke-control>` 退出码 0 + stdout `AI delivery ready`；diff name-set 恰为 {`GOVERNANCE_SOURCE.md`} ∪ 声明元数据 | delivery-ready 收据 + smoke control + git diff | AUTOMATION | FINAL_VERIFIER | NOT_STARTED |
| AC-08 | 控制仓库治理门禁保持全绿（`run-ai-governance-gates.mjs`），架构门禁在前端冒烟候选上 PASS | 两仓门禁 | 控制 validator 0 errors；前端 architecture errorCount=0 | 两仓 gate 收据 | AUTOMATION | FINAL_VERIFIER | NOT_STARTED |

## Verification Plan

| Dimension | Required | Command or inspection | Pass rule |
|---|---|---|---|
| STATIC | Yes | `git diff` 范围校验 + grep 过时措辞 + `.gitignore`/凭据检查 | 仅治理/文档/.gitignore；无过时措辞；无凭据 |
| AUTOMATION | Yes | 两仓 `node scripts/run-ai-governance-gates.mjs`；前端 `npm run typecheck/lint/test/build`；前端 `check-ai-delivery-ready.mjs` | 退出码 0 |
| RUNTIME | No | — | — |
| DEPLOYMENT | No | — | — |

## Implementation Slices

| Slice ID | Coherent boundary | AC IDs | Allowed write paths | Max files | Max production-line delta |
|---|---|---|---|---:|---:|
| SLICE-01 | 前端字节级移植治理资产 + sync 脚本 + provenance + .gitignore | AC-01, AC-02, AC-03 | web: `.agents/**`, `.zcode/**`, `.claude/**`, `scripts/**`, `GOVERNANCE_SOURCE.md`, `.gitignore` | 8 | 500 |
| SLICE-02 | 前端精简 active docs + AGENTS/CLAUDE + feature stubs | AC-04 | web: `AGENTS.md`, `CLAUDE.md`, `docs/**` | 8 | 500 |
| SLICE-03 | 控制仓库 D1/D2/D3/D4 事实修正（16 行/7 文件） | AC-06 | control: 7 个 docs 文件 | 8 | 500 |
| SLICE-04 | 前端真实 L1 冒烟微任务（仅改 GOVERNANCE_SOURCE.md）完整 lifecycle（implementer→reviewer→verifier→finalization） | AC-05, AC-07, AC-08 | web: `GOVERNANCE_SOURCE.md` + smoke control 文件 | 8 | 500 |

## Frozen Test Inventory

| Test ID | AC IDs | Kind | Required | Source path | Exact selector | Receipt path |
|---|---|---|---|---|---|---|
| TEST-01 | AC-01 | AUTOMATION | YES | scripts/run-ai-governance-gates.mjs (web) | exit code 0 + stdout 含 "QTA AI governance gates passed." | docs/development/tasks/FRONTEND-GOVERNANCE-BOOTSTRAP-20260802-*.receipt |
| TEST-02 | AC-02 | AUTOMATION | YES | scripts/sync-governance-from-source.mjs (web) --check --source <control> --baseline 563e84a | exit code 0 + stdout `0 byte diffs` over 命名 allowlist（`.agents/skills`,`.agents/schemas`,`.agents/skill-manifest.json`,`.agents/skill-evals/trigger-cases.json`,`.zcode`,`.claude/skills`,`scripts` 减 `sync-governance-from-source.mjs`）；显式排除 `AGENTS.md`/`CLAUDE.md`/`docs/**`/`GOVERNANCE_SOURCE.md`/`.gitignore` | receipt |
| TEST-03 | AC-03 | STATIC | YES | .gitignore + git check-ignore + git ls-files + git diff | (i) `.gitignore` 含 `.env`/`.env.*`/`!.env.example`/`.claude/settings.local.json`；(ii) `git check-ignore .claude/settings.local.json` 退出 0；(iii) `git ls-files --error-unmatch .claude/settings.local.json` 退出非 0；(iv) `git diff main --name-only` 无 `.env*`/凭据路径 | receipt |
| TEST-04 | AC-04 | STATIC | YES | scripts/validate-ai-governance.mjs (web) | exit code 0 + stdout "AI governance validation passed: 10 skills, 4 agents." | receipt |
| TEST-05 | AC-05 | AUTOMATION | YES | npm run typecheck/lint/test/build (web) | 四者退出码 0；且 `git diff main --name-only -- 'src/**'` 为空（无生产源码改动） | receipt |
| TEST-06 | AC-06 | STATIC | YES | git diff (control) + grep | (i) diff 恰触及 7 个命名文件；(ii) 16 行修正逐条出现；(iii) 负向 grep `条件验收`/`D2.*仍未实现`/`D3.*仍未实现`/`D4.*已.*实施` 在 D4 上下文外 0 命中；(iv) ADR `docs/decisions/` 与冻结任务工件 `SECURITY-DIRECTORY-D2*/D3*` 0 diff | receipt |
| TEST-07 | AC-07 | AUTOMATION | YES | scripts/check-ai-delivery-ready.mjs <web smoke control> | exit code 0 + stdout "AI delivery ready"；smoke control lifecycleState=DELIVERY_READY；`git diff <smoke-baseline>..HEAD --name-only` 恰为 {`GOVERNANCE_SOURCE.md`} ∪ 声明元数据 | receipt |
| TEST-08 | AC-08 | AUTOMATION | YES | node scripts/run-ai-governance-gates.mjs (control) + check-ai-architecture.mjs (web smoke) | control exit 0 + "QTA AI governance gates passed."；web `check-ai-architecture.mjs --base <smoke-baseline> --candidate-identity <id> --json-output <report>` exit 0 + report errorCount=0 + status=PASS（注：smoke 0 .ts/.tsx 改动，门禁空过仍需记录） | receipt |

## Architecture And Quality Gates

- Required architecture review: `YES`（前端冒烟候选需过 architecture 门禁）
- Triggered thresholds: 文件行数/方法行数/依赖数（`check-ai-architecture.mjs` 默认）
- Required layers/boundaries: 治理资产移植不引入新业务依赖；前端 ESLint 红线（features/pages 禁止直接 axios/localStorage）不变
- Responsibility-map evidence: `check-ai-architecture.mjs` report
- ADR exception and expiry: 无

## Role Assignments

- Test designer: fresh `qta-test-designer`（审查本契约草案）
- Implementer: fresh `qta-implementer` × 每个 slice
- Code reviewer: fresh `qta-code-reviewer`（对冻结候选功能+架构双轨）
- Final verifier: fresh `qta-final-verifier`（disposable worktree，执行全部 receipt）
- Omitted roles and justification: 无（L2 全四角色）

## Candidate And Git Policy

- Git automation: `DELIVERY_PUSH`
- User authorization evidence: 用户请求（git_automation=DELIVERY_PUSH；仅提交推送 `codex/frontend-governance-bootstrap-20260802`；禁止 main/merge/force-push）
- Task branch: `codex/frontend-governance-bootstrap-20260802`（两仓）
- Contract commit: （冻结后记录）
- Candidate mode: `COMMIT`
- Candidate commit: （冻结后记录）
- Candidate tree hash: （冻结后记录）
- Patch SHA-256: （冻结后记录）
- Candidate manifest path/hash: N/A（COMMIT 模式）
- Checkpoint push allowed: `YES`（仅任务分支备份）
- Delivery push target: `codex/frontend-governance-bootstrap-20260802`（两仓）
- Protected/default branch direct push: `NO`

## Checkpoint Policy

- Context budget: contextMeasurement=UNAVAILABLE；按 turn/wait/poll/compaction 限制执行
- Persist discoveries at: 25%
- Stop opening stages at: 40%
- Mandatory fresh-context handoff at: 60%
- Maximum waits per role run: 2
- Maximum shell polls per command: 3
- Automatic compaction policy: first compaction forces handoff; second is prohibited
- Maximum repair rounds for one failure fingerprint: 2
- Lane AC cap: L2 = 8（本契约 8 AC，已满）
- Blocking amendment cap: L2 = 3
- Blocking amendment history: （待 test-design）
- Stop conditions: 同因失败两次 → BLOCKED；产品/金融语义未决 → BLOCKED；外部依赖使所有安全路径不可行 → BLOCKED
