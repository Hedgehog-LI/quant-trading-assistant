# Quant Trading Assistant Web - Project Guide

## 新会话上下文加载（必读）

启用项目 skill `qta-context-bootstrap`（分阶段加载，避免一次读全部 docs）。信息真实性优先级与
任务路由见 `docs/AI_DEVELOPMENT_INDEX.md` §2/§4；开发结束文档同步见 `docs/DEVELOPMENT_WORKFLOW.md`。

本文件是给 AI（Claude / Codex / ZCode）读取的前端项目级开发指南。新对话接手时启用
`qta-context-bootstrap` 分阶段加载；Skill 规范源在 `.agents/skills/`，`.claude/skills/` 是其
生成的镜像。入口顺序见 `docs/AI_DEVELOPMENT_INDEX.md`；当前事实见 `docs/AI_HANDOFF.md`。
Historical 文档（清单见 `AI_DEVELOPMENT_INDEX §6`，当前为“none yet”）不在主流程，无需读取。

## 项目定位

`quant-trading-assistant-web` 是本地优先的量化交易辅助系统的前端，**不是自动交易前端**。

系统目标：

- 盘前写自选股观察、交易计划、止损位和仓位计划。
- 盘中快速使用风控计算器，手工记录真实操作。
- 盘后补交易结果，复盘是否按计划执行。
- 支持行情目录检索、行情工作台、板块与采集任务的查看。

明确不做：

- 不自动下单、不连接真实券商账户。
- 不在前端保存券商密码、交易密码或真实交易 API Key。
- 不宣传稳赚，不给出无风险收益承诺；所有风控计算仅辅助参考。

## 当前技术栈

- React 19
- TypeScript 6
- Vite 8（构建与开发服务器）
- Ant Design 6（`antd` + `@ant-design/icons`）
- React Router 7
- Zustand（全局状态：侧边栏等）
- TanStack Query（服务端状态预留）
- axios（仅由 `shared/api/client` 封装使用）
- decimal.js（风控计算精度）、dayjs（日期）、papaparse（CSV）
- Vitest（单元测试）

## 目录结构（feature-based）

```text
src/
├── app/            # 应用级：路由、布局、Provider
├── pages/          # 页面编排（路由级组件，不含业务逻辑）
├── features/<name>/ # 业务模块，每个含 model / api / hooks / components
├── shared/         # 共享基础
│   ├── api/        # localStorageClient、axios 封装 client、unwrappers
│   ├── components/ # 通用展示组件（DrawerFooter、ErrorBoundary、SecuritySelector）
│   ├── types/      # 领域类型
│   ├── utils/      # 日期、数字、费用、ID 工具
│   └── stores/     # Zustand 全局状态
└── styles/
```

`features` 当前包括：`watchlist`、`tradeplan`、`risk`、`journal`、`portfolio`、`position-snapshot`、
`review`、`dashboard`、`market-data`、`settings`、`build-status`。

## 架构红线（与 eslint.config.js 一致，强制）

业务层不得绕过共享访问层：

- `src/features/**` 与 `src/pages/**` **禁止**直接使用 `window.localStorage` 或 `localStorage`，
  必须统一走 `shared/api/localStorageClient`。
- `src/features/**` 与 `src/pages/**` **禁止**直接 `import 'axios'`，必须统一走 `shared/api/client`。
- `shared/api` 层（`localStorageClient` / `client` / `test-setup`）不受上述限制。

以上由 ESLint `no-restricted-syntax` / `no-restricted-imports` 强制，违反会让 `npm run lint` 失败。
不要关闭这两条规则或新增绕过封装的快捷调用。

## 数据模式

数据模式由「设置」页控制（每次请求现读设置，切换无需刷新页面）：

- **本地模式（mock）**：业务数据存浏览器 localStorage（前缀 `qta:`），不依赖后端。
- **后端模式（remote）**：核心业务数据通过 REST API 写后端；localStorage 仅存本地配置。
- 后端地址默认留空走同源 `/api/v1`（开发期 Vite proxy、生产 Nginx 反代）。

