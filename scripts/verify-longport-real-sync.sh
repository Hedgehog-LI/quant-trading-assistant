#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

API_BASE_URL="${QTA_VERIFY_API_BASE_URL:-http://localhost:8080}"
SYMBOL="${QTA_VERIFY_SYMBOL:-SH.600519}"
START_DATE="${QTA_VERIFY_START_DATE:-2026-07-10}"
END_DATE="${QTA_VERIFY_END_DATE:-2026-07-10}"
ADJUST_TYPE="${QTA_VERIFY_ADJUST_TYPE:-NONE}"
QUOTE_PERSIST="${QTA_VERIFY_QUOTE_PERSIST:-true}"
RUN_DAILY_BAR_SYNC="${QTA_VERIFY_RUN_DAILY_BAR_SYNC:-true}"
REBUILD_APP="${QTA_VERIFY_REBUILD_APP:-true}"
RESTORE_APP_AFTER_RUN="${QTA_VERIFY_RESTORE_APP_AFTER_RUN:-false}"
RUNTIME_LIB_INSPECTION="${QTA_VERIFY_RUNTIME_LIB_INSPECTION:-auto}"

cleanup() {
  local exit_code=$?
  if [[ "${RESTORE_APP_AFTER_RUN}" == "true" ]]; then
    echo "[qta] restoring default LongPort disabled app container"
    (
      cd "${ROOT_DIR}"
      docker compose up -d --no-build --force-recreate app >/dev/null
    ) || true
  fi
  exit "${exit_code}"
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[qta] missing required command: $1" >&2
    exit 1
  fi
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local label="$3"

  if [[ "${haystack}" != *"${needle}"* ]]; then
    echo "[qta] assertion failed for ${label}: expected to contain ${needle}" >&2
    echo "[qta] actual: ${haystack}" >&2
    exit 1
  fi
}

request() {
  curl -sS -w $'\nHTTP:%{http_code}' "$@"
}

has_shell_or_dotenv_value() {
  local name="$1"
  local value="${!name:-}"
  if [[ -n "${value}" ]]; then
    return 0
  fi
  if [[ -f ".env" ]] && grep -Eq "^${name}=.+" ".env"; then
    return 0
  fi
  return 1
}

wait_for_health() {
  local response=""
  for _ in {1..60}; do
    response="$(curl -fsS "${API_BASE_URL}/actuator/health" || true)"
    if [[ "${response}" == *'"status":"UP"'* ]]; then
      echo "[qta] health is UP"
      return
    fi
    sleep 1
  done

  echo "[qta] app did not become healthy in time" >&2
  (
    cd "${ROOT_DIR}"
    docker compose logs --tail=160 app >&2 || true
  )
  exit 1
}

require_command docker
require_command curl

cd "${ROOT_DIR}"

if [[ "${RUNTIME_LIB_INSPECTION}" == "true" ]]; then
  bash scripts/inspect-longport-runtime-libs.sh
elif [[ "${RUNTIME_LIB_INSPECTION}" == "auto" ]] \
    && find runtime-libs -maxdepth 1 -type f -name '*.jar' | grep -q .; then
  bash scripts/inspect-longport-runtime-libs.sh
elif [[ "${RUNTIME_LIB_INSPECTION}" == "auto" ]]; then
  echo "[qta] warning: runtime-libs has no SDK jar/native package; this is okay only if the SDK is already packaged in app.jar" >&2
elif [[ "${RUNTIME_LIB_INSPECTION}" != "false" ]]; then
  echo "[qta] unsupported QTA_VERIFY_RUNTIME_LIB_INSPECTION=${RUNTIME_LIB_INSPECTION}; expected auto, true, or false" >&2
  exit 1
fi

for credential_name in LONGPORT_APP_KEY LONGPORT_APP_SECRET LONGPORT_ACCESS_TOKEN; do
  if ! has_shell_or_dotenv_value "${credential_name}"; then
    echo "[qta] missing ${credential_name}; provide it in shell env or .env before running real LongPort verification" >&2
    exit 1
  fi
done

echo "[qta] starting app with LongPort enabled; credentials are read from shell env or .env"
if [[ "${REBUILD_APP}" == "true" ]]; then
  QTA_LONGPORT_ENABLED=true docker compose up -d --build app
else
  QTA_LONGPORT_ENABLED=true docker compose up -d --no-build --force-recreate app
fi

wait_for_health

