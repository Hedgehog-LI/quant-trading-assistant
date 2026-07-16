# ZCode Prompt - P1.2/P1.3 Acceptance Fix Round 3

继续完成 Quant Trading Assistant 的 P1.2/P1.3 收口。不要只输出计划，直接完成代码、测试、最小验证和文档交接。

## 自主执行

- 后端：`/Users/joker/code/quant-trading-assistant`；前端：`/Users/joker/code/quant-trading-assistant-web`。
- 两仓库均有未提交成果，先检查 git status/diff，原地增量修改；禁止 reset、checkout、clean 或覆盖用户改动。
- 启用 `qta-context-bootstrap`，只读入口文档和 `docs/ai/HANDOFF_2026-07-15_p12_acceptance_round3.md`，不要加载历史聊天或全量 docs。
- 遇到方案分歧自行选择与现有单体、MyBatis XML、React feature-based 架构最一致的最小方案，不询问用户。
- 本轮不依赖 LongPort 凭据，不做真实外联，不扩展分钟线 adapter、scheduler、指标、策略、回测或大屏。
- 不 commit、不 push、不部署；完成后停下等待 Codex 验收。

## 后端必须修复

1. `runPlan` 逐个累加子任务返回的 `totalCount/successCount/failCount/insertedCount/updatedCount/skippedCount`，空值按 0；父任务字段直接使用累加结果，禁止用 insertedCount 代替 successCount，禁止自行反推 failCount。
2. 明确定义并测试 count 不变量。若 provider 返回的数据不满足合理不变量，不得静默篡改统计；应保留真实值并记录可解释错误，或按项目既有错误策略失败。
3. 使用已保存的 `sub_task_id` 实现非终态收敛。建议采用低复杂度的“查询任务详情/列表时懒刷新”或现有调度边界内的轻量 reconciliation：读取 RUNNING/PENDING item 对应子任务最终状态，同步 item、父任务状态、计数和 finishedAt。不得引入 MQ、分布式任务平台或轮询线程风暴。
4. 收敛逻辑必须幂等；部分子任务仍非终态时父任务保持 RUNNING；全部终态后按 SUCCEEDED/PARTIAL_FAILED/FAILED 重新计算并写 finishedAt；错误码和错误摘要可追踪。
5. 补测试：updated/skipped 也属于 success 的样例；显式 failCount；多子任务汇总；PENDING/RUNNING 后变为成功、部分失败、失败；重复刷新不重复累计；无 child task/未知状态的可解释行为。

## 前端必须修复

1. 修复 `segmentApi.test.ts` 的 EntityId 类型错误。测试 UUID 前先用类型收窄；禁止用不安全断言掩盖生产类型。
2. `MarketSegmentMember.segmentId` 继续使用统一 `EntityId`，删除 `id as string` 这类无必要断言。
3. `MembersDrawer` 增加独立 `adding` 与 `removingSymbol`（或等价）状态：添加期间按钮 loading/disabled，移除期间对应行按钮 loading/disabled，并防止并发重复提交；关闭或切换 Drawer 时状态正确复位。
4. 新增真实页面组件测试，至少覆盖首次加载、分页请求、成员 Drawer 加载、创建失败、删除失败、重试、添加防重复、移除防重复。沿用现有 Vitest/Testing Library，不只测试 API adapter。
5. 保留现有 API mock/remote 测试，并确保新增测试参与 typecheck 和 build。

## 文档与验收

- 按 `docs/DEVELOPMENT_WORKFLOW.md` 更新当前事实、开发日志、验收日志、API/DB 契约和建设看板；历史记录保留。
- 把上一轮错误的“页面测试已完成/前端全绿”明确更正为历史误报；只有本轮命令真实通过后才恢复完成状态。
- 不把分钟 K LongPort adapter、盘中 scheduler 标 DONE。

## 必跑门禁

```bash
cd /Users/joker/code/quant-trading-assistant
./mvnw test
./mvnw -DskipTests package
git diff --check

cd /Users/joker/code/quant-trading-assistant-web
npm run typecheck
npm run lint
npm run test -- --run
npm run build
git diff --check
```

Docker和浏览器只在本地条件已就绪且不会阻塞时做最小验证；未执行必须记 SKIPPED，禁止伪造。完成后给出实际测试数、命令结果、改动文件、残留风险和两仓库 git status。
