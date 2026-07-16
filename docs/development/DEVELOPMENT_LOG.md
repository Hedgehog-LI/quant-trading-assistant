# Development Log

> 按版本追加开发记录。每条：目标 / 范围 / 前后端改动 / 接口变化 / 测试结果 / 产品决策 / 遗留问题 / 关联文档。**不粘贴命令流水和聊天全文。** 新条目用 `docs/templates/DEVELOPMENT_LOG_TEMPLATE.md`。

---

## 2026-07-16 — P1.2/P1.3 第六轮 Codex 收口（按任务重建 Drawer + 竞态防护 + 真实交互测试）

- **目标**：修复 TaskItemsDrawer 双 effect 重复请求、切换 task 竞态、缺少时间列展示、板块 3 条伪行为测试。
- **前端修复（TaskItemsDrawer）**：
  - 外层按 `lastTaskId` 为内部组件设置 key，任务切换时重建分页状态并自然回到 page=1；内部只保留一个随 task/page 加载的 effect。
  - request-id + active ref 同时阻止旧请求覆盖、effect 清理后的迟到响应，以及卸载后收敛回调继续刷新旧任务。
  - 表格补齐 `startedAt`、`finishedAt`，复用 `formatDateTime`，并设置横向滚动防挤压。
  - `TaskItemsDrawer` 导出以支持组件级行为测试。
- **前端修复（板块测试）**：
  - 测试 4（创建失败）：填写表单 → 点击 Drawer 内创建按钮（`findBtnInDrawer`，处理 Antd 空格间距）→ 断言 `createSegment` 调用 1 次 + `message.error` 被调用。
  - 测试 5（删除失败）：点击删除 → 点击 Popconfirm OK（`findPopconfirmOkBtn`，匹配 "OK"/"确定"）→ 断言 `deleteSegment` 调用 1 次 + `message.error` 被调用 + 数据仍在。
  - 测试 8（移除 pending）：点击移除并确认，pending 时再次点击 loading 按钮，断言 `removeSegmentMember` 仍只调用 1 次；随后在 `act` 中 resolve 并清理。
  - 测试 7（添加 pending）：同样加 `act` resolve + `unmount` 清理。
- **新增组件测试**：`market-workspace.test.tsx`（7 tests）——首次打开与时间格式、先翻到第二页再切换 task 只请求新任务 page=1、翻页单请求、旧响应隔离、收敛 pending 防重复、成功刷新、失败错误。
- **测试结果**：Codex 实测后端 **250 tests** + package；前端 typecheck + lint + **261 tests**（32 files）+ build；两仓库 diff check 全绿。Docker/浏览器/LongPort 外联 SKIPPED。
- **关联**：`ai/HANDOFF_2026-07-16_p12_acceptance_round6.md`、`AI_HANDOFF.md`、`acceptance/ACCEPTANCE_LOG.md`。

---

## 2026-07-16 — P1.2/P1.3 第六轮复验与交接

- **范围**：只读复核第五轮实现与测试；未修改业务代码。
- **已确认**：独立 `TaskReconcileService` 修复了同 Bean 自调用事务问题；任务明细入口和 remote adapter 已接入。
- **发现问题**：`TaskItemsDrawer` 两个 effect 同时请求明细，首次打开会重复调用，切换计划时旧页码请求可能覆盖新计划首页；表格缺少开始/结束时间；没有 Drawer 组件行为测试。
- **测试真实性**：`market-segments.test.tsx` 第 4/5/8 项只验证表单或按钮存在，没有提交创建、确认删除、确认移除，也没有断言 API、错误态、pending 防重复，不能计为约定的行为覆盖。
- **实测门禁**：后端 250 tests + package 通过；前端 lint + 253 tests + build 通过。门禁通过但用户路径与测试闭环不通过。
- **下一步**：按 `ai/HANDOFF_2026-07-16_p12_acceptance_round6.md` 与对应 ZCode prompt 做最小修复；未完成前不启动分钟 K 下一阶段。

---

## 2026-07-15 — P1.2/P1.3 第五轮收口（事务边界独立 Bean + 任务明细可达 + 8 项行为测试）

- **目标**：修复第五轮复验：reconcile self-invocation 事务失效、任务明细不可达、板块页面行为测试缺口。
- **后端修复（事务边界）**：
  - 新增 `TaskReconcileService`（独立 `@Service` + `@Transactional`），将 reconcile 逻辑从 `MarketDataWorkbenchService` 抽出。`listTaskItems` 懒收敛和 `reconcileTask` API 均通过 Spring 代理调用 `TaskReconcileService.reconcileTask`，解决 self-invocation 导致 `@Transactional` 失效。
  - 新增 `TaskReconcileServiceTest`（12 tests，含 6 count 字段、混合状态、null、child 缺失、501 item、幂等、task 不存在）。
  - `MarketDataWorkbenchServiceTest` 新增 2 个懒收敛测试（`listTaskItemsLazyReconcileCallsTaskReconcileService`、`listTaskItemsSkipsReconcileForTerminalTask`）。
- **前端修复（任务明细可达）**：
  - `/market-workspace` PlansTab 为有 `lastTaskId` 的计划新增"任务明细"按钮，打开 `TaskItemsDrawer`。
  - `TaskItemsDrawer` 调用 `listTaskItems` 展示 symbol/状态/行数/inserted/updated/skipped/subTaskId/错误/时间/分页；提供"刷新/收敛"按钮调用 `reconcileTask`，展示 loading/success/error + 防重复。
  - `workbenchApi.ts` 新增 `listTaskItems`/`reconcileTask` remote adapter 测试（2 tests：断言 path/params/body）。
- **前端修复（板块页面 8 项行为测试）**：
  - 使用 `vi.hoisted` + `vi.mock` 模块 mock + 可控 Promise，8 个测试覆盖：首次加载渲染、翻页请求、成员 Drawer 加载渲染、创建失败 catch、删除失败不误删、Alert 重试重新请求、添加防重复一次、移除防重复一次。
- **测试结果**：后端 **250 tests / 0 failures**；前端 typecheck + lint + **253 tests**（31 files）+ build 全绿。
- **未完成（不在本轮范围）**：分钟 K LongPort adapter + 盘中 scheduler + MINUTE_BAR_BACKFILL 执行链路。
- **关联**：`ai/HANDOFF_2026-07-15_p12_acceptance_round5.md`、`AI_HANDOFF.md`、`acceptance/ACCEPTANCE_LOG.md`。

---

## 2026-07-15 — P1.2/P1.3 第五轮复验与交接

- 实测后端247 tests/package、前端typecheck/lint/249 tests/build全绿。
- child count、501 item、null/缺失 child 的后端修复通过静态与单测核对。
- 发现前端 adapter 无页面调用，普通用户无法触发收敛；懒收敛同类自调用导致事务注解失效；页面关键行为测试仍缺失。
- 本轮未改业务代码。下一轮最小修复见 `docs/ai/HANDOFF_2026-07-15_p12_acceptance_round5.md`。

---

## 2026-07-15 — P1.2/P1.3 第四轮收口（reconcile 真实 count + 500 截断消除 + 懒收敛可达 + 页面测试补齐）

