# 证券目录与智能检索技术接手

## 任务状态

- 产品设计与 ADR：已完成。
- D0 精确代码在线验证：已完成并验收，详见 `HANDOFF_2026-07-17_exact_security_verification.md`。
- D1-D4 本地目录、模糊搜索、目录同步与跨模块推广：未开始。
- 技术接手入口：`docs/development/SECURITY_DIRECTORY_SEARCH_IMPLEMENTATION_PLAN.md`。

## 新会话最小读取顺序

1. `AGENTS.md`
2. `docs/AI_DEVELOPMENT_INDEX.md`
3. `docs/AI_HANDOFF.md`
4. `docs/features/SECURITY_DIRECTORY_SEARCH_DESIGN.md`
5. `docs/decisions/ADR-0009-local-first-security-directory.md`
6. `docs/development/SECURITY_DIRECTORY_SEARCH_IMPLEMENTATION_PLAN.md`
7. 仅按当前阶段读取对应 API/数据库/前端架构文档。

## 建议首轮任务

只执行 D1：migration、目录导入、搜索排名 API、后端测试和接口文档。D1 验收前不并行改造所有前端表单，不接外部 provider，不下载全市场行情。

## 不可破坏边界

- 扩展现有 `stock_basic`，不新建平行证券主表。
- 搜索本地优先；LongPort Static Info 仅作已知代码后的 metadata enricher。
- 全量维护的是证券元数据，不是全市场价格历史。
- 不自动交易、不接账户/订单、不提交密钥。
- 当前工作区可能有港美股支持的未提交改动，必须兼容，禁止回滚。

## 技术团队启动指令

```text
启用 qta-context-bootstrap，严格按 docs/ai/HANDOFF_2026-07-17_security_directory_search.md 的最小顺序渐进加载上下文。先输出本轮 Task Context Manifest，再只执行 docs/development/SECURITY_DIRECTORY_SEARCH_IMPLEMENTATION_PLAN.md 的 D1。遵守现有 Spring Boot + MyBatis XML + MapStruct + Flyway 架构，不建立平行证券主数据，不触碰交易/账户/订单能力。完成代码、测试、API/DB/架构/开发日志/验收日志/BUILD_CHECKLIST/AI_HANDOFF 同步；所有测试通过后给出真实结果和剩余边界。遇到非安全性实现分歧自行按 ADR 和现有代码模式决策，不等待用户逐项审批；遇到凭据、许可证或真实外联阻塞时降级为本地目录验收，不伪造结果。
```
