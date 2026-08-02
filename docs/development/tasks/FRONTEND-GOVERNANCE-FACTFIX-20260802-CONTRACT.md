# Task Contract: FRONTEND-GOVERNANCE-FACTFIX-20260802 D1/D2/D3/D4 事实对齐（控制仓单仓库账本）

## Contract Identity
- Status: FROZEN
- Contract version: 1
- Frozen at: 2026-08-02T16:35:00Z
- Lane: L0

## Objective (control-repo scope only)
Verify and finalize the D1/D2/D3/D4 fact corrections in the backend control repo: 16 stale lines across 7 docs corrected to reflect ground truth (D1/D2/D3 in main; D4 pending), preserving frozen candidate identities and runtime caveats. This single-repo ledger verifies the already-shipped fact-fix candidate on the control repo.

## Authority
- Baseline commit: 563e84a573426800b3f6aa8e4e0525bc5314b3a8 (control main)
- Candidate commit: 46df0deff963fb34d4371d8e044a24f9d3f25d74 (control HEAD, carries the fact fix + this ledger)
- Baseline branch: codex/frontend-governance-bootstrap-20260802
- Pre-existing dirty paths: none
- Allowed write paths: docs/AI_HANDOFF.md, docs/BUILD_CHECKLIST.md, docs/CURRENT_ARCHITECTURE_AND_MODULES.md, docs/PRODUCT_BLUEPRINT.md, docs/features/SECURITY_DIRECTORY_SEARCH_DESIGN.md, docs/development/SECURITY_DIRECTORY_SEARCH_IMPLEMENTATION_PLAN.md, docs/ai/HANDOFF_2026-07-17_security_directory_search.md, docs/acceptance/ACCEPTANCE_LOG.md

## Facts And Decisions
| Type | Item |
|---|---|
| FACT | D1/D2/D3 已进入控制仓 main；D4（及可选 D3-LongPort enricher）尚未实施。16 行过时措辞已修正，保留冻结候选身份（f3ba475/ff393bc）、406 tests、Docker/MySQL NOT_VERIFIED 警示。 |
| DECISION | 单仓库账本：本任务仅覆盖控制仓 AC-06（事实对齐），与 web 仓治理 bootstrap 分离。Lane L0（deliverable 已完成，仅 verify+finalize）。 |

## Scope
### In Scope
控制仓 7 个事实文档的 D1/D2/D3/D4 状态对齐（已交付于候选 46df0de）。

### Out Of Scope
web 仓治理 bootstrap（单独账本）；任何业务代码；ADR/冻结任务工件。

### Prohibited
编辑 ADR（docs/decisions/）或冻结任务工件；merge/push/force-push main。

## Acceptance Criteria
| AC-ID | Observable behavior | Preconditions/input | Expected result | Evidence | Dimension | Owner | Status |
|---|---|---|---|---|---|---|---|
| AC-01 | 7 个事实文档无过时 D-status 措辞，ADR/冻结工件零改动，治理门禁全绿 | 负向 grep + git diff + run-ai-governance-gates.mjs | grep 0 命中；ADR/frozen 0 diff；gates 退出 0 | grep+diff+gate 收据 | STATIC | FINAL_VERIFIER | NOT_STARTED |

## Verification Plan
| Dimension | Required | Command or inspection | Pass rule |
|---|---|---|---|
| STATIC | Yes | 负向 grep + ADR/frozen diff + run-ai-governance-gates.mjs | grep 0 hits; 0 diff; gates exit 0 |
| AUTOMATION | No | — | — |
| RUNTIME | No | — | — |
| DEPLOYMENT | No | — | — |

## Implementation Slices
| Slice ID | Coherent boundary | AC IDs | Allowed write paths | Max files | Max production-line delta |
|---|---|---|---|---:|---:|
| SLICE-01 | 7 个事实文档的 D-status 对齐（已交付于候选） | AC-01 | 7 个 docs 文件 | 8 | 500 |

## Frozen Test Inventory
| Test ID | AC IDs | Kind | Required | Source path | Exact selector | Receipt path |
|---|---|---|---|---|---|---|
| TEST-01 | AC-01 | STATIC | YES | docs/AI_HANDOFF.md | 已进入 main | docs/development/tasks/FRONTEND-GOVERNANCE-FACTFIX-20260802-TEST-01.receipt.json |

## Architecture And Quality Gates
- Required architecture review: NO（L0 docs-only，无生产代码）

## Role Assignments
- Implementer: fresh qta-implementer（证据绑定）
- Final verifier: fresh qta-final-verifier
- Omitted: test-designer, code-reviewer（L0 contract-lite: docs-only fact alignment, already shipped）

## Candidate And Git Policy
- Git automation: DELIVERY_PUSH
- Task branch: codex/frontend-governance-bootstrap-20260802
- Candidate mode: COMMIT
- Candidate commit: 46df0deff963fb34d4371d8e044a24f9d3f25d74

## Checkpoint Policy
- contextMeasurement=UNAVAILABLE；maxRepairRounds=2；maxWaitCallsPerRole=2；maxShellPollsPerCommand=3
- Stop conditions: 同因失败两次 → BLOCKED