- **目标**：修复第四轮复验：reconcile count 从 child task 真实字段累加（不推导）；消除 500 item 截断；收敛可达（懒收敛+API+前端）；页面行为测试。
- **后端修复**：
  - reconcile 统计改为从 child `market_data_sync_task` 的 `totalCount/successCount/failCount/insertedCount/updatedCount/skippedCount` 直接累加（不推导 success，不固定 fail=0），null 按 0。
  - 新增 `selectAllByTaskId` Mapper SQL（全量查询无截断），消除 500 条限制；`reconcileTask` 加 `@Transactional` 事务边界。
  - `reconcileTask` 处理 child 缺失（→ item FAILED + errorCode/message）和 subTaskId 为空（→ item FAILED + 可解释消息）。
  - `listTaskItems` 查询 RUNNING 父任务时安全懒收敛（用户查看任务明细即触发，不依赖手工 curl）。
  - 测试从 31 增加到 37 个（新增 6 个：6 count 字段精确断言、混合 SUCCEEDED+FAILED 汇总、null count、child 缺失→FAILED、501 item 不截断、重复 reconcile 幂等）。
- **前端修复**：
  - `workbenchApi.ts` 新增 `reconcileTask` API（mock + remote），导出。
  - 页面测试从 3 增加到 6 个：首次加载渲染预置数据、点击新建打开 Drawer、空列表不崩溃、成员数列、类型标签、停用标签。
- **测试结果**：后端 **247 tests / 0 failures**；前端 typecheck + lint + **249 tests**（31 files）+ build 全绿。
- **未完成（不在本轮范围）**：分钟 K LongPort adapter + 盘中 scheduler + MINUTE_BAR_BACKFILL 执行链路。
- **关联**：`api/MARKET_DATA_API.md`、`DATABASE_DESIGN.md`、`BUILD_CHECKLIST.md`、`acceptance/ACCEPTANCE_LOG.md`、`AI_HANDOFF.md`、`ai/HANDOFF_2026-07-15_p12_acceptance_round4.md`。

---

## 2026-07-15 — P1.2/P1.3 第四轮复验与交接

- Codex 实测后端 241 tests/package、前端 typecheck/lint/246 tests/build 全绿。
- 发现 reconcile 仍推导 success、丢失 failCount，并固定只读 500 个 item；显式 API 没有普通用户触发路径。
- 页面实现已有 adding/removing 状态，但新增的 3 个组件测试未覆盖上一轮规定的关键交互与失败路径。
- 本轮只更新验收事实和交接文档，未修改业务代码。修复任务见 `docs/ai/HANDOFF_2026-07-15_p12_acceptance_round4.md`。

---

## 2026-07-15 — P1.2/P1.3 第三轮收口（count 逐项累加 + reconcile 收敛 + 前端类型修复 + 页面组件测试）

- **目标**：修复第三轮复验问题：runPlan count 用子任务返回值逐项累加（不反推）；非终态任务有收敛路径；前端 EntityId 类型错误；MembersDrawer 独立操作状态；页面组件测试。
- **后端修复**：
  - runPlan count 全部从子任务 `MarketDataSyncTaskVO` 返回值逐项累加：`totalCount/successCount/failCount/insertedCount/updatedCount/skippedCount` 直接累加，不再用 insertedCount 代替 successCount，不反推 failCount。
  - 新增 `reconcileTask(taskId)` 方法 + `POST /sync-tasks/{taskId}/reconcile` API：查询 RUNNING/PENDING item 的 `sub_task_id` 对应子任务终态，同步 item 状态/计数/finishedAt；全部终态后重新计算主任务 SUCCEEDED/PARTIAL_FAILED/FAILED 并写 finishedAt；部分非终态保持 RUNNING。幂等。
  - 测试：从 24 增加到 31 个（新增 count 逐项累加、updated/skipped=success、多子任务汇总、reconcile 收敛成功/保持 RUNNING/幂等/收敛失败）。
- **前端修复**：
  - segmentApi.test.ts EntityId `.length` 改为类型收窄，修复 typecheck/build 失败。
  - segmentApi.ts 移除 `id as string` 断言。
  - MembersDrawer 新增 `adding`/`removingSymbol` 独立状态（添加/移除期间防重复提交）。
  - 新增 `market-segments.test.tsx`（3 tests：渲染标题/首次加载/Drawer 打开）。
- **测试结果**：后端 **241 tests / 0 failures**；前端 typecheck + lint + **246 tests**（31 files）+ build 全绿。
- **上一轮误报更正**：前两轮声称"页面测试完成/前端全绿"实际 typecheck/build 失败；本轮真实全绿。
- **未完成**：分钟 K LongPort adapter + 盘中 scheduler + MINUTE_BAR_BACKFILL 执行链路。
- **关联**：`api/MARKET_DATA_API.md`、`DATABASE_DESIGN.md`、`BUILD_CHECKLIST.md`、`acceptance/ACCEPTANCE_LOG.md`、`AI_HANDOFF.md`、`ai/HANDOFF_2026-07-15_p12_acceptance_round3.md`。

---

## 2026-07-15 — P1.2/P1.3 第三轮复验与交接

- Codex 只读复验第二轮成果，未修改业务代码。
- 后端 234 tests 和 package 通过；前端 lint、243 tests 通过，但 typecheck/build 因 `EntityId.length` 类型错误失败。
- 发现 runPlan 父任务 success/fail 行数仍未按子任务返回值汇总，非终态父任务缺收敛机制；板块成员操作缺防重复状态，页面组件测试缺失。
- 当前状态改为未收口；修复任务见 `docs/ai/HANDOFF_2026-07-15_p12_acceptance_round3.md` 与 `docs/prompts/ZCODE_P12_ACCEPTANCE_FIX_ROUND3_PROMPT_2026-07-15.md`。

---

## 2026-07-15 — P1.2/P1.3 第二轮收口（runPlan 严格状态机 + V12 sub_task_id + 板块 mock UUID/规范化）

- **目标**：修复第二轮复验发现的任务状态机、计数口径、主子任务追踪和板块 mock ID/计数问题。
- **后端修复（runPlan 严格状态机）**：
  - V12 migration：`market_data_sync_task_item` 增加 `sub_task_id BIGINT`（含索引），支持 plan execution item → daily bar child task 直接追踪。DO/Mapper XML/VO 同步更新。
  - 状态映射改为严格模式：子任务 SUCCEEDED→item SUCCEEDED；PARTIAL_FAILED→item PARTIAL_FAILED（不再当成功）；FAILED→item FAILED；PENDING/RUNNING→item 保留非终态（主任务不写 SUCCEEDED/finishedAt）；未知/null→item FAILED。
  - 计数口径统一为行情数据行单位：task 的 total/success/fail/inserted/updated/skipped 全部从子任务返回值按行累加；symbol 维度状态由 task_item 表达，不混入 count 字段。
  - 业务异常保留原错误码（BusinessException→原 ErrorCode），不降级成 INTERNAL_ERROR。
  - 测试：从 19 个增加到 24 个（新增 runPlanPendingSubTaskKeepsNonTerminal、runPlanRunningSubTaskKeepsNonTerminal、runPlanPartialFailedSubTaskMapsToPartialFailed、runPlanSubTaskReturnedFailedStatus、runPlanUnknownSubTaskStatusMapsToFailed、runPlanAllPartialFailedMainIsPartialFailed）。断言 item 和 main 状态、count 行数、sub_task_id 持久化、非终态不设 finishedAt。
- **前端修复（板块 mock UUID + 计数 + 规范化）**：
  - segmentApi mock ID 改用 `generateId()` UUID string（不再用时间戳 number）；`segmentId` domain 类型从 `number` 改为 `EntityId`。
  - addMember 验证板块存在（孤儿拒绝）；canonical symbol 去空格+转大写+格式校验；重复判断使用规范化后的 symbol。
  - removeMember 先计算 remaining，只有命中时更新，memberCount=remaining.length（绝不使用 members.length-1）。
  - deleteSegment 使用 `removeItem(memberKey(id))` 真正级联删除桶（不是写空数组）。
  - 页面：创建/删除捕获 API 错误显示 message.error；创建/删除有 loading/disabled 状态防重复提交；删除当前页最后一条后页码回退。
  - 测试：从 13 增加到 22 个（新增 UUID 格式、removeMember 不存在不改计数、空成员不出现负数、孤儿成员拒绝、symbol 规范化、非法 symbol、级联 key 真删除检查 null）。remote adapter 补 get/update/listMembers 测试。
