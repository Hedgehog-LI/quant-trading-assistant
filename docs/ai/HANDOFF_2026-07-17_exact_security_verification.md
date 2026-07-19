# 精确证券代码验证任务交接

## 当前事实

- 上一轮行情采集执行引擎已独立复核通过；冻结基线和残余风险见 `../acceptance/ACCEPTANCE_LOG.md` 顶部条目。
- 本轮产品决策已固化在 `../features/EXACT_SECURITY_VERIFICATION_DESIGN.md`。
- 实现与验收已完成；不要把既有未提交的执行引擎、港美股代码链路或本轮验证能力回滚。

## 本轮唯一目标

在行情采集计划中实现：选择 A/H/US 市场 -> 输入精确代码 -> 后端通过 LongPort Static Info + Quote 验证 -> 页面展示名称/统一代码/价格/时间/延迟 -> 用户确认后加入计划。

## 最终结果

- 新增只读 `POST /api/v1/market-data/securities/verify`，无 migration、无 DB 写入。
- 前端采集计划已接入 `SecurityVerificationField`，支持多标的确认加入和旧 scope 兼容。
- 后端 276 tests + package；前端 35 files / 270 tests + typecheck/lint/build；Docker health 和 A/H/US 三次真实 LongPort curl 通过。
- 浏览器 E2E 未执行；`quoteDelay` 当前为 `UNKNOWN`，页面以 `quoteTime` 为事实。
- P1.4b 本地目录和名称/拼音模糊搜索未开始；港美股分钟采集未解锁。

## 最小读取顺序

1. `AGENTS.md`
2. `docs/AI_DEVELOPMENT_INDEX.md`
3. `docs/AI_HANDOFF.md`
4. `docs/features/EXACT_SECURITY_VERIFICATION_DESIGN.md`
5. `docs/features/LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md`
6. `docs/api/MARKET_DATA_API.md`
7. 前后端仅加载 market-data provider、采集计划表单及对应测试。

## 约束

- 精确代码验证优先，不实施全量证券目录和模糊名称搜索。
- 验证 API 只读，不创建计划、不落报价事实、不写证券主表。
- 复用 `LongPortSymbolMapper` 和现有可选反射 SDK；不引入交易/账户/订单能力。
- 港美股可以验证身份和报价，但现有分钟采集执行仍只支持 A 股；前端不可误导。
- 用户输入变化必须使旧验证失效，只有显式确认才进入计划 scope。

## 已同步

`MARKET_DATA_API.md`、`API_INDEX.md`、`MOCK_REMOTE_CONTRACT.md`、`DEVELOPMENT_LOG.md`、`ACCEPTANCE_LOG.md`、`BUILD_CHECKLIST.md`、`AI_HANDOFF.md` 和本文件均已按实际结果同步。
