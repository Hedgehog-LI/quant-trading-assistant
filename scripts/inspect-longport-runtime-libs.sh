#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIB_DIR="${QTA_LONGPORT_SDK_LIB_DIR:-${ROOT_DIR}/runtime-libs}"
REQUESTED_TARGET="${QTA_LONGPORT_EXPECTED_RUST_TARGET:-}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[qta] missing required command: $1" >&2
    exit 1
  fi
}

target_native_path() {
  case "$1" in
    aarch64-apple-darwin) echo "natives/osx_arm64/liblongport_java.dylib" ;;
    x86_64-apple-darwin) echo "natives/osx_64/liblongport_java.dylib" ;;
    x86_64-unknown-linux-gnu) echo "natives/linux_64/liblongport_java.so" ;;
    aarch64-unknown-linux-gnu) echo "natives/linux_arm64/liblongport_java.so" ;;
    x86_64-pc-windows-msvc) echo "natives/windows_64/longport_java.dll" ;;
    *) echo "[qta] unsupported expected Rust target: $1" >&2; exit 1 ;;
  esac
}

detect_target() {
  local os_name arch_name
  if [[ -n "${REQUESTED_TARGET}" ]]; then
    echo "${REQUESTED_TARGET}"
    return
  fi

  os_name="$(uname -s)"
  arch_name="$(uname -m)"
  case "${os_name}:${arch_name}" in
    Darwin:arm64) echo "aarch64-apple-darwin" ;;
    Darwin:x86_64) echo "x86_64-apple-darwin" ;;
    Linux:x86_64) echo "x86_64-unknown-linux-gnu" ;;
    Linux:aarch64|Linux:arm64) echo "aarch64-unknown-linux-gnu" ;;
    *) echo "[qta] unsupported local platform: ${os_name}/${arch_name}" >&2; exit 1 ;;
  esac
}

jar_contains() {
  local jar_file="$1"
  local entry="$2"
  jar tf "${jar_file}" | grep -Fqx "${entry}"
}

any_jar_contains() {
  local entry="$1"
  local jar_file
  for jar_file in "${JARS[@]}"; do
    if jar_contains "${jar_file}" "${entry}"; then
      return 0
    fi
  done
  return 1
}

any_jar_contains_prefix() {
  local prefix="$1"
  local jar_file
  for jar_file in "${JARS[@]}"; do
    if jar tf "${jar_file}" | grep -Fq "${prefix}"; then
      return 0
    fi
  done
  return 1
}

fail() {
  echo "[qta] $1" >&2
  exit 1
}

require_command jar
require_command grep
require_command find

if [[ ! -d "${LIB_DIR}" ]]; then
  fail "runtime lib directory not found: ${LIB_DIR}"
fi

if [[ -f "${LIB_DIR}/qta-fake-longport-sdk.jar" ]]; then
  fail "test-only fake SDK jar is present; remove ${LIB_DIR}/qta-fake-longport-sdk.jar before real verification"
fi

JARS=()
while IFS= read -r jar_file; do
  JARS+=("${jar_file}")
done < <(find "${LIB_DIR}" -maxdepth 1 -type f -name '*.jar' | sort)

if [[ "${#JARS[@]}" -eq 0 ]]; then
  fail "no jar files found under ${LIB_DIR}; build/download LongPort Java SDK first"
fi

echo "[qta] inspecting runtime libs under ${LIB_DIR}"
for jar_file in "${JARS[@]}"; do
  echo "[qta] jar: $(basename "${jar_file}")"
done

SDK_JARS=()
for jar_file in "${JARS[@]}"; do
  if jar_contains "${jar_file}" "com/longport/Config.class" \
      && jar_contains "${jar_file}" "com/longport/quote/QuoteContext.class"; then
    SDK_JARS+=("${jar_file}")
  fi
done

if [[ "${#SDK_JARS[@]}" -eq 0 ]]; then
  fail "LongPort SDK jar not found; expected com/longport/Config.class and com/longport/quote/QuoteContext.class"
fi
if [[ "${#SDK_JARS[@]}" -gt 1 ]]; then
  fail "multiple candidate LongPort SDK jars found; keep exactly one SDK jar in runtime-libs"
fi

SDK_JAR="${SDK_JARS[0]}"
echo "[qta] SDK jar candidate: $(basename "${SDK_JAR}")"

REQUIRED_SDK_ENTRIES=(
  "com/longport/Config.class"
  "com/longport/quote/QuoteContext.class"
  "com/longport/quote/Period.class"
  "com/longport/quote/AdjustType.class"
  "com/longport/quote/TradeSessions.class"
)

for entry in "${REQUIRED_SDK_ENTRIES[@]}"; do
  if ! jar_contains "${SDK_JAR}" "${entry}"; then
    fail "SDK jar is missing required class: ${entry}"
  fi
done

TARGET="$(detect_target)"
NATIVE_ENTRY="$(target_native_path "${TARGET}")"
if ! jar_contains "${SDK_JAR}" "${NATIVE_ENTRY}"; then
  fail "SDK jar is missing native library for ${TARGET}: ${NATIVE_ENTRY}"
fi

if ! any_jar_contains "com/google/gson/Gson.class"; then
  fail "runtime dependency gson is missing from ${LIB_DIR}"
fi

if ! any_jar_contains "org/scijava/nativelib/NativeLoader.class" \
    && ! any_jar_contains_prefix "org/scijava/nativelib/"; then
  fail "runtime dependency native-lib-loader is missing from ${LIB_DIR}"
fi

echo "[qta] LongPort runtime libs inspection passed for target ${TARGET}"
