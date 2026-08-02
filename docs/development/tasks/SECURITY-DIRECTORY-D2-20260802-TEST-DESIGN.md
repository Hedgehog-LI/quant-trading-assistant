# Test Design Artifact: SECURITY-DIRECTORY-D2-20260802

> 由 qta-test-designer (roleRunId `TD-D2-20260802-01`, dispatchId `dispatch-TD-D2-20260802-01`) 在干净只读上下文中产出。父协调者持久化。本文件 SHA-256 与 CONTROL `contract.testInventory` 一起作为冻结 test inventory 的机器回执锚。

- roleRunId: TD-D2-20260802-01
- dispatchId: dispatch-TD-D2-20260802-01
- sessionId: codex-agent-429ac282-04d8-4a17-add8-d8fec7d63459
- startedAt: 2026-08-02T01:50:00Z
- finishedAt: 2026-08-02T01:53:00Z
- runtimeReceiptPath: .git/qta-governance/sessions/<parent-filled>.json
- dispatchReceiptPath: .git/qta-governance/dispatches/<sha256(taskId)>/<sha256(dispatchId)>.json
- waitCalls: 0
- compactionCount: 0
- enforcement: ADVISORY
- compensatingIsolation: read-only test-designer; no edits, no Git, no shell commands, no sub-agents; parent persists the returned artifact.
- verdict: READY_FOR_IMPLEMENTATION

## 1. CONTRACT_DEFECTS (ordered by risk)

| # | Risk | Defect | Resolution |
|---|---|---|---|
| D1 | HIGH | Draft has 10 ACs; L2 cap is 8. | Merge draft AC-02/03/04/05 into one composite AC-02 (SecuritySelector behavior+states, multiple evidence points); DROP draft AC-09 (redundant). Final AC count = 6. |
| D2 | MEDIUM | Draft AC-09 ("tests cover race/edit-fail/retry/submit…") merely restates "the required tests pass." | Drop AC-09. Coverage preserved by frozen TEST_INVENTORY + frozen-inventory receipt under AC-06. |
| D3 | LOW | Draft AC-10 / Verification Plan lists `npm run test` under STATIC, but vitest is AUTOMATION. | AC-06 (STATIC) covers ONLY: typecheck, lint, build, git diff --check, architecture gate, frozen-inventory receipt. vitest (`npm run test`) is AUTOMATION evidence per-testId. |

No further defects.

## 2. FINAL_AC_TABLE (≤8 rows; final, replaces draft AC-01..AC-10)

| AC-ID | Observable behavior | requiredEvidence | Dimension |
|---|---|---|---|
| AC-01 | `securityDirectoryApi` exposes `searchSecurities`/`getSecurity`; mock and remote return same-shape results (identical fields + identical deterministic ranking); Chinese ≥1 char and latin/digit ≥2 chars trigger search, below threshold does not call; default limit=20 (max 100); `markets`/`types`/`includeDelisted`/`limit` filters propagate correctly; catalog metadata (`catalogStatus`/`catalogUpdatedAt`/`stale`/`degraded`) is preserved end-to-end. | AUTOMATION (mock + remote vitest) | AUTOMATION |
| AC-02 | `SecuritySelector` (controlled `value: string` / `onChange: (string)=>void`, mirrors `SecurityVerificationField`) exhibits all of: (a) 250ms debounce; (b) stale/expired response protection; (c) loading / empty / error / retry states; (d) keyboard ArrowDown/ArrowUp navigation, Enter confirms, Esc closes; (e) after selection displays name + canonical symbol + market/exchange + security type; (f) editing text after selection immediately invalidates the prior selection; (g) same-name cross-market securities shown side-by-side and NOT auto-selected; (h) delisted hidden by default, visible+labeled when explicitly filtered; (i) three distinct empty states — catalog not initialized / normal no-match / request failed; (j) stale-catalog warning is informational only and does NOT block local results. | AUTOMATION (component vitest, multiple cases) | AUTOMATION |
| AC-03 | The four flows (最新价 QuoteSnapshotsTab, 历史日 K SyncTasksTab, 采集计划 scope PlansTab, 板块成员 MembersDrawer) submit the canonical symbol exactly matching the user's SecuritySelector selection in their respective payload/scopeJson/add-member call. | AUTOMATION (page vitest) | AUTOMATION |
| AC-04 | During search and selection in every flow, no business write request fires — specifically no quote snapshot fetch, no K-line/daily-bar sync, no sync-task creation, no collection-plan creation. Mock adapters are asserted-not-called. | AUTOMATION (component + page vitest) | AUTOMATION |
| AC-05 | Manual canonical-symbol entry fallback still submits in each flow; existing 采集计划 with legacy `scopeJson` (`{symbols:[...]}` or `{canonicalSymbol}`) load and render without error via `planToDraft`; the new structured scope builder emits valid `scopeJson` and remains read-compatible with old plans. | AUTOMATION (page + util vitest) | AUTOMATION |
| AC-06 | Frontend static gates all exit 0: `npm run typecheck`, `npm run lint`, `npm run build`, `git diff --check` (frontend root); architecture gate `node scripts/check-ai-architecture.mjs --files <absolute frontend production paths> --candidate-identity <id> --json-output <report>` (run at BACKEND root, analyzing frontend files) reports errors=0 bound to candidate identity with a structured disposition per WARN; the frozen test inventory machine receipt (this artifact, hash-bound in CONTROL) is present and every required testId maps 1:1 to a PASS receipt carrying its exact frozen selector. | STATIC | STATIC |

