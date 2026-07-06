# Frontend MVP Architecture And Claude Manual

> ⚠️ Historical（历史参考，非当前执行入口）。当前事实以 AI_HANDOFF.md + development/DEVELOPMENT_LOG.md + CURRENT_ARCHITECTURE_AND_MODULES.md 为准；新会话入口见 AI_DEVELOPMENT_INDEX.md。

本文是给 Claude Code 创建和开发前端项目用的执行手册。前端项目目标是让用户周一可以打开页面记录自选股、盘前计划、风险计算、交易记录和盘后复盘。

前端项目路径：

```text
/Users/joker/code/quant-trading-assistant-web
```

后端项目路径：

```text
/Users/joker/code/quant-trading-assistant
```

Claude Code 实现前必须先阅读后端仓库中的：

1. `AGENTS.md`
2. `CLAUDE.md`
3. `docs/FRONTEND_ARCHITECTURE.md`
4. `docs/claude/BACKEND_TODAY_MVP_IMPLEMENTATION_MANUAL.md`
5. 本文件

## 1. 产品定位

前端不是官网，不是营销页，而是个人交易工作台。

核心使用场景：

- 盘前：写自选股观察、交易计划、止损位和仓位计划。
- 盘中：快速使用风控计算器，手工记录真实操作。
- 盘后：补交易结果，复盘是否按计划执行。

页面必须明确显示：

```text
本系统只做交易辅助记录、风控计算和复盘，不自动交易。
```

## 2. 技术栈

使用：

- Vite
- React
- TypeScript
- Ant Design
- React Router
- TanStack Query
- Zustand
- ECharts
- Axios
- dayjs

可选：

- `@ant-design/icons` 用于菜单和按钮图标。

不要使用：

- Next.js。
- 微前端。
- 复杂低代码平台。
- 大型状态机。
- 任何券商 SDK。

## 3. 创建项目命令

Claude Code 在 `/Users/joker/code` 下创建前端项目：

```bash
npm create vite@latest quant-trading-assistant-web -- --template react-ts
cd quant-trading-assistant-web
npm install
npm install antd @ant-design/icons @tanstack/react-query zustand echarts echarts-for-react axios dayjs react-router-dom
```

如果网络或 npm 安装失败，Claude Code 应停止并汇报，不要改用不明来源依赖。

## 4. 页面范围

第一版必须完成 6 个业务页面，加 1 个基础设置页。

1. `Dashboard` 今日工作台。
2. `Watchlist` 自选股。
3. `TradePlan` 盘前计划。
4. `RiskCalculator` 风控计算器。
5. `TradeJournal` 交易记录。
6. `Review` 盘后复盘。
7. `Settings` 设置页，用于显示 mock/remote API 模式。

第一版先用 `localStorage`，不等待后端 API 完成。项目必须预留 remote API adapter。

## 5. 推荐目录结构

```text
quant-trading-assistant-web
├── README.md
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
├── .env.example
└── src
    ├── main.tsx
    ├── App.tsx
    ├── app
    │   ├── router.tsx
    │   ├── providers.tsx
    │   └── layout
    │       ├── AppLayout.tsx
    │       ├── Sidebar.tsx
    │       └── TopBar.tsx
    ├── api
    │   ├── http.ts
    │   ├── clientMode.ts
    │   ├── dashboardApi.ts
    │   ├── watchlistApi.ts
    │   ├── tradePlanApi.ts
    │   ├── riskApi.ts
    │   ├── tradeJournalApi.ts
    │   ├── reviewApi.ts
    │   └── local
    │       ├── localStorageClient.ts
    │       ├── watchlistLocalApi.ts
    │       ├── tradePlanLocalApi.ts
    │       ├── tradeJournalLocalApi.ts
    │       └── reviewLocalApi.ts
    ├── pages
    │   ├── dashboard
    │   ├── watchlist
    │   ├── trade-plan
    │   ├── risk-calculator
    │   ├── trade-journal
    │   ├── review
    │   └── settings
    ├── components
    │   ├── common
    │   ├── forms
    │   └── tables
    ├── types
    │   ├── domain.ts
    │   └── api.ts
    ├── stores
    │   └── appStore.ts
    ├── hooks
    ├── utils
    │   ├── date.ts
    │   ├── number.ts
    │   └── risk.ts
    └── styles
        └── global.css
```

