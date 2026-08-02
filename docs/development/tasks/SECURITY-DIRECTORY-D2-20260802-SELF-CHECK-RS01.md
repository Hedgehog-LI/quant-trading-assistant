# Self-Check: SECURITY-DIRECTORY-D2-20260802-RESLICE RS-01

- roleRunId: IMP-D2-RS01-20260802-02
- dispatchId: dispatch-IMP-D2-RS0102-20260802-01
- sessionId: codex-agent-47f30f05-2bc7-4cbd-8ae3-d21dc06b3d94
- slice: RS-01 (domain.ts 类型扩展 + securityDirectoryApi.ts remote+mock)
- AC: AC-01, AC-06 (AC-06 静态门禁在候选冻结时跨候选验证)
- status: SELF_CHECKED（非 ACCEPTED/VERIFIED）

## 实现

- `src/shared/types/domain.ts` 末尾追加：`SecurityMarket`、`SecurityType`、`ListStatus`、`MatchedBy` 联合字符串字面量类型；`SecuritySummary`、`Security`、`SecurityDetail=Security` 接口（不改既有导出）。
- `src/features/market-data/api/securityDirectoryApi.ts`（新建）：镜像 segmentApi 范式（mockApi + remoteApi + pick + 具名导出 + 内联 Input/Result 类型）。
  - 导出 `searchSecurities(params: SecuritySearchInput): Promise<SecuritySearchResult>`、`getSecurity(canonicalSymbol: string): Promise<Security>`。
  - mock：确定性 seed 目录（含同名跨市场、退市、改名别名、ETF/INDEX、>20 填充样本）；`meetsThreshold`（CJK≥1 或全拉丁/数字≥2）；markets/types/includeDelisted 筛选；ranking tiers（canonical/raw/name/alias/pinyin/contains，分值 100..50，score<50 剔除）；默认 limit=20、上限 100；返回 catalogStatus/catalogUpdatedAt/stale/degraded 元数据。
  - remote：`unwrap<SecuritySearchResult>(client.get('/market-data/securities/search', {params}))`、`unwrap<Security>(client.get('/market-data/securities/{canonicalSymbol}'))`；不触发任何业务写。

## 自检命令与结果

- `npm run typecheck` → EXIT 0。
- `npm run test -- securityDirectoryApi.test.ts securityDirectoryApi.remote.test.ts` → 5/5 pass（与 RS-02 同次验证）。

## 文件 hash（本 slice 产出，前端仓库）

- src/features/market-data/api/securityDirectoryApi.ts: e5f9d69949cb8e40f280b0213f2ea93d00960f9abeab2b0ea542a83fcca2c4a2
- src/shared/types/domain.ts: （RS-01 追加段，整体 hash 待候选冻结时记录）

## 未验证维度

typecheck 与聚焦 API 测试通过；前端 lint/build/full test 在候选冻结时由最终 verifier 执行。AC-06 静态门禁跨候选验证。
