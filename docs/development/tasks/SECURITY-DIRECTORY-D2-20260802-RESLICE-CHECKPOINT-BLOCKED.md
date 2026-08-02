# Checkpoint: SECURITY-DIRECTORY-D2-20260802-RESLICE → BLOCKED

- recordedAt: 2026-08-02T08:00:00Z
- actor: codex-parent-d2-reslice-recovery
- taskId: SECURITY-DIRECTORY-D2-20260802-RESLICE
- previous lifecycleState: CANDIDATE_FROZEN
- new lifecycleState: BLOCKED
- 阻塞点：架构门禁 hard-fail（机器 errors>0 不可豁免），且无法在 D2 安全范围内消除。

## 一、已完成且通过的部分（功能交付就绪，非架构）

全部 5 个 reslice slice + 1 个 REPAIR 全部 SELF_CHECKED 并被父协调者接受（CONTROL.roleRuns 完整）：

- RS-01：domain.ts 类型 + securityDirectoryApi（remote+mock），AC-01/AC-06。
- RS-02：securityDirectoryApi mock + remote 测试（TD-D2-API-01..05），AC-01。
- RS-03：SecuritySelector 共享组件 + 行为测试（TD-D2-COMP-01..10 + NOSIDEEFFECT-02），AC-02。
- RS-04：最新价 + 历史日 K 两页面接入 + 页面测试（TD-D2-PAGE-MD-01/02、NOSIDEEFFECT-01、FALLBACK-01），AC-03/04/05。
- RS-05：采集计划 scope + 板块成员两流程 + syncPlanForm util 测试（TD-D2-PAGE-WS-01、SG-01、NOSIDEEFFECT-03、LEGACY-01/02），AC-03/04/05。
- REPAIR-1：测试文件 lint 合规（localStorage→clearAll、移除 8 个无用 eslint-disable），AC-06。

前端全门禁（候选冻结证据）：

- `npm run typecheck` → EXIT 0。
- `npm run lint` → EXIT 0，0 errors / 0 warnings。
- `npm run test` → EXIT 0，**303/303 pass**（40 files）。
- `npm run build` → EXIT 0。
- 前端 `git diff --check` → 无空白错误。

候选身份（跨仓库）：

- candidate.commit（后端任务分支）= `00820f1e2d3f81e50c055eb29a5ec4d2838fe2b2`，tree=`3adbfaed47a8357eb0a3990f51a2ab0e7d09d6ab`。
- diffArtifact（后端 baseline cd901d5 → candidate 00820f1 全量 diff）= `…-BASELINE-CANDIDATE.patch`，SHA-256 `7a0e7980e59890093199c00e8316adf608f62c493171a0e14a4988d22f8591bf`。
- 前端实现冻结 diff（baseline 80c38324 → 前端工作区，13 文件）= `…-CANDIDATE.patch`，SHA-256 `479d6bf4b9f2fdce39d90fa49bb6cb251bae55202e0b98027fa21c3674164b06`。
- 治理热修复 commit = `92129bb9bfd23aa73fcce378430c8ed213c8fbb3`（chore(ai): enable unattended governed task execution）。

治理门禁：`node scripts/run-ai-governance-gates.mjs` → 38/38 pass。
CONTROL 校验：`node scripts/check-ai-task-control.mjs` → CANDIDATE_FROZEN EXIT 0。

## 二、阻塞根因（架构门禁 hard-fail）

按 TEST-DESIGN §5 与 CONTRACT，REVIEW_CLEAR 前必须在后端根运行架构门禁，`--files` 至少包含 7 个前端生产路径（含 3 个既有大页面）：

```
node scripts/check-ai-architecture.mjs --files \
  <SecuritySelector.tsx> <securityDirectoryApi.ts> <syncPlanForm.ts> \
  <market-data.tsx> <market-workspace.tsx> <market-segments.tsx> <domain.ts> \
  --candidate-identity 00820f1... --json-output <report>
```

结果（`…-ARCH-REPORT.json`，candidateIdentity=00820f1）：**errors=3，warnings=9，EXIT 1**。3 个 ERROR 均为「longest method > 100 lines」，分别来自：

- `src/pages/market-data.tsx`：longest method 115 行。
- `src/pages/market-workspace.tsx`：longest method 210 行。
- `src/pages/market-segments.tsx`：longest method 148 行。

### 这些是既有债务，非 D2 引入

3 个页面文件在 D2 baseline（前端 `80c38324`）就已是大型组件（671/604/669 行）。其「最长方法」是 React 函数组件本身（hooks + JSX 渲染），架构检测器按后端类方法口径计数。D2 仅在每文件加了 3–13 行 SecuritySelector 集成，未新增方法、未显著拉长组件：