## 6. UI 布局

使用后台工作台布局：

```text
TopBar: 项目名 / 当前日期 / API 模式 / 风险边界提示
Sidebar: Dashboard / Watchlist / Trade Plan / Risk Calculator / Trade Journal / Review / Settings
Main: 当前页面内容
```

设计要求：

- 使用 Ant Design `Layout`、`Menu`、`Table`、`Form`、`Drawer`、`Modal`、`Tag`、`Alert`、`Statistic`。
- 信息密度适中，适合每天反复打开。
- 不做大 Hero，不做营销文案。
- 表格和表单优先，按钮带图标。
- 不使用刺激情绪的买卖文案。
- 风控提示使用 `Alert` 或 `Tag`，不要用“稳赚”“必涨”。
- 页面宽度适配桌面端，移动端只保证不严重错位。

## 7. localStorage 设计

统一前缀：

```text
qta:
```

键名：

| key | 内容 |
| --- | --- |
| `qta:watchlist` | 自选股 |
| `qta:tradePlans` | 盘前计划 |
| `qta:tradeJournals` | 交易记录 |
| `qta:reviews` | 复盘 |
| `qta:settings` | 设置 |

localStorage 数据必须有 `id`、`createdAt`、`updatedAt`。

第一版 ID 可以用：

```text
Date.now().toString()
```

或浏览器 `crypto.randomUUID()`。如果使用 `crypto.randomUUID()`，需处理兼容性。

## 8. 前端编码规范

第一版虽然是 MVP，但代码要能让用户后续自己阅读和维护。Claude Code 必须按以下规范生成代码：

### 8.1 TypeScript

- 启用并遵守 Vite React TS 默认严格类型检查。
- 不使用 `any`。确实无法避免时，必须用 `unknown` 或补充明确类型，并说明原因。
- 领域类型统一放在 `src/types/domain.ts`，API 响应类型放在 `src/types/api.ts`。
- 表单值类型、localStorage 数据类型、API DTO 类型不要混用。
- 枚举值优先使用联合类型加展示配置，不在页面里散落魔法字符串。

示例：

```ts
export type AttentionLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export const ATTENTION_LEVEL_OPTIONS: Array<{
  value: AttentionLevel;
  label: string;
  color: string;
}> = [
  { value: 'HIGH', label: '高关注', color: 'red' },
  { value: 'MEDIUM', label: '中关注', color: 'orange' },
  { value: 'LOW', label: '低关注', color: 'blue' },
];
```

### 8.2 命名

- React 组件文件使用 PascalCase，例如 `WatchlistPage.tsx`、`RiskCalculatorPage.tsx`。
- hooks 使用 `useXxx`，例如 `useLocalStorageState`。
- 工具函数使用 lowerCamelCase，例如 `formatCurrency`、`calculatePositionSize`。
- 类型名使用 PascalCase，例如 `TradePlan`、`RiskCalculationResult`。
- 常量使用 UPPER_SNAKE_CASE，例如 `LOCAL_STORAGE_KEYS`。
- 页面目录可以使用 kebab-case，例如 `trade-plan`，但目录内组件仍使用 PascalCase。

### 8.3 组件职责

- `pages/*` 只做页面编排、筛选状态、调用数据方法。
- `components/forms` 放复用表单片段。
- `components/tables` 放复用表格。
- `api/local` 只做 localStorage 读写，不写 UI。
- `utils/risk.ts` 放风控计算纯函数，页面不直接写复杂公式。
- 不把所有逻辑堆在 `App.tsx` 或单个页面文件里。

### 8.4 React 写法

