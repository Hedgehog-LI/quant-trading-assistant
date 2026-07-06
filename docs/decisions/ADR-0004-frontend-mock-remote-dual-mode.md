# ADR-0004: 前端 mock/remote 双模式

- 状态：Accepted
- 日期：2026-06

## 背景
前端需要支持本地开发（无后端）、离线演示，以及正式部署（数据落 MySQL）。两套数据不应互相污染。

## 决策
前端每个 feature 的 api 层提供 mock（localStorage）与 remote（REST）两套实现，按 `settings.apiMode` 在运行时分流；mock 计算口径必须复刻后端。

## 原因
- 本地开发/离线可用，降低后端依赖。
- 正式部署切 remote，核心数据落库。
- 双模式口径一致保证切换无歧义。

## 影响
`shared/api/client.ts` 动态 baseURL；mock ID=UUID string，remote ID=DB Long，`EntityId=string|number`；详细契约见 `../mock/MOCK_REMOTE_CONTRACT.md`。

## 替代方案
仅 remote（本地开发成本高）/ 仅 mock（数据不落库）。

## 关联
`../mock/MOCK_REMOTE_CONTRACT.md`、`../../docs/FRONTEND_ARCHITECTURE.md`
