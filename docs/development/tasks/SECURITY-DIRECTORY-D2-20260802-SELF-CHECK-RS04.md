# Self-Check: SECURITY-DIRECTORY-D2-20260802-RESLICE RS-04

- roleRunId: IMP-D2-RS04-20260802-01
- dispatchId: dispatch-IMP-D2-RS04-20260802-01
- slice: RS-04（最新价 + 历史日 K 两页面接入 + 页面测试）
- AC: AC-03, AC-04, AC-05
- status: SELF_CHECKED（非 ACCEPTED/VERIFIED）

## 改动（均在两个允许写入路径内）

`src/pages/market-data.tsx`（修改）：
- 引入 `SecuritySelector`。
- `QuoteSnapshotsTab`、`SyncTasksTab` 加 `export`（便于隔离渲染测试；其余 tab 不导出）。
- 两 tab 各在既有手工输入旁加 `<SecuritySelector value={...} onChange={(symbol)=>set...(symbol)} />`，
  受控字段喂入同一 state（fetchSymbols / syncSymbol）。手工输入保留为 AC-05 后备。
- 提交处理、校验及其余行为不变；selector 路径不调用任何 quote/sync/采集写接口。

`src/pages/market-data.test.tsx`（新建，214 行，恰好 4 个冻结测试）：
- `最新价查询：SecuritySelector 选中后提交的 canonical symbol 与所选一致`（TD-D2-PAGE-MD-01, AC-03）
- `历史日 K 同步：SecuritySelector 选中后提交的 canonical symbol 与所选一致`（TD-D2-PAGE-MD-02, AC-03）
- `最新价/日 K 选择证券过程不调用 quote/sync/采集任务创建等写接口`（TD-D2-NOSIDEEFFECT-01, AC-04）
- `手工输入 canonical symbol 后备路径仍可提交`（TD-D2-FALLBACK-01, AC-05）
- mock 惯例沿用 market-workspace.test.tsx：vi.hoisted + importActual spread，覆盖 fetchLatestQuotes/createDailyBarSync/getQuoteSnapshots/getSyncTasks/getProviderStatus/searchSecurities；spyOn antd message；beforeEach clearAll + saveSettings(mock)。

## 自检命令与结果（前端仓库）

- `npm run test -- market-data.test.tsx` → EXIT 0，4/4 pass。
- `npm run typecheck` → EXIT 0。

## 关键实现决策（test-only）

Popconfirm 确认按钮选择：fake timers 会阻断 antd v6 Popconfirm portal motion 导致弹层不渲染；
按 role/text（确定 vs OK）受 locale 影响不稳定。最终用 real timers + `waitFor(() => document.querySelector('.ant-popover .ant-btn-primary'))`，
对 debounce 与 locale 均鲁棒。仅测试选择细节，生产行为不变；断言仍校验传入 fetchLatestQuotes/createDailyBarSync 的 canonical symbol。

## 文件 hash（前端仓库）

- src/pages/market-data.tsx: c54ca3c855dd72701593d115da16023686d82e8f348ad1aa7aa2801921bcf8be
- src/pages/market-data.test.tsx: d7f4ed31cf515d171c699568b75e0df3bf018ae98de895f0bb73bb302934208f

## 未验证维度

聚焦页面测试通过 + typecheck 通过；lint/build/full test 在候选冻结时由最终 verifier 执行。
jsdom 之外无 runtime/E2E 验证。
