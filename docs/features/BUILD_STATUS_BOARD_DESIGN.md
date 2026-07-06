# Build Status Board Design

> ⚠️ 本文件为**初始设计基线**。当前建设看板的事实以 `src/features/build-status/api/buildStatusData.ts`（前端静态快照）+ `docs/BUILD_CHECKLIST.md` 为准；本文件保留作历史参考，不再逐字段维护。

> 建设情况看板用于让用户一眼看懂 Quant Trading Assistant 还要建设什么、当前做到什么程度、下一步优先做什么。它是产品/研发管理页面，不是交易建议页面。

## 1. 功能定位

页面名称：`建设看板`

建议入口：

- 前端左侧菜单放在 `设置` 下方。
- 路由建议：`/build-status`
- 不建议塞进设置页底部。设置页是系统配置；建设看板是项目路线图和成熟度仪表盘，独立页面更清楚。

用户目标：

1. 快速知道当前系统哪些功能已经可用。
2. 快速知道下一步最应该做什么。
3. 从理财/交易管理视角看系统能力是否完整。
4. 给 Claude/Codex/OpenClaw 开发时提供明确 checklist。

## 2. 设计原则

- 第一版只做静态配置页面，不新增后端表。
- 数据来源建议是前端 `buildStatusData.ts` 静态配置，同时与后端 `docs/BUILD_CHECKLIST.md` 保持口径一致。
- 后续如果要多人协作或线上动态编辑，再考虑后端化。
- 看板只展示系统建设情况，不展示投资收益排名，不制造焦虑。

## 3. 用户视角

从产品经理视角，看板回答：

- 已经上线什么？
- 正在建设什么？
- 什么被阻塞？
- 哪些功能只完成了后端，前端还不能用？
- 哪些功能能真实落库，哪些还只是 localStorage/mock？

从理财经理视角，看板回答：

- 我的交易数据是否可信？
- 盈亏是否能解释清楚？
- 持仓是否能核对？
- 风险是否有约束？
- 复盘是否闭环？
- 未来接 AI 图片识别会不会影响数据准确性？

## 4. 建设成熟度等级

建议使用 `M0-M5`，比“百分比”更容易理解。

| 等级 | 名称 | 含义 | 页面颜色 |
| --- | --- | --- | --- |
| M0 | 未开始 | 只有想法或排期 | 灰色 |
| M1 | 已设计 | PRD / DB / API / UI 已明确 | 紫色 |
| M2 | 后端完成 | DB、API、测试基本完成 | 蓝色 |
| M3 | 前端完成 | 页面可操作，能联调 | 青色 |
| M4 | 已验收可用 | 前后端、测试、构建、文档通过 | 绿色 |
| M5 | 持续优化 | 已稳定使用，有体验/性能优化路线 | 金色 |

状态标签：

| 状态 | code | 含义 |
| --- | --- | --- |
| 已完成 | `DONE` | 可用且已验收 |
| 进行中 | `IN_PROGRESS` | 正在开发或联调 |
| 待开始 | `TODO` | 已排期但未动工 |
| 有风险 | `RISK` | 有口径、质量或部署风险 |
| 阻塞 | `BLOCKED` | 需要用户决策或外部条件 |

优先级：

- `P0`：影响当前能不能用。
- `P1`：提升核心交易记录/复盘价值。
- `P2`：增强分析能力。
- `P3`：体验、自动化、长期优化。

## 5. 页面信息架构

### 5.1 顶部总览

顶部放 4 个卡片：

1. **总体成熟度**
   - 示例：`M3.5 / 可日常记录，持仓快照待建设`
   - 展示整体进度条。

2. **当前最优先**
   - 示例：`P0 持仓快照：DB + 手工录入 + 历史查询`
   - 点击后定位到树节点。

3. **数据可信度**
   - 展示 remote DB 覆盖情况。
   - 示例：`核心记录已支持后端落库；持仓快照待补齐`

4. **交易闭环完整度**
   - 示例：`计划 -> 交易 -> 账本 -> 复盘 已基本闭环；实际持仓核对待补齐`

### 5.2 理财能力雷达 / 五维评分

