#!/usr/bin/env bash
# QTA-V2-MR0-DATA-SEMANTICS-POC-20260815 / SLICE-04（AC-07, AC-08）MR-0 PoC 一键编排，AMD-1 冻结语义。
# 固定序列：build → 起服务(18080/local/ingest-enabled) → 真实导入(2026-07 窗口, 预热 2026-04-01, Top150)
# → analyze#1 → 二次导入(同窗口) → analyze#2 → 断言 → report(json+markdown) → 证据+报告工件 → 停服务。
# 退出码（AMD-1 冻结 + 已披露扩展）：0=全链成功且两次分析哈希一致；2=公共源不可用（证据 status=
# RUNTIME_BLOCKED，不满足 AC-07）；3=两次哈希不一致(HASH_MISMATCH) / 内容健全性失败(ANALYSIS_INVALID，
# exit-3 扩展断言：tradingDays≥15、存在 validStocks>0 交易日、universeSize≥100) / 二次导入 inserted≠0
# (IDEMPOTENCY_VIOLATION，exit-3 扩展)；4=build/启动/存储故障（含 analyze/report 调用失败路径，证据
# status=RUNTIME_BLOCKED + diagnostic 说明）。
# 依赖前提（D8）：tradingDays 由基准 SH.000001 日 K 推导；若 ingest 未抓取基准日 K（任务缺陷 F-001），
# 本脚本将以 ANALYSIS_INVALID(tradingDays=0) 失败，不会产出 POC-REPORT.md。
# 时区（REC-10）：fetched_at=JVM 默认时区，证据 timezone=`date +%Z`。环境变量为 compose 默认 dev 值
# （非密钥）显式传给 java 进程；本脚本不读取 .env。幂等可重跑：工件覆盖写、数据侧 ODKU 幂等。
# frozen-selector: bash scripts/run-mr0-poc.sh (exit 0; POC-EVIDENCE.json key set + analysisHashRun1==analysisHashRun2 + second ingest inserted=0)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TASK=QTA-V2-MR0-DATA-SEMANTICS-POC-20260815
PORT=18080; BASE="http://localhost:${PORT}"; API="${BASE}/api/v1/market-research/mr0-poc"
EVIDENCE="${ROOT}/docs/development/tasks/${TASK}-POC-EVIDENCE.json"
REPORT_MD="${ROOT}/docs/development/tasks/${TASK}-POC-REPORT.md"
HASH_SRC="${ROOT}/src/main/java/com/quant/trade/marketdata/poc/Mr0PocAnalysisService.java"
W_START=2026-07-01; W_END=2026-07-31; W_WARMUP=2026-04-01; W_SAMPLE=150
BODY='{"analysisStart":"'"$W_START"'","analysisEnd":"'"$W_END"'","warmupStart":"'"$W_WARMUP"'","sampleSize":'"$W_SAMPLE"'}'
STARTED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"; TZ_NAME="$(date +%Z)"; SECONDS=0; PID=""
TMP="$(mktemp -d "${TMPDIR:-/tmp}/mr0-poc.XXXXXX")"
ING1="$TMP/ingest1.json"; ING2="$TMP/ingest2.json"; RUN1="$TMP/analyze1.json"; RUN2="$TMP/analyze2.json"; RJSON="$TMP/report.json"
HASH1=null; HASH2=null; AS_OF=null; USHA=null
SECOND_COUNTS='{"universe":null,"membership":null,"dailyBar":null,"moneyFlow":null}'
REAL_ROWS='{"bar":null,"membership":null,"moneyflow":null,"derivation":null}'

cleanup() { if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then kill "$PID" 2>/dev/null || true; sleep 3; kill -9 "$PID" 2>/dev/null || true; fi; rm -rf "$TMP"; }
trap cleanup EXIT
log() { echo "[mr0-poc $(date '+%H:%M:%S')] $*"; }
die4() { log "ERROR(exit 4): $*"; exit 4; }
jstr() { if [ -n "$1" ]; then printf '"%s"' "$1"; else echo null; fi; }
rows() { jq "$2 | (.inserted//0)+(.updated//0)" "$1"; }