## 3. FINAL_SLICES (confirmed; each ≤3 ACs, ≤8 files, ≤500 prod lines)

| Slice ID | acIds | allowedWritePaths (frontend repo) | maxFiles | maxProdLineDelta | notes |
|---|---|---|---:|---:|---|
| SLICE-01 | [AC-01] | `src/features/market-data/api/securityDirectoryApi.ts`(+`.test.ts`,+`.remote.test.ts`), `src/shared/types/domain.ts` | 4 | 400 | Adds `Security`/`SecuritySummary`/`SecurityType`/`ListStatus`/unified market types + search response/metadata types to `domain.ts`. Mock seeds catalog in localStorage key `securityDirectoryCatalog`. |
| SLICE-02 | [AC-02] | `src/shared/components/SecuritySelector.tsx`(+`.test.tsx`); optional helper under `src/shared/components/` | 3 | 500 | Controlled-field contract mirrors `SecurityVerificationField`. First autocomplete component; fake timers for debounce in tests. |
| SLICE-03 | [AC-03, AC-04, AC-05] | `src/pages/market-data.tsx`(+`.test.tsx`) | 2 | 350 | `market-data.test.tsx` does NOT exist today — must be CREATED. Covers QuoteSnapshotsTab + SyncTasksTab flows. |
| SLICE-04 | [AC-03, AC-04, AC-05] | `src/pages/market-workspace.tsx`(+`.test.tsx`), `src/pages/market-segments.tsx`(+`.test.tsx`), `src/features/market-data/utils/syncPlanForm.ts`(+`.test.ts`) | 6 | 500 | `market-workspace.test.tsx` and `market-segments.test.tsx` EXIST — extend. `syncPlanForm.test.ts` to be created alongside util. |

## 4. CROSS_REPO_CANDIDATE_DECISION

**CONFIRMED** (recommended alternative in contract §跨仓库门禁运行约束; consistent with finalized D3 CONTROL).

