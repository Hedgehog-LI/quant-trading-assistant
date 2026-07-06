---
name: qta-context-bootstrap
description: 为 QTA（Quant Trading Assistant）新会话分阶段加载项目上下文；只读不写业务代码，是任意任务的首道入口。在用户开始任何开发/设计/验收任务前使用，避免一次性读取全部 docs。
---

# QTA Context Bootstrap

单一职责：为新会话加载必要上下文（**只读，不写业务代码**）。按 5 阶段执行。

仓库：后端+文档 `/Users/joker/code/quant-trading-assistant`；前端 `/Users/joker/code/quant-trading-assistant-web`。

## 阶段 1：读入口（始终执行）
按顺序读：`AGENTS.md` → `CLAUDE.md` → `docs/AI_DEVELOPMENT_INDEX.md` → `docs/AI_HANDOFF.md`。

## 阶段 2：识别任务类型
从用户请求识别（可多选）：产品设计 / 后端开发 / 前端开发 / API 联调 / Mock 开发 / 测试验收 / 部署修复。

## 阶段 3：按类型路由（只读必要文档）
查 `docs/AI_DEVELOPMENT_INDEX.md §4 任务类型路由`，只读对应文档。**不要一次加载整个 docs**。Historical 文档（§6）不必读。

## 阶段 4：开发前输出 Context Digest
向用户输出简短摘要：
- 当前版本与状态（从 `BUILD_CHECKLIST.md` / `AI_HANDOFF.md`）
- 本次任务目标
- 相关模块
- 必须遵守的契约（API / Mock / DB / ADR）
- 已知风险与遗留问题（从 `development/DEVELOPMENT_LOG.md`）
- 准备修改和待同步的文档清单

## 阶段 5：开发后检查清单（开发任务结束时）
逐项确认（有变化才更新），详见 `docs/DEVELOPMENT_WORKFLOW.md §2`：
- **API 变化** → `docs/api/API_INDEX.md` + 对应 `api/*.md` + `docs/mock/MOCK_REMOTE_CONTRACT.md`
- **DB 变化** → 新增 `db/migration/V*.sql` + `DATABASE_DESIGN.md` + `CURRENT_ARCHITECTURE_AND_MODULES.md`
- **Mock 契约变化** → `docs/mock/MOCK_REMOTE_CONTRACT.md`
- **产品状态变化** → `BUILD_CHECKLIST.md` + `PRODUCT_BLUEPRINT.md`
- **重要架构决策** → 新增 `docs/decisions/ADR-XXXX-*.md` + 更新 `ADR_INDEX.md`
- **重要开发记录**（产品/架构/功能/缺陷/契约/治理有实质变化时）→ `docs/development/DEVELOPMENT_LOG.md` 追加（普通问答/只读检查/错别字不追加）
- **验收执行** → `docs/acceptance/ACCEPTANCE_LOG.md` 追加
- **AI_HANDOFF** 仅更新当前接手事实，历史进 DEVELOPMENT_LOG，不无限追加

## 信息真实性优先级（冲突裁决）
migration + 代码 + 测试 > `CURRENT_ARCHITECTURE_AND_MODULES` + `BUILD_CHECKLIST` > API/DB/产品 > `DEVELOPMENT_LOG` + `ACCEPTANCE_LOG` > 历史交接/提示词。**禁止**用旧聊天或旧文档覆盖当前代码事实。
