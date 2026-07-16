# ZCode Prompt - P1.2/P1.3 Acceptance Fix Round 5

完成 Quant Trading Assistant P1.2/P1.3 最后一轮闭环。直接修改代码、测试和文档，不要只给计划。

## 规则

- 后端 `/Users/joker/code/quant-trading-assistant`；前端 `/Users/joker/code/quant-trading-assistant-web`。
- 先读 `docs/ai/HANDOFF_2026-07-15_p12_acceptance_round5.md`，保留两仓库所有未提交改动，禁止 reset/checkout/clean。
- 自主选择最小实现，不询问用户；不扩展 LongPort分钟线、scheduler、指标、策略、回测或大屏。
- 不 commit、不 push、不部署。

## A. 修正事务边界

- 禁止依赖同一 Service 内 `listTaskItems -> reconcileTask` 自调用触发 `@Transactional`。
- 将收敛写操作抽到职责清晰的独立 Spring Service/Manager，或采用项目中已有且能经过代理的事务边界；不要使用 self 注入、AopContext 或手工获取 Bean。
- 明细查询调用事务组件后再查询最新数据；收敛失败不得吞掉并伪装成功。若查询仍允许降级返回旧数据，响应/UI必须明确告知收敛失败。
- 补测试证明懒收敛入口调用事务组件、更新后返回最新 item、异常策略明确。

## B. 完成用户可见任务闭环

- 在 `/market-workspace` 采集计划区域，为存在 `lastTaskId` 的计划增加“任务明细”入口。
- 打开 Drawer 或独立区域后调用 `listTaskItems(lastTaskId, ...)`，显示 symbol、状态、行数、inserted/updated/skipped、错误信息、开始/结束时间和分页。
- 提供刷新/收敛按钮，remote 模式调用 `reconcileTask(lastTaskId)` 后重新加载明细；展示 loading、成功和错误，防重复点击。mock 模式保持可解释。
- 不做自动高频轮询。
- 补 workbenchApi remote path/body 测试和页面组件测试，证明按钮真的调用 API，而不只是导出未使用函数。

## C. 补齐板块页面行为测试

使用模块 mock + 可控 Promise，真实覆盖以下8项，不得再用标签渲染替代：

1. 首次加载调用 listSegments 并渲染结果。
2. 翻页用新 page 参数重新请求。
3. 打开成员 Drawer 调用 listSegmentMembers 并渲染成员。
4. 创建失败展示错误且按钮恢复。
5. 删除失败展示错误且数据不误删。
6. Alert 重试重新请求。
7. 添加请求 pending 时重复点击只调用一次。
8. 移除请求 pending 时重复确认只调用一次。

测试应在删除对应生产逻辑后失败。可保留已有展示测试，但不能用它们替代上述用例。

## D. 验证与文档

- 更新 API、开发日志、验收日志、AI_HANDOFF、建设看板和 round5 handoff。只记录实际结果。
- 必跑：后端 test/package/diff check；前端 typecheck/lint/test/build/diff check。
- Docker、浏览器、LongPort 不强制，未执行写 SKIPPED。
- 完成后报告测试数量、事务实现、页面操作路径、8项测试名称和两仓库 git status，然后停止等待验收。
