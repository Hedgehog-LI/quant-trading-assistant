# ZCode Prompt - P1.2/P1.3 Acceptance Fixes

你现在接手 Quant Trading Assistant 的 P1.2/P1.3 收口修复。请直接执行开发、测试、联调、文档同步和交接，不要只输出计划。

## 自主执行要求

- 这是已有上下文的延续任务，不要创建全新架构，不要读取全量 docs。
- 遇到多个合理选项时自行选择风险最低、最符合现有代码模式的方案；不要询问用户确认，不要停下来等待选择。
- 保留前后端当前所有未提交改动，在其基础上修复；禁止 reset、checkout、覆盖或丢弃现有改动。
- 不要因为缺 LongPort App Key、真实 SDK 环境或真实外联条件而中断。本轮不要求真实 LongPort 调用；缺条件就记录 SKIPPED 并继续完成其他工作。
- 不执行 commit、push、远程部署。完成后输出两仓库 `git status --short`，交给用户复核提交。
- 失败后只做一轮直接相关修复和重跑；仍失败则记录精确阻塞，不要递归重试或无限扩展上下文。

## 第一步：渐进式加载

1. 在后端仓库 `/Users/joker/code/quant-trading-assistant` 启用 `.claude/skills/qta-context-bootstrap`。
2. 先读：`AGENTS.md`、`CLAUDE.md`、`docs/AI_DEVELOPMENT_INDEX.md`、`docs/AI_HANDOFF.md`、`docs/ai/HANDOFF_2026-07-14_p12_acceptance_fixes.md`。
3. 再按测试验收 + 前后端开发路由，只读 handoff 指定的必要文档和本轮改动文件。
4. 查看前后端 `git status --short`、`git diff`，先确认现有未提交改动，禁止覆盖。

## 本轮严格范围

只修以下问题：

### A. 前端板块管理可用性

仓库：`/Users/joker/code/quant-trading-assistant-web`

1. 将 `segmentApi` mock 实现改为真正可持久化的本地 CRUD：
   - 沿用项目现有 `localStorageClient` 和 key 规范，不自行造另一套存储。
   - create 后 list/get 能查到；update 保留未修改字段；delete 同时清理成员；分页、keyword/type/enabled 筛选有效。
   - add/list/remove member 有持久化效果；同一板块同一 canonical symbol 不允许静默重复；memberCount 与成员实际数量一致。
   - ID、时间字段和 mock/remote 返回结构与现有 domain 类型一致。
2. 修复 `/market-segments`：
   - 首次加载、分页切换、创建/删除后的当前页刷新均正确。
   - 成员 Drawer 切换板块时先清理旧数据，再加载新数据。
   - 列表和成员加载、创建、删除失败均有清晰 error/retry 状态，不产生未处理 Promise。
   - 异步组件卸载后不 setState；避免复制两套相同加载逻辑。
3. 补真实测试：
   - mock 完整生命周期：create -> list/get -> update -> add member -> memberCount -> remove member -> delete。
   - 分页和筛选测试。
   - remote adapter 必须真正调用 axios client 的对应 method/path/params/body，并断言 unwrap 结果；删除当前只 mock fetch、但不调用 API 的空测试。
   - 为页面至少覆盖首次加载、分页触发请求、成员 Drawer 打开加载、失败重试；沿用仓库现有 React 测试模式。

### B. 后端采集计划执行结果真实性

仓库：`/Users/joker/code/quant-trading-assistant`

1. 修复 `MarketDataWorkbenchService.runPlan`：
   - 使用 `marketQuoteService.createAndExecuteDailyBarSync` 返回的 `MarketDataSyncTaskVO`，不得硬编码 rowCount/insertedCount/success。
   - 正确汇总 total/success/fail/inserted/updated/skipped，正确映射 SUCCEEDED/PARTIAL_FAILED/FAILED/PENDING/RUNNING/幂等复用状态。
   - 不允许把 RUNNING、PARTIAL_FAILED、全 skipped 或复用旧任务表达成“新插入 1 条且成功”。
   - 保证 plan execution task、task item 与实际日 K 子任务的关系可追踪。优先使用现有表/字段和现有服务能力；如确需 DB 字段，必须新增更高版本 Flyway migration，禁止修改 V1-V11。
   - 明确事务边界：任务执行留痕不能因单个 symbol 失败全部回滚；也不能遗留无法解释的永久 RUNNING 空壳任务。
