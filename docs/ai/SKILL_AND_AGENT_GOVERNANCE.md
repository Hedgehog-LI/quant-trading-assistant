# Skill And Agent Governance

> 本文是项目级 Skill、固定 Agent、父协调流程、证据身份和 Git 阶段门禁的唯一事实来源。
> Skill 定流程，Agent 定角色，父协调器定顺序，Hook/脚本定强制约束，任务状态文件负责跨对话记忆。

## 1. 三阶段路线与原十项映射

原十项治理能力没有删除，归并为三个可独立验收的实施阶段：

| 原序号 | 能力 | 所属阶段 | 当前状态 |
|---|---|---|---|
| 1 | 规范源、渐进式 Skill 和触发边界 | 核心流程层 | VERIFIED |
| 2 | 四个固定 Agent 角色与工具/Skill 白名单 | 核心流程层 | VERIFIED |
| 3 | 父协调器、顺序状态机和三种任务通道 | 核心流程层 | VERIFIED |
| 4 | TaskPacket、契约/候选/补丁哈希和跨角色 artifact | 核心流程层 | VERIFIED |
| 5 | 任务分支、阶段 commit、checkpoint push 和 delivery push | 核心流程层 | VERIFIED |
| 6 | Hook：阻止越权写入、递归 Agent、危险 Git/Bash | 强制执行层 | PLANNED |
| 7 | Schema、原子同步、精确静态路由和机器门禁 | 强制执行层 | PARTIAL；精确路由已实现 |
| 8 | ZCode 真实 Skill/Agent 发现、调用和拒写冒烟测试 | 强制执行层 | PARTIAL；Skill 发现已验证 |
| 9 | CI/pre-commit、跨平台和 Claude/Codex 兼容验证 | 持续治理层 | PLANNED |
| 10 | 真实模型触发抽样、治理指标、定期复盘和版本维护 | 持续治理层 | PLANNED |

核心流程层完成不等于整套治理完成。只有强制执行层通过后，才可以说角色隔离和自动路由具有
机器证据；持续治理层负责防止以后逐渐失效。

## 2. 目录与兼容策略

- `.agents/skills/`：十个项目 Skill 的规范源，ZCode 和 Codex 从这里发现项目 Skill。
- `.agents/skill-manifest.json`：静态启发式路由策略，不是模型真实触发器。
- `.agents/skill-evals/trigger-cases.json`：静态精确集合回归用例。
- `.claude/skills/`：Claude 兼容镜像，内容必须与规范源一致。
- `.zcode/agents/`：四个项目级固定 ZCode 角色模板。
- `.zcode/commands/qta-run.md`：显式父协调入口 `/qta-run`。
- `docs/development/tasks/`：任务契约、状态、角色 artifact 和验收报告。
- `scripts/evaluate-skill-triggers.mjs`：启发式静态路由 lint，不代表模型真实选择结果。
- `scripts/validate-ai-governance.mjs`：结构、镜像、元数据和角色策略静态校验。

修改 Skill 时先改规范源与 manifest，再同步 Claude 镜像并运行门禁。禁止长期人工维护不同内容。

## 3. 十个 Skill 的边界

| Skill | 负责 | 不负责 |
|---|---|---|
| `qta-context-bootstrap` | 最小上下文、任务分类、单阶段路由 | 实现、测试、验收 |
| `qta-development-orchestration` | 父级 lane、状态机、角色顺序、Git 门禁 | 充当任何子角色 |
| `qta-product-design` | 产品行为、业务口径、范围、AC 草案 | 写代码、判定交付 |
| `qta-task-contract` | 冻结范围、AC、证据、角色、停止条件 | 实现 |
| `qta-backend-implementation` | 后端实现与自检 | 独立验收 |
| `qta-frontend-implementation` | 前端实现与自检 | 独立验收 |
| `qta-openclaw-integration` | OpenClaw 安全和领域约束叠加 | 泛化 Agent/专家团任务 |
| `qta-task-checkpoint` | 进度、证据、阻塞、下一步存档 | 宣称完成 |
| `qta-independent-verification` | 干净上下文独立验收 | 修复代码或普通审查 |
| `qta-delivery-finalization` | 验收后的文档、看板、部署交底 | 绕过验收或修复代码 |

ZCode 真实触发使用 `name + description（约前 250 字符）+ when_to_use` 语义判断。静态 manifest
只用于发现明显冲突。运行时一次选择一个 lifecycle stage，可额外选择一个 domain overlay；
父协调器是 controller，不是 lifecycle stage。

## 4. 固定角色与持久化

| Agent | 上下文 | 工具边界 | 输出 |
|---|---|---|---|
| `qta-test-designer` | 干净 | 只读、不执行命令 | 可证伪 AC、测试矩阵、契约 amendment artifact |
| `qta-implementer` | 独立实现上下文 | 可读写、自测，不可 Git/子代理 | 代码、自检、变更清单、候选提交建议 |
| `qta-code-reviewer` | 干净 | 只读、不执行命令 | 绑定 candidate hash 的 findings 或 `REVIEW_CLEAR` |
| `qta-final-verifier` | 干净临时 worktree | 可执行门禁、不可编辑/Git | 逐 AC 证据、前后 hash、唯一验收结论 |