- 使用函数组件和 hooks。
- 表单使用 Ant Design `Form`，不要手写大量非受控表单。
- 列表使用 Ant Design `Table`，操作使用 `Button` + 图标。
- 新增/编辑使用 `Drawer` 或 `Modal`，不要在页面底部堆超长表单。
- 涉及删除、清空、导入覆盖，必须二次确认。
- 不使用过时生命周期或 class component。

### 8.5 状态与数据

- MVP 的业务数据先走 localStorage adapter。
- 页面不要直接调用 `window.localStorage`，统一通过 `localStorageClient`。
- `Zustand` 只放全局 UI 状态和设置，例如侧边栏折叠、API 模式、当前日期。
- `TanStack Query` 预留给 remote API；mock/local 模式可以简单封装，但不要过度复杂。
- 数据写入时必须维护 `createdAt`、`updatedAt`。

### 8.6 可读性

- 关键交易规则、风控公式处加简短注释。
- 不生成大段无意义注释。
- 不写超过 300 行的超大组件；如果页面变长，应拆表单、表格或工具函数。
- 错误提示和空状态要清楚，例如“暂无交易计划”“请先填写止损价”。
- 页面文案使用中文，代码命名使用英文。

### 8.7 样式

- 优先使用 Ant Design 组件能力和少量 CSS。
- 全局样式放 `src/styles/global.css`。
- 不使用大面积渐变、hero、营销风布局。
- 不使用内联 style 堆复杂样式；简单布局可以接受。
- 确保表格、按钮、表单在常见桌面宽度下不互相遮挡。

### 8.8 质量检查

实现后必须运行：

```bash
npm run build
```

如果项目配置了 lint，也必须运行：

```bash
npm run lint
```

如果 lint 未配置，不要临时引入复杂 lint 体系阻塞 MVP，但要在汇报中说明。

## 9. TypeScript 核心类型

建议在 `src/types/domain.ts` 定义：

```text
MarketType = 'A_SHARE' | 'HK' | 'US' | 'ETF' | 'OTHER'
TradeStyle = 'SHORT_TERM' | 'DO_T' | 'SWING' | 'OBSERVE'
AttentionLevel = 'HIGH' | 'MEDIUM' | 'LOW'
PlanStatus = 'DRAFT' | 'ACTIVE' | 'DONE' | 'CANCELLED'
TradeSide = 'BUY' | 'SELL'
ReviewStatus = 'PENDING' | 'REVIEWED'
RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
```

核心模型：

- `WatchlistItem`
- `TradePlan`
- `RiskCalculationInput`
- `RiskCalculationResult`
- `TradeJournal`
- `ReviewNote`
- `DashboardSummary`

字段应与后端手册中的 API 对齐，前端不要自己发明另一套语义。

## 10. 页面设计

### 10.1 Dashboard

展示：

- 启用自选股数量。
- 今日交易计划数量。
- 今日交易记录数量。
- 待复盘数量。
- 今日风险提醒。
- 快捷按钮：新增自选股、新增计划、打开风控计算器、记录交易、写复盘。
- 最近自选股、今日计划、待复盘交易。

数据来源：

- mock/localStorage 聚合。
- 后续切 `GET /api/v1/dashboard/today`。

### 10.2 Watchlist

功能：

- 表格展示自选股。
- 新增、编辑、停用。
- 支持按关键词、交易风格、关注级别筛选。

字段：

- 股票代码。
- 股票名称。
- 市场。
- 分组。
- 关注理由。
- 交易风格。
- 关注级别。
- 支撑位。
- 压力位。
- 止损位。
- 风险备注。
- 是否启用。

交互：

- 新增/编辑使用 Drawer。
- 停用使用二次确认。
- 停用不物理删除。

### 10.3 TradePlan

功能：

- 按日期查看计划。
- 新增、编辑计划。
- 标记状态。
- 标记今日是否允许交易。

字段：