write_evidence() { # $1=status $2=exitCode $3=diagnostic；全部键真实值，缺失为 null
  local failures; failures="$(jq -s '[.[].data.failures[]?] | group_by(.stage) | map({stage:.[0].stage, count:length})' "$ING1" "$ING2" 2>/dev/null || echo '[]')"
  jq -n --arg task "$TASK" --arg status "$1" --argjson exitCode "$2" --arg diag "${3:-}" \
    --arg started "$STARTED_AT" --argjson dur "${SECONDS}" --arg tz "$TZ_NAME" \
    --argjson h1 "$HASH1" --argjson h2 "$HASH2" --argjson second "$SECOND_COUNTS" \
    --argjson usize "${USIZE:-0}" --argjson usha "$USHA" --argjson asOf "$AS_OF" \
    --argjson real "$REAL_ROWS" --argjson failures "$failures" --argjson whitelist "$WHITELIST" \
    --arg ws "$W_START" --arg we "$W_END" --arg ww "$W_WARMUP" \
    '{task:$task, slice:"SLICE-04", generatedBy:"scripts/run-mr0-poc.sh", generatedAt:(now|todate),
      status:$status, exitCode:$exitCode, diagnostic:$diag, startedAt:$started, durationSeconds:$dur, timezone:$tz,
      analysisHashRun1:$h1, analysisHashRun2:$h2, hashAlgorithm:"sha256", hashFieldWhitelist:$whitelist,
      secondIngestCounts:$second, universeAsOfDate:$asOf, universeSize:$usize, universeSymbolsSha256:$usha,
      realRowCounts:$real, window:{analysisStart:$ws, analysisEnd:$we, warmupStart:$ww}, failures:$failures,
      pocReportWritten:($status=="SUCCESS")}' > "$EVIDENCE"
}

command -v jq >/dev/null 2>&1 || die4 "jq 不可用"; command -v curl >/dev/null 2>&1 || die4 "curl 不可用"
curl -sf --max-time 2 "$BASE/actuator/health" >/dev/null 2>&1 && die4 "端口 ${PORT} 已有服务响应（先停止旧实例）"
# analysisContentHash 字段白名单：从 Mr0PocAnalysisService 代码常量提取（不硬编码）
WHITELIST="$(awk '/HASH_FIELD_WHITELIST = \{/,/\};/' "$HASH_SRC" | sed -e 's/^[^{]*{//' -e 's/}.*$//' \
  | tr -d ' "\n' | tr ',' '\n' | sed '/^$/d' | sed 's/.*/"&"/' | paste -sd, - | sed 's/^/[/;s/$/]/')"
jq -e 'type=="array" and length>0' <<<"$WHITELIST" >/dev/null || die4 "hash 字段白名单提取失败"

log "步骤1 build：./mvnw -q -DskipTests package"
(cd "$ROOT" && ./mvnw -q -DskipTests package) || die4 "mvnw package 失败"
JAR="$(ls -t "$ROOT"/target/*.jar 2>/dev/null | head -n 1 || true)"; [ -n "$JAR" ] || die4 "target/*.jar 不存在"

log "步骤2 起服务（${PORT}，local profile，日志 ${TMP}/server.log）"
QTA_MYSQL_HOST=127.0.0.1 QTA_MYSQL_PORT=3306 QTA_MYSQL_DATABASE=quant_trading_assistant QTA_MYSQL_USER=qta \
QTA_MYSQL_PASSWORD=qta_dev_password nohup java -jar "$JAR" --spring.profiles.active=local \
  --server.port="$PORT" --qta.mr0-poc.ingest-enabled=true >"$TMP/server.log" 2>&1 & PID=$!
UP=1; for w in 15 30 60; do sleep "$w"; curl -sf --max-time 5 "$BASE/actuator/health" 2>/dev/null | jq -e '.status=="UP"' >/dev/null 2>&1 && UP=0 && break; done
[ "$UP" -eq 0 ] || { tail -n 40 "$TMP/server.log" >&2 2>/dev/null || true; die4 "服务健康检查未 UP"; }

log "步骤3 首次真实导入（curl --max-time 2400）"
H1="$(curl -s -o "$ING1" -w '%{http_code}' --max-time 2400 -X POST "$API/ingest" -H 'Content-Type: application/json' -d "$BODY" || echo 000)"
if [ "$H1" != "200" ] || ! jq -e '.success==true' "$ING1" >/dev/null 2>&1; then
  write_evidence RUNTIME_BLOCKED 2 "ingest#1 http=${H1} body头部=$(head -c 200 "$ING1" 2>/dev/null | tr '\n' ' ')"
  log "FAIL(exit 2) RUNTIME_BLOCKED：ingest#1 失败"; exit 2
