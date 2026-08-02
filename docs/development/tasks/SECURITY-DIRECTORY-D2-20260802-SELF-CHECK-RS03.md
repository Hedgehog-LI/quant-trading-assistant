# Self-Check: SECURITY-DIRECTORY-D2-20260802-RESLICE RS-03

- roleRunId: IMP-D2-RS03-20260802-02
- dispatchId: dispatch-IMP-D2-RS03-20260802-02
- slice: RS-03 (SecuritySelector 共享组件 + 行为测试；恢复轮接管，非从头实现)
- AC: AC-02
- status: SELF_CHECKED（非 ACCEPTED/VERIFIED）

## 恢复背景

上一轮 RS-03（IMP-D2-RS03-20260802-01）因旧 acceptEdits 权限被 harness CANCELLED，但已写入
SecuritySelector.tsx 与 SecuritySelector.test.tsx。本轮全新 implementer 接管：不重写，聚焦修复
已知失败 + 通过 typecheck。

## 改动（均在两个允许写入路径内）

`src/shared/components/SecuritySelector.test.tsx`：
- TD-D2-COMP-03 强制修复：重试按钮定位由 `screen.getByText((content, element) => element?.tagName === 'BUTTON' ? ...)`
  改为 `screen.getByRole('button', { name: /重\s*试/ })`（无障碍名聚合后代文本，忽略 antd 插入的 CJK 空格）。
  后续 `fireEvent.click(retryButton)` 不变。
- typecheck 修复：第 352 行 `searchSecurities.mock.calls` → `vi.mocked(searchSecurities).mock.calls`（与 340/349 行既有用法一致，纯类型修复，不改 no-side-effect 断言语义）。

`src/shared/components/SecuritySelector.tsx`：
- typecheck 修复：第 68 行 `useState('')` → `useState(value ?? '')`，使 `value` prop 不再未使用（TS6133）。
  对 11 个测试均行为中性（无测试传 value）。

## 自检命令与结果（前端仓库）

- `npm run test -- SecuritySelector.test.tsx` → EXIT 0，11/11 pass（1 file passed）。
- `npm run typecheck` → EXIT 0，无诊断。

注：debounce 测试（TD-D2-COMP-01）存在既有 `act(...)` 警告（仅警告，非失败，本轮改动前已存在，不影响通过）。

## 文件 hash（前端仓库）

- src/shared/components/SecuritySelector.tsx: 7b0cbfca49a5e561aab536624607109d7ccd80a4b0c56574becae0101f7455e7
- src/shared/components/SecuritySelector.test.tsx: d9ebad97f244466ab4f032ddd1337fd5c2f74067da1e0297613ba193b3bd3435

## 未验证维度

聚焦组件测试通过 + typecheck 通过；lint/build/full test 在候选冻结时由最终 verifier 执行。
implementer 诚实声明：除强制定位修复外，另做了 2 处最小且行为保持的 typecheck 修复（均在允许写入路径内），
原因是接管文件按原样无法通过 typecheck；已标记供 reviewer 裁量。
