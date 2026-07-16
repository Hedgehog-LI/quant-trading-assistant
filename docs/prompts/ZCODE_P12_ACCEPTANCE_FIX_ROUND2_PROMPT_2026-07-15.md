# ZCode Prompt - P1.2/P1.3 Acceptance Fix Round 2

继续执行 Quant Trading Assistant 的 P1.2/P1.3 第二轮收口修复。直接完成代码、测试、最小联调、文档和交接，不要只给计划。

## 自主执行规则

- 后端仓库：`/Users/joker/code/quant-trading-assistant`。
- 前端仓库：`/Users/joker/code/quant-trading-assistant-web`。
- 保留两个仓库全部未提交改动并在其上继续；禁止 reset、checkout、clean、覆盖或丢弃现有成果。
- 遇到多种实现时，自行选择最符合现有架构和数据真实性的方案，不询问用户，不等待确认。
- 不因 LongPort 凭据、SDK 或真实外联条件中断。本轮不要求真实 LongPort 调用。
- 不 commit、不 push、不部署；完成后等待 Codex 验收。
- 只读必要文档，不加载历史聊天、旧提示词全集或全量 docs。

## 渐进式加载

1. 启用 `.claude/skills/qta-context-bootstrap`。
2. 读取入口：`AGENTS.md`、`CLAUDE.md`、`docs/AI_DEVELOPMENT_INDEX.md`、`docs/AI_HANDOFF.md`。
3. 必读当前事实：`docs/ai/HANDOFF_2026-07-15_p12_acceptance_round2.md`。
4. 再按后端/前端/验收路由读取其中列出的 API、DB、Mock 文档和直接相关代码。
5. 先执行两个仓库 `git status --short` 和 `git diff`，确认现有修改后再编辑。

## A. 后端 runPlan 状态和统计真实性

修复 `MarketDataWorkbenchService.runPlan`，满足以下硬性契约：

1. 状态映射：
   - 子任务 `SUCCEEDED`：item 才可标 `SUCCEEDED`。
   - 子任务 `PARTIAL_FAILED`：item 必须表达 `PARTIAL_FAILED`，主任务必须为 `PARTIAL_FAILED`，不得增加“完全成功 symbol”计数。
   - 子任务 `FAILED`：item/main 正确失败。
   - 子任务 `PENDING`/`RUNNING`：item 保留对应非终态；主任务不得写 `SUCCEEDED`、不得写 `finishedAt`。若采用其他明确状态策略，必须能证明不虚报完成且文档/API/UI 能解释。
   - 未知/null 状态按可解释失败处理，保存 error code/message。
2. 计数口径：
   - `market_data_sync_task.total_count/success_count/fail_count/inserted_count/updated_count/skipped_count` 必须全部使用“行情数据行”同一单位，汇总子任务返回值。
   - symbol 总数和每个 symbol 状态由 `market_data_sync_task_item` 表达，不得把 symbol 数与 bar 行数混进同一 count 字段。
   - PARTIAL_FAILED 子任务的 success/fail/inserted/updated/skipped 均按返回值真实累加。
3. 主子任务追踪：
   - task item 必须保存真实 `subTask.id`，从 plan execution task -> item -> daily bar child task 可直接追踪。
   - 若现有字段不足，新增 `V12__...sql`，为 `market_data_sync_task_item` 增加 `sub_task_id`（含必要索引），同步 DO、Mapper XML、VO/API/DB 文档；禁止修改 V1-V11。
4. 事务和留痕：
   - 单个 symbol 异常不能抹掉其他 symbol 已完成结果。
   - 数据库写入异常或未知状态不能遗留无法解释的 RUNNING 空壳。
   - 对业务异常优先保留原业务错误码，不要全部降级成 INTERNAL_ERROR。
