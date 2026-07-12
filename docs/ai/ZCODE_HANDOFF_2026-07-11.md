# ZCode Handoff 2026-07-11

> Purpose: lightweight handoff for a fresh ZCode/Claude/GLM session. Read this file instead of replaying the long chat. Do not load all docs unless a concrete task requires it.
>
> 2026-07-12 update: sections about `SyncScopeLockMapper` are now historical. For the current LongPort single-symbol engine work, read the 2026-07-12 section below first, then `docs/ai/HANDOFF_2026-07-11_longport_single_symbol_engine.md`.

## 0. Current LongPort Continuation 2026-07-12

- Current work is P1.1 LongPort single-symbol manual sync engine.
- Backend/docs repo: `/Users/joker/code/quant-trading-assistant`.
- Frontend repo: `/Users/joker/code/quant-trading-assistant-web`.
- Do not commit/push unless the user explicitly asks.
- Do not load the whole `docs/` tree or historical chat logs.

Current implementation facts:

- Backend reflection adapter is implemented and compiles without the official SDK at build time.
- Docker runtime `runtime-libs/` classpath channel exists and has been fake-SDK tested.
- Official LongPort Java SDK artifact is still not available from Maven Central in the latest local check; real external verification still needs official SDK jar/native libs and read-only credentials.
- Frontend `/market-data` has provider status pre-check, canonical symbol validation, latest quote limit, date validation and HF disabled for LongPort sync.
- `scripts/check-longport-readiness.sh` is the first command to run before any real external LongPort verification.

Minimal context to read:

```text
AGENTS.md
docs/AI_DEVELOPMENT_INDEX.md
docs/AI_HANDOFF.md
docs/ai/PROGRESSIVE_DISCLOSURE_PROTOCOL.md
docs/ai/HANDOFF_2026-07-11_longport_single_symbol_engine.md
docs/development/LONGPORT_SDK_RUNTIME_INSTALLATION.md
docs/development/LONGPORT_OFFICIAL_JAVA_CONTRACT.md
docs/api/MARKET_DATA_API.md
```

Minimal current prompt for ZCode/Claude/Codex:

```text
只做 LongPort 单股票手动同步这条线，不要开启专家团，不要读取全量 docs，不要做自动交易/订单/账户能力。

先读取：
1. AGENTS.md
2. docs/AI_DEVELOPMENT_INDEX.md
3. docs/AI_HANDOFF.md
4. docs/ai/PROGRESSIVE_DISCLOSURE_PROTOCOL.md
5. docs/ai/HANDOFF_2026-07-11_longport_single_symbol_engine.md
6. docs/development/LONGPORT_SDK_RUNTIME_INSTALLATION.md
7. docs/development/LONGPORT_OFFICIAL_JAVA_CONTRACT.md
8. docs/api/MARKET_DATA_API.md

当前目标：
1. 检查当前未提交 LongPort 相关改动，不要误改无关模块。
2. 如果只做准备检查，先运行：
   scripts/check-longport-readiness.sh
3. 若 SDK/凭据还没准备好，只做代码/脚本/文档静态验收，不要伪造行情数据。
4. 若官方 SDK jar/native libs 和只读凭据已准备好，按文档运行：
   scripts/check-longport-readiness.sh
   scripts/verify-longport-real-sync.sh
5. 真实调用只允许一个 A 股 symbol 和小日期范围，不做全量扫描。
6. 开发或修复后同步：
   docs/AI_HANDOFF.md
   docs/development/DEVELOPMENT_LOG.md
   docs/acceptance/ACCEPTANCE_LOG.md
   docs/ai/HANDOFF_2026-07-11_longport_single_symbol_engine.md

限制：
- 不要读取所有 docs。
- 不要打印或提交 LongPort 密钥。
- 不要连接交易/订单/账户 API。
- 不要无限循环验证；失败后一轮直接相关修复，仍失败就写阻塞原因。
- 不要 commit/push，除非用户另行要求。
```

## 1. Current Situation

- Repository: `/Users/joker/code/quant-trading-assistant`
- Frontend repository exists at `/Users/joker/code/quant-trading-assistant-web`, but the current task is backend only.
- Branch: `main`
- Latest committed backend changes before the interrupted work:
  - `ae0413c docs: P1.1 全量口径同步 — 移除'待实现/设计目标/CANCELLED'`
  - `1a21eca fix: scope 级行锁防并发 sibling retry + V9 lock 表 + 并发测试`
- Current interrupted work is not committed.

## 2. Why The Previous GLM Session Hit The Limit

The previous Claude/GLM session did not show evidence of recursive self-calls or infinite file generation.

Observed likely causes:

- The long Claude session log reached about `14M` / `4851` lines.
- Late tool calls showed `cache_read_input_tokens` around `1,030,000` per turn.
- The session was running in team mode / long-lived mode and repeatedly performed test/package/docker/curl checks.
- The final error was a 429 usage limit: reset around `2026-07-11 20:20:05`.

Conclusion: the issue was context bloat plus repeated verification, not a recursive script.

## 3. Current Uncommitted Changes

Expected `git status --short`:

```text
 M src/main/resources/mapper/SyncScopeLockMapper.xml
?? src/test/java/com/quant/trade/marketdata/dao/
```

Changed intent:

- Replace H2-only `MERGE INTO ... KEY (...)` in `SyncScopeLockMapper.xml`.
- Use MySQL-compatible `INSERT ... ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP`.
- Add `SyncScopeLockMapperTest` to cover idempotent upsert and `SELECT ... FOR UPDATE`.

Important: do not edit old migrations. `V9__add_sync_scope_lock.sql` has already been committed and should remain additive/history-safe.

## 4. Last Known Verification From Interrupted Claude Log

