# ADR-0007: 快照对账只读，不自动修改交易流水

- 状态：Accepted
- 日期：2026-07

## 背景
持仓快照（用户盘点）与 FIFO 账本（由交易流水计算）可能不一致。用户希望看到差异，但不希望系统自动改写已录入的交易记录。

## 决策
`GET /position-snapshots/{id}/reconciliation` 只读：以数量为核心一致性判断（MATCHED/QUANTITY_MISMATCH/SNAPSHOT_ONLY/LEDGER_ONLY），超卖视为异常并 warning，成本差异只展示不判错；**不自动补写、修改或删除交易流水**。

## 原因
- 自动改写流水会破坏可追溯性，掩盖数据录入错误。
- 对账目的是定位问题，由用户决定是否修正。

## 影响
对账接口无副作用；前端对账页明确"只读 + 不构成投资建议"；差异需用户回交易记录页手工修正。

## 替代方案
对账后自动补流水/调整数量——明确拒绝。

## 关联
`../features/TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md`、`../api/POSITION_SNAPSHOT_API.md`、`../mock/MOCK_REMOTE_CONTRACT.md`
