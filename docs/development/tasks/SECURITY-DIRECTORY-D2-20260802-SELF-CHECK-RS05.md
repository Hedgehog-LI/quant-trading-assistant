# Self-Check: SECURITY-DIRECTORY-D2-20260802-RESLICE RS-05

- roleRunId: IMP-D2-RS05-20260802-01
- dispatchId: dispatch-IMP-D2-RS05-20260802-01
- slice: RS-05（采集计划 scope + 板块成员两流程接入 + syncPlanForm util 测试；最终 slice）
- AC: AC-03, AC-04, AC-05
- status: SELF_CHECKED（非 ACCEPTED/VERIFIED）

## 改动（均在六个允许写入路径内）

`src/pages/market-workspace.tsx`（修改，+13 行）：
- 引入 SecuritySelector；`PlansTab` 加 export。
- 在 symbols Form.Item 内新增非绑定的「从目录选择标的」选择器，onChange 将选中 canonical symbol 追加到既有 symbols 表单字段（逗号分隔、去重）。
- SecurityVerificationField 与所有提交/校验逻辑不变。

`src/pages/market-segments.tsx`（修改，+3 行）：
- 引入 SecuritySelector；`MembersDrawer` 加 export。
- 在既有手工 symbol Input 上方加 `<SecuritySelector value={symbol} onChange={(s)=>setSymbol(s)} />`（手工 Input 为 AC-05 后备）。handleAdd 不变。

`src/features/market-data/utils/syncPlanForm.ts`：未改动（逻辑已满足两冻结 util 测试）。

`src/features/market-data/utils/syncPlanForm.test.ts`（+88 行）：基线已存在 3 个既有测试，追加 2 个冻结测试（保留既有）：
- `旧 scopeJson {symbols:[...]} 计划可被 planToDraft 解析展示不报错`（TD-D2-LEGACY-01, AC-05）
- `结构化 scope builder 生成正确 scopeJson 且与旧格式读取兼容`（TD-D2-LEGACY-02, AC-05）

`src/pages/market-workspace.test.tsx`（+154 行）：扩展 workbenchApi mock（createSyncPlan/updateSyncPlan/listSyncPlans）+ securityDirectoryApi.searchSecurities mock；新增 2 个冻结测试（保留既有 TaskItemsDrawer 7 个）：
- `采集计划 scope：SecuritySelector 选中后 buildPlanInput 的 scopeJson 含正确 canonical symbol`（TD-D2-PAGE-WS-01, AC-03）
- `采集计划/板块成员选择过程不触发 quote/K 线同步写`（TD-D2-NOSIDEEFFECT-03, AC-04）

`src/pages/market-segments.test.tsx`（+134 行）：新增 searchSecurities mock + MembersDrawer 导入；新增 2 个测试（保留既有 8 个）：
- `板块成员：SecuritySelector 选中后 addSegmentMember 提交的 canonical symbol 与所选一致`（TD-D2-PAGE-SG-01, AC-03）
- `板块成员：仅选择不提交时 addSegmentMember 不被调用`（AC-04 no-side-effect）

## 关键实现决策

- TD-D2-PAGE-WS-01 采用 TaskPacket 明确授权的兜底断言：驱动真实 SecuritySelector（query→debounce→click data-canonical-symbol="SH.603308"），读回 symbols 表单值并经真实 buildPlanInput 校验 scopeJson 含 SH.603308；未用 antd v6 易碎的保存按钮点击。no-side-effect 测试渲染完整 PlansTab 断言无业务写。
- syncPlanForm.ts 不改：其逻辑已满足两冻结 util 测试，改它违反「不改产品语义」。

## 自检命令与结果（前端仓库）

- `npm run test -- syncPlanForm.test.ts market-workspace.test.tsx market-segments.test.tsx` → EXIT 0，24/24 pass（syncPlanForm 5、market-workspace 9、market-segments 10）。
- `npm run typecheck` → EXIT 0。

## 文件 hash（前端仓库）

- src/pages/market-workspace.tsx: 2db9047c151163e8d7a6ff672a7ec8e67b0fa4e630018e496c5a40a8fb21dcab
- src/pages/market-workspace.test.tsx: 23b663928c526a7b315f5a239dba3883821cb57ca6e4c6741c1bcb36d1b82e9d
- src/pages/market-segments.tsx: 9416bc3df8223b729d7cbf4478a82ac519167555d893cddcb6191167296f89a6
- src/pages/market-segments.test.tsx: cbf2e55e01dd13dc6f92fad6c98784e11bd6f016252e883401b5eddc82b6cc31
- src/features/market-data/utils/syncPlanForm.ts: b27fffb3fc8152689d77b6b3b941a479a342aece56b0ade9eaef05486158eb8a
- src/features/market-data/utils/syncPlanForm.test.ts: b909aaa6af05cf69bce8c4253a50a3729c56840e505e656cdbcaa7a590030d59

## 未验证维度

聚焦 slice 测试通过 + typecheck 通过；lint/build/full test 在候选冻结时由最终 verifier 执行。jsdom 之外无 runtime/E2E。