- **测试结果**：后端 `./mvnw test` **234 tests / 0 failures**；前端 typecheck + lint + **243 tests** + build 全绿。Docker curl smoke test 通过。
- **未完成（不在本轮范围）**：分钟 K LongPort adapter + 盘中 scheduler + MINUTE_BAR_BACKFILL 执行链路。
- **关联**：`api/MARKET_DATA_API.md`、`api/API_INDEX.md`、`mock/MOCK_REMOTE_CONTRACT.md`、`DATABASE_DESIGN.md`、`BUILD_CHECKLIST.md`、`acceptance/ACCEPTANCE_LOG.md`、`AI_HANDOFF.md`。

---

## 2026-07-14 — P1.2/P1.3 收口验收修复（mock 持久化 + runPlan 真实汇总 + 文档一致）

- **目标**：修复 P1.2/P1.3 验收问题：板块 mock 不持久化、runPlan 虚报成功、分页不触发加载、成员抽屉错误态缺失、remote 测试空覆盖、文档事实冲突。
- **前端修复（板块 mock 持久化 + 页面可用性）**：
  - `segmentApi.ts` mock 实现全部改为 `localStorageClient` 持久化：create/list/get/update/delete + member add/list/remove 有真实存储效果；delete 级联清理成员；addMember 禁止同板块同 symbol 重复；memberCount 与成员数一致；list 支持 segmentType/enabled/keyword 筛选和分页；update 保留未修改字段。
  - remote 实现用 `unwrapVoid` 处理 DELETE 操作（之前用 `unwrap<void>` 对 data=null 报错）。
  - `/market-segments` 页面：首次加载 `useEffect` 依赖 `[page]` 实现分页切换自动加载；MembersDrawer 切换板块先清理旧数据再加载，补 error Alert + 重试按钮；handleAdd/handleRemove 捕获异常显示错误。
  - 测试：`segmentApi.test.ts` 重写为 13 tests（mock 完整生命周期 create→list→get→update→addMember→memberCount→removeMember→delete + 分页 + 筛选 + remote adapter 调用断言）。
- **后端修复（runPlan 真实汇总 + scope 校验）**：
  - `runPlan` 不再硬编码 `insertedCount=1/successCount++`；使用 `createAndExecuteDailyBarSync` 返回的 `MarketDataSyncTaskVO` 映射 item 状态和计数（inserted/updated/skipped/total 来自子任务真实结果）。
  - 子任务状态映射：SUCCEEDED→item SUCCEEDED；PARTIAL_FAILED→item SUCCEEDED（部分数据已写入）；PENDING/RUNNING→item SKIPPED（幂等复用）；FAILED→item FAILED。主 task 状态根据汇总的 success/fail 判定 SUCCEEDED/PARTIAL_FAILED/FAILED。
  - `parseScope` 用 Jackson（正常 import，不用全限定类名），校验：symbol 格式（SH/SZ/BJ.数字）、去空白去重、startDate<=endDate、非法 JSON/日期/symbol 抛 BusinessException。修正 Javadoc 与代码一致。
  - 测试：新增 8 个测试（成功链路验证 startDate/endDate/adjustType/symbol 透传 + inserted/updated/skipped 汇总断言；幂等 RUNNING→SKIPPED；子任务 FAILED→主 task FAILED；多 symbol PARTIAL_FAILED；重复 symbol 去重；非法 JSON；非法日期范围；非法 symbol 格式）。
- **建设看板修复**：删除重复 `market-ops-workbench`；`market-collection-jobs` TODO→IN_PROGRESS（日K手动执行已接入，分钟K/scheduler TODO）；`minute-bar-asset` TODO→IN_PROGRESS（表+质量校验已实现，LongPort adapter 未接入）。
- **测试结果**：后端 `./mvnw test` **229 tests / 0 failures**；前端 typecheck + lint + **234 tests** + build 全绿。
- **未完成（不在本轮范围）**：分钟 K LongPort 批量 adapter（`getMinuteBars`）；盘中 scheduler（`@Scheduled`）；`MINUTE_BAR_BACKFILL`/`INTRADAY_*` 执行链路（手动执行返回业务错误）。
- **关联**：`api/MARKET_DATA_API.md`、`api/API_INDEX.md`、`mock/MOCK_REMOTE_CONTRACT.md`、`BUILD_CHECKLIST.md`、`acceptance/ACCEPTANCE_LOG.md`、`AI_HANDOFF.md`。

---

## 2026-07-13/14 — P1.2 收口验收修复（页面可用 + 状态真实 + 文档一致）

- **目标**：把 P1.2 从"能编译"修到"页面可用、状态真实、文档一致"，不扩散开发范围。
- **前端修复（/market-segments 页面）**：
  - `SegmentListTab` 缺 `useEffect` 导致首次进入不加载列表 → 补 `useEffect` 首次自动调 `listSegments`。
  - `MembersDrawer` 用 `useCallback` 代替 `useEffect`（只缓存不执行）→ 改为 `useEffect`，打开抽屉自动加载成员。
  - 新增 `segmentApi.test.ts`（8 tests 覆盖 create/list/delete/members mock）。
- **后端修复（runPlan 状态 + scope 解析）**：
  - 非 `DAILY_BAR_BACKFILL` 类型不再标 SKIPPED 蒙混 → 直接抛 `BusinessException`（"执行链路尚未接入"），不创建空壳任务误导用户。
  - `extractSymbolsFromScope` 正则解析 → 改为 Jackson `ObjectMapper` 结构化解析（`parseScope`），同时提取 `startDate`/`endDate` 传给 `createAndExecuteDailyBarSync`。
  - 新增 2 个测试：`runPlanRejectsNonDailyTaskType`、`runPlanRejectsEmptyScope`。
- **建设看板修复**：
  - 删除重复的 `market-ops-workbench`（IN_PROGRESS/30%）节点（已被 `market-workspace` DONE/80% 取代）。
  - `market-collection-jobs` 从 TODO/15% 改为 IN_PROGRESS/50%（日K手动执行已接入，分钟K/盘中调度 TODO）。
  - `minute-bar-asset` 从 TODO/5% 改为 IN_PROGRESS/40%（表+质量校验+API 已实现，LongPort adapter 未接入）。
  - `market-workspace` risks 修正（"概览聚合计数为占位" → 已接 DAO 真实查询）。
- **API 文档**：`MARKET_DATA_API.md` 新增 §3（工作台/采集计划/分钟K/水位/板块 API 清单 + 质量校验说明 + 手动执行说明）。
- **测试结果**：后端 219 tests / 0 failures；前端 229 tests / typecheck / lint / build 全绿。
- **未完成**：分钟 K LongPort 批量 adapter（`getMinuteBars`）+ 盘中 scheduler（`@Scheduled`）未接入；`MINUTE_BAR_BACKFILL` / `INTRADAY_*` 手动执行返回业务错误。
- **关联**：`api/MARKET_DATA_API.md`、`BUILD_CHECKLIST.md`、`acceptance/ACCEPTANCE_LOG.md`、`AI_HANDOFF.md`。

---

