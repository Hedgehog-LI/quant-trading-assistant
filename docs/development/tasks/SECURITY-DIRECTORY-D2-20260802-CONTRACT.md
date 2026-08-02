# Task Contract: SECURITY-DIRECTORY-D2-20260802 前端共享 SecuritySelector 与首批四个行情流程接入

## Contract Identity

- Status: `FROZEN`
- Contract version: 1
- Frozen at: 2026-08-02T01:55:00Z
- Frozen by parent run: codex-parent-d2-1
- Lane: `L2`
- Test-design role: TD-D2-20260802-01 (READY_FOR_IMPLEMENTATION, no blocking amendments)
- Final AC count: 6 (合并组件行为 AC + 删除冗余 AC-09，落在 L2 上限 8 内)

冻结后父协调者计算本文件 SHA-256 并记入 task state 与 TaskPacket；本文件不含自身 hash。

## Objective

为 P1.4b-D2 在前端仓库 `quant-trading-assistant-web` 新增共享 `SecuritySelector` 组件与统一证券目录 API adapter，并将首批四个行情流程从手工 canonical symbol 输入改造为「目录搜索 → 明确选择 → 自动填充 canonical symbol」。后端 D1 搜索 API 已于 2026-07-29 验收，本轮只读复用，不修改后端业务代码。

## Authority

- Product/design: `docs/features/SECURITY_DIRECTORY_SEARCH_DESIGN.md`（§4 交互、§4.2 排序由后端给出、§4.3 首批接入、§7 API、§9 验收标准、§10 不做项）
- Implementation plan: `docs/development/SECURITY_DIRECTORY_SEARCH_IMPLEMENTATION_PLAN.md`（D2 段、§3 D2-01/02/03、§4 工程约束）
- API/data contract: `docs/api/MARKET_DATA_API.md` 中 `GET /api/v1/market-data/securities/search` 与 `GET /api/v1/market-data/securities/{canonicalSymbol}` 的请求/响应；D1 搜索响应项含 `canonicalSymbol`、`symbol`、`displayName`、`market`、`exchange`、`currency`、`securityType`、`listStatus`、`matchedBy`，元数据含 `catalogStatus`、`catalogUpdatedAt`、`stale`、`degraded`。
- Mock/remote contract: 前端 `src/features/*/api/*Api.ts` 现行 `mockApi`/`remoteApi`/`pick` 约定 + `src/shared/api/{client,unwrappers,types,localStorageClient}.ts`；mock 与 remote 同形、同函数签名、同返回类型（共享 `src/shared/types/domain.ts`），mock 用 localStorage 持久化、remote 用 axios+unwrap。`MOCK_REMOTE_CONTRACT.md` 仅在后端 docs，前端无 docs 目录。
- Frontend architecture: 现有 feature-sliced 布局：`src/app/`、`src/pages/`、`src/features/<feature>/{api,components,hooks,utils}/`、`src/shared/{api,components,stores,types,utils}/`；测试 `vite.config.ts` 的 `test` 块（globals/jsdom/setupFiles）、`<file>.test.ts(x)` 就近放置、`*.remote.test.ts` 拆分远端测试。
- Baseline commit (governance/backend): `a8b2b1d3ec63be26a2b61dcb5fe8314e25ef127a`（治理基线固化后）
- Baseline commit (implementation/frontend): `80c38324f58ba58cf6f96884184e16c86b967f96`
- Baseline branch: `codex/security-directory-d2-20260802`（前后端同名任务分支）
- Pre-existing dirty paths:
  - `docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-CANDIDATE.patch`
  - `docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-CONTROL.json`
- Allowed write paths（前端）:
  - `src/shared/types/domain.ts`
  - `src/shared/components/SecuritySelector.tsx`（含 `.test.tsx`）
  - `src/features/market-data/api/securityDirectoryApi.ts`（含 `.test.ts`、`.remote.test.ts`）
  - `src/features/market-data/components/`（如需辅助组件/字段）
  - `src/pages/market-data.tsx`、`src/pages/market-data.test.tsx`
  - `src/pages/market-workspace.tsx`、`src/pages/market-workspace.test.tsx`
  - `src/pages/market-segments.tsx`、`src/pages/market-segments.test.tsx`
  - `src/features/market-data/utils/syncPlanForm.ts`（结构化 scope builder，含 `.test.ts`）
  - 新增/调整 mock 数据（在 `securityDirectoryApi.ts` 内）
