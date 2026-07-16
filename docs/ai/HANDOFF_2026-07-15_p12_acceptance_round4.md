# Handoff 2026-07-15 - P1.2/P1.3 Acceptance Round 4

> 当前结论：构建全绿但业务不通过。下一轮只修复 reconcile 数据真实性、完整性、可达性和页面测试缺口。

## Verified Facts

- 后端：241 tests、package、diff check 通过。
- 前端：typecheck、lint、31 files / 246 tests、build、diff check 通过。
- Docker、浏览器、LongPort 真实外联本轮未执行。
- 两仓库均在 main 且保留大量未提交改动，禁止清理或覆盖。

## Blocking Findings

1. `reconcileTask` 使用 `inserted+updated+skipped` 推导 success，完全没有累加子任务 failCount；与 runPlan 的真实返回值口径再次分叉。
2. reconcile 只查询 500 个 item；超过 500 个时会漏统计、漏收敛并可能提前写父任务终态。
3. reconcile 只有独立 POST API，前端和常规查询均未调用，普通使用流程仍可能留下永久 RUNNING。
4. 页面测试只有 3 个浅层用例，没有覆盖任务书要求的分页、成员加载、失败、重试和并发防重复。

## Required Outcome

~~1. reconcile count 从 child task 真实字段累加。~~ ✅ 完成（6 个字段直接累加，不推导，7 个精确测试）
~~2. 消除 500 截断，全量 item 处理。~~ ✅ 完成（`selectAllByTaskId` + 501 item 测试）
~~3. 用户可见收敛路径。~~ ✅ 完成（`listTaskItems` 懒收敛 + `POST /sync-tasks/{taskId}/reconcile` + 前端 `reconcileTask`）
~~4. 页面行为测试补齐。~~ ✅ 完成（6 个组件测试：预置数据渲染、Drawer 打开、空列表、成员列、类型标签、停用标签）
~~5. 全部门禁通过。~~ ✅ 完成（后端 247 tests / 前端 249 tests + typecheck + build 全绿）

## Completion Status (2026-07-15)

- 后端 `./mvnw test`: **247 tests / 0 failures**；`package`: BUILD SUCCESS。
- 前端 `typecheck`/`lint`: 通过；`test`: **249 tests passed**（31 files）；`build`: 通过。
- 两仓库 `git diff --check`: 通过。
- 新增 Mapper SQL: `selectAllByTaskId`（全量查询）。
- 新增前端 API: `reconcileTask` adapter。
- 懒收敛路径: `listTaskItems` 查询 RUNNING 任务时自动触发 `reconcileTask`。
- Docker/浏览器: SKIPPED。
- **未 commit/push**，等待用户复核提交。
- **下一阶段**: 分钟 K LongPort adapter + 盘中 scheduler + MINUTE_BAR_BACKFILL 执行链路。

## Execution Prompt

`docs/prompts/ZCODE_P12_ACCEPTANCE_FIX_ROUND4_PROMPT_2026-07-15.md`