## 2026-07-12 — P1.2 行情工作台 + 分钟线资产 + P1.3 板块/自定义分组

- **目标**：把行情能力从"单次接口验证"升级为"可配置、可追踪、可复用的数据资产建设流程"，完成 P1.2 工作台/采集/分钟线/水位/质量治理 + P1.3 板块管理。
- **范围**：后端 V10+V11 migration（8 张新表）+ 完整分层代码 + 29 个新单测 + 前端 2 个新页面 + API 层 mock/remote 双模式。不接交易/订单/账户/持仓；不保存密钥；不改历史 migration。
- **后端改动**：
  - V10 migration：`stock_minute_bar`、`market_trading_session`、`market_calendar`、`market_data_sync_plan`、`market_data_sync_task_item`、`market_data_watermark`。
  - V11 migration：`market_segment`、`market_segment_member`。
  - `MinuteBarQualityManager`：OHLC 合法、volume/amount 非负、时段校验、冲突检测、VALID/SUSPECT/REJECTED。
  - `TradingSessionManager`：DB 优先 + A 股默认窗口/周末规则回退 + 幂等初始化。
  - `MarketDataWorkbenchService`：采集计划 CRUD/启停、分钟 K 幂等写入（冲突不覆盖+alert/质量拒绝+alert）、自动水位更新、工作台概览。
  - `MarketSegmentService`：板块 CRUD + 成员增删改查。
  - `MarketDataWorkbenchController`（12 个 API）+ `MarketSegmentController`（8 个 API）。
  - 完整 DO/Mapper/XML/DTO/VO 分层，PageResultVO 补 `of` 工厂方法。
- **前端改动**：
  - `/market-workspace` 页面（4 Tab：概览/采集计划/分钟K/水位）。
  - `/market-segments` 页面（板块列表 + 成员管理 Drawer）。
  - `workbenchApi.ts` + `segmentApi.ts`（mock/remote 双模式）。
  - 路由注册 + 侧边栏菜单（行情工作台 + 板块管理）。
  - mock 测试覆盖。
- **测试结果**：后端 `./mvnw test` 217 tests / 0 failures；前端 typecheck + lint + 221 tests + build 全绿。
- **LongPort 真实外联**：跳过（SKIPPED）—— 无凭据/容器，且本轮代码不涉及 LongPort 反射链路。
- **遗留问题 / 待办**：盘中自动调度未实现（trigger_type 有配置但无定时器）；分钟 K 批量拉取（getMinuteBars）未接通 LongPort adapter；工作台概览聚合计数为占位；日历表无初始化数据。详见 `docs/ai/HANDOFF_2026-07-12_market_data_long_run.md`。
- **关联文档**：`features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`、`api/MARKET_DATA_API.md`、`BUILD_CHECKLIST.md`、`acceptance/ACCEPTANCE_LOG.md`、`ai/HANDOFF_2026-07-12_market_data_long_run.md`。

---

## 2026-07-12 — P1.2 行情工作台与采集任务设计 + 建设看板同步

- **目标**：在 LongPort P1.1 真实外联验收通过后，重新规划下一阶段行情系统建设，避免直接跳到指标/策略/回测导致数据资产地基不足。
- **范围**：产品/架构设计文档 + 当前事实文档 + 前端建设看板数据与测试；不改后端业务代码、不改 DB migration、不接交易能力。
- **产品决策**：
  - 行情能力放到工作台下形成“行情工作台”，高频展示 provider 状态、重点标的、最近同步、失败任务和未处理提醒。
  - 配置类能力放到“行情数据配置中心”，管理历史补档、盘中定时采集、数据源、标的池、板块池、提醒规则和任务日志。
  - 异动大屏作为盘中展示模式，先围绕持仓股、自选股、计划股和自定义板块，不做全市场扫描。
  - 采集频率与 K 线粒度明确拆分；历史 30min K 线不能由最新价快照拼接冒充。
- **架构决策**：
  - 保留 `stock_basic`、`stock_daily_bar`、`stock_quote_snapshot`、`market_data_sync_task`、`market_data_alert`。
  - 下一阶段优先新增 `stock_minute_bar`、交易日历/交易时段、采集计划、任务明细、水位、板块和异动事件。
  - 行情数据分为原始行情事实、衍生统计、任务/质量治理三层。
  - LongPort 继续作为主线，同时在 provider 抽象中预留 Tushare、AKShare、BaoStock 和专业数据导入桥。
- **文档改动**：
  - 新增 `docs/features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`。
  - 更新 `AI_DEVELOPMENT_INDEX.md`、`AI_HANDOFF.md`、`PRODUCT_BLUEPRINT.md`、`BUILD_CHECKLIST.md`、`CURRENT_ARCHITECTURE_AND_MODULES.md`。
  - 修正 `MARKET_DATA_FOUNDATION_DESIGN.md` 和 `LONGPORT_SINGLE_SYMBOL_SYNC_ENGINE_DESIGN.md` 的旧口径，明确 P1.1 已完成，P1.2 才是下一阶段。
- **前端看板改动**：
  - `longport-quote-snapshot`、`longport-history-sync` 更新为 DONE/M4。
  - 新增 `longport-hardening`、`market-ops-workbench`、`market-collection-jobs`、`minute-bar-asset`、`market-movement-dashboard`、`multi-source-provider-research`。
  - summary 当前最优先改为 `P1.2 行情工作台与采集任务`。
- **测试结果**：本轮为文档/看板同步，执行 `git diff --check` 和前端建设看板测试；结果见 `../acceptance/ACCEPTANCE_LOG.md` 对应条目。
- **遗留问题**：P1.2 尚未实现业务代码；下一轮应按新设计开发行情工作台 MVP、采集任务配置和分钟线资产。
- **关联文档**：`../features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`、`../BUILD_CHECKLIST.md`、`../AI_HANDOFF.md`。

---

## 2026-07-12 — LongPort SDK 安装 + 域名覆盖 + 真实外联验收

- **目标**：完成 P1.1 LongPort 单股票手动同步真实外联的最后一公里 —— 安装官方 Java SDK、解决 SDK 默认域名废弃问题、单 symbol 单日真实落库验收。
- **范围**：后端代码（配置 + 反射 adapter）+ 配置文件透传 + `.env`/`.env.example` + docker-compose + 文档；不新增 DB migration；不接交易、账户、订单、真实持仓能力；不保存密钥。
- **关键发现 1：官方 SDK artifact 早已可用，之前 groupId 查错**：
  - 之前所有"Maven Central 查不到"的结论根因是官方源码 `java/javasrc/pom.xml` 里 groupId `io.github.longport` 缺 `app` 后缀。
  - 正确坐标 `io.github.longportapp:openapi-sdk:4.3.3`（`<release>=4.3.3`，`versionCount=68`，`lastUpdated=20260701095601`）。
  - `openapi-sdk-4.3.3.jar`（约 35MB）内置全平台 native（linux/osx/windows × 64/arm64），含本项目反射 adapter 需要的全部 `com.longport.*` 类。一个 jar 同时覆盖本机 osx_arm64 与服务器 linux_64，无需源码构建。
- **关键发现 2：SDK 默认域名已废弃，需切换到 Longbridge 新域名**：
  - native lib 硬编码默认域名 `https://openapi.longport.cn`（HTTP）+ `wss://openapi-quote.longport.cn/v2`（quote ws）已废弃，DNS 解析失败（长桥已更名 Longbridge）。
  - 可用同源域名：`https://openapi.longbridge.cn` + `wss://openapi-quote.longbridge.cn/v2`（解析到阿里云国内节点）。