2. scope 解析和校验：
   - 保留 Jackson，使用正常 import，不在字段和循环里堆全限定类名。
   - canonicalSymbol/symbols 去空、去重并按既有规则校验。
   - 校验 startDate <= endDate；非法 JSON、非法日期或空 symbol 返回明确 `BusinessException`，不要悄悄按空 scope 吞掉原因。
   - 修正与代码冲突的 Javadoc。
3. 补后端测试：
   - 成功链路验证 startDate/endDate/adjustType/symbol 真实传给 `MarketQuoteService`。
   - 验证真实 inserted/updated/skipped/total 汇总。
   - 覆盖 SUCCEEDED、PARTIAL_FAILED、FAILED、RUNNING/PENDING 幂等结果。
   - 覆盖多 symbol、重复 symbol、非法 JSON、非法日期范围、空 scope、非 DAILY 类型。
   - 断言 task、task_item、plan lastRunAt/lastTaskId 的最终状态，不只 `assertThrows`。

### C. 文档和建设状态收口

按 `docs/DEVELOPMENT_WORKFLOW.md §2` 同步，至少检查并更新：

- `docs/api/API_INDEX.md`：登记 workbench/sync-plans/task-items/minute-bars/trading-sessions/watermarks/segments 完整 `/api/v1/...` 路径，删除“P1.2 待扩展”旧描述。
- `docs/api/MARKET_DATA_API.md`：与最终 runPlan 语义和响应统计一致。
- `docs/mock/MOCK_REMOTE_CONTRACT.md`：补行情工作台和板块 mock/remote 存储、key、分页、成员级联契约。
- `docs/DATABASE_DESIGN.md`、`docs/CURRENT_ARCHITECTURE_AND_MODULES.md`、`docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`：删除与 V10/V11 实际实现冲突的“待实现/下一阶段”旧事实，并明确分钟 K provider/scheduler 仍未完成。
- `docs/BUILD_CHECKLIST.md`、`docs/PRODUCT_BLUEPRINT.md`、前端建设看板及测试：状态统一，不能把未接 LongPort 分钟 K 和 scheduler 标成 DONE。
- `docs/development/DEVELOPMENT_LOG.md`：追加本轮实质修复摘要。
- `docs/acceptance/ACCEPTANCE_LOG.md`：修复前保留“有条件不通过”历史；修复后另追加真实验收，不覆盖历史。测试数量必须取最终命令实际输出。
- `docs/AI_HANDOFF.md`：只保留修复后的当前事实，不堆历史。
- 更新 `docs/ai/HANDOFF_2026-07-14_p12_acceptance_fixes.md` 的完成状态、最终 git 状态和下一步。

## 明确禁止扩展

本轮不要开发：LongPort `getMinuteBars`、分钟 K 批量 adapter、盘中 scheduler、异动大屏、指标、策略、回测、多数据源、AI 图片识别。它们仍留在下一阶段，不能借本轮修复扩大范围。

## 验证要求

必须执行并记录真实结果：

后端：

```bash
cd /Users/joker/code/quant-trading-assistant
./mvnw test
./mvnw -DskipTests package
git diff --check
```

前端：

```bash
cd /Users/joker/code/quant-trading-assistant-web
npm run typecheck
npm run lint
npm run test -- --run
npm run build
git diff --check
```

如果 Docker 可用且不需要真实 LongPort 凭据，再执行无外联依赖的最小联调：health、workbench overview、板块 CRUD/member CRUD、采集计划非 DAILY 业务错误和 DAILY 参数校验。测试数据使用明显前缀并在结束前清理。若本地浏览器工具可用，验证 `/market-segments` 的 mock 生命周期、remote 首次加载/分页/成员 Drawer，并检查控制台无 error。

## 完成标准

- 板块 mock 模式创建的数据不会立即消失，CRUD/member/pagination 可用。
- remote adapter 测试真实执行并断言，不存在空测试。
- runPlan 不再虚报成功和插入量，状态、计数、日期范围均有测试保护。
- API/Mock/DB/架构/产品/建设看板/交接文档与代码事实一致。
- 所有质量门禁通过；未执行项明确标为 SKIPPED，不能伪造通过。
- 最终汇报：改动文件、关键决策、测试数字、未运行项、剩余工作、前后端 `git status --short`。完成后停止，等待用户验收。