- All governance gates run with `cwd = BACKEND root` `/Users/joker/code/quant-trading-assistant`.
- `candidate.mode = COMMIT`; `candidate.commit = <BACKEND task-branch commit>` containing (a) the frozen frontend diff patch artifact at `docs/development/tasks/SECURITY-DIRECTORY-D2-20260802-CANDIDATE.patch`, (b) the contract + CONTROL + role artifacts.
- `control.git.branch = codex/security-directory-d2-20260802` (BACKEND branch).
- Frontend implementation lives on the frontend task branch; its frozen diff `git -C /Users/joker/code/quant-trading-assistant-web diff --binary 80c38324f58ba58cf6f96884184e16c86b967f96 <frontend HEAD>` is stored as the backend patch artifact; its SHA-256 = `candidate.patchSha256` = `candidate.diffArtifactSha256`.
- Architecture gate: `node scripts/check-ai-architecture.mjs --files <ABSOLUTE frontend production file paths> --candidate-identity <backend-commit-id> --json-output <report>` — analyzes frontend source directly via `--files`; runs at backend cwd where `scripts/` and `.agents/schemas/` live.
- Honesty requirement (verbatim in verification artifact): "candidate identity = backend commit `<id>`; frontend implementation is evidenced by the frozen diff patch artifact `…CANDIDATE.patch` (SHA-256 = `patchSha256`); the architecture gate analyzed the frontend production files directly via `--files`."
- No frontend repo pollution (no `.agents/`/`scripts/` mirror seeded into frontend).

## 5. TEST_INVENTORY (frozen; selectors enforced verbatim by verifier receipts)

sourcePath is relative to the FRONTEND repo root `/Users/joker/code/quant-trading-assistant-web/`.