- market-data.tsx：baseline 671 行，D2 +少量；longest method 是既有 QuoteSnapshotsTab/SyncTasksTab 渲染。
- market-workspace.tsx：baseline 604 行，D2 +13 行；longest method 是既有 PlansTab 组件。
- market-segments.tsx：baseline 669 行，D2 +3 行；longest method 是既有 MembersDrawer 组件。

### D2 自身架构清洁

仅对 D2 新建/拥有的文件运行同一门禁（`…-ARCH-REPORT-D2OWNED.json`，candidateIdentity=00820f1）：
SecuritySelector.tsx、securityDirectoryApi.ts、syncPlanForm.ts、domain.ts → **errors=0，warnings=1（domain.ts 行数，非阻塞）**，EXIT 0。

## 三、为何不在本轮消除（安全范围判断）

治理规则（GOVERNANCE_V2_POLICY §架构 + orchestration Skill）：

- 机器架构 error 不可由 reviewer 文字豁免；errorCount 必须 = 0 才能 REVIEW_CLEAR。
- 若检测器本身有误，需在「单独的受控任务」修复检测器后重新生成候选绑定报告，不得内联重解释。

将 3 个既有大型 React 组件拆到 longest method ≤100 行，需要把 hooks 与 JSX 渲染切分为多个子组件/自定义 hook，是显著重构：

- 这 3 个页面是多 Tab 页面（行情状态/证券主数据/最新价/日 K/同步/异常提醒；采集计划/任务明细；板块榜单/层级/数据资产/成员）。D2 的页面测试只覆盖 SecuritySelector 接入的 4 个流程，不覆盖其他 Tab/Drawer 的既有行为。
- 在无组件级测试保护下做大规模 JSX 提取，回归风险高，超出 D2（共享 SecuritySelector + 四流程接入）的冻结范围，违反「不扩大到另一产品任务」与「实现者只做冻结 slice」。
- 检测器为后端类方法设计，对前端 React 函数组件产生系统性误报；修检测器属另一受控任务。

因此本轮无法安全达到架构门禁 errors=0；不通过挑选更小文件集来掩盖既有债务（那样虽能机械过校验器，但违背 TEST-DESIGN §5 的强制文件列表与诚实原则）。

## 四、roleRun / executionOutcome 总览

| roleRunId | role | slice | outcome | accepted |
|---|---|---|---|---|
| TD-D2-RESLICE-20260802-01 | TEST_DESIGNER | — | COMPLETED | true |
| IMP-D2-RS01-20260802-01 | IMPLEMENTER | RS-01 | CANCELLED（harness 取消，无 receipt；记录于 RECOVERY checkpoint） | false |
| IMP-D2-RS0102-20260802-01 | IMPLEMENTER | RS-02 | COMPLETED | true |
| IMP-D2-RS01-20260802-02 | IMPLEMENTER | RS-01 verify | COMPLETED | true |
| IMP-D2-RS03-20260802-01 | IMPLEMENTER | RS-03 | CANCELLED（旧 acceptEdits，18 次审批，~600s 取消；无 receipt；记录于 RECOVERY checkpoint） | false |
| IMP-D2-RS03-20260802-02 | IMPLEMENTER | RS-03 | COMPLETED | true |
| IMP-D2-RS04-20260802-01 | IMPLEMENTER | RS-04 | COMPLETED | true |
| IMP-D2-RS05-20260802-01 | IMPLEMENTER | RS-05 | COMPLETED | true |
| IMP-D2-REPAIR1-20260802-01 | IMPLEMENTER | REPAIR-1 | COMPLETED | true |

本轮恢复**未再发生权限审批**：RS-03..05 + REPAIR-1 全部在新 bypassPermissions profile 下无人值守完成。

## 五、未验证维度 / 下一步

- 未达 REVIEW_CLEAR（架构门禁 hard-fail 阻断），故未派发 qta-code-reviewer、qta-final-verifier，未做 delivery-finalization。
- `node scripts/check-ai-delivery-ready.mjs` 未运行（前提未满足）。
- 唯一下一步（需新受控任务/明确授权，不在本 qta-run 范围）：
  1. 方案 A（推荐）：开一个独立的「前端架构债重构」受控任务，把 3 个页面的 React 组件拆分到 longest method ≤100（或为先修检测器使其对前端函数组件不计为 100 行硬阈值），该任务自带组件级测试，完成后再以同一 D2 候选重跑架构门禁。
  2. 方案 B：扩展 D2 契约把 3 个页面重构纳入冻结 slice 并补组件测试（会扩大产品范围，需用户授权）。
- 功能层面 D2 已 100% 就绪（6 AC 的功能行为与静态门禁除架构门禁外全部通过）；阻塞纯架构门禁既有债。
- 是否具备合并 main 条件：**否**。架构门禁未过，未独立验收，未 delivery-ready。