- Allowed write paths（后端，仅治理/文档/工件，非业务代码）:
  - `docs/development/tasks/SECURITY-DIRECTORY-D2-20260802-*.md/json`（契约、CONTROL、test-design、self-check、review、verification、checkpoint、candidate patch）
  - 独立验收通过后才允许：`docs/features/SECURITY_DIRECTORY_SEARCH_DESIGN.md`、`docs/development/SECURITY_DIRECTORY_SEARCH_IMPLEMENTATION_PLAN.md`、`docs/development/DEVELOPMENT_LOG.md`、`docs/acceptance/ACCEPTANCE_LOG.md`、`docs/AI_HANDOFF.md`、`docs/BUILD_CHECKLIST.md`、前端 README、Mock 契约和能力矩阵（仅真实变化项）

## Facts And Decisions

| Type | Item |
|---|---|
| FACT | D1 搜索 API 已验收：`GET /api/v1/market-data/securities/search?q=&markets=&types=&includeDelisted=&limit=` 与 `GET /api/v1/market-data/securities/{canonicalSymbol}` 已实现，前端只读复用。 |
| FACT | 前端无 `src/components`/`src/store`/`src/types`/`src/utils`/`src/mocks`，跨 feature 共享代码在 `src/shared/*`；`SecuritySelector` 应位于 `src/shared/components/`。 |
| FACT | 现有最近似「选择器」的是 `src/features/market-data/components/SecurityVerificationField.tsx`，controlled `value: string`/`onChange: (string)=>void`，可放入 antd `Form.Item`；`SecuritySelector` 须沿用同一 controlled-field 契约。 |
| FACT | 现有四个流程均为手工 canonical symbol 输入：最新价（`pages/market-data.tsx` QuoteSnapshotsTab free-text TextArea）、历史日 K（同文件 SyncTasksTab free-text Input）、采集计划 scope（`pages/market-workspace.tsx` PlansTab antd Form，`scopeJson` 序列化为 `{symbols:[...]}`）、板块成员（`pages/market-segments.tsx` MembersDrawer free-text Input）。 |
| FACT | 前端无 AutoComplete/debounce 现成组件；`SecuritySelector` 是首个 autocomplete 组件。 |
| FACT | `src/shared/types/domain.ts` 已有 `StockBasic`（`canonicalSymbol`/`symbol`/`name`/`market:string`/`delisted`），但无统一 `Security` 聚合类型、统一 market 枚举、security type 枚举；market 现有 `MarketType('A_SHARE'|'HK'|'US'|'ETF'|'OTHER')`、`StockMarket('SH'|'SZ'|'BJ')`、verify 的 `'CN'|'HK'|'US'`、板块的 `'CN'|'HK'|'US'` 不一致。 |
| DECISION | 统一 market 采用目录 API 实际返回值（按 D1 响应的 `market` 字段，与 `GET /securities/search` 一致）；新增 `SecurityType`、`ListStatus` 联合字符串字面量类型（不引入 `enum`，遵守 tsconfig `erasableSyntaxOnly`）。 |
| DECISION | 候选模式：前端 `COMMIT`（前端任务分支提交实现）+ 后端 `COMMIT`（后端任务分支提交治理/契约/工件/冻结 diff patch）。治理验证脚本以后端根为 `cwd` 运行；`control.git.branch=前端分支`、`candidate.mode=COMMIT`、`candidate.commit=前端 HEAD`、架构门用 `--files <前端路径>` 在前端文件上运行、交付门在前端根目录运行（前端 diff 可被 git 验证）。见 §Candidate And Git Policy。 |
| DECISION | 采集计划 scope 改为结构化 scope builder（`src/features/market-data/utils/syncPlanForm.ts` 增结构化构造与解析），保留旧 `scopeJson` 兼容读取展示，不破坏已有计划数据。 |
| DECISION | 不自动创建采集任务；搜索/选择不得触发 quote、K 线同步、采集任务创建或其他业务写请求（AC-07）。 |
| ASSUMPTION | 后端 D1 搜索响应字段稳定；如实际响应与文档不符，以代码契约（D1 测试）为准并据实记录。 |
| ASSUMPTION | 现有 Playwright/浏览器运行环境未必可直接使用；RUNTIME 维度若缺既有环境记为 NOT_VERIFIED，AUTOMATION 必须全过。 |
| OPEN_QUESTION | 无（可逆实现选择由协调者按现有架构决定）。 |

