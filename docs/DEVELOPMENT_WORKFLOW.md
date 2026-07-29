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
- 标准/长任务由父上下文启用 `qta-development-orchestration` 或 `/qta-run`，选择 lane 并冻结
  `contract_hash`。子角色只接收 TaskPacket。
- 契约冻结后由父协调者创建 `contract` 阶段提交；子角色不得操作 Git。
- 只有任务契约记录用户授权的 `git_automation` 允许时才能实际提交或推送；缺省为 `NONE`。

### 1.4 开发与自检
- **后端**：分层 `controller/service/manager/dao/model/dto/vo/convert`，MyBatis XML SQL，MapStruct 转换，BigDecimal 金额，`ErrorCodeEnum` 错误码，中文 Javadoc。
- **前端**：feature-based，mock/remote 双模式，不用 `any`，覆盖 loading/empty/error/retry 状态，盈利红亏损绿。
- **测试**：覆盖核心场景与边界（参考 `acceptance/ACCEPTANCE_LOG.md` 已有覆盖度）。
- **角色边界**：实施者可实现和运行自测，但只能标记 `SELF_CHECKED`，不得给出最终验收结论。
- **断点**：阶段完成、重复失败、外部阻塞或达到上下文预算时启用 `qta-task-checkpoint`。
- **候选版本**：自检通过后由父协调者创建 `candidate` 提交，记录 tree hash 和 patch SHA-256；
  该版本尚未独立验收，不能标记可部署。

### 1.5 联调
- 后端 `docker compose up -d --build`；前端 `VITE_DEV_PROXY_TARGET=http://localhost:8080 npm run dev`（不覆盖 `.env.local`）。
- curl 端到端 + 浏览器（Playwright）验证关键路径与控制台。

### 1.6 独立测试验收
- 必须由未参与实现的干净上下文执行 `qta-independent-verification`；验收者不得修复生产代码。
- 后端：`./mvnw test` + `./mvnw package`。
- 前端：`npm run typecheck` / `lint` / `test` / `build`。
- 浏览器：页面渲染 + 控制台无 deprecated/error。
- 按任务契约分别记录 `STATIC/AUTOMATION/RUNTIME/DEPLOYMENT`，未执行是 `NOT_VERIFIED`，环境缺失是 `BLOCKED`。
- 验收输出 `ACCEPTED/CONDITIONALLY_ACCEPTED/REJECTED/BLOCKED`，不得用笼统“全绿”代替逐 AC 证据。
- Reviewer 与 verifier 必须绑定同一 contract/candidate/patch hash。候选变化后旧结论自动失效。
- Verifier 在 disposable worktree 中执行门禁，前后 tracked tree 必须不变。

### 1.7 交付收口
- 只有独立验收允许交付后才启用 `qta-delivery-finalization`。
- 更新 `AI_HANDOFF.md`（只保留当前接手事实，历史进 `DEVELOPMENT_LOG`，不无限追加）。
- 必要时更新 `BUILD_CHECKLIST.md`（**只有实际验收通过才勾选**）。
- 实测结果追加 `acceptance/ACCEPTANCE_LOG.md`（用 `docs/templates/ACCEPTANCE_TEMPLATE.md`）。
- 长任务 / 中断任务 / 跨模型接力任务：用 `docs/templates/TASK_HANDOFF_TEMPLATE.md` 新增 `docs/ai/HANDOFF_YYYY-MM-DD_<topic>.md`，记录当前 git 状态、变更文件、已跑命令、失败点和下一步提示词。
- 父协调者创建 `finalization` 提交。可先 checkpoint-push 任务分支备份；只有 accepted revision
  才能 delivery-push，禁止自动直推受保护/default 分支。

## 2. 开发结束文档同步检查（必做）

开发完成后逐项确认（**有变化才更新**）：

| 检查项 | 触发条件 | 必须更新的文档 |
| --- | --- | --- |
| API 变化（新增/修改/删除接口） | 是 | `api/API_INDEX.md` + 对应 `api/*.md` + `mock/MOCK_REMOTE_CONTRACT.md` |
| DB 变化（新表/字段/migration） | 是 | 新增 `src/main/resources/db/migration/V*.sql` + `DATABASE_DESIGN.md` + `CURRENT_ARCHITECTURE_AND_MODULES.md` |
| Mock 契约变化（key/ID/计算口径） | 是 | `mock/MOCK_REMOTE_CONTRACT.md` |
| 产品状态/功能完成度/优先级/路线图变化 | 是 | `BUILD_CHECKLIST.md` + `PRODUCT_BLUEPRINT.md` + 前端 `src/features/build-status/api/buildStatusData.ts` + `buildStatusData.test.ts` |
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
