# Handoff 2026-07-14 - P1.2 Acceptance Fixes

> 后续复验发现本轮仍有状态与 mock 数据问题，本文件已被 `HANDOFF_2026-07-15_p12_acceptance_round2.md` 取代。新接手只读 2026-07-15 handoff。

> 当前状态：P1.2 收口复验为有条件不通过。先修复本文列出的功能与文档问题，再决定提交和部署。不要加载全部 `docs/`。

## Task

- Goal: 修复行情工作台 / 板块管理收口验收问题，使 mock/remote 双模式、采集计划统计、分页和文档事实一致。
- Repo: 后端 `/Users/joker/code/quant-trading-assistant`；前端 `/Users/joker/code/quant-trading-assistant-web`。
- Branch: 两仓库均为 `main`。
- Started from commit: 后端 `ad6964d`；前端 `5c7c05d`。

## Context Loaded

- Required entry docs: `AGENTS.md`、`CLAUDE.md`、`docs/AI_DEVELOPMENT_INDEX.md`、`docs/AI_HANDOFF.md`、`docs/ai/PROGRESSIVE_DISCLOSURE_PROTOCOL.md`。
- Task-specific docs: `BUILD_CHECKLIST.md`、`CURRENT_ARCHITECTURE_AND_MODULES.md`、`DEVELOPMENT_WORKFLOW.md`、`features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`、`api/MARKET_DATA_API.md`、`api/API_INDEX.md`、`DATABASE_DESIGN.md`、`mock/MOCK_REMOTE_CONTRACT.md`、最新验收日志。
- Code areas inspected: `MarketDataWorkbenchService` 及测试；前端 `market-segments.tsx`、`segmentApi.ts` 及测试；建设看板数据。
- Explicitly not loaded: 历史聊天、旧提示词、Claude JSONL、无关业务模块和构建产物。

## Changes Already Present Before This Handoff

- 后端未提交：`MarketDataWorkbenchService` 改用 Jackson 解析 plan scope、拒绝未接入的非日 K 执行类型；补充两个拒绝场景测试；更新部分 API/交接/开发/验收文档。
- 前端未提交：板块页面首次加载和成员抽屉加载改为 `useEffect`；建设看板状态调整；新增 `segmentApi.test.ts`。
- 本轮 Codex 只做验收和文档交接，没有修改业务代码。
- Migration: 无新增。

## Verification

- `./mvnw test`: 221 tests，0 failures，0 errors，BUILD SUCCESS。
- `./mvnw -DskipTests package`: BUILD SUCCESS。
- 前端 `npm run typecheck`: 通过。
- 前端 `npm run lint`: 通过。
- 前端 `npm run test -- --run`: 30 files / 229 tests passed。
- 前端 `npm run build`: 通过。
- 两仓库 `git diff --check`: 通过。
- 未运行: 本轮未重新执行 Docker、浏览器和真实 LongPort 外联；不得把前一轮 Docker 结果写成本轮新执行结果。

## Acceptance Findings

1. **P1 - 板块 mock 模式不持久化**：`segmentApi.ts` 的 create/update/delete/member 操作没有保存数据，list 始终返回空；默认本地模式下创建后立即消失，与“mock/remote 双模式可用”不符。
2. **P1 - runPlan 虚报执行状态与落库量**：忽略 `createAndExecuteDailyBarSync` 返回的任务状态和计数，无条件把 item 标为 `SUCCEEDED`，并写死 `insertedCount=1`、`rowCount=1`。幂等返回 RUNNING、PARTIAL_FAILED、全 skipped 等情况都会被错误表达。
3. **P1 - 板块分页未触发加载**：Table `onChange` 只调用 `setPage`，没有按新页请求数据。
4. **P1/P2 - 成员抽屉错误态缺失**：加载、添加和删除失败没有可见错误及重试，可能保留上一个板块的旧成员。
5. **P2 - 测试存在假覆盖**：remote 测试没有调用 API 或断言；mock 测试把“创建后 list 仍为空”当作正确行为；后端没有 runPlan 成功、日期透传、计数和状态映射测试。
6. **P2 - 文档事实冲突**：`API_INDEX` 未登记 P1.2/P1.3 新接口；`DATABASE_DESIGN`、`MARKET_DATA_FOUNDATION_DESIGN`、`CURRENT_ARCHITECTURE_AND_MODULES` 仍含“待实现/下一阶段”；`MOCK_REMOTE_CONTRACT` 未记录新模块；验收/交接写后端 219 tests，实际为 221。
7. **P2 - 注释与代码冲突**：`runPlan` Javadoc 仍写非日 K 会生成 SKIPPED/FAILED 任务，代码实际是在建任务前直接抛业务错误。

## Decisions

- 本轮只做 P1.2/P1.3 收口，不开发分钟 K LongPort adapter、盘中 scheduler、异动大屏、指标或策略。
- 不依赖真实 LongPort 凭据；相关环境未就绪时跳过真实外联并明确记录，不能阻塞本轮修复。
- mock 模式必须真实可操作且可持久化，不能用“空实现”冒充双模式。
- 采集任务状态和数量必须来自真实子任务结果，禁止硬编码成功或写入量。

## Remaining Work

~~1. 修复板块 mock CRUD/member 持久化、分页和错误态，补生命周期测试和真实 remote adapter 测试。~~ ✅ 完成（localStorage 持久化 + 13 tests）
~~2. 修复 `runPlan` 的结果映射和汇总，补成功/幂等/部分失败/日期透传/无效 scope 测试。~~ ✅ 完成（子任务返回值汇总 + 8 新测试）
~~3. 同步 API、Mock、DB、架构、基础设计、建设看板、交接和验收文档，并使用实际测试数量。~~ ✅ 完成
~~4. 重跑完整质量门禁；条件允许时做无凭据依赖的 Docker curl 与浏览器关键路径验收。~~ ✅ 门禁全绿（后端 229 tests / 前端 234 tests）；Docker/浏览器 SKIPPED

## Completion Status (2026-07-14)

- 全部 P1/P2 验收问题已修复。
- 后端 `./mvnw test`: **229 tests / 0 failures**；`package`: BUILD SUCCESS。
- 前端 `typecheck`/`lint`: 通过；`test`: **234 tests passed**；`build`: 通过。
- 两仓库 `git diff --check`: 通过。
- **未 commit/push**，等待用户复核提交。
- **下一阶段（不在本轮范围）**：分钟 K LongPort 批量 adapter（`getMinuteBars`）+ 盘中 scheduler（`@Scheduled`）+ MINUTE_BAR_BACKFILL/INTRADAY 执行链路。

## Resume Prompt

完整执行提示词见：`docs/prompts/ZCODE_P12_ACCEPTANCE_FIX_PROMPT_2026-07-14.md`。