elif [ "$(rows "$ING1" '.data.universe')" -le 0 ] || [ "$(rows "$ING1" '.data.dailyBar')" -le 0 ]; then
  write_evidence RUNTIME_BLOCKED 2 "ingest#1 未建立数据：universe/dailyBar 写入行数=0（公共源不可用特征）"
  log "FAIL(exit 2) RUNTIME_BLOCKED：数据未建立"; exit 2
fi

log "步骤4 analyze#1"
curl -sf --max-time 300 "${API}/analyze?start=${W_START}&end=${W_END}" -o "$RUN1" \
  || { write_evidence RUNTIME_BLOCKED 4 "analyze#1 调用失败（服务/存储故障路径）"; die4 "analyze#1 失败"; }
log "步骤5 二次导入（同窗口）→ 步骤6 analyze#2"
H2="$(curl -s -o "$ING2" -w '%{http_code}' --max-time 2400 -X POST "$API/ingest" -H 'Content-Type: application/json' -d "$BODY" || echo 000)"
if [ "$H2" != "200" ] || ! jq -e '.success==true' "$ING2" >/dev/null 2>&1; then
  write_evidence RUNTIME_BLOCKED 2 "ingest#2 http=${H2}（二次导入失败）"; log "FAIL(exit 2) RUNTIME_BLOCKED"; exit 2
fi
curl -sf --max-time 300 "${API}/analyze?start=${W_START}&end=${W_END}" -o "$RUN2" \
  || { write_evidence RUNTIME_BLOCKED 4 "analyze#2 调用失败（服务/存储故障路径）"; die4 "analyze#2 失败"; }

# 步骤7+9 断言，顺序：哈希一致 → 内容健全性（exit-3 扩展）→ 二次导入幂等（exit-3 扩展）
HASH1_RAW="$(jq -r '.data.analysisContentHash // empty' "$RUN1")"; HASH2_RAW="$(jq -r '.data.analysisContentHash // empty' "$RUN2")"
HASH1="$(jstr "$HASH1_RAW")"; HASH2="$(jstr "$HASH2_RAW")"
AS_OF="$(jstr "$(jq -r '.data.universe.asOfDate // empty' "$RUN1")")"
USHA="$(jstr "$(jq -r '.data.universe.universeSymbolsSha256 // empty' "$RUN1")")"
USIZE="$(jq '.data.universe.universeSize // 0' "$RUN1")"
TDAYS="$(jq '.data.tradingDays.count // 0' "$RUN1")"
VALID_DAYS="$(jq '[.data.breadth.daily[]? | select(.validStocks>0)] | length' "$RUN1")"
NZ="$(jq '[.data.universe.inserted,.data.membership.inserted,.data.dailyBar.inserted,.data.moneyFlow.inserted] | map(.//0) | map(select(.>0)) | length' "$ING2")"
SECOND_COUNTS="$(jq '{universe:{inserted:(.data.universe.inserted//null),updated:(.data.universe.updated//null)},membership:{inserted:(.data.membership.inserted//null),updated:(.data.membership.updated//null)},dailyBar:{inserted:(.data.dailyBar.inserted//null),updated:(.data.dailyBar.updated//null)},moneyFlow:{inserted:(.data.moneyFlow.inserted//null),updated:(.data.moneyFlow.updated//null)}}' "$ING2")"
STATUS=""; DIAG=""
if [ -z "$HASH1_RAW" ] || [ -z "$HASH2_RAW" ]; then STATUS=ANALYSIS_INVALID; DIAG="analysisContentHash 缺失"
elif [ "$HASH1_RAW" != "$HASH2_RAW" ]; then STATUS=HASH_MISMATCH; DIAG="run1=${HASH1_RAW} run2=${HASH2_RAW}"
elif [ "$TDAYS" -lt 15 ]; then STATUS=ANALYSIS_INVALID; DIAG="tradingDays=${TDAYS}<15（基准日 K 缺失时=0，D8/F-001）"
elif [ "$VALID_DAYS" -lt 1 ]; then STATUS=ANALYSIS_INVALID; DIAG="breadth 无 validStocks>0 的交易日"
elif [ "$USIZE" -lt 100 ]; then STATUS=ANALYSIS_INVALID; DIAG="universeSize=${USIZE}<100"
elif [ "$NZ" -ne 0 ]; then STATUS=IDEMPOTENCY_VIOLATION; DIAG="二次导入存在 inserted>0：$(jq -c . <<<"$SECOND_COUNTS")"; fi
if [ -n "$STATUS" ]; then write_evidence "$STATUS" 3 "$DIAG"; log "FAIL(exit 3) ${STATUS}：${DIAG}"; exit 3; fi