## Scope

### In Scope

- 新增 `securityDirectoryApi.ts`：`searchSecurities`、`getSecurity`，mock+remote 同形，remote 调用 D1 端点；mock 提供与 remote 同形结果与排名行为。
- 在 `src/shared/types/domain.ts` 增 `Security`/`SecuritySummary`、`SecurityType`、`ListStatus`、统一 market 类型与目录搜索响应/元数据类型。
- 新增 `src/shared/components/SecuritySelector.tsx`：250ms debounce、过期请求保护、loading/empty/error/retry、键盘上下/Enter/Esc、选择后展示名称+canonical symbol+市场/交易所+证券类型、再编辑文本立即失效旧选择、退市默认隐藏（显式筛选可见且标注）、目录未初始化/无匹配/请求失败三态、目录陈旧只提示不阻断本地结果。
- 首批四个流程接入：最新价、历史日 K、采集计划 scope、板块成员，提交正确 canonical symbol。
- 采集计划结构化 scope builder + 旧计划兼容展示。
- 组件行为测试 + API 测试（mock + remote）+ 四个页面提交行为测试。

### Out Of Scope

- D4 第二批接入（自选股、交易计划、交易记录、风控、持仓快照）。
- 后端业务代码改动（D1/D3 不重新实现）。
- 真实浏览器/Docker/LongPort 端到端运行验收（记为 RUNTIME NOT_VERIFIED，除非既有环境直接可用）。

### Prohibited

- 自动下单、券商接口、密钥读取、自动交易。
- 修改后端业务代码、后端 migration、后端业务测试。
- push、部署、rebase、force-push、修改默认分支。
- 删除或改写两个 pre-existing D3 工件。
- 扩展到 D4 或未验收能力；把未验收项标为完成。
- 搜索/选择触发 quote/K 线/采集任务等业务写请求。
- 自动替用户选择同名/退市证券。

## Acceptance Criteria

> 经 qta-test-designer (TD-D2-20260802-01) 收敛：合并草稿 AC-02/03/04/05 为复合 AC-02，删除冗余 AC-09（其行为落到 TEST_INVENTORY 的稳定 testId + selector），STATIC 仅含 typecheck/lint/build/diff/architecture/inventory receipt（vitest 归 AUTOMATION）。最终 6 个 AC，落在 L2 上限 8 内。无 blocking amendments。

| AC-ID | Observable behavior | Preconditions/input | Expected result | Evidence | Dimension | Owner | Status |
|---|---|---|---|---|---|---|---|
| AC-01 | `securityDirectoryApi` 暴露 `searchSecurities`/`getSecurity`；mock 与 remote 返回同形结果（字段一致 + 排名一致）；中文≥1 字符、英文/数字≥2 字符触发搜索、阈值以下不调用；默认 limit=20（最大 100）；`markets`/`types`/`includeDelisted`/`limit` 筛选正确传递；目录元数据（`catalogStatus`/`catalogUpdatedAt`/`stale`/`degraded`）端到端保留 | mock 与 remote 模式分别调用，不同关键词/markets/types/includeDelisted/limit | 同形、阈值、上限、筛选、元数据行为符合预期 | AUTOMATION（mock + remote vitest） | AUTOMATION | implementer | NOT_STARTED |
| AC-02 | `SecuritySelector`（controlled `value:string`/`onChange:(string)=>void`，镜像 `SecurityVerificationField`）满足全部：(a) 250ms debounce；(b) 过期响应保护（旧关键词的迟响应不覆盖新结果）；(c) loading/空/失败/重试 四态；(d) 键盘 ArrowDown/ArrowUp 导航、Enter 确认、Esc 关闭；(e) 选中后展示名称+canonical symbol+市场/交易所+证券类型；(f) 选中后再次编辑文本立即失效旧选择；(g) 同名跨市场证券并列展示且不自动选择；(h) 退市默认隐藏、显式筛选后可见并标注；(i) 目录未初始化/正常无匹配/请求失败 三态可区分；(j) 目录陈旧只提示不阻断本地结果 | 渲染组件，连续/过期输入、空结果、失败、键盘交互、同名/退市数据、三种目录状态、陈旧元数据 | 全部子行为正确（每个子项一个 evidence point） | AUTOMATION（组件 vitest） | AUTOMATION | implementer | NOT_STARTED |
| AC-03 | 最新价、历史日 K、采集计划 scope、板块成员四个流程各自提交的 canonical symbol 与 SecuritySelector 所选完全一致 | 四个页面用 SecuritySelector 选中后提交 | 各流程 payload/scopeJson/add-member 中 canonical symbol 与所选一致 | AUTOMATION（页面 vitest） | AUTOMATION | implementer | NOT_STARTED |
| AC-04 | 搜索与选择全过程不触发业务写请求（不调 quote 快照、K 线/日 K 同步、同步任务创建、采集计划创建）；mock adapter 断言 not-called | 在四个页面与组件交互 | 未调用任何业务写接口 | AUTOMATION（组件 + 页面 vitest） | AUTOMATION | implementer | NOT_STARTED |
| AC-05 | 手工 canonical symbol 后备路径仍可提交；已有采集计划的 legacy `scopeJson`（`{symbols:[...]}` 或 `{canonicalSymbol}`）经 `planToDraft` 可读展示不报错；新结构化 scope builder 生成有效 `scopeJson` 且与旧格式读取兼容 | 手工输入 canonical symbol；加载旧计划 | 手工路径可用；旧计划兼容；结构化 builder 输出正确且向后兼容 | AUTOMATION（页面 + util vitest） | AUTOMATION | implementer | NOT_STARTED |
| AC-06 | 前端静态门禁全部 exit 0：`npm run typecheck`、`npm run lint`、`npm run build`、`git diff --check`（前端根）；架构门 `node scripts/check-ai-architecture.mjs --files <前端生产文件绝对路径> --candidate-identity <id> --json-output <report>`（在后端根运行，分析前端文件）报告 errors=0、绑定候选身份、每个 WARN 有结构化 disposition；冻结 test inventory 机器回执（本 artifact）存在且每个 required testId 1:1 映射到携带其冻结 selector 的 PASS receipt | 在前端/后端根分别执行 | 全部 exit 0，架构 report errors=0 绑定候选身份 | STATIC（各命令 receipt + 架构 report + inventory 回执） | STATIC | implementer/verifier | NOT_STARTED |

