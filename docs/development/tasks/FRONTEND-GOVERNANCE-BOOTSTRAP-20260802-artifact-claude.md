# Claude Code Project Context (Frontend)

## 新会话流程（必读）

1. 启用 skill `qta-context-bootstrap`（规范源 `.agents/skills/`；`.claude/skills/` 是生成的镜像）。
2. 读 `docs/AI_DEVELOPMENT_INDEX.md`（路由）+ `docs/DEVELOPMENT_WORKFLOW.md`（流程与文档同步规则）。
3. 冲突裁决按 `docs/AI_DEVELOPMENT_INDEX.md §2`：migration+代码+测试 > 架构事实 > API/DB >
   开发/验收日志 > 历史交接。跨仓产品/API/DB 事实以后端控制仓库文档为更高事实来源。
4. 非简单任务先启用 `qta-task-contract`；实现者只做自检，独立验收使用干净上下文和
   `qta-independent-verification`。
5. 标准、跨仓或长任务由父上下文启用 `qta-development-orchestration`（或 `/qta-run`），固定角色只接收
   TaskPacket。固定角色是一次性模板实例：每轮实现、repair、review、verification 都新开 role/session，
   禁止续用。

## 开发结束必做

- 执行 `docs/DEVELOPMENT_WORKFLOW.md §2` 文档同步检查（更新 `docs/AI_HANDOFF.md`、
  `docs/AI_DEVELOPMENT_INDEX.md` 路由；API 变化同步 mock/契约）。
- 前端四门：`npm run typecheck` / `lint` / `test` / `build` 全绿，且遵守 ESLint 架构红线
  （`features/`、`pages/` 不得直接用 localStorage 或 axios，必须走 `shared/api/*`）。
- 只有实际验收通过才能勾选 `docs/BUILD_CHECKLIST.md`。父协调者按 contract、candidate、repair-N、
  finalization 建阶段提交；子角色不操作 Git。checkpoint push 仅备份任务分支，delivery push
  必须绑定 accepted revision。
- 固定角色使用 `.zcode/agents/`，不得让同一角色同时实现、修复并给出最终验收结论。

入口与路由统一在 `docs/AI_DEVELOPMENT_INDEX.md`（含任务类型路由与 Historical 文档清单，当前为
“none yet”）；本文件不再维护重复必读列表。

## 一句话目标

用 React 19 + Vite + TypeScript + Ant Design 6 做一个本地优先的交易辅助前端工作台，覆盖自选股、
计划、交易、账本、持仓快照、复盘、行情与设置。它只做记录、计算、复盘展示，不做自动下单，不连券商，
不保存真实密钥。

## 重要边界

- 不连接真实券商账户。
- 不保存任何真实交易密钥、券商密码、交易密码。
- 不生成“稳赚”“必涨”“无风险”之类结论。
- 所有买卖提示都应表达为“辅助信号 + 风险提示 + 人工确认”。

## 当前状态

P1 功能已上线：包括 security-directory D2 的 `SecuritySelector`（`shared/components/SecuritySelector.tsx`）、
持仓快照、交易账本、复盘、行情工作台等。当前事实以 `docs/AI_HANDOFF.md` + `docs/AI_DEVELOPMENT_INDEX.md`
为准；跨仓产品/API/DB/治理事实以后端控制仓库为更高来源。AI 治理已接入前端仓库，可独立 `/qta-run`
（见 `AGENTS.md` 的“AI 治理接入”段）。

## 开发偏好

- 前后端均已存在，按任务类型进入对应仓库开发：前端本仓库，后端 `/Users/joker/code/quant-trading-assistant`。
- 保持 feature-based 分层；领域类型在 `shared/types`，不滥用 `any`。
- mock 模式不得伪造外部数据或伪造成功；remote 模式 provider 失败要有可解释降级。
