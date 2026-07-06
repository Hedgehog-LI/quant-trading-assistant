# Acceptance Log

> 按版本记录**实际执行过**的验收（测试数 / 构建 / Docker / curl / 浏览器）。**只记实际结果，不虚构**；新条目用 `../templates/ACCEPTANCE_TEMPLATE.md`。

---

## 2026-07 — 文档体系治理验收

- **后端** `./mvnw test`：121 通过（业务代码未改，回归通过）。
- **前端** typecheck/lint/test/build：0 error / 179 测试通过 / build 成功（业务代码未改）。
- **文档一致性**：主流程无 JPA/Repository 冲突、无 `v0.1.1 当前/待开发/Iteration 0.5` 残留、无不存在接口（`/api/risk-alerts`/`/api/review-notes`/`/api/backtests`）；Controller 路径与 `api/API_INDEX.md` 一致；`localStorageClient` `qta:` 前缀与 `mock/MOCK_REMOTE_CONTRACT.md` 一致；`API_INDEX` Portfolio 完整路径 `/api/v1/portfolio/positions`。
- **git diff --check**：两仓库 clean。
- **结论**：文档治理通过；**未改业务代码、未改 DB migration、未 commit/push**。

---

## 2026-07 — 文档治理最终收尾验收

在文档治理基础上修复 6 项残留：① `AGENTS.md` 删除"读取 CONVERSATION_HANDOFF"旧指令 + "前端可以后续生成"改为"前后端均已存在"；② `BUILD_CHECKLIST.md` "当前 P0.5" 改为 "已完成并验收"；③ `api/API_INDEX.md` 全部路径改完整 `/api/v1/...`（27 条，含 Position Snapshot 的 `/api/v1/position-snapshots/{id}/reconciliation` 等）；④ `qta-context-bootstrap` + `DEVELOPMENT_WORKFLOW` 删除"每轮总是追加 DEVELOPMENT_LOG"（改为"仅产品/架构/功能/缺陷/契约/治理重要变更才追加"）；⑤ `FRONTEND_ARCHITECTURE.md` 部署改为"宿主机 Nginx 托管 dist + Docker qta-server/qta-mysql，前端不容器化"（删除虚构的 qta-frontend 容器）；⑥ 日志规则修正（不声称未执行的检查通过）。

**验证（真实执行）**：
- 后端 `./mvnw test`：121 通过（业务代码未改，回归通过）。
- 前端 typecheck/lint/test/build：0 error / 179 测试通过 / build 成功（业务代码未改）。
- 残留复查：上一轮声明 `当前 P0.5`、AGENTS `CONVERSATION_HANDOFF`、`总是追加`、`qta-frontend`、`前端可以后续生成` 为 none，但**实际漏检** `PRODUCT_BLUEPRINT.md:87`、`CLAUDE.md:37`、`BUILD_CHECKLIST.md:51` 三处（"当前 P0.5"、"前端可以后续生成"、"下一阶段 P0: 建设看板"）。本轮已修复这三处并重新精确搜索，确认上述关键词在 `docs/`、`CLAUDE.md`、`AGENTS.md` 中**真正 none**（`ACCEPTANCE_LOG` 内的引用为历史记录说明，不计为残留）。
- Controller 方法级路径（`/{id}`、`/today`、`/comparison`、`/{snapshotId}/reconciliation`、`/calculations/position-size`、`/positions`、`/closed-trades`、`/summary`、`/symbol/{symbol}`、`/prices`、`/latest`、`/{id}/confirm`、`/{id}/cancel`、`/{id}/enabled`、`/{id}/review-status`、`/{id}/status`）全部出现在 API_INDEX 完整路径中。
- `git diff --check`：两仓库 clean。

**结论**：文档治理最终收尾通过；**未改业务代码、未改 DB migration、未 commit/push**。

---

## v0.1.1 — 基础交易闭环优化（最终交付，2026-07）

- **后端测试**：`./mvnw test` → `Tests run: 121, Failures: 0, Errors: 0`，BUILD SUCCESS。
- **后端打包**：`./mvnw package` → BUILD SUCCESS，`target/quant-trading-assistant-0.0.1-SNAPSHOT.jar` 31MB。
- **前端**：`npm run typecheck` 0 error；`npm run lint` 0 error/0 warning；`npm run test` = Test Files 26 / Tests 179 passed；`npm run build` 成功。
- **Docker**：`docker compose up -d --build` → `qta-mysql` healthy + `qta-server` `/actuator/health` UP，Flyway V1-V4 应用成功。
- **curl 端到端**：
  - `/actuator/health` UP。
  - `/dashboard/today` todos `targetPath` 全 `/journal*` 或 `/position-snapshots`（复数，无 404）。
  - `/dashboard/today?date=2026-06-27` `pendingReviewCount=1`，不含未来交易日。
  - `/position-snapshots/comparison` 正向 `INCREASED`，反向 `POSITION_SNAPSHOT_COMPARISON_INVALID`。
  - `/position-snapshots/{id}/reconciliation` 纯超卖（空快照+只卖出）→ `QUANTITY_MISMATCH` + 超卖 warning。
  - `PUT /trade-journals/{id}` `unlinkPlan=true` → `planId=null`（解绑成功）。
  - `/reviews` 含历史脏数据（空段/非法/重复）查询 → `SUCCESS`（不 500）。
- **浏览器（Playwright chromium）**：mock 模式访问 `/dashboard`、`/journal`、`/position-snapshots`、`/settings`，控制台 `DEPRECATED_WARNINGS=0, CONSOLE_ERRORS=0`。
- **结论**：v0.1.1 验收通过。

---

## v0.1.0 — Today MVP + 交易账本 + 持仓快照

- 后端基础测试 + 前端基础测试通过（具体数量低于 v0.1.1，已被后续版本覆盖）。
- Docker 冷构建 + 联调通过。
- 详细结果见 `../BUILD_CHECKLIST.md` 第 2-5 节勾选项。