- **后端改动**：
  - `LongPortProperties` 新增可选 `httpUrl`、`quoteWebsocketUrl` 字段及 getter/setter/hasXxx，由 `LONGPORT_HTTP_URL` / `LONGPORT_QUOTE_WEBSOCKET_URL` 环境变量驱动，默认空（不影响默认行为）。
  - `ReflectiveLongPortQuoteClient.createConfig` 在创建 `Config` 后，若配了上述字段则反射调用 `Config.httpUrl(...)` / `Config.quoteWebsocketUrl(...)` 覆盖默认域名。
  - `application.properties` 增加 `qta.market-data.longport.http-url` / `quote-websocket-url` 占位符绑定。
  - `docker-compose.yml` app 服务：透传 `LONGPORT_HTTP_URL` / `LONGPORT_QUOTE_WEBSOCKET_URL`；新增 `dns`（默认 `223.5.5.5` / `119.29.29.29`，由 `QTA_DNS_SERVER_1/2` 覆盖）保证容器内 native resolver 解析外部域名。
  - `.env.example` 增加 `LONGPORT_HTTP_URL=` / `LONGPORT_QUOTE_WEBSOCKET_URL=`。
- **凭据管理**：本地只读凭据放在独立的 `.env.longport`（被 `.gitignore` 的 `.env.*` 规则忽略），运行前 `set -a; source .env.longport; set +a` 注入；`.env` 只保留本地基础配置 + 非密开关。两文件均不入 Git。
- **测试结果**：
  - `./mvnw test` 187 tests / 0 failures / 0 errors。
  - `inspect-longport-runtime-libs.sh` 对 osx_arm64 与 linux_64 均通过。
  - `check-longport-readiness.sh` errors=0。
  - `verify-longport-real-sync.sh`（SH.600519 / 2026-07-10 / NONE）全绿：provider `configured=true / reachable=true`；latest quote 写入 `stock_quote_snapshot`（dataSource=LONGPORT）；daily bar 写入 `stock_daily_bar`（data_source=LONGPORT）；sync task `SUCCEEDED / insertedCount=1`。
- **遗留问题 / 部署注意**：
  - 服务器部署必须配 `LONGPORT_HTTP_URL=https://openapi.longbridge.cn` + `LONGPORT_QUOTE_WEBSOCKET_URL=wss://openapi-quote.longbridge.cn/v2`，否则 SDK 默认域名解析失败。详见 `docs/development/LONGPORT_SDK_RUNTIME_INSTALLATION.md`。
  - `docker-compose.yml` 的 `dns` 默认值是国内公共 DNS；海外部署如需可经 `QTA_DNS_SERVER_1/2` 覆盖。
- **关联文档**：`docs/development/LONGPORT_SDK_RUNTIME_INSTALLATION.md`、`api/MARKET_DATA_API.md`、`BUILD_CHECKLIST.md`、`acceptance/ACCEPTANCE_LOG.md`。

### 2026-07-12 追加：文档口径统一收口 + 域名覆盖补测试 + 全门禁复核

- **目标**：用户复核确认真实外联链路跑通后，做最终收口 —— 统一所有当前事实文档的旧口径（"SDK 待安装 / Maven 查不到 / 外联未完成"）、为域名覆盖逻辑补最小单测、跑全量前后端质量门禁、只读复核真实外联状态。
- **文档口径修正（7 个入口文档 + 1 个合约文档）**：
  - `docs/api/MARKET_DATA_API.md`：头注 + §2 标题 + §2 实现状态 + §3 安全约束，统一改为"真实外联已验收 + 正确坐标 `io.github.longportapp:openapi-sdk:4.3.3` + runtime-libs gitignored + 域名覆盖必配"。
  - `docs/PRODUCT_BLUEPRINT.md`：P1.1 从"部分实现"改为"已完成 + 真实外联已验收 + 域名覆盖必配"。
  - `docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`：当前实现事实从"SDK 包安装和凭据联调待完成"改为"已装 + 真实外联已验收"。
  - `docs/features/LONGPORT_SINGLE_SYMBOL_SYNC_ENGINE_DESIGN.md`：§2.5"仍未完成/外部阻塞"整段改为"外部阻塞已全部解除 + 验收通过 + 域名覆盖必配"。
  - `docs/prompts/ZCODE_LONGPORT_RESUME_PROMPT_2026-07-12.md`：顶部加"历史状态(归档)"说明，标注后续读 `AI_HANDOFF.md` 获取当前事实，原 prompt 块保留作历史归档。
  - `docs/development/LONGPORT_OFFICIAL_JAVA_CONTRACT.md`：合约表补 `Config.httpUrl(String)` / `Config.quoteWebsocketUrl(String)` 两行。
  - `docs/AI_HANDOFF.md`、`docs/BUILD_CHECKLIST.md`：前几轮已更新为最新口径，本轮无需改。
  - 原则：历史日志（DEVELOPMENT_LOG/ACCEPTANCE_LOG 历史条目、resume prompt 原文）保留不动，只改"当前入口文档"避免误导新会话。
- **补测试（域名覆盖逻辑的最小单测）**：
  - `src/test/java/com/longport/Config.java`（fake SDK）：新增链式 `httpUrl(String)` / `quoteWebsocketUrl(String)` 方法 + getter，对齐官方 SDK fluent 风格。
  - `src/test/java/com/quant/trade/marketdata/provider/ReflectiveLongPortQuoteClientTest.java`：新增 `reflectiveSdkPathHonoursDomainOverrides` 测试 —— 配置 httpUrl + quoteWebsocketUrl 后验证 healthCheck configured/reachable + quote + daily bar 全成功。
  - `scripts/check-longport-official-java-contract.sh`：新增 `Config.httpUrl(String)` / `Config.quoteWebsocketUrl(String)` 合约断言，防止 SDK 升级破坏域名覆盖。
- **质量门禁结果（全绿）**：
  - 后端：`bash -n scripts/*.sh`（6 脚本）通过；`git diff --check` 通过；`./mvnw test` **188 tests / 0 failures**（较上轮 187 +1 新测试）；`./mvnw -DskipTests package` BUILD SUCCESS。
  - 前端：`git diff --check` 通过；`npm run typecheck` 通过；`npm run lint` 通过；`npm run test` **214 tests passed**；`npm run build` 通过。
- **真实外联只读复核（HTTP 200，容器未重建）**：
  - provider `configured=true / reachable=true / lastError=null`。
  - quote-snapshots：SH.600519 贵州茅台 1 条 LONGPORT 数据（price=1204.98 / vol=52212）。
  - daily-bars：8 条 LONGPORT 日 K（7/1-7/10 跳过周末，OHLC 合理，7/10 收盘 1204.98 与快照一致）。
- **安全复核**：`.env` / `.env.longport` / runtime-libs jar 均不在 git status（gitignored）；所有 tracked 改动无 LongPort 凭据明文；无交易/订单/账户/持仓能力接入；未 commit/push。
- **遗留风险**：(1) 域名漂移 —— 已靠合约检查脚本兜底；(2) 仅单 symbol 验收，多 symbol 并发/边界日期/QF 复权未压测（BUILD_CHECKLIST 已记）；(3) volume 同日差 1 是实时快照 vs 历史 K 线两个 API 的正常口径差异，非 bug。
- **关联**：`acceptance/ACCEPTANCE_LOG.md`（2026-07-12 收口追加）、`AI_HANDOFF.md`、`LONGPORT_SDK_RUNTIME_INSTALLATION.md`。

---

## 2026-07-11 — LongPort 单股票同步后端 adapter