The interrupted Claude session log showed these results after the SQL change:

- `./mvnw test`: `Tests run: 176, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`
- `docker compose up -d --build`: server started, health became ready
- `GET /actuator/health`: `UP`
- `GET /api/v1/market-data/providers/LONGPORT/status`: `configured=false`
- `POST /api/v1/market-data/sync-tasks/daily-bars`: returned HTTP `400` with `BUSINESS_RULE_VIOLATION`
- Failed sync task trace existed in DB

These should still be rechecked once in a fresh session before commit.

## 4.1 Codex Follow-up Verification

Codex rechecked the current working tree after applying the documentation status update:

- `./mvnw -q -Dtest=SyncScopeLockMapperTest,MarketQuoteServiceTest test`: passed
  - `SyncScopeLockMapperTest`: 2 tests, 0 failures, 0 errors
  - `MarketQuoteServiceTest`: 10 tests, 0 failures, 0 errors
- Existing local service curl:
  - `POST /api/v1/market-data/sync-tasks/daily-bars`
  - result: HTTP `400`, code `BUSINESS_RULE_VIOLATION`, not `500`
- `./mvnw -q -DskipTests package`: `PACKAGE_OK`

Docker was not rebuilt in this follow-up to conserve quota; the curl check used the currently running local service.

## 4.2 Codex Validation After Context Compression

Codex validated the current working tree again after the context compression:

- Fixed a stale JavaDoc comment in `SyncScopeLockMapper.java` so it matches the XML upsert implementation.
- Added `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` with `mock-maker-subclass`.
  - Reason: local `./mvnw test` on Oracle JDK 17.0.6 / macOS failed because Mockito inline mock maker could not self-attach.
  - The failure was test infrastructure related, not a business assertion failure.
- `./mvnw -q -Dtest=SyncScopeLockMapperTest,MarketQuoteServiceTest test`: passed before the full-suite fix.
- `./mvnw -q test`: passed after adding the Mockito mock maker test resource.
- `./mvnw -q -DskipTests package`: passed.
- `git diff --check`: passed.

Docker validation was still not run in this pass to conserve local AI quota. The MySQL runtime fix is covered by static SQL review (`INSERT ... ON DUPLICATE KEY UPDATE`) and H2 MySQL-mode mapper tests.

## 5. Documentation Status

The interrupted Claude session stopped before completing this documentation edit:

- `docs/features/MARKET_ALERT_RULES_DESIGN.md`

Target status line:

```text
状态：基础数据质量提醒部分实现 / 量价观察规则待实现
```

This handoff may be read after the edit has already been applied. In that case, just verify the status line and do not edit it again.

Rationale:

- Already implemented: provider not configured, sync failed, empty daily bars style foundation alerts.
- Not implemented yet: advanced volume/price observation rules such as `PRICE_GAP`, `VOLUME_BREAKOUT`, `INTRADAY_REVERSAL`.

## 6. Minimal ZCode Prompt

Use this prompt in a fresh ZCode/Claude/GLM session:

```text
只做轻量接手，不要开启专家团，不要读取全量 docs，不要做前端，不要开发新功能。

请先读取：
1. AGENTS.md
2. docs/AI_DEVELOPMENT_INDEX.md
3. docs/AI_HANDOFF.md
4. docs/ai/ZCODE_HANDOFF_2026-07-11.md

当前目标：
1. 检查当前未提交改动：
   - src/main/resources/mapper/SyncScopeLockMapper.xml
   - src/test/java/com/quant/trade/marketdata/dao/SyncScopeLockMapperTest.java
2. 确认 SyncScopeLockMapper.xml 不再使用 MERGE INTO，而是 MySQL 8.4 可运行的 upsert。
3. 确认 docs/features/MARKET_ALERT_RULES_DESIGN.md 状态行已经是：
   “基础数据质量提醒部分实现 / 量价观察规则待实现”。
4. 只跑必要验收：
   ./mvnw test
   ./mvnw package
   docker compose up -d --build
   curl http://localhost:8080/actuator/health
   curl http://localhost:8080/api/v1/market-data/providers/LONGPORT/status
   curl -H 'Content-Type: application/json' -X POST http://localhost:8080/api/v1/market-data/sync-tasks/daily-bars -d '{"taskType":"DAILY_BAR_SYNC","provider":"LONGPORT","canonicalSymbol":"SH.619001","startDate":"2026-07-11","endDate":"2026-07-11","adjustType":"NONE"}'
5. sync curl 在 provider 未配置时必须返回 HTTP 400 + BUSINESS_RULE_VIOLATION，不能是 500。
6. 通过后 git add 并 commit，commit message:
   fix: MySQL 兼容同步 scope 锁 upsert

限制：
- 不要读取所有 docs。
- 不要跑前端命令。
- 不要修改 V1-V9 migration。
- 如果验收失败，只做一轮直接相关修复；仍失败就输出阻塞原因，不要无限循环。
- 不要 push，除非用户另行要求。
```

## 7. Optional Manual Commands

If the AI tool is not available, a human can finish manually:

```bash
git status --short
git diff -- src/main/resources/mapper/SyncScopeLockMapper.xml
sed -n '1,80p' src/test/java/com/quant/trade/marketdata/dao/SyncScopeLockMapperTest.java

./mvnw test
./mvnw package
docker compose up -d --build
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/market-data/providers/LONGPORT/status
curl -H 'Content-Type: application/json' \
  -X POST http://localhost:8080/api/v1/market-data/sync-tasks/daily-bars \
  -d '{"taskType":"DAILY_BAR_SYNC","provider":"LONGPORT","canonicalSymbol":"SH.619001","startDate":"2026-07-11","endDate":"2026-07-11","adjustType":"NONE"}'
```
