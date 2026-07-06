# Frontend Architecture

> 前端任务必读。反映 `quant-trading-assistant-web` 当前**真实结构**。早期版本中的 `/api/risk-alerts`、`/api/review-notes`、`/api/backtests`、"今天先创建项目"、"等待后端 API"等表述已失效。

## 1. 仓库与技术栈

- 仓库：`/Users/joker/code/quant-trading-assistant-web`
- React 19 + Vite + TypeScript + Ant Design 6 + React Router 7 + Zustand + decimal.js + dayjs + axios + Vitest。
- 不使用 `enum`（联合类型 + const 对象映射）；不用 `any`。

## 2. 目录结构（feature-based）

```text
src/
├── app/            # router.tsx / layout.tsx / providers.tsx
├── pages/          # 页面编排：dashboard / watchlist / trade-plan / risk / journal /
│                   #              portfolio / position-snapshot / review / settings / build-status / not-found
├── features/<feature>/   # 每个 feature：api / hooks / components / model
│   ├── watchlist / tradeplan / journal / review / portfolio / position-snapshot
│   ├── dashboard / risk / settings / build-status
├── shared/
│   ├── api/        # client.ts(动态 baseURL) / localStorageClient.ts(qta: 前缀) / types.ts / unwrappers.ts
│   ├── components/ # DrawerFooter / ErrorBoundary
│   ├── stores/ types/ utils/  # date / fee / id / number
└── styles/ assets/
```

## 3. 路由

`/dashboard` `/watchlist` `/trade-plan` `/risk` `/journal` `/portfolio` `/position-snapshots` `/review` `/settings` `/build-status`（注意持仓快照是复数 `/position-snapshots`，待办 targetPath 必须用复数）。

## 4. 数据模式（mock / remote 双模式）

- `settings.apiMode`（mock / remote），每次请求现读（`shared/api/client.ts` `buildApiBaseUrl`），切换无需刷新。
- **mock**：localStorage（物理 key 带 `qta:` 前缀，详见 `mock/MOCK_REMOTE_CONTRACT.md`）。
- **remote**：REST API；`apiBaseUrl` 留空走同源 `/api/v1`（开发期 vite proxy、生产 Nginx 反代）。
- 每个 feature 的 api 层提供 mock + remote 两套实现，按 mode 分流；mock 计算口径必须复刻后端。

## 5. 已实现 feature 与对应后端 API

| feature | 后端 API | 备注 |
| --- | --- | --- |
| dashboard | `GET /dashboard/today` | remote 用后端聚合（含 todos），mock 同口径纯函数 |
| watchlist | `/watchlist` | |
| tradeplan | `/trade-plans` | |
| journal | `/trade-journals` | 含 `unlinkPlan` 三态、`planDate/planStatus` |
| review | `/reviews` | 一致性回算 + 删除保护 |
| portfolio | `/portfolio/*` | FIFO 账本 |
| position-snapshot | `/position-snapshots/*` | 含 `comparison` / `reconciliation` |
| risk | **前端本地纯函数**（`features/risk/api/riskCalculator.ts`，decimal.js） | 后端 `/risk/calculations/position-size` 已实现但**前端页面未接 remote adapter**，当前页面用本地纯函数 |
| settings | localStorage | `apiMode/apiBaseUrl` + localhost 防误配 + 只读测试连接 |
| build-status | 静态数据 | 无后端 |

## 6. 命令

```bash
npm run typecheck   # tsc 类型检查
npm run lint        # eslint
npm run test        # vitest run
npm run build       # vite build
npm run dev         # vite dev，端口 5173，/api proxy 到 VITE_DEV_PROXY_TARGET（默认 localhost:8080）
```

## 7. 约定

- 盈利红、亏损绿（A 股习惯）。
- 所有页面覆盖 loading / empty / error / retry。
- 对比 / 对账 / 盈亏页明确"不构成投资建议"。
- 不放 API Key；不直接 `window.localStorage`（统一走 `shared/api/localStorageClient`）。
- Antd 6：`Alert` 用 `title`（非 deprecated 的 `message`）；`Spin` 用 `description`；`Space` 用 `orientation`；`Drawer` 用 `size`；Dashboard `List` 已替换为 `ul/li + Flex`。

## 8. 部署

- 生产 `build` 默认 remote，`apiBaseUrl` 留空走同源 `/api/v1`。
- 部署形态：**宿主机 Nginx** 托管前端 `vite build` 产物（dist），同源 `/api` 反代到后端；后端 `qta-server` 与 `qta-mysql` 运行在 Docker（`docker compose`）。前端不容器化（无前端 Docker 容器）。
- 本地开发：前端 `http://localhost:5173`、后端 `http://localhost:8080`、MySQL `127.0.0.1:3306`。
