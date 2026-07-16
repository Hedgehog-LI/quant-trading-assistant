# ZCode Prompt - P1.2/P1.3 Acceptance Fix Round 6

你现在负责 Quant Trading Assistant P1.2/P1.3 的最终收口。直接完成代码、测试、验证和文档闭环，不要只输出计划，不要询问用户；遇到多种合理方案自行选择影响范围最小且可验证的一种。

## 上下文与边界

- 后端及文档：`/Users/joker/code/quant-trading-assistant`
- 前端：`/Users/joker/code/quant-trading-assistant-web`
- 先读 `AGENTS.md`、`docs/AI_HANDOFF.md`、`docs/ai/HANDOFF_2026-07-16_p12_acceptance_round6.md`，随后只读任务直接相关文件。
- 保留两仓库全部未提交修改；禁止 reset、checkout、clean，禁止覆盖他人改动。
- 本轮只修验收缺口。禁止开发分钟 K LongPort adapter、scheduler、指标、策略、回测、大屏；禁止 commit、push、部署和真实外联。

## A. 修复任务明细 Drawer

1. 删除双 effect 重复加载，形成单一、可推理的数据加载流程。
2. 打开 Drawer 或切换 `lastTaskId` 时将分页重置为 1，并确保只发一次首页请求。
3. 翻页只发一次对应 page 请求；使用 request id、取消标志或等价机制，保证旧 task/旧 page 的迟到响应不能覆盖当前数据和 loading/error。
4. 保留重试与刷新/收敛；pending 时重复点击 `reconcileTask` 只能调用一次，成功后重新加载当前有效页，失败展示可见错误。
5. 表格补齐 `startedAt`、`finishedAt`，使用项目已有日期格式化工具，宽度/横向滚动应保证内容不挤压。

为 Drawer 增加组件行为测试，至少覆盖：首次打开只请求一次；切换 task 重置 page=1；翻页只请求一次；旧响应不覆盖新 task；reconcile pending 防重复；成功后刷新；失败错误与重试。允许为可测性把 Drawer 提取成同 feature 的独立组件，不做无关重构。

## B. 把三条伪行为测试改成真实测试

修改 `src/pages/market-segments.test.tsx`，不要只改测试标题：

- 创建失败：填写必填字段并点击真正的创建按钮，断言 `createSegment` 调用一次；reject 后页面出现错误且按钮恢复可操作。
- 删除失败：点击删除并确认 Popconfirm，断言 `deleteSegment` 调用一次；reject 后错误可见且原数据仍在。
- 移除 pending：点击移除并确认，保持 Promise pending，再次尝试不得产生第二次 `removeSegmentMember`；断言 loading/禁用状态；最后在 `act` 中 resolve 并完成清理。
- 同步修正添加 pending 测试的 Promise 清理，避免测试结束后悬空更新。
- 测试必须能在删除对应生产 catch/防重复逻辑后失败；不得以“按钮存在”“表单存在”替代行为断言。

## C. 验证与文档闭环

- 后端执行 `./mvnw test`、`./mvnw -DskipTests package`、`git diff --check`。
- 前端执行 typecheck、lint、test、build、`git diff --check`。
- 精确报告测试数量；不得把未执行的 Docker/浏览器/LongPort 写成通过。
- 更新 `docs/AI_HANDOFF.md`、`docs/development/DEVELOPMENT_LOG.md`、`docs/acceptance/ACCEPTANCE_LOG.md` 和本 round6 handoff；历史记录保留，不篡改成当时已通过。
- 复查测试源码，逐条列出本轮新增/修正的真实交互和 API 断言。最后输出两仓库 `git status --short` 并停止，等待 Codex 验收。
