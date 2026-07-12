#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FAKE_JAR="${ROOT_DIR}/runtime-libs/qta-fake-longport-sdk.jar"
TMP_DIR=""

cleanup() {
  local exit_code=$?

  if [[ -n "${TMP_DIR}" && -d "${TMP_DIR}" ]]; then
    rm -rf "${TMP_DIR}"
  fi
  if [[ -f "${FAKE_JAR}" ]]; then
    rm -f "${FAKE_JAR}"
  fi

  echo "[qta] restoring default LongPort disabled app container"
  (
    cd "${ROOT_DIR}"
    docker compose up -d --no-build --force-recreate app >/dev/null
  ) || true

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

wait_for_health() {
  local response=""
  for _ in {1..40}; do
    response="$(curl -fsS http://localhost:8080/actuator/health || true)"
    if [[ "${response}" == *'"status":"UP"'* ]]; then
      echo "[qta] health is UP"
      return
    fi
    sleep 1
  done

  echo "[qta] app did not become healthy in time" >&2
  docker compose logs --tail=120 app >&2 || true
  exit 1
}

require_command javac
require_command jar
require_command docker
require_command curl

cd "${ROOT_DIR}"

if [[ -f "${FAKE_JAR}" ]]; then
  echo "[qta] refusing to overwrite existing ${FAKE_JAR}" >&2
  exit 1
fi

mkdir -p runtime-libs
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/qta-fake-longport-sdk.XXXXXX")"
mkdir -p "${TMP_DIR}/classes"

FAKE_SOURCES=()
while IFS= read -r source_file; do
  FAKE_SOURCES+=("${source_file}")
done < <(find src/test/java/com/longport -name '*.java' | sort)
if [[ "${#FAKE_SOURCES[@]}" -eq 0 ]]; then
  echo "[qta] no fake LongPort SDK test sources found under src/test/java/com/longport" >&2
  exit 1
fi

echo "[qta] compiling test-only fake LongPort SDK"
javac -d "${TMP_DIR}/classes" "${FAKE_SOURCES[@]}"
jar cf "${FAKE_JAR}" -C "${TMP_DIR}/classes" .

echo "[qta] starting app with fake LongPort SDK jar and fake credentials"
QTA_LONGPORT_ENABLED=true \
LONGPORT_APP_KEY=fake-app-key \
LONGPORT_APP_SECRET=fake-app-secret \
LONGPORT_ACCESS_TOKEN=fake-access-token \
docker compose up -d --no-build --force-recreate app >/dev/null

wait_for_health

STATUS_RESPONSE="$(curl -fsS http://localhost:8080/api/v1/market-data/providers/LONGPORT/status)"
assert_contains "${STATUS_RESPONSE}" '"configured":true' "provider status configured"
assert_contains "${STATUS_RESPONSE}" '"reachable":true' "provider status reachable"
echo "[qta] provider status reached configured=true / reachable=true"

QUOTE_RESPONSE="$(curl -fsS \
  -H 'Content-Type: application/json' \
  -X POST http://localhost:8080/api/v1/market-data/quotes/latest \
  -d '{"canonicalSymbols":["SH.600519"],"persist":false}')"
assert_contains "${QUOTE_RESPONSE}" '"success":true' "quote success"
assert_contains "${QUOTE_RESPONSE}" '"canonicalSymbol":"SH.600519"' "quote canonical symbol"
assert_contains "${QUOTE_RESPONSE}" '"dataSource":"LONGPORT"' "quote data source"
echo "[qta] quote endpoint reached reflective SDK path with persist=false"

echo "[qta] runtime-libs verification passed"