log "步骤8 质量报告（format=json 与 format=markdown）"
curl -sf --max-time 300 "${API}/report?start=${W_START}&end=${W_END}&format=json" -o "$RJSON" \
  || { write_evidence RUNTIME_BLOCKED 4 "report(json) 调用失败（服务/存储故障路径）"; die4 "report(json) 失败"; }
curl -sf --max-time 300 "${API}/report?start=${W_START}&end=${W_END}&format=markdown" -o "$TMP/report.md" \
  || { write_evidence RUNTIME_BLOCKED 4 "report(markdown) 调用失败（服务/存储故障路径）"; die4 "report(markdown) 失败"; }

# 步骤9 POC-EVIDENCE.json（真实行数来源见 derivation 字段）
BAR_ROWS="$(jq -r '.data.families[] | select(.family=="COVERAGE") | .details[]' "$RJSON" | sed -n 's/.*tencentBars=\([0-9][0-9]*\).*/\1/p' | awk '{s+=$1} END{print s+0}')"
GAP_N="$(jq '.data.industryTurnover.coverageGap.count // 0' "$RUN1")"
SAMPLE_N="$(jq '.data.universe.sampleSymbols // 0' "$RUN1")"
MEM_ROWS=$((SAMPLE_N - GAP_N)); MF_ROWS="$(rows "$ING1" '.data.moneyFlow')"
REAL_ROWS="$(jq -n --argjson bar "$BAR_ROWS" --argjson mem "$MEM_ROWS" --argjson mf "$MF_ROWS" '{bar:$bar, membership:$mem, moneyflow:$mf, derivation:"bar=Σ(report COVERAGE details tencentBars, 分析窗); membership=universe.sampleSymbols-industryTurnover.coverageGap.count(analyze); moneyflow=ingest#1 moneyFlow inserted+updated(分析窗)"}')"
write_evidence SUCCESS 0 ""

# 步骤10 POC-REPORT.md（全部真实数值；仅成功路径生成，含 MR-1 输入边界固定标记 MR-1-BND-A/B/C/D）
COV_DETAIL="$(jq -r '.data.families[] | select(.family=="COVERAGE") | .details[-1] // ""' "$RJSON")"
STALE_DETAIL="$(jq -r '.data.families[] | select(.family=="STALENESS") | .details[] | select(startswith("marketCalendarCnRows"))' "$RJSON" | head -n 1)"
FAIL_SUMMARY="$(jq -s -r '[.[].data.failures[]?] | group_by(.stage) | map({stage:.[0].stage,count:length}) | if length==0 then "无（两次导入 0 失败）" else map("\(.stage)=\(.count)") | join(", ") end' "$ING1" "$ING2")"
FAM_MD="$(jq -r '.data.families[] | "### \(.family)\n- status: \(.status)\n- reasonCode: \(.reasonCode)\n- affectedCount: \(.affectedCount)\n" + ([(.details // [])[0:8][] | "- " + .] | join("\n")) + (if ((.details // []) | length) > 8 then "\n- …共 \(.details|length) 条 detail，以上为前 8 条" else "" end) + "\n"' "$RJSON")"
cat > "$REPORT_MD" <<EOF
# ${TASK} MR-0 PoC 报告

> 本文件由 scripts/run-mr0-poc.sh 于真实运行成功后生成（SLICE-04，AC-07/AC-08）；全部数值来自
> POC-EVIDENCE.json 与 /analyze、/report 真实响应，禁止手写伪造或未运行生成。

