# Development Workflow

> 定义从需求到交接的标准流程，以及每阶段必须读 / 必须更新的文档。**AI 开发结束后必须执行"开发结束文档同步检查"。**

## 1. 流程阶段

### 1.0 上下文加载
- **Level 1 必读**：`AGENTS.md` + `CLAUDE.md` + `AI_DEVELOPMENT_INDEX.md` + `AI_HANDOFF.md` + `git status --short`。
- **条件读取**：长任务、恢复任务或上下文风险任务再读 `ai/PROGRESSIVE_DISCLOSURE_PROTOCOL.md`。
- **先产出**：本轮 Task Context Manifest，明确任务类型、影响模块、必读文档、禁止读取范围、计划验证命令。
- **原则**：只按任务路由读取必要文档；不得一次性读取整个 `docs/`、历史提示词或长日志。
- **Skill**：启用 `qta-context-bootstrap`；本阶段只读和路由，不改代码、不判定完成。

### 1.1 需求
- **读**：对应 `docs/features/<设计>.md` + `BUILD_CHECKLIST.md`，不重复读取入口文档。
- **产出**（新功能）：用 `docs/templates/FEATURE_DESIGN_TEMPLATE.md` 起草设计，沉淀到 `docs/features/`。
- **Skill**：需求不清或行为变化时启用 `qta-product-design`。

### 1.2 设计
- **读**：`CURRENT_ARCHITECTURE_AND_MODULES.md` + `DATABASE_DESIGN.md` + `decisions/ADR_INDEX.md` + `mock/MOCK_REMOTE_CONTRACT.md`。
- **决策**：重要且长期有效的架构决策新增 ADR（`docs/templates/ADR_TEMPLATE.md`）。

### 1.3 任务契约
- 非简单任务使用 `qta-task-contract` 冻结范围、非目标、AC、证据、验证维度、角色和停止条件。
- 在实现前由独立测试设计者检查 AC 是否可证伪；测试设计者不改代码。
- 冻结实现切片与测试清单：每个初始 slice 最多 3 个 AC、8 个预期文件、500 行生产代码增量；每个
  required test 必须有稳定 test ID、AC 映射、source path 和 exact selector。
- 标准/长任务由父上下文启用 `qta-development-orchestration` 或 `/qta-run`，选择 L0-L3 风险 lane 并冻结
  `contract_hash`。子角色只接收 TaskPacket。
- `/qta-run` 不安装 Stop Hook。使用 Goal 模式时，ZCode 原生 Goal 是唯一续跑控制器；项目门禁只
  判定状态，不触发下一轮。中途停止必须写 `CHECKPOINTED` 或 `BLOCKED`；只有显式通过
  `check-ai-delivery-ready.mjs` 才能宣称交付。
- Hook 内部异常必须转换为明确阻断；失败派发没有 PreToolUse 回执时不再重复报错，成功派发没有
  回执则阻断。活动任务内父子角色均不得调用 `AskUserQuestion`。
- `/qta-run` 是无人值守流程：禁止调用 `AskUserQuestion`。可逆工程选择自动采用文档或明确推荐项；
  产品/金融含义、破坏性操作、凭据授权或外部依赖确实无法安全继续时，必须持久化 `BLOCKED`，不得
  挂起等待用户选择。
- 父协调者创建 `<TASK-ID>-CONTROL.json`，每次角色派发和状态迁移前运行
  `node scripts/check-ai-task-control.mjs <control-file>`。
- Codex 沙箱若因 `.git/qta-governance` 只读返回 `EPERM/EACCES`，仅为上述控制命令申请受限 `.git`
  写权限并重跑；不得设置关闭 anchor 的环境变量。
- 契约冻结后由父协调者创建 `contract` 阶段提交；子角色不得操作 Git。
- 只有任务契约记录用户授权的 `git_automation` 允许时才能实际提交或推送；缺省为 `NONE`。

