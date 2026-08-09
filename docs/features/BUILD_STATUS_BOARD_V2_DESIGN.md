# 建设看板 V2 产品设计

> 状态：`DECISION / FROZEN FOR IMPLEMENTATION`
>
> 目标：让用户在 30 秒内回答“系统建设了多少、最近交付了什么、哪些已经能在线使用、下一步做什么”。
> 本文替代 `BUILD_STATUS_BOARD_DESIGN.md` 的页面信息架构；旧文档仅保留为 V1 历史基线。

## 1. 问题与结论

### 1.1 当前问题（FACT）

- 页面仍显示“最近同步 2026-07-06”，已经落后于 7 月和 8 月多轮交付。
- 当前首页主要展示主观百分比和一棵大树，无法直接看到完成项数量、建设中数量和阻塞项数量。
- 节点没有统一的最后更新时间、交付版本、提交、验收层级和完成口径。
- `DONE / M4 / progress` 混合表达“写完代码、自动化通过、运行验证、已经部署”，用户无法判断是否真正可用。
- 开发日志和验收日志已有时间信息，但看板没有“最近交付”视图，导致开发成果不可见。

### 1.2 产品决策（DECISION）

1. 看板从“静态能力树”改为“建设总览 + 最近交付 + 当前行动 + 能力目录”。
2. 总体进度必须由叶子能力节点自动统计，禁止手填一个无法解释的总体百分比。
3. “研发状态”和“验证层级”分开表达；代码完成不等于已部署。
4. 每个改变产品能力的已验收任务必须产生一条交付记录，并更新受影响节点。
5. 第一版继续使用前端静态快照，不新增 DB 表和后端 API；看板是随版本发布的项目元数据。
6. 历史只展示最近 12 条，完整事实仍以 `DEVELOPMENT_LOG.md` 和 `ACCEPTANCE_LOG.md` 为准。

## 2. 页面信息架构

页面按以下顺序展示，保留现有 `/build-status` 路由。

### 2.1 状态基线

首屏顶部必须展示：

- 看板数据截至时间 `snapshotAt`。
- 对应前端和后端基线提交短哈希。
- 当前发布阶段，例如 `P1 行情数据资产与证券目录`。
- 最近一次交付名称和日期。
- “静态发布快照”提示，避免误解为实时研发管理系统。

当 `snapshotAt` 缺失或早于最近交付记录时，测试必须失败，不允许继续显示过期基线。

### 2.2 建设总览

总览只使用可核对的计数：

| 指标 | 口径 |
| --- | --- |
| 已部署可用 | 叶子节点 `validationStage=DEPLOYED` |
| 已验收待部署 | 叶子节点 `validationStage=RUNTIME_VERIFIED` 或 `AUTOMATION_VERIFIED`，但尚未部署 |
| 建设中 | `deliveryStatus=IN_PROGRESS` |
| 待开始 | `deliveryStatus=PLANNED` 或 `DESIGNED` |
| 阻塞/风险 | `deliveryStatus=BLOCKED` 或存在未解除风险 |

可展示“能力完成率”，但公式固定为：

```text
已验收能力数 / 已纳入计划的叶子能力总数
```

其中“已验收”至少要求 `AUTOMATION_VERIFIED`。不得按模型主观判断填写 90%、96% 等总体数字。

### 2.3 最近交付

使用纵向时间线，默认展示最近 6 条，可展开到 12 条。每条必须包含：

- `deliveredAt`：验收或部署日期。
- `title`：用户能理解的功能名称。
- `summary`：本轮实际新增或修复了什么，最多两句。
- `modules`：影响模块。
- `stage`：代码完成、自动化验收、运行验收或生产部署。
- `backendCommit` / `frontendCommit`：适用时记录短哈希。
- `acceptanceRef`：指向验收日志标题或任务验收文档。
- `limitations`：仍未验证或明确不包含的边界。

最近交付不能从聊天记录推断；只允许从 Git 提交、当前代码、任务最终验收和验收日志提炼。

### 2.4 当前行动区

将用户最关心的事情分为三列，不再藏在树节点抽屉里：

1. **现在可直接使用**：最多 6 项，链接到实际页面。
2. **正在建设**：显示负责人状态、当前阶段、最后更新时间和剩余验收。
3. **下一步建议**：按 P0-P3 排序，每项只给一个最小下一动作和依赖。

阻塞项单独使用警告区域，明确是代码问题、部署问题、权限问题还是外部数据源问题。

### 2.5 能力目录

保留现有能力树作为第二屏以下的详细目录，并做以下调整：

- 默认折叠，不再抢占首屏。
- 支持按优先级、研发状态、验证层级和模块筛选。
- 节点标题只展示名称、优先级、状态和验证层级，避免一行堆叠过多标签。
- 点击后抽屉展示用户价值、已交付内容、完成标准、当前限制、下一动作、提交和验收证据。
- 原“能力成熟度百分比”若无客观公式则移除；可保留业务能力条，但必须标注计算口径。

## 3. 状态模型

