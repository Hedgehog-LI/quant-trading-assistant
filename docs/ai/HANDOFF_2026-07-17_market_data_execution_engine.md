# Handoff 2026-07-17 - 行情采集执行引擎

> 当前结论：代码、静态门禁、用户手动重建后的 Docker MySQL curl 联动，以及 A 股真实分钟 K 最小链路均已通过。浏览器验收按用户要求跳过，本轮已收口。

## 已完成

- 后端统一校验采集计划的 task type、trigger、interval、scope 和时间范围。
- `DAILY_BAR_BACKFILL`、`MINUTE_BAR_BACKFILL`、`INTRADAY_MINUTE_REFRESH` 均进入真实执行编排。
- LongPort SDK 4.3.3 分钟 K 支持原生 1M/5M/15M/30M/60M，按 1000 根调用上限分块，并受 60 次/30 秒客户端限流器约束。
- 分钟 K 经过质量校验后幂等入库、更新水位；A 股会话外边界 bar 计入 skipped，不落为 `SUSPECT`。
- A 股盘中 scheduler 使用 DB claim 防重复，支持重启恢复，时段判断通过注入 `Clock` 可测试。
- 前端采集计划使用结构化表单；旧非法计划有纠正提示；执行 pending 防重复；任务摘要与明细可查看。
- mock 只提供计划 CRUD，执行和任务查询明确报错，不制造成功任务。

## 已执行验收

- 后端：270 tests、package 成功。
- 前端：typecheck、lint、34 files / 267 tests、build 成功。
- 先前 Docker fake：成功、幂等重跑、受控部分失败均通过。
- 先前真实 LongPort：`SH.601318 / 2026-07-10 / 5M` 返回 49、落库 48、会话边界 skipped 1，最后一根与水位均为 14:55。

## 最终 curl 证据

1. 宿主机 health `UP`，MySQL healthy，Flyway schema version=13。
2. task 51 首次执行 inserted=4；task 52 复跑 inserted=0/skipped=4，行数和水位不增长，重复 reconcile 幂等。
3. 非法分钟计划、盘中计划手工运行均被明确业务错误拒绝；非交易时段 scheduler 不创建任务。
4. task 48 的 `PARTIAL_FAILED` 和逐标的 `MARKET_DATA_PROVIDER_TIMEOUT` 在重建后仍可查询。
5. 浏览器验收按用户要求停止并跳过，不影响本轮 curl 验收结论。

## 边界

- 不自动下单，不接 LongPort Trade 能力，不保存或输出凭据。
- A 股 scheduler 不直接扩展到港股/美股。
- 未 commit、push；工作区包含用户及本轮未提交改动，继续操作时必须保留。

完整命令、curl 精简证据、改动文件和风险边界见 `../development/MARKET_DATA_EXECUTION_ENGINE_DELIVERY_2026-07-17.md`。
