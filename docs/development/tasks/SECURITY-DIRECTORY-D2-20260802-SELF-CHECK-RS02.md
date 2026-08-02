# Self-Check: SECURITY-DIRECTORY-D2-20260802-RESLICE RS-02

- roleRunId: IMP-D2-RS02-20260802-01
- dispatchId: dispatch-IMP-D2-RS0102-20260802-01
- sessionId: codex-agent-47f30f05-2bc7-4cbd-8ae3-d21dc06b3d94
- slice: RS-02 (securityDirectoryApi mock + remote 测试)
- AC: AC-01
- status: SELF_CHECKED（非 ACCEPTED/VERIFIED）

## 实现

- `src/features/market-data/api/securityDirectoryApi.test.ts`（新建，mock）：冻结标题逐字——
  - `searchSecurities mock 与 remote 同形：相同关键词返回字段一致且排名一致`（TD-D2-API-01）
  - `中文≥1 字符、英文/数字≥2 字符才触发搜索；阈值以下不调用`（TD-D2-API-02）
  - `默认 limit=20 且 markets/types/includeDelisted 筛选正确传递`（TD-D2-API-03）
- `src/features/market-data/api/securityDirectoryApi.remote.test.ts`（新建，remote）：冻结标题逐字——
  - `searchSecurities remote 调用 GET /market-data/securities/search 并解包 items 与目录元数据`（TD-D2-API-04）
  - `getSecurity remote 调用 GET /market-data/securities/{canonicalSymbol} 并在 404 时抛错`（TD-D2-API-05；用合法格式 SH.999999 + mocked success:false 触发 unwrap 抛错）

## 自检命令与结果

- `npm run test -- securityDirectoryApi.test.ts securityDirectoryApi.remote.test.ts` → EXIT 0，5/5 pass。
- `npm run typecheck` → EXIT 0。

## 文件 hash（前端仓库）

- src/features/market-data/api/securityDirectoryApi.test.ts: fd6d1c8fe6c07758824545b8e4c68af7e7063b439c43e01e728ffe65bff8dbb0
- src/features/market-data/api/securityDirectoryApi.remote.test.ts: ab4aa4e7ccd9b1fe1377048148d3abe9b9e2b005c64e355fac0e964da3ab4080

## 未验证维度

聚焦 API 测试通过；前端 lint/build/full test 在候选冻结时由最终 verifier 执行。