| testId | acIds | kind | required | sourcePath | selector (exact observable string) |
|---|---|---|---|---|---|
| TD-D2-API-01 | AC-01 | AUTOMATION | true | `src/features/market-data/api/securityDirectoryApi.test.ts` | `searchSecurities mock 与 remote 同形：相同关键词返回字段一致且排名一致` |
| TD-D2-API-02 | AC-01 | AUTOMATION | true | `src/features/market-data/api/securityDirectoryApi.test.ts` | `中文≥1 字符、英文/数字≥2 字符才触发搜索；阈值以下不调用` |
| TD-D2-API-03 | AC-01 | AUTOMATION | true | `src/features/market-data/api/securityDirectoryApi.test.ts` | `默认 limit=20 且 markets/types/includeDelisted 筛选正确传递` |
| TD-D2-API-04 | AC-01 | AUTOMATION | true | `src/features/market-data/api/securityDirectoryApi.remote.test.ts` | `searchSecurities remote 调用 GET /market-data/securities/search 并解包 items 与目录元数据` |
| TD-D2-API-05 | AC-01 | AUTOMATION | true | `src/features/market-data/api/securityDirectoryApi.remote.test.ts` | `getSecurity remote 调用 GET /market-data/securities/{canonicalSymbol} 并在 404 时抛错` |
| TD-D2-COMP-01 | AC-02 | AUTOMATION | true | `src/shared/components/SecuritySelector.test.tsx` | `250ms debounce：阈值内连续输入只触发一次搜索` |
| TD-D2-COMP-02 | AC-02 | AUTOMATION | true | `src/shared/components/SecuritySelector.test.tsx` | `过期响应不覆盖新关键词结果（竞态保护）` |
| TD-D2-COMP-03 | AC-02 | AUTOMATION | true | `src/shared/components/SecuritySelector.test.tsx` | `loading / 空结果 / 失败 / 重试 四态正确` |
| TD-D2-COMP-04 | AC-02 | AUTOMATION | true | `src/shared/components/SecuritySelector.test.tsx` | `键盘 ArrowDown/ArrowUp 导航、Enter 确认、Esc 关闭` |
| TD-D2-COMP-05 | AC-02 | AUTOMATION | true | `src/shared/components/SecuritySelector.test.tsx` | `选中后展示名称/canonical symbol/市场交易所/证券类型` |
| TD-D2-COMP-06 | AC-02 | AUTOMATION | true | `src/shared/components/SecuritySelector.test.tsx` | `再次编辑文本立即失效旧选择（清除已选）` |
| TD-D2-COMP-07 | AC-02 | AUTOMATION | true | `src/shared/components/SecuritySelector.test.tsx` | `同名跨市场证券并列展示且不自动选择` |
| TD-D2-COMP-08 | AC-02 | AUTOMATION | true | `src/shared/components/SecuritySelector.test.tsx` | `退市证券默认隐藏；显式筛选后可见并标注状态` |
| TD-D2-COMP-09 | AC-02 | AUTOMATION | true | `src/shared/components/SecuritySelector.test.tsx` | `目录未初始化 / 正常无匹配 / 请求失败 三态可区分` |
| TD-D2-COMP-10 | AC-02 | AUTOMATION | true | `src/shared/components/SecuritySelector.test.tsx` | `目录陈旧只提示不阻断本地结果展示` |
| TD-D2-PAGE-MD-01 | AC-03 | AUTOMATION | true | `src/pages/market-data.test.tsx` | `最新价查询：SecuritySelector 选中后提交的 canonical symbol 与所选一致` |
| TD-D2-PAGE-MD-02 | AC-03 | AUTOMATION | true | `src/pages/market-data.test.tsx` | `历史日 K 同步：SecuritySelector 选中后提交的 canonical symbol 与所选一致` |
| TD-D2-PAGE-WS-01 | AC-03 | AUTOMATION | true | `src/pages/market-workspace.test.tsx` | `采集计划 scope：SecuritySelector 选中后 buildPlanInput 的 scopeJson 含正确 canonical symbol` |
| TD-D2-PAGE-SG-01 | AC-03 | AUTOMATION | true | `src/pages/market-segments.test.tsx` | `板块成员：SecuritySelector 选中后 addSegmentMember 提交的 canonical symbol 与所选一致` |
| TD-D2-NOSIDEEFFECT-01 | AC-04 | AUTOMATION | true | `src/pages/market-data.test.tsx` | `最新价/日 K 选择证券过程不调用 quote/sync/采集任务创建等写接口` |
| TD-D2-NOSIDEEFFECT-02 | AC-04 | AUTOMATION | true | `src/shared/components/SecuritySelector.test.tsx` | `SecuritySelector 搜索过程不触发任何业务写请求` |
| TD-D2-NOSIDEEFFECT-03 | AC-04 | AUTOMATION | true | `src/pages/market-workspace.test.tsx` | `采集计划/板块成员选择过程不触发 quote/K 线同步写` |
| TD-D2-FALLBACK-01 | AC-05 | AUTOMATION | true | `src/pages/market-data.test.tsx` | `手工输入 canonical symbol 后备路径仍可提交` |
| TD-D2-LEGACY-01 | AC-05 | AUTOMATION | true | `src/features/market-data/utils/syncPlanForm.test.ts` | `旧 scopeJson {symbols:[...]} 计划可被 planToDraft 解析展示不报错` |
| TD-D2-LEGACY-02 | AC-05 | AUTOMATION | true | `src/features/market-data/utils/syncPlanForm.test.ts` | `结构化 scope builder 生成正确 scopeJson 且与旧格式读取兼容` |
| TD-D2-STATIC-TYPECHECK | AC-06 | STATIC | true | (frontend root) | `npm run typecheck` |
| TD-D2-STATIC-LINT | AC-06 | STATIC | true | (frontend root) | `npm run lint` |
| TD-D2-STATIC-BUILD | AC-06 | STATIC | true | (frontend root) | `npm run build` |
| TD-D2-STATIC-DIFF | AC-06 | STATIC | true | (frontend root) | `git diff --check` |
| TD-D2-STATIC-ARCH | AC-06 | STATIC | true | (backend root) | `node scripts/check-ai-architecture.mjs --files <absolute frontend prod paths> --candidate-identity <id> --json-output <report>` |
| TD-D2-STATIC-INVENTORY | AC-06 | STATIC | true | (backend artifact) | `SECURITY-DIRECTORY-D2-20260802-TEST-DESIGN.md` (frozen-inventory machine receipt; hash-bound via CONTROL `contract.testInventory`) |

Architecture-gate `--files` must include at minimum these absolute frontend production paths:
`/Users/joker/code/quant-trading-assistant-web/src/shared/components/SecuritySelector.tsx`, `/Users/joker/code/quant-trading-assistant-web/src/features/market-data/api/securityDirectoryApi.ts`, `/Users/joker/code/quant-trading-assistant-web/src/features/market-data/utils/syncPlanForm.ts`, `/Users/joker/code/quant-trading-assistant-web/src/pages/market-data.tsx`, `/Users/joker/code/quant-trading-assistant-web/src/pages/market-workspace.tsx`, `/Users/joker/code/quant-trading-assistant-web/src/pages/market-segments.tsx`, `/Users/joker/code/quant-trading-assistant-web/src/shared/types/domain.ts`.