### 3.1 研发状态 `deliveryStatus`

| 状态 | 含义 |
| --- | --- |
| `PLANNED` | 已进入路线图，尚未形成冻结设计 |
| `DESIGNED` | 产品与技术边界已冻结，尚未开始实现 |
| `IN_PROGRESS` | 正在实现、修复或联调 |
| `DELIVERED` | 所需验证层级已通过并完成交付收口 |
| `BLOCKED` | 存在明确阻断条件 |
| `DEFERRED` | 明确暂缓，不计入当前在建 |

### 3.2 验证层级 `validationStage`

| 层级 | 含义 |
| --- | --- |
| `NOT_VERIFIED` | 只有设计或代码，没有有效验收 |
| `STATIC_VERIFIED` | 静态检查通过 |
| `AUTOMATION_VERIFIED` | 单测、类型、Lint、构建等自动化门禁通过 |
| `RUNTIME_VERIFIED` | Docker/curl/浏览器等目标运行链路验证通过 |
| `DEPLOYED` | 对应修订已经部署且完成最小线上冒烟 |

验证层级只能逐级提升。服务器部署事实不能由本地测试推断；没有证据必须保持原级别。

### 3.3 完成判定

- `deliveryStatus=DELIVERED` 必须同时有 `acceptanceRef`、`lastUpdatedAt` 和至少一个代码提交或明确的纯文档交付标识。
- 页面存在但 API 不通，不算交付。
- 只有后端或只有前端完成时保持 `IN_PROGRESS`，并在 `remainingWork` 中写清缺口。
- 外部权限、行情源或服务器未验证时，可以代码交付，但验证层级不得写成 `DEPLOYED`。
- 历史交付记录只追加、不改写；能力节点展示当前事实，可更新。

## 4. 前端数据契约

建议把现有大文件拆为：

```text
src/features/build-status/
├── data/buildStatusSnapshot.ts
├── model/types.ts
├── model/selectors.ts
├── components/BuildStatusHeader.tsx
├── components/BuildStatusOverview.tsx
├── components/RecentDeliveries.tsx
├── components/CurrentActions.tsx
├── components/BuildStatusTree.tsx
└── components/BuildStatusDetailDrawer.tsx
```

快照至少包含：

```ts
interface BuildStatusSnapshot {
  snapshotAt: string;
  releaseStage: string;
  backendCommit: string;
  frontendCommit: string;
  recentDeliveries: BuildDeliveryRecord[];
  capabilities: BuildStatusNode[];
}
```

统计卡片、完成率、状态数量和“当前行动”必须通过 selector 从快照推导，不允许再分别手填多份数字。

## 5. 事实盘点规则

DeepSeek 实施前按以下优先级核实当前事实：

1. 当前 `main` 代码、Flyway、路由、测试和 Git 提交。
2. 当前任务独立验收/最终验收记录。
3. `BUILD_CHECKLIST.md`、`AI_HANDOFF.md`、API/架构文档。
4. `DEVELOPMENT_LOG.md` 和 `ACCEPTANCE_LOG.md` 的历史条目。
5. 聊天、旧 handoff、历史 prompt 只能作为线索，不能作为完成证据。

首次 V2 迁移必须逐个核实现有叶子节点。无法确认的状态向下取保守值，并写入限制，不得为了让数字好看而补高进度。

## 6. 强制同步规则

每个会改变用户可见能力、完成度、优先级或验证层级的任务，在独立验收允许交付后必须：

1. 更新受影响能力节点的当前事实、状态、验证层级和日期。
2. 新增一条最近交付记录；纯内部重构且能力未变化可不新增，但需在 finalization 明确说明。
3. 更新 `BUILD_CHECKLIST.md` 和必要的产品路线图。
4. 更新快照时间和对应仓库提交；提交未知时不得伪造。
5. 运行看板数据一致性测试以及前端四项门禁。

未执行上述同步时，任务只能称为“代码已验收”，不能称为“交付收口完成”。

## 7. 验收标准

- 首屏直接展示截至日期、完成/在建/待办/阻塞计数和最近一次交付。
- 最近交付至少覆盖当前事实中最近 6 次有用户价值的交付，日期与证据可追踪。
- 统计全部由叶子节点推导，测试证明父子节点不会重复计数。
- `DELIVERED`、`DEPLOYED`、阻塞和暂缓口径符合本文规则。
- 旧的“最近同步 2026-07-06”及无依据总体百分比被移除。
- 桌面和窄屏内容不重叠，时间线和树可扫描。
- `npm run typecheck`、`npm run lint`、`npm run test`、`npm run build` 全部通过。
- 浏览器验证 `/build-status`：无控制台错误，筛选、展开、抽屉和窄屏布局可用。

## 8. 非目标

- 本轮不新增后端表、管理接口或在线编辑功能。
- 不把研发看板与交易收益、行情大屏混在一起。
- 不自动读取 GitHub、服务器或聊天历史。
- 不为了展示“进度”虚构精确工时、发布日期或负责人。