## 运行概要
- 命令：bash scripts/run-mr0-poc.sh；退出码：0（status=SUCCESS）；时长：${SECONDS}s；时区：${TZ_NAME}（REC-10：fetched_at=JVM 默认时区）
- 窗口（D5 冻结）：analysisStart=${W_START}、analysisEnd=${W_END}、warmupStart=${W_WARMUP}、sampleSize=${W_SAMPLE}
- analysisContentHash：run1=run2=${HASH1_RAW}（sha256、字段白名单与二次导入计数见 POC-EVIDENCE.json）
- 退出码语义：0=成功且哈希一致；2=公共源不可用；3=哈希不一致/内容健全性失败/幂等违反（exit-3 扩展）；4=build/启动/存储故障

## 数据规模
- universe：as_of $(jq -r '.data.universe.asOfDate // empty' "$RUN1")、universeSize=${USIZE}（样本 ${SAMPLE_N}，基准 SH.000001 恒入快照不算样本）
- tradingDays：${TDAYS}（INDEX_KLINE_DERIVED，由基准 SH.000001 日 K 推导，D8）
- 真实行数：bar(分析窗 tencentBars 合计)=${BAR_ROWS}、membership(覆盖样本)=${MEM_ROWS}、moneyflow(分析窗写入)=${MF_ROWS}
- 二次导入 inserted：universe=$(jq '.data.universe.inserted//0' "$ING2")、membership=$(jq '.data.membership.inserted//0' "$ING2")、dailyBar=$(jq '.data.dailyBar.inserted//0' "$ING2")、moneyFlow=$(jq '.data.moneyFlow.inserted//0' "$ING2")（全 0=幂等，AMD-1）
- ingest 失败明细：${FAIL_SUMMARY}

## 八族质量结果（来自 /report format=json，族顺序固定）
${FAM_MD}
## 覆盖与缺口结论
- 成员覆盖：${COV_DETAIL}
- 占比覆盖域缺口：coverageGap=${GAP_N} 只样本股无行业成分（不入占比分母，计入 coverageGap 单独报告）
- 日历陈旧度：${STALE_DETAIL}
- TIME_POINT_LOOKAHEAD：当前成分聚合历史=显式时点假设（PIT 行业成分被阻断，见 MR-1-BND-B）

## MR-1 输入边界
- **MR-1-BND-A（MR-1 可直接依赖的数据与口径）**：指标字典公式引擎（公式/单位/缺失语义冻结）；样本级市场广度、行业成交占比、20 日波动率、流动性代理计算链（本运行 tradingDays=${TDAYS}、universeSize=${USIZE}、样本=${SAMPLE_N}，两次分析哈希一致=${HASH1_RAW}）；公共源真实可得性证据（TENCENT_PUBLIC 日 K、SINA_PUBLIC 证券池/行业成分/资金流，实测见 Provider 矩阵）；幂等导入（二次导入 inserted=0，MySQL 方言 ODKU）。
- **MR-1-BND-B（仍被阻断的数据）**：全市场逐股历史覆盖的成本与稳定性（本 PoC 仅流通市值 Top-${SAMPLE_N} 样本 + 单一交易月 + 2026-04-01 起预热）；PIT 申万/官方行业成分（现用 SINA_INDUSTRY 当前成分聚合历史=显式时点假设，见 TIME_POINT_LOOKAHEAD 族）；官方口径资金流（Tushare NOT_VERIFIED 无凭据、Longbridge NOT_RETESTED）。
- **MR-1-BND-C（禁止使用的伪指标）**：价量猜资金（字典红线）；非互斥板块汇总成 100%；跨 Provider 混算（flowIntensity 类混源必须显式标注、不得静默合并）；无 Provider/口径标签的百分数；SINA_INDUSTRY 冒充申万。
- **MR-1-BND-D（下一任务精确输入边界）**：数据集=全 A 证券池 + 日 K（2021-01-01 起）+ PIT 行业成分 + 官方资金流；窗口/Provider/门槛=由 MR-1 契约冻结，凭据就绪前对应维度保持阻断。
<!-- frozen-selector: grep -c 'MR-1 输入边界' -> >=1 with four required elements present -->
EOF

log "步骤11 停服务+清理临时文件；SUCCESS exit 0（证据=${EVIDENCE} 报告=${REPORT_MD}）"
exit 0