- **目标**：把 LongPort 单股票手动同步从“接口壳 + DB 留痕”推进到后端 provider adapter 可运行状态，同时避免不可用 SDK 坐标拖垮构建。
- **范围**：后端代码 + 配置 + 测试 + 文档；不新增 DB migration；不接交易、账户、订单、真实持仓能力；不保存密钥。
- **后端改动**：
  - 新增 `LongPortProperties`：统一绑定 LongPort enabled、legacy API key、timeout、quote time zone。
  - 新增 `LongPortQuoteClient` 与 `ReflectiveLongPortQuoteClient`：运行时反射调用官方 Java SDK，只读调用 `getQuote` 与 `getHistoryCandlesticksByDate`。
  - 新增 `LongPortMarketDataProvider`：负责 canonical symbol 转换、`NONE/QF` 复权映射、`HF` 明确拒绝、provider 状态。
  - 调整 `MarketDataConfig` / `DisabledMarketDataProvider`：默认 disabled，`qta.market-data.longport.enabled=true` 时切换 LongPort provider。
  - 调整 `MarketQuoteService`：provider 不可用时返回具体原因（未启用 / SDK 缺失 / 凭据缺失），并写入 alert/task。
  - `.env.example`、`docker-compose.yml` 增加 LongPort 环境变量透传。
- **外部调研结论**：
  - 官方 Java README/Javadoc 存在，API 方法形状已确认。
  - Maven Central 当前查询不到 `io.github.longport:openapi-sdk` artifact；GitHub `v4.3.3` release 当前未提供 Java jar。
  - 因此本轮采用反射式 adapter，等待 SDK jar/native libs 可安装后做真实小调用验收。
- **测试结果**：
  - `./mvnw -q -Dtest=LongPortMarketDataProviderTest,ReflectiveLongPortQuoteClientTest,LongPortSymbolMapperTest,MarketQuoteServiceTest test` 通过。
  - `./mvnw -q -Dtest=LongPortEnabledWithoutSdkContextTest,LongPortMarketDataProviderTest,ReflectiveLongPortQuoteClientTest,MarketQuoteServiceTest test` 通过。
- **遗留问题**：
  - 前端 `/market-data` 小改已补：状态页展示 SDK/凭据未就绪，历史同步禁用 `HF`。
  - 未执行 Docker 重构建与真实 LongPort 外联；真实外联取决于 SDK jar/native libs 安装。
- **关联文档**：`features/LONGPORT_SINGLE_SYMBOL_SYNC_ENGINE_DESIGN.md`、`api/MARKET_DATA_API.md`、`BUILD_CHECKLIST.md`、`acceptance/ACCEPTANCE_LOG.md`、`ai/HANDOFF_2026-07-11_longport_single_symbol_engine.md`。

### 2026-07-11 追加：运行时 classpath 与 SDK 分发复核

- **目标**：避免后续拿到 LongPort SDK jar 后仍因 Docker `java -jar` 启动方式无法加载外部 jar。
- **改动**：
  - `Dockerfile` 改为通过 Spring Boot `PropertiesLauncher` 启动，`loader.path` 指向 `/app/libs`。
  - `docker-compose.yml` 将项目 `runtime-libs/` 只读挂载到容器 `/app/libs`。
  - `.gitignore` 忽略 `runtime-libs/*`，仅保留 `.gitkeep`，防止 vendor jar/native 包误提交。
  - 新增 `development/LONGPORT_SDK_RUNTIME_INSTALLATION.md`，沉淀官方 artifact 查询结论、推荐安装路径和最小真实外联验收命令。
  - `ReflectiveLongPortQuoteClient` 支持注入 ClassLoader 便于测试；等待 SDK Future 时补中断处理；错误信息会脱敏显式 LongPort 凭据。
  - 增加 test-only fake LongPort SDK 类，覆盖 `QuoteContext#create`、`getQuote`、`getHistoryCandlesticksByDate` 的反射调用路径。
- **外部复核结论**：
  - LongPort `v4.3.3` release workflow 存在 `build-java-jni` 和 `publish-java-sdk`，理论上会把 JNI 打入 Java jar 并 deploy 到 Maven Central。
  - 但 `repo.maven.apache.org` metadata 当前仍 404，`search.maven.org` 精确查询仍为 0，GitHub release 仍未挂 Java jar。
- **遗留问题**：SDK 获取本身仍未解决；真实 quote/candlestick 小调用等待 SDK artifact 或源码构建产物 + 用户只读凭据。
- **验证**：`./mvnw -q clean test` 通过（183 tests，0 failures/errors）；`./mvnw -q -DskipTests package` 通过；`docker compose up -d --build app` 成功；`curl /actuator/health` 200 UP；`curl /api/v1/market-data/providers/LONGPORT/status` 200 + provider 未启用。
- **runtime-libs 外部 jar 验证**：2026-07-12 临时将 test-only fake LongPort SDK 打成 jar 放入 `runtime-libs/`，使用 fake 凭据重建容器；status 返回 `configured=true/reachable=true`，`POST /quotes/latest` + `persist=false` 返回 `SH.600519` fake quote。验证后已删除 fake jar 并恢复默认 disabled 容器。
- **可重复脚本**：新增并执行 `scripts/verify-longport-runtime-libs.sh`，后续可一键复验 runtime-libs 外部 jar 加载链路；脚本会自动清理 fake jar 并恢复默认 disabled 容器。
- **真实外联验收脚本**：新增 `scripts/verify-longport-real-sync.sh`，用于官方 SDK 和真实只读凭据到位后，一键验证 provider status、最新价落库和日 K 同步任务；默认 symbol/date 为 `SH.600519` / `2026-07-10`，可用环境变量覆盖。脚本启动前会预检 LongPort 三项凭据，默认保留 enabled 容器用于继续联调，可用 `QTA_VERIFY_RESTORE_APP_AFTER_RUN=true` 退出时恢复默认 disabled 容器。
- **收尾复验**：2026-07-12 10:36 重新执行 `./mvnw -q test`，Surefire 汇总 183 tests / 0 failures / 0 errors；`bash -n` 两个 LongPort 验收脚本通过；`git diff --check` 通过；默认容器 status 确认为 HTTP 200 + provider 未启用。
- **latest quote 请求校验补强**：2026-07-12 继续补齐 `FetchQuotesRequestDTO` Bean Validation、controller `@Valid`、service 直接调用校验；空 `canonicalSymbols`、超过 500 个标的、空代码、非法 canonical symbol 会在 provider 调用前返回参数/代码格式错误。同步修正 `MARKET_DATA_API.md` 中 `quote-snapshots` 与 `sync-tasks` 查询参数说明。
- **latest quote 补强后复验**：`./mvnw -q -Dtest=MarketQuoteControllerValidationTest,MarketQuoteServiceTest,LongPortMarketDataProviderTest,ReflectiveLongPortQuoteClientTest test` 通过；`./mvnw -q test` 通过，Surefire 汇总 187 tests / 0 failures / 0 errors；`./mvnw -q -DskipTests package` 通过；两个 LongPort 验收脚本 `bash -n` 通过；`git diff --check` 通过。
- **前端联调防呆补强**：行情页 latest quote 支持 canonical symbol 格式校验、去重、单次 500 个上限；历史日 K 同步支持 canonical symbol、日期范围、HF 禁用校验；两个写操作在请求前先检查 LongPort provider status，未配置/不可达时只提示用户，不制造失败同步任务。Provider 状态页补充面向 SDK 缺失/凭据缺失/不可达的 Alert。前端 `npm run typecheck` / `lint` / `test`（214 tests）/ `build` 通过。
- **SDK 源码构建脚本**：新增 `scripts/build-longport-java-sdk-from-source.sh`，根据官方 release workflow 的 `build-java-jni` / `publish-java-sdk` 步骤，支持从官方 `longportapp/openapi` tag 构建当前平台或 `QTA_LONGPORT_RUST_TARGET` 指定平台的 JNI，执行 Java Maven package，并把 SDK jar 与 runtime 依赖复制到 `runtime-libs/`。脚本默认不覆盖已有 jar，不删除已有 build 目录，不读取任何 LongPort 凭据；本轮仅执行 `bash -n`，未做真实源码构建。
- **SDK 离线检查脚本**：新增 `scripts/inspect-longport-runtime-libs.sh`，用于真实外联前离线检查 `runtime-libs/` 中 SDK jar、目标平台 native、`gson`、`native-lib-loader` 是否齐全，并拒绝 fake SDK jar。当前空 `runtime-libs/` 下脚本会明确提示需要先 build/download SDK。已用临时 fake SDK/dependency jars 验证正向路径、缺 native 失败路径、fake SDK jar 残留失败路径。
- **官方 SDK 合约检查脚本**：新增 `scripts/check-longport-official-java-contract.sh` 和 `docs/development/LONGPORT_OFFICIAL_JAVA_CONTRACT.md`，用于升级 SDK tag 前检查官方 Java 源码中的类、方法、getter、枚举常量是否仍匹配 `ReflectiveLongPortQuoteClient`。本轮对 `v4.3.3` 本地官方源码缓存检查通过；在线 GitHub raw 检查受当前代理/DNS 影响失败。
- **真实外联预检脚本**：新增 `scripts/check-longport-readiness.sh`，用于在真实外联前集中检查 LongPort 三项只读凭据、`QTA_LONGPORT_ENABLED`、`runtime-libs` SDK/native/dependency 结构、可选官方源码合约和可选 provider status；脚本不会打印密钥。`scripts/verify-longport-real-sync.sh` 增加 `QTA_VERIFY_RUNTIME_LIB_INSPECTION=auto|true|false`，默认在 `runtime-libs` 有 jar 时先做离线结构检查。
- **遗留真实外联**：未执行真实 LongPort 外联；2026-07-12 复查 Maven Central metadata 仍 404，`search.maven.org` 仍 `numFound=0`，仍缺官方 SDK jar/native libs 与用户只读凭据。