## Verification Plan

| Dimension | Required | Command or inspection | Pass rule |
|---|---|---|---|
| STATIC | Yes | 前端 `npm run typecheck`、`npm run lint`、`npm run build`、`git diff --check`；后端 `git diff --check`；候选绑定架构检查 `node scripts/check-ai-architecture.mjs --files <前端生产文件> --candidate-identity <id> --json-output <report>`（前端文件分析）；冻结 test inventory 的机器回执 | 全部 exit 0；架构 report errors=0、绑定候选身份 |
| AUTOMATION | Yes | 前端 `npm run test`（vitest run），覆盖 AC-01..AC-09 全部用例 | vitest 全绿；testEvidence 含每个 required test 的 PASS receipt（含 observedSelectors 含冻结 selector） |
| RUNTIME | No | 既有 Playwright/浏览器环境直接可用时验证四流程/错误态/键盘/控制台错误；否则记 NOT_VERIFIED | 若执行则四流程可用、无控制台错误；若缺环境记 NOT_VERIFIED，AUTOMATION 须全过 |
| DEPLOYMENT | No | 不启动 Docker、不调真实 LongPort、不用任何凭据 | 记 NOT_VERIFIED |

## Implementation Slices

每个初始 slice 最多 3 个 AC、8 个预期文件、500 行生产代码增量；一个全新 implementer 接一个 slice；跨 slice 装配由父协调者负责，不写实现。test-designer 已确认每 slice 在限内（基于实际文件规模核实）。

| Slice ID | Coherent boundary | AC IDs | Allowed write paths | Max files | Max production-line delta |
|---|---|---|---|---:|---:|
| SLICE-01 | 目录 API adapter + 类型 + mock 数据/测试 | AC-01 | `src/features/market-data/api/securityDirectoryApi.ts`(+.test.ts/+.remote.test.ts)、`src/shared/types/domain.ts` | 4 | 400 |
| SLICE-02 | `SecuritySelector` 共享组件 + 行为测试（复合 AC-02 全部子项） | AC-02 | `src/shared/components/SecuritySelector.tsx`(+.test.tsx)；如需辅助组件在 `src/shared/components/` | 3 | 500 |
| SLICE-03 | 最新价 + 历史日 K 两页面接入 | AC-03, AC-04, AC-05 | `src/pages/market-data.tsx`(+.test.tsx) | 2 | 350 |
| SLICE-04 | 采集计划 scope + 板块成员两流程接入 | AC-03, AC-04, AC-05 | `src/pages/market-workspace.tsx`(+.test.tsx)、`src/pages/market-segments.tsx`(+.test.tsx)、`src/features/market-data/utils/syncPlanForm.ts`(+.test.ts) | 6 | 500 |

