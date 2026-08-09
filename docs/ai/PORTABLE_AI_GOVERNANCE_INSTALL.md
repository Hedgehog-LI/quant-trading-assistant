# Portable AI Governance Install

## 1. 能否直接复制

同一个 QTA 项目换电脑时不需要手工复制：提交这些文件并在另一台电脑 `git pull` 即可。

复制到其他项目时必须区分两层：

- **治理内核**：状态机、固定角色、Hook、安全门禁、候选与证据脚本，可复用。
- **项目适配层**：上下文入口、产品/架构文档路由、实现 Skill、测试命令，必须按目标项目改写。

不能把 QTA 的 `qta-context-bootstrap` 和前后端实现规范原样放进无关项目后直接运行，否则模型会读取
不存在或错误的 QTA 文档。

## 2. 同一 QTA 项目复制清单

若另一台电脑运行的仍是本项目，复制以下相对路径即可；推荐通过 Git 提交后拉取，避免漏文件：

```text
AGENTS.md
CLAUDE.md
.agents/
.zcode/
.claude/skills/
docs/AI_DEVELOPMENT_INDEX.md
docs/AI_HANDOFF.md
docs/DEVELOPMENT_WORKFLOW.md
docs/ai/
scripts/check-ai-architecture.mjs
scripts/check-ai-delivery-ready.mjs
scripts/check-ai-task-control.mjs
scripts/create-candidate-diff.mjs
scripts/create-candidate-manifest.mjs
scripts/evaluate-skill-triggers.mjs
scripts/run-ai-evidence-command.mjs
scripts/run-ai-governance-gates.mjs
scripts/validate-ai-governance.mjs
scripts/zcode-governance-hook.mjs
scripts/tests/ai-governance.test.mjs
```

## 3. 不同项目的治理内核清单

从本仓库复制到目标项目根目录的相同相对路径：

```text
.zcode/config.json
.zcode/commands/qta-run.md
.zcode/agents/
.agents/schemas/qta-task-control.schema.json
.agents/skills/qta-development-orchestration/
.agents/skills/qta-task-contract/
.agents/skills/qta-task-checkpoint/
.agents/skills/qta-independent-verification/
.agents/skills/qta-delivery-finalization/
scripts/check-ai-architecture.mjs
scripts/check-ai-delivery-ready.mjs
scripts/check-ai-task-control.mjs
scripts/create-candidate-diff.mjs
scripts/create-candidate-manifest.mjs
scripts/run-ai-evidence-command.mjs
scripts/zcode-governance-hook.mjs
scripts/validate-ai-governance.mjs
scripts/evaluate-skill-triggers.mjs
scripts/run-ai-governance-gates.mjs
scripts/tests/ai-governance.test.mjs
```

目标项目 `.gitignore` 增加：

```gitignore
.qta-governance/
```

`validate-ai-governance.mjs` 当前会检查 QTA 的十个 Skill 和镜像完整性。不同项目不能在只复制内核后
立即运行它；必须先完成下一节适配，或者为目标项目编写自己的静态验证器。

## 4. 必须改写的项目适配层

在目标项目创建或改写：

```text
AGENTS.md
CLAUDE.md
.agents/skills/qta-context-bootstrap/SKILL.md
.agents/skills/qta-backend-implementation/SKILL.md
.agents/skills/qta-frontend-implementation/SKILL.md
docs/AI_DEVELOPMENT_INDEX.md
docs/AI_HANDOFF.md
docs/DEVELOPMENT_WORKFLOW.md
```

至少替换：项目名称、仓库边界、权威文档路径、技术栈、测试/构建命令、允许写路径、完成定义和业务
禁止事项。若目标项目不是前后端项目，删除不适用的实现 Skill，并同步修改实施者 Agent 的 `skills`。

## 5. 启用规则

1. 不注册 `Stop` Hook；客户端原生 Goal 是唯一续跑控制器。
2. 一个 control 只负责一个 Git 仓库；跨仓任务按仓库拆分。
3. 子角色禁止 Git 和递归创建 Agent，父协调器是唯一 Git owner。
4. 候选补丁只放 `.qta-governance/candidates/`，不得提交到任务文档。
5. 首次只跑 L0 小任务；治理测试和显式 delivery gate 通过后再使用 L1/L2。
6. 不要一开始运行整夜 L3 或跨仓 Goal。

## 6. 安装后验证

```bash
node scripts/evaluate-skill-triggers.mjs
node scripts/validate-ai-governance.mjs
node --test scripts/tests/ai-governance.test.mjs
node scripts/run-ai-governance-gates.mjs
```

以上四条适用于同一 QTA 项目，或已经完成 Skill、Agent、manifest、触发用例和静态验证器适配的新项目。
静态门禁通过后，再用一个只改一份文档、最多两个 AC 的 L0 任务做真实 ZCode 冒烟测试。