### 1.4 开发与自检
- **后端**：分层 `controller/service/manager/dao/model/dto/vo/convert`，MyBatis XML SQL，MapStruct 转换，BigDecimal 金额，`ErrorCodeEnum` 错误码，中文 Javadoc。
- **前端**：feature-based，mock/remote 双模式，不用 `any`，覆盖 loading/empty/error/retry 状态，盈利红亏损绿。
- **测试**：覆盖核心场景与边界（参考 `acceptance/ACCEPTANCE_LOG.md` 已有覆盖度）。
- **角色边界**：实施者可实现和运行自测，但只能标记 `SELF_CHECKED`，不得给出最终验收结论。
- **实施切片**：一个干净 implementer 只接一个 frozen slice。父协调者不得在子角色超时后接管实现；
  两次同 slice timeout 后记录 `BLOCKED` 并重新切片。
- **角色实例**：初始实现、每轮 repair、每代 review、最终 verifier 都使用新的 role/session；禁止
  延续旧子会话。
- **无人值守权限**：implementer 与 final verifier 使用 `bypassPermissions` 运行其冻结范围内的 Bash
  门禁；该模式不扩大工具、路径、Git 或业务边界。测试设计者与 reviewer 继续保持只读 `plan`。
- **Shell 节制**：实现者用 Read/Glob/Grep 浏览文件，Bash 只运行必要的聚焦门禁；同一失败不得通过
  反复更换 `grep/head/tail` 形式重跑完整测试。
- **测试节奏**：开发/repair 跑聚焦测试；最终候选冻结前跑一次 full/package；独立 verifier 再跑
  一次，不对未变化候选重复跑全量门禁。
- **架构自检**：候选冻结前运行 `node scripts/check-ai-architecture.mjs --base <baseline>
  --architecture-review-count <count> --candidate-identity <candidate> --json-output <report>`；SNAPSHOT
  模式增加 `--manifest <candidate-manifest>`。任何 error/非零退出均阻断，不能由 reviewer 文字豁免。
- **断点**：阶段完成、重复失败、外部阻塞或达到上下文预算时启用 `qta-task-checkpoint`。
- **候选版本**：自检通过后由父协调者创建 `candidate` 提交，记录 tree hash 和 patch SHA-256；
  该版本尚未独立验收，不能标记可部署。
- **候选补丁**：统一用 `scripts/create-candidate-diff.mjs` 写入 Git 忽略的
  `.qta-governance/candidates/<TASK-ID>/`；默认超过 512 KiB 必须拆分，禁止将完整 patch 提交到
  `docs/development/tasks/`。
- **跨仓任务**：每个仓库使用独立 control/candidate/gate，再建立只做联调证据的集成任务；一个
  control 的 `allowedWritePaths` 不能包含绝对路径或 `..`。

### 1.5 联调
- 后端 `docker compose up -d --build`；前端 `VITE_DEV_PROXY_TARGET=http://localhost:8080 npm run dev`（不覆盖 `.env.local`）。
- curl 端到端 + 浏览器（Playwright）验证关键路径与控制台。

### 1.6 独立测试验收
- 必须由未参与实现的干净上下文执行 `qta-independent-verification`；验收者不得修复生产代码。
- 后端：`./mvnw test` + `./mvnw package`。
- 前端：`npm run typecheck` / `lint` / `test` / `build`。
- 浏览器：页面渲染 + 控制台无 deprecated/error。
- 按任务契约分别记录 `STATIC/AUTOMATION/RUNTIME/DEPLOYMENT`，未执行是 `NOT_VERIFIED`，环境缺失是 `BLOCKED`。
- 同时记录 `FUNCTIONAL/ARCHITECTURE`；二者都通过才可验收。
- 验收输出 `ACCEPTED/CONDITIONALLY_ACCEPTED/REJECTED/BLOCKED`，不得用笼统“全绿”代替逐 AC 证据。
- Reviewer 与 verifier 必须绑定同一 contract/candidate/patch hash。候选变化后旧结论自动失效。
- Verifier 在 disposable worktree 中执行门禁，前后 tracked tree 必须不变。
- Verifier 必须处于可执行命令的模式，并用 `scripts/run-ai-evidence-command.mjs` 为冻结 test inventory
  逐项生成机器回执；plan-only、旧报告、泛化“测试全绿”或父角色代跑均不算验收。