Note: draft AC-09 is intentionally absent as an AC; its behaviors are realized by the AUTOMATION rows above (race → TD-D2-COMP-02, edit-fail → TD-D2-COMP-06, retry → TD-D2-COMP-03, submit → TD-D2-PAGE-*).

## 6. ENVIRONMENT_FIXTURES

Mock dataset (seed inside `securityDirectoryApi.ts` mock, persisted to localStorage under `securityDirectoryCatalog`; deterministic, shared by mock+remote-shape parity tests):

- `SH.603308` 应流股份, market `SH`, exchange `SSE`, currency `CNY`, type `STOCK`, status `LISTED`, pinyinFull `yingliugufen`, pinyinAbbr `ylgf`, symbol `603308`.
- `HK.02498` 速腾聚创, market `HK`, exchange `HKEX`, currency `HKD`, type `STOCK`, status `LISTED`, aliases [`RoboSense`, `速腾`], symbol `02498` (zero-pad behavior).
- `US.AAPL` Apple Inc., market `US`, exchange `NASDAQ`, currency `USD`, type `STOCK`, status `LISTED`, aliases [`Apple`, `苹果`], symbol `AAPL`.
- Two same-name cross-market samples: e.g. `SH.600600` 同名X / `HK.06000` 同名X (identical `displayName`, different market) — assert no auto-select and side-by-side market labels.
- A renamed sample: e.g. `SH.600000` 当前名 with `FORMER_NAME` alias `旧名称` — assert alias match still locates it.
- A DELISTED sample: e.g. `SH.600001` 退市样本, status `DELISTED` — assert default-hidden, visible+labeled when `includeDelisted=true`.
- Additional markets/types for filter tests: `SZ.000001`, `BJ.430047`, one `ETF` (e.g. `SH.510300`), one `INDEX` to validate `types`/`markets` filters and the STOCK-only MVP form note.
- Enough rows (>20) to validate `limit=20` default truncation and `limit` max 100.

localStorage keys: `settings` (apiMode `mock`/`remote`, apiBaseUrl), new `securityDirectoryCatalog` mock seed. Tests call `clearAll()` in `beforeEach` and `saveSettings({apiMode:'mock'|'remote', apiBaseUrl:''})`.

vitest setup notes: `src/test-setup.ts` already polyfills `localStorage`, `matchMedia`, `ResizeObserver`, `getComputedStyle`. `SecuritySelector` tests require `vi.useFakeTimers()`/`vi.advanceTimersByTime(250)` for debounce, `fireEvent.keyDown(input, {key:'ArrowDown'|'Enter'|'Escape'})` for keyboard. For stale-response protection, use `mockImplementationOnce` returning a deferred promise (pattern from `SecurityVerificationField.test.tsx`). For no-side-effect assertions, `vi.mock` sibling write adapters (`quoteApi`/`workbenchApi.createSyncTask`/`createPlan`/`addSegmentMember`) and assert `not.toHaveBeenCalled()` (hoisted-mock convention in `market-workspace.test.tsx`). Page tests render the relevant Tab/Drawer subcomponent.

## 7. BLOCKING_AMENDMENTS

none.

## 8. VERDICT

`READY_FOR_IMPLEMENTATION`

## 9. ROLE_RUN_METADATA

- roleRunId: TD-D2-20260802-01
- sessionId: codex-agent-429ac282-04d8-4a17-add8-d8fec7d63459
- startedAt: 2026-08-02T01:50:00Z
- finishedAt: 2026-08-02T01:53:00Z
- waitCalls: 0
- compactionCount: 0
- enforcement: ADVISORY
- compensatingIsolation: read-only test-designer; no edits, no Git, no shell commands, no sub-agents; parent persists the returned artifact.