用 5 个横向能力条，避免复杂图表也能看清：

| 能力 | 当前判断 | 目标 |
| --- | --- | --- |
| 数据沉淀 | 已有自选、计划、交易、复盘、账本 | 增加持仓快照 |
| 盈亏解释 | FIFO 账本已可解释已结算交易 | 补齐实际持仓核对 |
| 风险控制 | 有仓位计算器和风险提示 | 增加组合级风险 |
| 复盘闭环 | 可记录计划执行和错误标签 | 增加周期报告 |
| 自动化录入 | 暂无正式 AI 导入 | 图片识别生成草稿 |

### 5.3 建设树

主体使用树状结构或分组折叠面板。每个一级节点是一条建设主线：

```text
数据与基础设施
├── 后端基础框架
├── 前端工程框架
├── 生产部署和数据模式
└── AI 协作文档与 skills

交易记录闭环
├── 自选股
├── 交易计划
├── 交易记录
├── 盘后复盘
└── 工作台聚合

持仓与盈亏
├── 交易账本 FIFO
├── 当前价维护
├── 持仓快照
└── 快照对比

智能录入
├── 图片识别调研
├── 图片上传/粘贴
├── AI 识别草稿
└── 人工确认入库

量化分析
├── 日 K 数据导入
├── 技术指标
├── 策略信号
├── 回测引擎
└── 报告输出
```

### 5.4 节点卡片字段

点击树节点，右侧或抽屉展示详情：

| 字段 | 说明 |
| --- | --- |
| 模块名称 | 如“持仓快照” |
| 用户价值 | 这个模块解决什么问题 |
| 优先级 | P0/P1/P2/P3 |
| 成熟度 | M0-M5 |
| 状态 | DONE / IN_PROGRESS / TODO / RISK / BLOCKED |
| 当前证据 | 已有哪些代码、API、文档、测试 |
| 下一步动作 | 最小可执行任务 |
| 后端状态 | 未开始 / 设计 / API 完成 / 已验收 |
| 前端状态 | 未开始 / 页面完成 / 已联调 / 已验收 |
| 数据状态 | localStorage / DB / derived / external |
| 风险提示 | 口径、质量、隐私、部署风险 |
| 文档链接 | 指向 docs 文件 |

## 6. 初始看板内容建议

### 6.1 数据与基础设施

| 模块 | 优先级 | 成熟度 | 状态 | 下一步 |
| --- | --- | --- | --- | --- |
| 后端基础框架 | P0 | M4 | DONE | 持续跟随新模块补测试 |
| 前端工程框架 | P0 | M4 | DONE | 保持 typecheck/lint/test/build |
| 生产部署和数据模式 | P0 | M3 | RISK | 确认生产默认 remote，同源 `/api/v1` |
| AI 协作文档与 skills | P0 | M4 | DONE | 后续开发必须使用对应 skill |

### 6.2 交易记录闭环

| 模块 | 优先级 | 成熟度 | 状态 | 下一步 |
| --- | --- | --- | --- | --- |
| 自选股 | P0 | M4 | DONE | 继续优化筛选体验 |
| 交易计划 | P0 | M4 | DONE | 与交易记录建立更强关联 |
| 交易记录 | P0 | M4 | DONE | 确认费用和复盘状态口径 |
| 盘后复盘 | P0 | M4 | DONE | 增加周期复盘报告 |
| 工作台聚合 | P1 | M3 | IN_PROGRESS | 增加建设提醒和数据质量提醒 |

### 6.3 持仓与盈亏

| 模块 | 优先级 | 成熟度 | 状态 | 下一步 |
| --- | --- | --- | --- | --- |
| 交易账本 FIFO | P0 | M4 | DONE | 与持仓快照做核对 |
| 当前价维护 | P0 | M3 | DONE | 优化批量维护 |
| 持仓快照 | P0 | M1 | TODO | 实现 DB + 手工录入 + 历史查询 |
| 快照对比 | P1 | M0 | TODO | 等持仓快照稳定后做 |

### 6.4 智能录入