### 1.7 交付收口
- 只有独立验收允许交付后才启用 `qta-delivery-finalization`。
- 更新 `AI_HANDOFF.md`（只保留当前接手事实，历史进 `DEVELOPMENT_LOG`，不无限追加）。
- 必要时更新 `BUILD_CHECKLIST.md`（**只有实际验收通过才勾选**）。
- 实测结果追加 `acceptance/ACCEPTANCE_LOG.md`（用 `docs/templates/ACCEPTANCE_TEMPLATE.md`）。
- 任务改变用户可见能力、完成度、优先级或验证层级时，必须按
  `features/BUILD_STATUS_BOARD_V2_DESIGN.md §6` 同步建设看板：更新能力节点、追加最近交付、刷新快照时间和
  可核实的提交/验收证据。只改代码或日志而未同步看板，不算交付收口完成。
- 长任务 / 中断任务 / 跨模型接力任务：用 `docs/templates/TASK_HANDOFF_TEMPLATE.md` 新增 `docs/ai/HANDOFF_YYYY-MM-DD_<topic>.md`，记录当前 git 状态、变更文件、已跑命令、失败点和下一步提示词。
- 父协调者创建 `finalization` 提交。可先 checkpoint-push 任务分支备份；只有 accepted revision
  才能 delivery-push，禁止自动直推受保护/default 分支。
- `FINALIZED` 后将控制状态推进到 `DELIVERY_READY`，确认所有任务 artifact 已跟踪且工作树无新增脏
  路径，再运行 `node scripts/check-ai-delivery-ready.mjs <control-file>`。只有 exit 0 才允许 Goal 完成。

## 2. 开发结束文档同步检查（必做）

开发完成后逐项确认（**有变化才更新**）：

| 检查项 | 触发条件 | 必须更新的文档 |
| --- | --- | --- |
| API 变化（新增/修改/删除接口） | 是 | `api/API_INDEX.md` + 对应 `api/*.md` + `mock/MOCK_REMOTE_CONTRACT.md` |
| DB 变化（新表/字段/migration） | 是 | 新增 `src/main/resources/db/migration/V*.sql` + `DATABASE_DESIGN.md` + `CURRENT_ARCHITECTURE_AND_MODULES.md` |
| Mock 契约变化（key/ID/计算口径） | 是 | `mock/MOCK_REMOTE_CONTRACT.md` |
| 产品状态/功能完成度/优先级/验证层级/路线图变化 | 是 | `BUILD_CHECKLIST.md` + `PRODUCT_BLUEPRINT.md` + `features/BUILD_STATUS_BOARD_V2_DESIGN.md` 规定的前端快照、最近交付与测试 |
| 重要架构决策 | 是 | 新增 `decisions/ADR-XXXX-*.md` + 更新 `ADR_INDEX.md` |
| 重要开发记录 | 产品/架构/功能/缺陷/契约/治理有实质变化时 | `development/DEVELOPMENT_LOG.md` 追加一条（用 `DEVELOPMENT_LOG_TEMPLATE.md`）；普通问答/只读检查/错别字不追加 |
| 验收执行 | 是 | `acceptance/ACCEPTANCE_LOG.md` 追加 |
| 跨会话接力 / 任务中断 / 上下文过大 | 是 | `docs/ai/HANDOFF_YYYY-MM-DD_<topic>.md`（用 `TASK_HANDOFF_TEMPLATE.md`） |

## 3. 禁止

- 把未实际执行的验证写成通过。
- 用旧聊天或旧文档覆盖当前代码事实（冲突时按 `AI_DEVELOPMENT_INDEX.md §2` 优先级裁决）。
- 每轮把所有历史追加到 `AI_HANDOFF.md`（历史进 `DEVELOPMENT_LOG`）。
- 复制多份接口定义（用 `API_INDEX.md` 链接到唯一详细文档）。
- 在未确认任务范围前开启专家团、读取全量文档、读取历史 JSONL 或无限重跑验证。
- 验证失败后无上限地循环修复；同一 failure fingerprint 最多两轮，第二轮仍失败且无新证据时，
  写明阻塞原因和下一步。
- 由同一个上下文完成实现、修改测试、修复缺陷并给自己最终验收通过。
- 子代理继续创建子代理，或多个角色重复读取全量背景、重复制定同一份计划。
- 子角色 stage/commit/push，或父协调者把未验收的 checkpoint push 描述为可部署交付。
- 复用上一轮 implementer/reviewer/verifier 会话，或让发生 compaction/禁止工具调用的角色 artifact
  继续参与验收。
- 用短间隔 `wait_agent`/`write_stdin` 制造状态轮询，或对同一候选重复执行未变化的全量门禁。