每个角色只接收 TaskPacket，不接收完整聊天历史。只读角色不写仓库；父协调者原样保存其结构化
artifact。任何角色都禁止创建子 Agent。

## 5. 父协调状态机

显式长任务优先使用 `/qta-run <任务或契约路径>`。父协调器选择：

- `TRIVIAL`：contract-lite + implementer + clean final verifier。
- `STANDARD`：test designer + implementer + code reviewer + final verifier。
- `LONG_HIGH_RISK`：四角色 + 专用任务分支/worktree + 阶段 checkpoint。

状态必须顺序迁移：

```text
CONTEXT_READY -> CONTRACT_DRAFTED -> TEST_DESIGN_READY -> CONTRACT_FROZEN
-> IMPLEMENTING -> SELF_CHECKED -> CANDIDATE_FROZEN -> REVIEW_CLEAR
-> VERIFIED -> FINALIZED
```

Candidate identity 使用 `COMMIT`（commit/tree/patch hash）或 `SNAPSHOT`（确定性文件清单和
manifest/entry-set hash）。无 Git 写授权时使用 SNAPSHOT。Candidate 改变会使旧 review/verdict
失效；contract 改变会使 candidate/review/verdict 全部失效。
同一 failure fingerprint 最多两轮修复，不能通过新开上下文清零次数。

## 6. Git、Commit 和 Push

父协调者是唯一 Git owner，子角色不得 stage、commit、rebase、merge 或 push。

任务契约必须根据用户明确授权冻结 `git_automation`：

- `NONE`：只准备路径和提交信息。
- `COMMIT`：父协调者可创建阶段提交。
- `COMMIT_AND_CHECKPOINT_PUSH`：可额外推送完整阶段到任务分支。
- `DELIVERY_PUSH`：可额外推送 accepted finalization revision 到批准目标。

未记录授权默认为 `NONE`，不能从“自主开发/跑一整晚”推断 Git 写权限。

阶段提交：

1. `contract`：测试设计完成、契约冻结。
2. `candidate`：实现完成并达到 `SELF_CHECKED`。
3. `repair-N`：每组已确认 findings 对应一个修复提交。
4. `finalization`：独立验收允许交付后，提交文档和交付记录。

每次提交前必须检查：

- staged 路径只属于当前 TaskPacket。
- 不包含任务开始前的 dirty paths、密钥、`.env` 或运行产物。
- 阶段要求的门禁已通过。
- task state 已准备好提交前可知字段。

提交后再计算 immutable commit/tree/patch identity，并更新 task state。需要远程 checkpoint 时，
另建只包含状态和角色 artifact 的 metadata commit；它不是新 candidate，reviewer/verifier 仍绑定
原 candidate identity，而不是任务分支 HEAD。

在授权级别允许时，`checkpoint push` 可以把完整阶段提交推到任务分支作为备份，但不能宣传为可部署。
`delivery push` 只能推送未变化的 accepted candidate + finalization。禁止自动直推受保护/default
分支、force push，禁止把 push 失败写成成功。

## 7. 防止假绿灯和无限循环

- 验收标准在实现前冻结，不能由实现者在失败后降低。
- 实现者最高只能标记 `SELF_CHECKED`。
- 独立核验分开记录 `STATIC/AUTOMATION/RUNTIME/DEPLOYMENT`。
- 未执行为 `NOT_VERIFIED`；外部环境缺失为 `BLOCKED`。
- 测试数量、构建成功或 HTTP 200 不能单独证明业务 AC。
- Reviewer 和 verifier 检查相同 candidate/hash generation。
- Verifier 只在 disposable worktree 运行命令，前后 tracked tree 必须不变。
- 两轮同因失败后停止并 checkpoint；不得递归建团或无限重跑。

## 8. 上下文和启动清单

唯一 Level 1 启动清单是：

```text
AGENTS.md
CLAUDE.md
docs/AI_DEVELOPMENT_INDEX.md
docs/AI_HANDOFF.md
git status --short
```

长任务、恢复任务或上下文风险任务再读 `docs/ai/PROGRESSIVE_DISCLOSURE_PROTOCOL.md`。任务契约只
链接文档，不复制产品历史。25% 固化发现，40% 停止开启新工作流，60% 交接干净上下文。

## 9. 当前静态维护门禁

修改 Skill/Agent 后必须：

1. 说明触发与不触发场景。
2. 保持单一职责。
3. 更新精确触发回归用例。
4. 更新 manifest 和 Agent policy。
5. 同步 Claude 镜像。
6. 运行 `node scripts/evaluate-skill-triggers.mjs`。
7. 运行 `node scripts/validate-ai-governance.mjs`。
8. 用本机 ZCode CLI 检查 Skill discovery。
9. 由未参与修改的干净上下文复核。

这些静态门禁不能替代下一阶段的 Hook、真实角色调用和真实模型触发测试。