| 模块 | 优先级 | 成熟度 | 状态 | 下一步 |
| --- | --- | --- | --- | --- |
| 图片识别调研 | P1 | M1 | DONE | 等持仓快照模块完成 |
| 图片上传/粘贴 | P1 | M0 | TODO | 先做前端草稿入口 |
| AI 识别草稿 | P1 | M0 | TODO | 后端封装 provider |
| 人工确认入库 | P1 | M0 | TODO | 复用持仓快照保存 API |

### 6.5 量化分析

| 模块 | 优先级 | 成熟度 | 状态 | 下一步 |
| --- | --- | --- | --- | --- |
| 日 K 数据导入 | P2 | M0 | TODO | CSV 导入 |
| 技术指标 | P2 | M0 | TODO | MA/MACD/RSI/BOLL |
| 策略信号 | P2 | M0 | TODO | 均线趋势 + 成交量过滤 |
| 回测引擎 | P2 | M0 | TODO | 简化日线回测 |
| 报告输出 | P2 | M0 | TODO | JSON/页面报告 |

## 7. 前端实现建议

### 7.1 路由和菜单

- 新增路由：`/build-status`
- 菜单名称：`建设看板`
- 菜单位置：`设置` 下方
- 图标建议：`ProjectOutlined`、`ApartmentOutlined` 或 `ClusterOutlined`

### 7.2 组件拆分

```text
src/features/build-status/
├── model/
│   └── types.ts
├── api/
│   └── buildStatusData.ts
├── components/
│   ├── BuildStatusSummary.tsx
│   ├── BuildStatusCapabilityBars.tsx
│   ├── BuildStatusTree.tsx
│   ├── BuildStatusDetailDrawer.tsx
│   └── BuildStatusLegend.tsx
└── hooks/
    └── useBuildStatus.ts

src/pages/build-status.tsx
```

### 7.3 数据结构草案

```ts
export type BuildPriority = 'P0' | 'P1' | 'P2' | 'P3';
export type BuildStatus = 'DONE' | 'IN_PROGRESS' | 'TODO' | 'RISK' | 'BLOCKED';
export type BuildMaturity = 'M0' | 'M1' | 'M2' | 'M3' | 'M4' | 'M5';
export type DataOwnership = 'DB' | 'LOCAL_STORAGE' | 'DERIVED' | 'EXTERNAL' | 'NONE';

export interface BuildStatusNode {
  id: string;
  title: string;
  category: string;
  priority: BuildPriority;
  status: BuildStatus;
  maturity: BuildMaturity;
  progress: number;
  productValue: string;
  currentEvidence: string[];
  nextActions: string[];
  backendState: string;
  frontendState: string;
  dataOwnership: DataOwnership;
  risks: string[];
  docLinks: Array<{ label: string; path: string }>;
  children?: BuildStatusNode[];
}
```

## 8. UI 视觉建议

整体风格：像一个安静的项目作战室，不要做营销风。

布局：

```text
标题 + 简短说明
四个总览卡片
五维能力条
筛选区：优先级 / 状态 / 成熟度
左侧建设树 + 右侧详情
底部：下一步推荐行动
```

颜色：

- DONE：绿色
- IN_PROGRESS：蓝色
- TODO：灰色
- RISK：橙色
- BLOCKED：红色

注意：这里是建设状态，不是盈亏，不使用 A 股红涨绿跌口径。

## 9. 不做什么

- 第一版不做后端表。
- 第一版不做在线编辑建设项。
- 第一版不做团队协作权限。
- 第一版不自动解析 Markdown checklist。
- 不把建设进度和投资收益混在一起展示。

## 10. 验收清单

- [ ] 菜单在 `设置` 下方显示 `建设看板`。
- [ ] 页面能一眼看到总体成熟度和当前最优先事项。
- [ ] 页面能看到完整建设树。
- [ ] 每个节点能看到优先级、状态、成熟度、下一步。
- [ ] 用户能区分“已可用”“已设计”“未开始”“有风险”。
- [ ] 持仓快照显示为 P0/M1/TODO，并指向设计文档。
- [ ] 图片识别显示为 P1，且依赖持仓快照。
- [ ] 页面文案明确这是系统建设状态，不是投资建议。
- [ ] `npm run typecheck`、`lint`、`test`、`build` 通过。