- 计划日期。
- 股票代码和名称。
- 买入条件。
- 卖出条件。
- 止损价。
- 止盈价。
- 计划仓位比例。
- 最大可承受亏损。
- 是否允许交易。
- 风险备注。

交互：

- 如果勾选允许交易但没有止损价，前端表单阻止保存。
- 如果没有买入条件，允许保存草稿但给 warning。

### 10.4 RiskCalculator

功能：

- 输入总资金、单笔风险比例、买入价、止损价、单票最大仓位、最小交易单位。
- 计算最大可买数量、预计亏损、仓位占比。
- 输出风险等级和警告。

默认值：

- 单笔风险比例：`0.005`
- 单票最大仓位：`0.15`
- A 股最小交易单位：`100`

规则：

- `stopLossPrice >= buyPrice` 时不能计算。
- 计算结果只做辅助，不构成交易建议。
- 结果区域必须展示免责声明。

### 10.5 TradeJournal

功能：

- 新增、编辑交易记录。
- 按日期、股票、复盘状态筛选。
- 标记是否按计划执行。
- 设置情绪标签和错误标签。

字段：

- 交易日期。
- 交易时间。
- 股票代码和名称。
- 买/卖。
- 价格。
- 数量。
- 仓位比例。
- 关联计划。
- 交易理由。
- 计划止损。
- 计划止盈。
- 是否按计划执行。
- 情绪标签。
- 错误标签。
- 实际结果。
- 复盘状态。

### 10.6 Review

功能：

- 新增每日总复盘。
- 新增个股复盘。
- 关联交易记录。
- 按日期和股票筛选。

字段：

- 复盘日期。
- 股票代码，可为空。
- 标题。
- 市场环境。
- 原计划。
- 实际操作。
- 做对了什么。
- 做错了什么。
- 规则修正。
- 下一步。
- 关联交易记录。

### 10.7 Settings

功能：

- 显示当前 API 模式：mock/local/remote。
- 显示后端地址：默认 `http://localhost:8080`。
- 提供 localStorage 数据导出 JSON。
- 提供 JSON 导入恢复。
- 提供清空本地数据按钮，必须二次确认。

## 11. API 模式

`.env.example`：

```text
VITE_API_MODE=mock
VITE_API_BASE_URL=http://localhost:8080
```

模式：

| 模式 | 行为 |
| --- | --- |
| `mock` | 使用 localStorage 和本地计算 |
| `remote` | 使用后端 REST API |

第一版必须 mock 可用。remote 可以只预留 adapter，不要求所有接口真实连通。

## 12. 验收标准

Claude Code 完成前端后必须验证：

```bash
npm run build
npm run dev
```

验收项目：

- 浏览器能打开首页。
- Sidebar 菜单可切换 7 个页面。
- 能新增自选股，刷新后不丢。
- 能新增盘前计划，刷新后不丢。
- 风控计算器能计算数量和预计亏损。
- 能新增交易记录，刷新后不丢。
- 能新增复盘，刷新后不丢。
- Settings 能导出 localStorage JSON。
- 页面明确显示“不自动交易”。

## 13. 实施顺序

Claude Code 应按以下顺序开发：

1. 创建 Vite React TS 项目。
2. 安装依赖。
3. 建立目录结构。
4. 实现 App providers、router、layout。
5. 定义 domain types。
6. 实现 localStorage client。
7. 实现 Dashboard。
8. 实现 Watchlist。
9. 实现 TradePlan。
10. 实现 RiskCalculator。
11. 实现 TradeJournal。
12. 实现 Review。
13. 实现 Settings 导入导出。
14. 跑 `npm run build`。
15. 启动 `npm run dev` 并报告本地 URL。

## 14. Claude Code 完成后的汇报格式

```text
已创建前端项目：
- /Users/joker/code/quant-trading-assistant-web

已实现：
- ...

验证：
- npm run build 通过 / 失败原因
- npm run dev URL: http://localhost:5173

注意：
- 第一版使用 localStorage
- 未连接券商
- 未实现自动下单
```