5. 测试必须明确断言：
   - SUCCEEDED、PARTIAL_FAILED、FAILED、PENDING、RUNNING、未知/null 状态的 item 和 main 状态。
   - 全部 RUNNING/PENDING 时主任务绝不 SUCCEEDED。
   - 全部 PARTIAL_FAILED 时主任务为 PARTIAL_FAILED。
   - 子任务 ID 被持久化到 item。
   - 所有 count 字段按行数正确汇总，不能混入 symbol 数。
   - 多 symbol、日期透传、去重、非法 JSON/symbol/date 继续覆盖。
   - 删除当前“全 skipped 不应成功但实际应成功”的矛盾注释和弱断言。

## B. 前端 segment mock 和页面闭环

1. `segmentApi.ts`：
   - 使用 `shared/utils/id.ts` 的 `generateId()`，mock segment/member ID 均为 UUID string；禁止时间戳 number 和 `Number(id)`。
   - `segmentId` 保持 `EntityId` 契约；如 domain 类型过窄，按项目统一方式修正，不使用类型断言掩盖问题。
   - addMember 前验证板块存在；不存在时抛与 remote 可理解的一致错误。
   - canonical symbol 去首尾空格、转大写，并复用已有校验规则；重复判断使用规范化后的 symbol。
   - removeMember 先计算 remaining；只有命中时更新，`memberCount=remaining.length`，绝不使用 `members.length - 1`。
   - 删除板块使用 `removeItem(memberKey(id))` 真正级联删除桶。
   - update/remove 不存在板块的行为与 remote 契约保持一致并有测试。
2. `/market-segments` 页面：
   - 创建和删除操作捕获 API 错误，展示可见错误并允许重试；不得产生未处理 Promise。
   - 添加/删除期间提供稳定 loading/disabled 状态，避免重复提交。
   - 删除当前页最后一条后，如页码超出总页数，回到有效页并重新加载。
   - 保留首次加载、分页和成员 Drawer 已完成行为。
3. 前端测试：
   - API 测试增加 UUID string、删除不存在成员不改变计数、空成员不出现负数、孤儿成员拒绝、symbol 规范化、级联 key 真删除。
   - remote 测试补 get/update/listMembers，确保所有 adapter method/path/params/body 有真实断言。
   - 新增页面组件测试，至少覆盖首次加载、分页触发请求、成员 Drawer 加载、创建失败、删除失败、重试；沿用仓库现有 Vitest/Testing Library 模式。

## C. 文档与事实同步

按 `docs/DEVELOPMENT_WORKFLOW.md §2` 执行：

- 若新增 V12：同步 `DATABASE_DESIGN.md`、`CURRENT_ARCHITECTURE_AND_MODULES.md`、API 文档及 migration 清单。
- 更新 `MARKET_DATA_API.md`，明确 plan task/item/child task 关系、状态映射和 count 单位。
- 更新 `MOCK_REMOTE_CONTRACT.md`，确保 UUID、EntityId、symbol 和级联规则与代码一致。
- 建设看板继续保持采集编排/分钟线为 IN_PROGRESS，不得因本轮收口标 DONE。
- 保留历史“通过”与“复验不通过”记录；在 `ACCEPTANCE_LOG.md` 追加新的第三轮真实结果，不覆盖历史。
- 更新 `DEVELOPMENT_LOG.md` 和 `AI_HANDOFF.md` 当前事实。
- 将 `docs/ai/HANDOFF_2026-07-15_p12_acceptance_round2.md` 更新为最终状态、测试数字和 git 状态。

## 禁止扩展

不要开发 LongPort `getMinuteBars`、分钟 K 批量 adapter、盘中 scheduler、异动大屏、指标、策略、回测、多数据源或 AI 图片识别。

## 完整验证

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

如果 Docker 可用，执行不依赖 LongPort 凭据的 health、segments CRUD/member 和 runPlan 非 DAILY/非法参数最小 curl；使用测试前缀并清理数据。浏览器工具可用时验证 `/market-segments` mock/remote 关键路径和控制台。条件不具备则写 SKIPPED，不得阻塞或伪造。

## 完成后汇报

- 按 P1/P2 逐项说明修复证据。
- 给出实际测试数量和所有命令结果。
- 列出 migration/API/Mock/文档变化。
- 列出未执行项与真实原因。
- 输出前后端 `git status --short`。
- 不 commit、不 push，完成后停止等待验收。