---

## 2026-07-10 — LongPort 只读行情源产品与架构设计

- **目标**：研究 LongPort/长桥 OpenAPI 是否适合接入 A 股行情，并沉淀下一轮前后端开发设计。
- **范围**：只做产品/架构/文档设计，不改业务实现代码，不接真实交易能力。
- **发现**：
  - 代码事实已包含 `marketdata` 模块、V5/V6、`stock_basic`、`stock_daily_bar`、CSV 日 K 导入和 `/api/v1/market-data/*` 基础接口。
  - 部分文档仍把行情基础标为规划，已在本轮同步。
  - LongPort 能力覆盖实时行情、历史 K 线、MCP/SDK，但 MCP 也暴露交易/账户能力，必须通过 ADR 限定 quote-only。
- **产品决策**：
  - LongPort 只作为只读行情 provider。
  - 最新价进入 `stock_quote_snapshot`，历史日 K 进入 `stock_daily_bar(data_source=LONGPORT)`。
  - 外部行情不得覆盖 `portfolio_price_snapshot` 手工当前价。
  - 异常提醒先做数据质量，再做量价观察，不输出买卖建议。
- **新增文档**：
  - `features/LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md`
  - `features/MARKET_ALERT_RULES_DESIGN.md`
  - `development/2026-07-10-longport-market-data-research.md`
  - `decisions/ADR-0008-longport-quote-only-provider.md`
  - `api/MARKET_DATA_API.md`
  - `prompts/LONGPORT_MARKET_DATA_CLAUDE_PROMPT.md`
- **同步文档**：`PRODUCT_BLUEPRINT.md`、`BUILD_CHECKLIST.md`、`CURRENT_ARCHITECTURE_AND_MODULES.md`、`DATABASE_DESIGN.md`、`api/API_INDEX.md`、`AI_HANDOFF.md`、`decisions/ADR_INDEX.md`。

---

## 2026-07-06 — 生产环境实测验证

- **目标**：验证生产 Nginx → 后端 → MySQL 链路，确认 production-data-mode 真实状态。
- **验证（只读 GET）**：
  - `http://129.204.169.155:18080/` 首页 → HTTP 200。
  - `/api/v1/watchlist` → `success=true, data=[]`。
  - `/api/v1/trade-plans` → `success=true, data=[]`。
  - `/api/v1/dashboard/today` → `success=true`，含完整 date/todos/pendingReviewJournals 数据（1 条 AAPL PENDING 交易）。
- **结论**：生产同源 /api/v1 + Nginx 反代 + Docker qta-server + MySQL 链路**实测通过**。production-data-mode 升级为 DONE/M4。
- **关联**：`acceptance/ACCEPTANCE_LOG.md`、前端 `buildStatusData.ts`。

---

## 2026-07 — 建设看板状态同步与发布收口

- **目标**：让建设看板与 v0.1.1 已验收事实、BUILD_CHECKLIST、PRODUCT_BLUEPRINT 完全一致。
- **范围**：前端看板数据 + 同步机制，不改业务代码/DB。
- **改动**：
  - `buildStatusData.ts` 重写：修正 6 类过期节点（pnl-explainability target、portfolio-pnl IN_PROGRESS→DONE、production-data-mode RISK→DONE、ai-collaboration "已推送"→"已沉淀"、trade-loop/position-snapshot nextActions）；新增 `market-data-foundation` P1 一级节点（stock-basic/daily-bar-import/market-data-provider）；`ai-input` P1→P2；`daily-bar-import` 从 quant-analysis 移入行情基础。
  - `pages/build-status.tsx` 加看板基线提示（v0.1.1 / 2026-07-06 / 与 BUILD_CHECKLIST 同步）。
  - `useBuildStatus` selectedId 初始 `null`（进入/刷新不默认打开抽屉）。
  - `production-data-mode` currentEvidence 分两条（同源 /api/v1 + curl 链路 / mock 4 页面 Playwright）。
  - 同步机制：`DEVELOPMENT_WORKFLOW` + `qta-context-bootstrap` 加 buildStatusData 同步规则；`BUILD_STATUS_BOARD_DESIGN` 标初始基线。
  - 口径统一：BUILD_CHECKLIST/PRODUCT_BLUEPRINT/buildStatusData "证券主数据**与**行情基础"。
- **测试**：`buildStatusData.test.ts` 重写（v0.1.1 DONE/M4、snapshot-comparison 100%、market-data-foundation P1、ai-input 非 P1、无过期下一步、一级分类含行情基础）；新增 `useBuildStatus.test.ts`（初始未选中/选择/关闭）。
- **验收**：后端 121、前端 191 测试通过；浏览器 /build-status 控制台 0 deprecated/error；基线 + P1 行情基础 + 节点显示；production-data-mode 降级 RISK/M3（生产 Nginx 反代未实测，不与"已验证"矛盾）。
- **关联文档**：`BUILD_CHECKLIST.md`、`PRODUCT_BLUEPRINT.md`、`acceptance/ACCEPTANCE_LOG.md`。

---

## 2026-07 — 文档体系治理与上下文加载 Skill