新增功能若涉及本地存储或后端调用，必须走 `shared/api` 对应封装，并在 mock 与 remote 两种模式
下都有可解释行为；mock 不得伪造外部数据或伪造成功。

## 开发原则

1. 前后端均已存在：前端为 React feature-based，后端为 Spring Boot 单体；按任务类型在对应仓库实现。
2. 先做数据积累和可解释规则，不碰自动实盘；所有买卖提示均表达为“辅助信号 + 风险提示 + 人工确认”。
3. 页面覆盖 loading / empty / error / validation / network / retry / permission 状态，盈利红亏损绿。
4. 不使用 `any`；领域类型集中在 `shared/types`。
5. 修改代码前先查看现有结构，不盲目新建平行体系。

## 常用命令

```bash
npm install
npm run dev          # 开发服务器 http://localhost:5173
npm run typecheck    # TypeScript 类型检查
npm run lint         # ESLint（含架构红线）
npm run test         # Vitest run
npm run build        # 生产构建（tsc -b && vite build）
npm run format       # Prettier 格式化 src
npm run preview      # 预览生产构建
```

## AI 治理接入（前端仓库）

本前端仓库已接入 QTA AI 治理，可独立启动 `/qta-run`，使用与后端一致的固定角色、TaskPacket、
Hook、独立验收和 delivery-ready 门禁。

- 规范源 Skill：`.agents/skills/`（ZCode / Codex 从这里发现项目 Skill）。
- 镜像：`.claude/skills/`（Claude 兼容镜像，由同步脚本生成，不要手编）。
- 固定角色模板：`.zcode/agents/`（测试设计者、实施者、代码审查者、最终核验者）。
- 父协调入口：`/qta-run <任务或契约路径>`；Hook 与机器控制文件由 `.zcode/config.json` 配置。
- 治理校验：`node scripts/validate-ai-governance.mjs`、`node scripts/run-ai-governance-gates.mjs`。

**单一规范源同步模型**：本仓库的治理树（`.agents/`、`.zcode/`、`.claude/skills/`、`scripts/`）
从后端控制仓库 byte-identical 同步，**禁止本地漂移编辑**；详见 `GOVERNANCE_SOURCE.md`。
本地手写的只有 `AGENTS.md`、`CLAUDE.md`、`docs/**`、`GOVERNANCE_SOURCE.md`、`.gitignore`。

## AI 协作要求

- 非简单任务先用 `qta-task-contract` 冻结范围、验收标准和证据；长任务按 `qta-task-checkpoint`
  主动存档，禁止等到上下文耗尽才压缩。
- 标准、跨仓或长任务由父上下文启用 `qta-development-orchestration`，也可用 `/qta-run` 显式启动。
  父协调者负责 L0-L3 风险 lane、TaskPacket、机器控制文件、角色顺序、候选哈希和 Git；子角色不得操作 Git。
- 固定 Agent 是模板，不是持续会话。初始实现、每轮 repair、每代 review 和最终 verifier 必须使用新的
  role/session；子角色结束即销毁。
- 实现者只可把任务标记为 `SELF_CHECKED`。独立验收必须由未参与实现的干净上下文使用
  `qta-independent-verification` 完成，验收者不得同时修代码。
- 跨仓产品/API/DB 事实以后端控制仓库文档为更高事实来源（优先级见 `docs/AI_DEVELOPMENT_INDEX.md §2`）。
- Skill/Agent 的职责、触发和维护规则以 `docs/ai/SKILL_AND_AGENT_GOVERNANCE.md` 为准。
- 修改代码前先查看现有结构；不要引入真实交易、券商接口或密钥读取功能。

## 完成定义（前端）

`npm run typecheck` / `lint` / `test` / `build` 全部通过；文案不误导用户把 localStorage 当正式数据源；
新增后端联调同步更新 `docs/AI_HANDOFF.md` 与 `docs/AI_DEVELOPMENT_INDEX.md` 路由。实现结束只记为
`SELF_CHECKED`，独立验收通过后才执行交付文档同步。
