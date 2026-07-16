# ZCode Prompt - P1.2/P1.3 Acceptance Fix Round 4

继续 Quant Trading Assistant P1.2/P1.3 最终收口。直接完成代码、测试、最小联调和文档，不要只输出计划。

## 执行规则

- 后端 `/Users/joker/code/quant-trading-assistant`；前端 `/Users/joker/code/quant-trading-assistant-web`。
- 先读 `docs/ai/HANDOFF_2026-07-15_p12_acceptance_round4.md`，按 qta-context-bootstrap 渐进加载；不要读取历史聊天或全量 docs。
- 两仓库均有未提交改动，原地增量修改；禁止 reset、checkout、clean、覆盖或丢弃成果。
- 自行选择最符合现有 Spring Boot/MyBatis XML/React 架构的最小实现，不询问用户。
- 不扩展 LongPort 分钟线、scheduler、指标、策略、回测、大屏；不需要凭据或真实外联。
- 不 commit、不 push、不部署，完成后等待 Codex 验收。

## A. reconcile 数据真实性与完整性

1. 收敛统计必须直接使用每个 child `market_data_sync_task` 的 `totalCount/successCount/failCount/insertedCount/updatedCount/skippedCount`，空值按明确契约处理；禁止用 inserted/updated/skipped 推导 success，禁止把 fail 固定为 0。
2. 对 runPlan 时已经终态以及 reconcile 时刚转终态的所有 item，统一使用同一套聚合逻辑。可在收敛时按 `sub_task_id` 查询 child task，或为 item 持久化缺失字段；选择改动最小且数据可追溯的方案。
3. 消除固定 500 条截断。新增按 taskId 查询全部 item 的专用 Mapper SQL，或稳定分页遍历直到 count 全部处理；父任务写终态前必须确认实际处理数量等于数据库 item 总数。
4. 所有 Integer 统计运算必须 null-safe。reconcile 更新多个 item 和父任务应具备合理事务边界，并避免并发调用产生部分更新或重复累计。
5. 子任务缺失、subTaskId 为空、未知状态必须保持可解释且不能虚报完成；明确 errorCode/message。
6. 补精确测试：成功 child 的六个 count；FAILED/PARTIAL_FAILED 的 success/fail；多个 child 混合汇总；501 个 item 不漏项；仍有非终态时保持 RUNNING；重复 reconcile 幂等；null count；child 缺失。

## B. 收敛功能可达

- 不能只留下供 curl 调用的 POST。选择一种最小闭环：任务明细查询时对 RUNNING 父任务执行安全懒收敛，或在行情工作台任务区域提供明确的刷新/收敛按钮并调用 API。
- 用户必须能看到 loading、成功、失败和最新状态；避免自动高频轮询。
- 补对应 API/controller 或前端 adapter/component 测试。

## C. 板块页面测试补齐

现有 3 个测试不能算完成。使用 Vitest/Testing Library 与模块 mock，至少真实覆盖：

1. 首次加载确实调用 listSegments 并渲染结果。
2. 翻页以新 page 参数再次请求。
3. 打开成员 Drawer 调用 listSegmentMembers 并渲染成员。
4. 创建失败显示错误且按钮恢复可用。
5. 删除失败显示错误且页面数据不被误删。
6. Alert 重试重新发起请求。
7. 添加 pending 时重复点击只发起一次请求。
8. 移除 pending 时重复确认只发起一次请求。

不要用“页面标题存在”代替行为断言；测试必须能在删除相应生产逻辑后失败。

## D. 文档与门禁

- 更新 API、数据库/Mapper、开发日志、验收日志、AI_HANDOFF、建设看板和本 handoff；保留历史记录。
- 不得在命令实际通过前写“全绿”或“完成”。Docker/浏览器未执行写 SKIPPED。

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

完成后报告实际测试数、关键计数断言、501-item 证据、用户触发路径、文件变化和两仓库 git status，然后停止。