- **目标**：建立可自洽的文档体系，让任意 AI 新会话不依赖历史聊天即可继续开发。
- **范围**：纯文档 + 项目级 skill，**不改业务代码、不改 DB migration**。
- **改动**：
  - 新建：`AI_DEVELOPMENT_INDEX`（路由型）、`DEVELOPMENT_WORKFLOW`、`api/API_INDEX`、`mock/MOCK_REMOTE_CONTRACT`、`development/DEVELOPMENT_LOG`、`decisions/ADR_INDEX`+7 ADR、`acceptance/ACCEPTANCE_LOG`、`templates/` 5 模板、`.claude/skills/qta-context-bootstrap`。
  - 治理：`CLAUDE.md` 删旧必读清单 + Today MVP 指令；`AGENTS.md` 下一阶段优先级重写（删早期建表/指标/策略计划）；`DEVELOPMENT_ROADMAP` 重写（v0.1.1 已完成 + 下一阶段证券主数据，删 Entity/Repository/创建前端）；`FRONTEND_ARCHITECTURE` 按实际 React 项目重写（删 `/api/risk-alerts` 等不存在接口）；`CONVERSATION_HANDOFF` 精简为 Historical；10 个早期文档加 Historical 标记。
  - 契约修正：`MOCK_REMOTE_CONTRACT` 物理 key 带 `qta:` 前缀 + Risk Calculator 前端纯函数（未接 remote adapter）；`API_INDEX` Portfolio 完整路径 `/api/v1/portfolio/positions` 等；`PRODUCT_BLUEPRINT` v0.1.1 "待开发"→"已完成"。
  - 信息真实性优先级写入 `AI_DEVELOPMENT_INDEX §2` + `DEVELOPMENT_WORKFLOW`。
- **测试结果**：后端 `./mvnw test` 121 通过；前端 typecheck/lint/test/build 全绿；`git diff --check` 两仓库干净；grep 主流程无 JPA/Repository 冲突、无旧测试数残留、Controller 路径与 API_INDEX 一致、localStorageClient `qta:` 前缀与 MOCK_REMOTE_CONTRACT 一致。
- **产品决策**：Historical 文档原文保留（不删），仅顶部标记 + 主索引降级；单一事实来源（API_INDEX/MOCK_REMOTE_CONTRACT/ADR/DEVELOPMENT_LOG/ACCEPTANCE_LOG）。
- **关联文档**：`AI_DEVELOPMENT_INDEX.md`、`DEVELOPMENT_WORKFLOW.md`、`acceptance/ACCEPTANCE_LOG.md`。

---

## v0.1.1 — 基础交易闭环优化（含两轮质量收尾 + 最终交付）

**目标**：把计划 / 交易 / 账本 / 快照 / 复盘 / 工作台串成可信、可追溯闭环。

**范围**：6 大功能 + 两轮收尾 + 最终交付（路由 / 解绑 / 历史日期 / FIFO 对齐 / Antd deprecated / 文案 / 文档治理）。

**后端改动**：
- 新增 `PositionSnapshotComparisonManager`（纯计算）、`PositionSnapshotReconciliationManager`（FIFO 对账，复用 `FifoCalculatorManager`）、`DashboardTodoVO` + `DashboardTodoCodeEnum` / `DashboardTodoLevelEnum`、`SnapshotChangeTypeEnum` / `ReconciliationStatusEnum`、6 个对比/对账 VO。
- `TradeJournalManager`：`planId` 关联校验 + `unlinkPlan` 三态；`recalculateReviewStatus`。
- `ReviewManager`：扫全表解析 `linked_journal_ids`（CSV，容忍脏数据 + 去重）；删除保护。
- `DashboardManager`：`buildTodos(date)`（6 类待办，历史日期口径 `trade_date<=date`，STALE 用 `getLatestConfirmedUpTo`）。
- `TradeJournalMapper`：`selectAllOrderedUpTo`（截止时点 FIFO）、`selectByReviewStatusUpTo` / `countByReviewStatusUpTo`（历史日期）。
- `PositionSnapshotMapper`：`selectLatestConfirmedUpTo`。
- 5 个新错误码 + `MessageConstants` 文案。
- **未新增表，未修改 V1-V4 migration。**

**前端改动**：
- `TradeJournalForm` 计划选择器 + 自动带入；`PositionSnapshotInspectionDrawer`（对比 + 对账 + 成本列 + 横向滚动）；`DashboardTodos`（ul/li，无 List）；`dashboardApi`（remote 用后端聚合，mock 同口径）；`settingsApi`（localhost 防误配 + 测试连接）；`positionSnapshotReconciliation`（FIFO 含 totalFee + 稳定排序 + 超卖停止）；`DataManagement`（动态文案 + 导出范围说明）。
- Antd 6.4 deprecated 全清理：`Alert message→title`、`Spin tip→description`、`Space direction→orientation`、`Drawer width→size`。

**接口变化**：
- 新增 `GET /position-snapshots/comparison`、`GET /position-snapshots/{id}/reconciliation`。
- `GET /dashboard/today` 响应增 `todos`（旧字段保留）；待办 `targetPath` 全 `/journal*` 或 `/position-snapshots`（复数）。
- `PUT /trade-journals/{id}` 增 `unlinkPlan`（三态）；响应增 `planDate/planStatus`。
- `reviews` 新增/编辑/删除后回算 reviewStatus；`trade-journals/{id}` 删除前引用保护。

**测试结果**：后端 `./mvnw test` = 121 通过；前端 `typecheck/lint/test/build` = 179 测试通过；Docker 冷构建 + curl 端到端 + Playwright 4 页面控制台 `DEPRECATED_WARNINGS=0, CONSOLE_ERRORS=0`。详见 `../acceptance/ACCEPTANCE_LOG.md`。

**产品决策**：对账只读不改流水；TRADE_AGAINST_PLAN 含 `followedPlan=false`；历史日期统一 `trade_date<=date`；超卖视为 QUANTITY_MISMATCH；mock FIFO 必须复刻后端（含 totalFee）；JSON 导出仅 localStorage 不含 MySQL。

**遗留问题**：浏览器自动化目视仍建议手动复核（Playwright 已验证控制台）；联调测试数据未清理（`TEST01/TEST01C/TEST02/UNLINK1/CMP1/HISTFX1/FUTFX1/OVERFX1` 等）。

**关联文档**：`../features/TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md`、`../api/POSITION_SNAPSHOT_API.md`、`../api/API_INDEX.md`、`../mock/MOCK_REMOTE_CONTRACT.md`、`../BUILD_CHECKLIST.md`、`../acceptance/ACCEPTANCE_LOG.md`。

---

## v0.1.0 — Today MVP + 交易账本 + 持仓快照

**目标**：本地运行的基础交易记录工具。

**范围**：Dashboard / Watchlist / Trade Plan / Risk / Trade Journal / Review + Portfolio FIFO 账本 + Position Snapshot。

**后端**：Spring Boot 3.5 + MyBatis XML + MapStruct + Flyway V1-V4 + MySQL 8.4 + H2 test；分层 controller/service/manager/dao/model/dto/vo/convert；`ApiResponse` + `ErrorCodeEnum` + `BusinessException`。

**前端**：React 19 + Vite + TypeScript + Ant Design 6 + feature-based + mock/remote 双模式 + `shared/api/client` 动态 baseURL。

**接口**：见 `../api/API_INDEX.md`。

**测试**：后端基础测试 + 前端基础测试（数量低于 v0.1.1，已被覆盖）。

**关联文档**：`../API_TODAY_MVP.md`、`../api/PORTFOLIO_API.md`、`../api/POSITION_SNAPSHOT_API.md`、`../DATABASE_DESIGN.md`、`../CURRENT_ARCHITECTURE_AND_MODULES.md`。