注：AC-02 仅在 SLICE-02 验证；AC-03/04/05 在 SLICE-03/04 跨流程验证（同一行为在不同流程，正常）；TEST_INVENTORY 的并集覆盖全部。每 slice acIds ≤3。

## test-designer 裁决结果（TD-D2-20260802-01，READY_FOR_IMPLEMENTATION，无 blocking amendments）

1. AC 总数：采用合并方案 B（草稿 AC-02/03/04/05 → 复合 AC-02），并删除冗余 AC-09（其行为落到稳定 testId+selector）。最终 6 个 AC，落在 L2 上限 8 内。
2. STATIC/AUTOMATION 分离：AC-06 STATIC 仅含 typecheck/lint/build/diff/architecture/inventory receipt；vitest（`npm run test`）归 AUTOMATION，每个 testId 一份携带冻结 selector 的 PASS receipt。
3. 跨仓库候选：CONFIRMED 推荐备选——全部门禁在后端根运行，candidate.commit=后端提交（含前端冻结 diff patch + 契约/CONTROL/role 工件），架构门用 `--files` 直接分析前端生产文件，control.git.branch=后端分支。verification artifact 须诚实记录「candidate identity=后端提交，前端实现以冻结 diff patch 佐证」。
4. Slice 规模：基于实际文件规模核实，全部在限内。
5. 完整裁决见 `SECURITY-DIRECTORY-D2-20260802-TEST-DESIGN.md`。

## Frozen Test Inventory

由 test-designer (TD-D2-20260802-01) 冻结；完整表见 `SECURITY-DIRECTORY-D2-20260802-TEST-DESIGN.md` §5。sourcePath 相对前端仓库根。selector 为 vitest `it`/`test` 标题（AUTOMATION）或命令/token（STATIC），须逐字出现在源文件与 receipt 中，由最终 verifier 的机器 receipt 强制校验。

| Test ID | AC IDs | Kind | Required | Source path | Exact selector |
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

架构门 `--files` 至少含：`/Users/joker/code/quant-trading-assistant-web/src/shared/components/SecuritySelector.tsx`、`/Users/joker/code/quant-trading-assistant-web/src/features/market-data/api/securityDirectoryApi.ts`、`/Users/joker/code/quant-trading-assistant-web/src/features/market-data/utils/syncPlanForm.ts`、`/Users/joker/code/quant-trading-assistant-web/src/pages/market-data.tsx`、`/Users/joker/code/quant-trading-assistant-web/src/pages/market-workspace.tsx`、`/Users/joker/code/quant-trading-assistant-web/src/pages/market-segments.tsx`、`/Users/joker/code/quant-trading-assistant-web/src/shared/types/domain.ts`。

## Architecture And Quality Gates

- Required architecture review: `YES`（L2、跨 feature 共享组件）
- Triggered thresholds: 单文件 significant lines>400/methods>20/longest>60/direct deps>10 为 WARN；>600 lines+>30 methods+>3 responsibilities、longest method>100、controller 跨事务/持久化、service 合并 file-protocol+persistence、SQL 出现在 mapper 外为 ERROR。注意前端 TS 规则：方法计数包含 TS class method/arrow function，但不计 if/for/while 条件块。
- Required layers/boundaries: `SecuritySelector` 在 `src/shared/components/`（跨 feature 共享）；adapter 在 `src/features/market-data/api/`；不得在页面内散落请求逻辑；adapter 不直接做行情/同步/采集任务写调用。
- Responsibility-map evidence: 架构 report（`--files` 前端文件）绑定候选身份、errors=0；每个 WARN 须有结构化 disposition。
- ADR exception and expiry: 无。

## Role Assignments

- Test designer: 全新 qta-test-designer（READ_ONLY，先冻结 AC/test inventory/slice）
- Implementer: 每个 slice 一个全新 qta-implementer（READ_WRITE）
- Code reviewer: 候选冻结后全新 qta-code-reviewer（READ_ONLY）
- Final verifier: REVIEW_CLEAR 后全新 qta-final-verifier（VERIFY_EXECUTE）
- Omitted roles and justification: 无（L2 全四角色）

## Candidate And Git Policy