STATUS_RESPONSE="$(request "${API_BASE_URL}/api/v1/market-data/providers/LONGPORT/status")"
assert_contains "${STATUS_RESPONSE}" 'HTTP:200' "provider status http"
assert_contains "${STATUS_RESPONSE}" '"configured":true' "provider status configured"
assert_contains "${STATUS_RESPONSE}" '"reachable":true' "provider status reachable"
echo "[qta] provider status reached configured=true / reachable=true"

QUOTE_PAYLOAD="{\"canonicalSymbols\":[\"${SYMBOL}\"],\"persist\":${QUOTE_PERSIST}}"
QUOTE_RESPONSE="$(request \
  -H 'Content-Type: application/json' \
  -X POST "${API_BASE_URL}/api/v1/market-data/quotes/latest" \
  -d "${QUOTE_PAYLOAD}")"
assert_contains "${QUOTE_RESPONSE}" 'HTTP:200' "latest quote http"
assert_contains "${QUOTE_RESPONSE}" '"success":true' "latest quote success"
assert_contains "${QUOTE_RESPONSE}" "\"canonicalSymbol\":\"${SYMBOL}\"" "latest quote symbol"
assert_contains "${QUOTE_RESPONSE}" '"dataSource":"LONGPORT"' "latest quote data source"
if [[ "${QUOTE_PERSIST}" == "true" ]]; then
  SNAPSHOT_RESPONSE="$(request "${API_BASE_URL}/api/v1/market-data/quote-snapshots?canonicalSymbol=${SYMBOL}&dataSource=LONGPORT&page=1&size=5")"
  assert_contains "${SNAPSHOT_RESPONSE}" 'HTTP:200' "quote snapshot query http"
  assert_contains "${SNAPSHOT_RESPONSE}" '"success":true' "quote snapshot query success"
  assert_contains "${SNAPSHOT_RESPONSE}" "\"canonicalSymbol\":\"${SYMBOL}\"" "quote snapshot query symbol"
  assert_contains "${SNAPSHOT_RESPONSE}" '"dataSource":"LONGPORT"' "quote snapshot query data source"
fi
echo "[qta] latest quote verification passed for ${SYMBOL}; persist=${QUOTE_PERSIST}"

if [[ "${RUN_DAILY_BAR_SYNC}" == "true" ]]; then
  SYNC_PAYLOAD="{\"taskType\":\"DAILY_BAR_SYNC\",\"provider\":\"LONGPORT\",\"canonicalSymbol\":\"${SYMBOL}\",\"startDate\":\"${START_DATE}\",\"endDate\":\"${END_DATE}\",\"adjustType\":\"${ADJUST_TYPE}\"}"
  SYNC_RESPONSE="$(request \
    -H 'Content-Type: application/json' \
    -X POST "${API_BASE_URL}/api/v1/market-data/sync-tasks/daily-bars" \
    -d "${SYNC_PAYLOAD}")"
  assert_contains "${SYNC_RESPONSE}" 'HTTP:200' "daily bar sync http"
  assert_contains "${SYNC_RESPONSE}" '"success":true' "daily bar sync success"
  assert_contains "${SYNC_RESPONSE}" '"provider":"LONGPORT"' "daily bar sync provider"
  assert_contains "${SYNC_RESPONSE}" '"status":"SUCCEEDED"' "daily bar sync status"
  assert_contains "${SYNC_RESPONSE}" '"failCount":0' "daily bar sync fail count"
  DAILY_BAR_RESPONSE="$(request "${API_BASE_URL}/api/v1/market-data/daily-bars?canonicalSymbol=${SYMBOL}&fromDate=${START_DATE}&toDate=${END_DATE}&adjustType=${ADJUST_TYPE}&dataSource=LONGPORT&page=1&size=5")"
  assert_contains "${DAILY_BAR_RESPONSE}" 'HTTP:200' "daily bar query http"
  assert_contains "${DAILY_BAR_RESPONSE}" '"success":true' "daily bar query success"
  assert_contains "${DAILY_BAR_RESPONSE}" "\"canonicalSymbol\":\"${SYMBOL}\"" "daily bar query symbol"
  assert_contains "${DAILY_BAR_RESPONSE}" '"dataSource":"LONGPORT"' "daily bar query data source"
  echo "[qta] daily bar sync verification passed for ${SYMBOL} ${START_DATE}..${END_DATE} ${ADJUST_TYPE}"
else
  echo "[qta] daily bar sync skipped because QTA_VERIFY_RUN_DAILY_BAR_SYNC=${RUN_DAILY_BAR_SYNC}"
fi

echo "[qta] real LongPort verification passed"
