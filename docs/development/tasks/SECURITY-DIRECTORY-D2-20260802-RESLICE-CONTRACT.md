# Task Contract: SECURITY-DIRECTORY-D2-20260802-RESLICE（重新切片续作）

> 续作任务。父任务 `SECURITY-DIRECTORY-D2-20260802` 因 SLICE-01 两次 implementer 子代理 600s 超时进入 BLOCKED（见 `…-CHECKPOINT.md`）。本续作沿用同一冻结合约的 6 AC、同一冻结 test inventory（逐字 selector 不变），仅**重新切片**为更小、TaskPacket 内联完整可写规范的边界，消除子代理探索时间压力。BLOCKED 控制被单调锁定，无法原地改切片，故另起续作任务 ID。

## Contract Identity

- Status: `FROZEN`
- Contract version: 1
- Frozen at: 2026-08-02T05:49:00Z
- Frozen by parent run: codex-parent-d2-reslice-1
- Lane: `L2`
- 继承冻结 AC（6 个）与冻结 test inventory（31 项，selector 逐字，见 `SECURITY-DIRECTORY-D2-20260802-TEST-DESIGN.md`）。
- 父契约（证据来源）：`docs/development/tasks/SECURITY-DIRECTORY-D2-20260802-CONTRACT.md`（hash `1d52ca6c…`）。

## Objective

沿用父任务目标：前端仓库新增共享 `SecuritySelector` + 统一证券目录 API adapter，首批四个流程接入。后端只读复用 D1 搜索 API，不改后端业务代码。

## Authority

- Product/design: `docs/features/SECURITY_DIRECTORY_SEARCH_DESIGN.md`（§4/§4.2/§4.3/§7/§9/§10）
- Implementation plan: `docs/development/SECURITY_DIRECTORY_SEARCH_IMPLEMENTATION_PLAN.md`（D2/§3/§4）
- API: `docs/api/MARKET_DATA_API.md` 的 `GET /market-data/securities/search` 与 `GET /market-data/securities/{canonicalSymbol}`。
- Frontend 约定：`src/features/market-data/api/segmentApi.ts`（mockApi/remoteApi/pick 范式）、`src/shared/api/{client,unwrappers,localStorageClient,types}.ts`、`src/features/market-data/components/SecurityVerificationField.tsx`（controlled-field 契约）、`src/shared/types/domain.ts`、四个页面、`syncPlanForm.ts`、`vite.config.ts`。
- Baseline commit (backend/governance): `cd901d5`（父任务 BLOCKED 提交之后）
- Baseline commit (frontend/impl): `80c38324f58ba58cf6f96884184e16c86b967f96`
- Branch: `codex/security-directory-d2-20260802`（前后端同名）
- Pre-existing dirty paths: `docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-CANDIDATE.patch`、`docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-CONTROL.json`

## Facts And Decisions

| Type | Item |
|---|---|
| FACT | 父任务 SLICE-01 两次 implementer 子代理超时（attempt1 零产出；attempt2 写了 4 文件未自检，只读评估 4 pass/3 fail）。前端工作树已由父协调者恢复到 clean baseline。 |
| DECISION | 重新切片为 RS-01..RS-05；每个 TaskPacket 内联完整可写代码规范（含逐字测试标题），子代理只做最少读取 + 机械写入 + 跑聚焦测试，消除探索导致的超时。 |
| DECISION | 续作沿用同一冻结 AC 与 test inventory（selector 不变），候选身份仍按父任务跨仓库方案（全部门禁后端根运行，candidate.commit=后端提交含前端冻结 diff patch，架构门 `--files` 分析前端文件）。 |
| DECISION | 不询问用户；可逆实现选择由协调者按现有架构决定（用户已明确授权）。 |

## Acceptance Criteria

与父任务完全相同（6 个 AC：AC-01..AC-06）。逐字见父契约 `SECURITY-DIRECTORY-D2-20260802-CONTRACT.md` 与冻结 test inventory。

## Implementation Slices（重新切片）

| Slice ID | 边界 | AC IDs | Allowed write paths (前端) | Max files | Max prod delta |
|---|---|---|---|---:|---:|
| RS-01 | domain.ts 类型 + securityDirectoryApi（remote+mock 完整） | AC-01, AC-06 | `src/shared/types/domain.ts`、`src/features/market-data/api/securityDirectoryApi.ts` | 2 | 400 |
| RS-02 | securityDirectoryApi mock + remote 测试 | AC-01 | `src/features/market-data/api/securityDirectoryApi.test.ts`、`src/features/market-data/api/securityDirectoryApi.remote.test.ts` | 2 | 300 |
| RS-03 | SecuritySelector 共享组件 + 行为测试 | AC-02 | `src/shared/components/SecuritySelector.tsx`、`src/shared/components/SecuritySelector.test.tsx` | 2 | 500 |
| RS-04 | 最新价 + 历史日 K 两页面接入 + 页面测试 | AC-03, AC-04, AC-05 | `src/pages/market-data.tsx`、`src/pages/market-data.test.tsx` | 2 | 350 |
| RS-05 | 采集计划 scope + 板块成员两流程 + util 测试 | AC-03, AC-04, AC-05 | `src/pages/market-workspace.tsx`(+.test.tsx)、`src/pages/market-segments.tsx`(+.test.tsx)、`src/features/market-data/utils/syncPlanForm.ts`(+.test.ts) | 6 | 500 |

## Frozen Test Inventory

逐字沿用 `SECURITY-DIRECTORY-D2-20260802-TEST-DESIGN.md` §5（31 项）。selector 不变。

## Candidate And Git Policy

- Git automation: `COMMIT`（前端实现 + 后端治理/工件；仅本地 commit；不 push/部署/rebase/force-push）
- Candidate mode: COMMIT；candidate.commit = 后端任务分支提交（含前端冻结 diff patch `…-RESLICE-CANDIDATE.patch` + 契约/CONTROL/role 工件）；架构门 `--files` 直接分析前端生产文件；control.git.branch = 后端分支。
- 验收 artifact 须诚实记录：候选身份=后端提交，前端实现以冻结 diff patch 佐证。

## Stop Conditions

DELIVERY_READY（`node scripts/check-ai-delivery-ready.mjs <CONTROL>` exit 0）或 BLOCKED（记录真实阻塞证据、失败指纹、已执行次数、唯一下一步）。不询问用户。
