#!/usr/bin/env bash
set -euo pipefail

OPENAPI_TAG="${QTA_LONGPORT_OPENAPI_TAG:-v4.3.3}"
BASE_URL="${QTA_LONGPORT_OPENAPI_RAW_BASE_URL:-https://raw.githubusercontent.com/longportapp/openapi/${OPENAPI_TAG}/java/javasrc/src/main/java}"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/qta-longport-contract.XXXXXX")"
CURL_DIRECT_FALLBACK="${QTA_LONGPORT_CONTRACT_CURL_DIRECT_FALLBACK:-true}"

cleanup() {
  if [[ -d "${TMP_DIR}" ]]; then
    rm -rf "${TMP_DIR}"
  fi
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[qta] missing required command: $1" >&2
    exit 1
  fi
}

download() {
  local relative_path="$1"
  local output_file="${TMP_DIR}/$(basename "${relative_path}")"
  local url="${BASE_URL}/${relative_path}"
  if ! curl -fsSL "${url}" -o "${output_file}"; then
    if [[ "${CURL_DIRECT_FALLBACK}" == "true" ]]; then
      echo "[qta] curl via current proxy/env failed, retrying direct: ${relative_path}" >&2
      if ! curl --noproxy '*' -fsSL "${url}" -o "${output_file}"; then
        echo "[qta] failed to download official source: ${url}" >&2
        return 1
      fi
    else
      return 1
    fi
  fi
  if [[ ! -s "${output_file}" ]]; then
    echo "[qta] downloaded source is empty: ${url}" >&2
    return 1
  fi
  echo "${output_file}"
}

assert_contains() {
  local file="$1"
  local needle="$2"
  local label="$3"
  if ! grep -Fq "${needle}" "${file}"; then
    echo "[qta] official contract check failed: ${label}" >&2
    echo "[qta] file: ${file}" >&2
    echo "[qta] expected to contain: ${needle}" >&2
    exit 1
  fi
}

require_command curl
require_command grep
require_command mktemp

CONFIG_FILE="$(download com/longport/Config.java)"
QUOTE_CONTEXT_FILE="$(download com/longport/quote/QuoteContext.java)"
SECURITY_QUOTE_FILE="$(download com/longport/quote/SecurityQuote.java)"
CANDLESTICK_FILE="$(download com/longport/quote/Candlestick.java)"
PERIOD_FILE="$(download com/longport/quote/Period.java)"
ADJUST_TYPE_FILE="$(download com/longport/quote/AdjustType.java)"
TRADE_SESSIONS_FILE="$(download com/longport/quote/TradeSessions.java)"

assert_contains "${CONFIG_FILE}" "public static Config fromApikey(String appKey, String appSecret, String accessToken)" "Config.fromApikey"
assert_contains "${CONFIG_FILE}" "public static Config fromApikeyEnv()" "Config.fromApikeyEnv"
assert_contains "${CONFIG_FILE}" "public Config httpUrl(String httpUrl)" "Config.httpUrl (domain override, used because SDK default domain openapi.longport.cn is deprecated)"
assert_contains "${CONFIG_FILE}" "public Config quoteWebsocketUrl(String quoteWebsocketUrl)" "Config.quoteWebsocketUrl (quote ws domain override, paired with httpUrl)"
assert_contains "${QUOTE_CONTEXT_FILE}" "public static QuoteContext create(Config config)" "QuoteContext.create"
assert_contains "${QUOTE_CONTEXT_FILE}" "public CompletableFuture<String> getQuoteLevel()" "QuoteContext.getQuoteLevel"
assert_contains "${QUOTE_CONTEXT_FILE}" "public CompletableFuture<SecurityQuote[]> getQuote(String[] symbols)" "QuoteContext.getQuote"
assert_contains "${QUOTE_CONTEXT_FILE}" "public CompletableFuture<Candlestick[]> getHistoryCandlesticksByDate(String symbol, Period period," "QuoteContext.getHistoryCandlesticksByDate first line"
assert_contains "${QUOTE_CONTEXT_FILE}" "AdjustType adjustType, LocalDate start, LocalDate end, TradeSessions tradeSessions)" "QuoteContext.getHistoryCandlesticksByDate parameters"

for getter in getSymbol getLastDone getPrevClose getOpen getHigh getLow getTimestamp getVolume getTurnover getTradeStatus; do
  assert_contains "${SECURITY_QUOTE_FILE}" " ${getter}()" "SecurityQuote.${getter}"
done

for getter in getClose getOpen getLow getHigh getVolume getTurnover getTimestamp; do
  assert_contains "${CANDLESTICK_FILE}" " ${getter}()" "Candlestick.${getter}"
done

assert_contains "${PERIOD_FILE}" "Day," "Period.Day"
assert_contains "${ADJUST_TYPE_FILE}" "NoAdjust," "AdjustType.NoAdjust"
assert_contains "${ADJUST_TYPE_FILE}" "ForwardAdjust," "AdjustType.ForwardAdjust"
assert_contains "${TRADE_SESSIONS_FILE}" "All," "TradeSessions.All"

echo "[qta] LongPort official Java contract check passed for ${OPENAPI_TAG}"
