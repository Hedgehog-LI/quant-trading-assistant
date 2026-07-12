#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_BASE_URL="${QTA_LONGPORT_READINESS_API_BASE_URL:-http://localhost:8080}"
CHECK_OFFICIAL_CONTRACT="${QTA_LONGPORT_READINESS_CHECK_OFFICIAL_CONTRACT:-false}"
REQUIRE_OFFICIAL_CONTRACT="${QTA_LONGPORT_REQUIRE_OFFICIAL_CONTRACT:-false}"
CHECK_RUNTIME_LIBS="${QTA_LONGPORT_READINESS_CHECK_RUNTIME_LIBS:-true}"
CHECK_PROVIDER_STATUS="${QTA_LONGPORT_READINESS_CHECK_PROVIDER_STATUS:-true}"
REQUIRE_RUNNING_APP="${QTA_LONGPORT_REQUIRE_RUNNING_APP:-false}"
REQUIRE_PROVIDER_READY="${QTA_LONGPORT_REQUIRE_PROVIDER_READY:-false}"

ERROR_COUNT=0
WARN_COUNT=0

info() {
  echo "[qta] $1"
}

ok() {
  echo "[qta] ok: $1"
}

warn() {
  echo "[qta] warn: $1" >&2
  WARN_COUNT=$((WARN_COUNT + 1))
}

error() {
  echo "[qta] error: $1" >&2
  ERROR_COUNT=$((ERROR_COUNT + 1))
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    error "missing required command: $1"
    return 1
  fi
  return 0
}

dotenv_value() {
  local name="$1"
  if [[ ! -f "${ROOT_DIR}/.env" ]]; then
    return 0
  fi
  awk -F= -v key="${name}" '
    $1 == key {
      sub(/^[^=]*=/, "")
      gsub(/^[[:space:]]+|[[:space:]]+$/, "")
      gsub(/^"|"$/, "")
      gsub(/^'\''|'\''$/, "")
      print
      exit
    }
  ' "${ROOT_DIR}/.env"
}

credential_value() {
  local name="$1"
  local value="${!name:-}"
  if [[ -n "${value}" ]]; then
    printf '%s' "${value}"
    return
  fi
  dotenv_value "${name}"
}

is_placeholder_value() {
  local value="$1"
  local lower_value
  lower_value="$(printf '%s' "${value}" | tr '[:upper:]' '[:lower:]')"

  [[ -z "${value}" ]] \
    || [[ "${value}" == "***" ]] \
    || [[ "${lower_value}" == "changeme" ]] \
    || [[ "${lower_value}" == "change-me" ]] \
    || [[ "${lower_value}" == "your_"* ]] \
    || [[ "${lower_value}" == "your-"* ]] \
    || [[ "${lower_value}" == *"placeholder"* ]]
}

check_credentials() {
  local credential_name value missing_count
  missing_count=0

  for credential_name in LONGPORT_APP_KEY LONGPORT_APP_SECRET LONGPORT_ACCESS_TOKEN; do
    value="$(credential_value "${credential_name}")"
    if is_placeholder_value "${value}"; then
      error "${credential_name} is missing or still uses a placeholder; set it in shell env or .env"
      missing_count=$((missing_count + 1))
    fi
  done

  if [[ "${missing_count}" -eq 0 ]]; then
    ok "LongPort credentials are present; values are intentionally not printed"
  fi
}

check_enabled_flag() {
  local enabled_value
  enabled_value="$(credential_value QTA_LONGPORT_ENABLED)"
  if [[ "${enabled_value}" == "true" ]]; then
    ok "QTA_LONGPORT_ENABLED=true"
  else
    warn "QTA_LONGPORT_ENABLED is not true; real LongPort provider will stay disabled until enabled"
  fi
}

check_runtime_libs() {
  if [[ "${CHECK_RUNTIME_LIBS}" != "true" ]]; then
    warn "runtime-libs inspection skipped because QTA_LONGPORT_READINESS_CHECK_RUNTIME_LIBS=${CHECK_RUNTIME_LIBS}"
    return
  fi

  if ! require_command jar; then
    return
  fi

  if bash "${ROOT_DIR}/scripts/inspect-longport-runtime-libs.sh"; then
    ok "LongPort SDK runtime libs passed offline inspection"
  else
    error "LongPort SDK runtime libs inspection failed"
  fi
}

check_official_contract() {
  if [[ "${CHECK_OFFICIAL_CONTRACT}" != "true" ]]; then
    warn "official source contract check skipped; set QTA_LONGPORT_READINESS_CHECK_OFFICIAL_CONTRACT=true to enable"
    return
  fi

  if ! require_command curl; then
    return
  fi

  if bash "${ROOT_DIR}/scripts/check-longport-official-java-contract.sh"; then
    ok "official LongPort Java source contract check passed"
  elif [[ "${REQUIRE_OFFICIAL_CONTRACT}" == "true" ]]; then
    error "official LongPort Java source contract check failed"
  else
    warn "official LongPort Java source contract check failed; this is often network/proxy related, not necessarily a code mismatch"
  fi
}

check_provider_status() {
  local status_response curl_exit_code

  if [[ "${CHECK_PROVIDER_STATUS}" != "true" ]]; then
    warn "provider status check skipped because QTA_LONGPORT_READINESS_CHECK_PROVIDER_STATUS=${CHECK_PROVIDER_STATUS}"
    return
  fi

  if ! require_command curl; then
    return
  fi

  set +e
  status_response="$(curl -fsS --max-time 5 "${API_BASE_URL}/api/v1/market-data/providers/LONGPORT/status" 2>&1)"
  curl_exit_code=$?
  set -e

  if [[ "${curl_exit_code}" -ne 0 ]]; then
    if [[ "${REQUIRE_RUNNING_APP}" == "true" ]]; then
      error "provider status endpoint is not reachable at ${API_BASE_URL}; start the backend app first"
    else
      warn "provider status endpoint is not reachable at ${API_BASE_URL}; start the backend app for runtime status"
    fi
    return
  fi

  if [[ "${status_response}" == *'"configured":true'* && "${status_response}" == *'"reachable":true'* ]]; then
    ok "LongPort provider status is configured=true and reachable=true"
  elif [[ "${REQUIRE_PROVIDER_READY}" == "true" ]]; then
    error "LongPort provider is not ready according to status endpoint: ${status_response}"
  else
    warn "LongPort provider is not ready yet according to status endpoint: ${status_response}"
  fi
}

cd "${ROOT_DIR}"

info "checking LongPort single-symbol sync readiness"
check_credentials
check_enabled_flag
check_runtime_libs
check_official_contract
check_provider_status

if [[ "${ERROR_COUNT}" -gt 0 ]]; then
  info "readiness check failed: errors=${ERROR_COUNT}, warnings=${WARN_COUNT}"
  exit 1
fi

info "readiness check finished: errors=0, warnings=${WARN_COUNT}"