- Git automation: `COMMIT`（前端实现提交 + 后端治理/工件提交；仅本地 commit）
- User authorization evidence: 用户 `/qta-run` 任务包授权 + 明确「全部按推荐执行，不询问」
- Task branch: `codex/security-directory-d2-20260802`（前后端同名）
- Contract commit: 后端任务分支提交本契约 + CONTROL + test-design（contract 阶段提交）
- Candidate mode: `COMMIT`
- Candidate commit: 前端任务分支的 SELF_CHECKED 提交 HEAD（实现冻结后回填）
- Candidate tree hash: 前端 HEAD^{tree}（回填）
- Patch SHA-256: `git -C <前端> diff --binary <frontend baseline> <candidate commit>` 的 SHA-256（回填）；同时把该 patch 作为后端工件 `docs/development/tasks/SECURITY-DIRECTORY-D2-20260802-CANDIDATE.patch` 提交，其 SHA-256 须等于 patchSha256
- Candidate manifest path/hash: 不适用（COMMIT 模式）
- Checkpoint push allowed: NO（不 push）
- Delivery push target: 无（不 push、不部署）
- Protected/default branch direct push: NO

跨仓库候选身份说明：`candidate.commit/treeHash` 取自前端仓库；`candidate.patchSha256` = `candidate.diffArtifactSha256` = 前端 `git diff --binary <frontend-base> <candidate>` 的 SHA-256，且该 patch 工件存于后端并被 git 跟踪。架构门以 `--files <前端生产文件绝对路径>` 在前端文件上运行（不依赖 git candidate）。交付门在前端根目录运行（前端 diff/branch 可被 git 验证、前端 worktree 干净、前端 artifacts 被跟踪——前端需存在等价治理脚本/schemas，见下「跨仓库门禁运行约束」）。

## 跨仓库门禁运行约束（DECISION，须 test-designer/verifier 知悉）

治理脚本（`check-ai-task-control.mjs`、`check-ai-architecture.mjs`、`run-ai-evidence-command.mjs`、`check-ai-delivery-ready.mjs`）以 `process.cwd()` 为 root、相对 root 读 `.agents/schemas` 与 artifacts。前端仓库当前无 `.agents`/`.zcode`/`scripts`。为使门禁可在前端根目录运行并验证前端 candidate，需将以下只读治理种子镜像到前端仓库（与后端保持一致，仅本轮快照，不长期维护）：

- `.agents/schemas/qta-task-control.schema.json`
- `scripts/check-ai-architecture.mjs`、`scripts/check-ai-delivery-ready.mjs`、`scripts/check-ai-task-control.mjs`、`scripts/run-ai-evidence-command.mjs`（及其 import 依赖）
- 这些镜像文件作为前端候选的一部分提交，并在前端任务分支上被 git 跟踪；交付门在前端根目录 `node scripts/check-ai-delivery-ready.mjs <control-copy>` 运行。

> 若 test-designer/协调者判断此方案对前端仓库污染过大或与「业务实现只修改前端、不引入治理基础设施」冲突，备选为：**全部门禁在后端根目录运行，candidate 用后端 COMMIT（后端提交含 patch 工件+契约+工件），架构门 `--files` 指向前端绝对路径，control.git.branch=后端分支**。该备选不污染前端，但 candidate.commit 不代表前端实际代码，须在 verification artifact 诚实记录「candidate identity = 后端提交，前端实现以冻结 diff patch 佐证」。**推荐本备选**（不污染前端、单一治理根）。最终由协调者按推荐（备选）执行，记入 CONTROL。

## Checkpoint Policy

- Context budget: UNAVAILABLE（无可靠 telemetry）
- Persist discoveries at: 25%
- Stop opening stages at: 40%
- Mandatory fresh-context handoff at: 60%
- Maximum waits per role run: 2
- Maximum shell polls per command: 3
- Automatic compaction policy: first compaction forces handoff; second is prohibited
- Maximum repair rounds for one failure fingerprint: 2
- Lane AC cap: 8（若 test-designer 按推荐方案 B 合并组件行为 AC，最终 AC 数 ≤8）
- Blocking amendment cap: 3（L2）
- Blocking amendment history: （test-designer 收敛后回填）
- Stop conditions: DELIVERY_READY（`node scripts/check-ai-delivery-ready.mjs <CONTROL>` exit 0）或 BLOCKED（记录真实阻塞证据、失败指纹、已执行次数、唯一下一步）
