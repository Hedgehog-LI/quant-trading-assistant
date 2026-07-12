#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

OPENAPI_REPO="${QTA_LONGPORT_OPENAPI_REPO:-https://github.com/longportapp/openapi.git}"
OPENAPI_TAG="${QTA_LONGPORT_OPENAPI_TAG:-v4.3.3}"
BUILD_DIR="${QTA_LONGPORT_OPENAPI_BUILD_DIR:-${TMPDIR:-/tmp}/qta-longport-openapi}"
INSTALL_DIR="${QTA_LONGPORT_SDK_INSTALL_DIR:-${ROOT_DIR}/runtime-libs}"
BUILD_TOOL="${QTA_LONGPORT_JNI_BUILD_TOOL:-cargo}"
REQUESTED_TARGET="${QTA_LONGPORT_RUST_TARGET:-}"
OVERWRITE="${QTA_LONGPORT_SDK_OVERWRITE:-false}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[qta] missing required command: $1" >&2
    exit 1
  fi
}

target_metadata() {
  case "$1" in
    aarch64-apple-darwin) echo "aarch64-apple-darwin|lib|.dylib|osx_arm64|liblongport_java.dylib" ;;
    x86_64-apple-darwin) echo "x86_64-apple-darwin|lib|.dylib|osx_64|liblongport_java.dylib" ;;
    x86_64-unknown-linux-gnu) echo "x86_64-unknown-linux-gnu|lib|.so|linux_64|liblongport_java.so" ;;
    aarch64-unknown-linux-gnu) echo "aarch64-unknown-linux-gnu|lib|.so|linux_arm64|liblongport_java.so" ;;
    x86_64-pc-windows-msvc) echo "x86_64-pc-windows-msvc||.dll|windows_64|longport_java.dll" ;;
    *) echo "[qta] unsupported Rust target: $1" >&2; exit 1 ;;
  esac
}

detect_target() {
  local os_name arch_name
  if [[ -n "${REQUESTED_TARGET}" ]]; then
    target_metadata "${REQUESTED_TARGET}"
    return
  fi

  os_name="$(uname -s)"
  arch_name="$(uname -m)"

  case "${os_name}:${arch_name}" in
    Darwin:arm64) target_metadata "aarch64-apple-darwin" ;;
    Darwin:x86_64) target_metadata "x86_64-apple-darwin" ;;
    Linux:x86_64) target_metadata "x86_64-unknown-linux-gnu" ;;
    Linux:aarch64|Linux:arm64) target_metadata "aarch64-unknown-linux-gnu" ;;
    *) echo "[qta] unsupported platform for local Java JNI build: ${os_name}/${arch_name}" >&2; exit 1 ;;
  esac
}

checkout_openapi() {
  if [[ -d "${BUILD_DIR}/.git" ]]; then
    echo "[qta] updating existing LongPort OpenAPI checkout: ${BUILD_DIR}"
    git -C "${BUILD_DIR}" fetch --tags origin
    git -C "${BUILD_DIR}" checkout "${OPENAPI_TAG}"
    git -C "${BUILD_DIR}" submodule update --init --recursive
    return
  fi

  if [[ -e "${BUILD_DIR}" && "${OVERWRITE}" != "true" ]]; then
    echo "[qta] build dir exists but is not a git checkout: ${BUILD_DIR}" >&2
    echo "[qta] set QTA_LONGPORT_OPENAPI_BUILD_DIR to an empty path and retry" >&2
    exit 1
  fi
  if [[ -e "${BUILD_DIR}" ]]; then
    echo "[qta] refusing to delete existing non-git build dir: ${BUILD_DIR}" >&2
    echo "[qta] choose a different QTA_LONGPORT_OPENAPI_BUILD_DIR" >&2
    exit 1
  fi

  echo "[qta] cloning LongPort OpenAPI ${OPENAPI_TAG} into ${BUILD_DIR}"
  git clone --recurse-submodules --branch "${OPENAPI_TAG}" --depth 1 "${OPENAPI_REPO}" "${BUILD_DIR}"
}

copy_if_absent_or_overwrite() {
  local source_file="$1"
  local target_file="$2"
  if [[ -e "${target_file}" && "${OVERWRITE}" != "true" ]]; then
    echo "[qta] target already exists: ${target_file}" >&2
    echo "[qta] set QTA_LONGPORT_SDK_OVERWRITE=true to overwrite existing SDK/dependency jars" >&2
    exit 1
  fi
  cp "${source_file}" "${target_file}"
}

require_command git
require_command cargo
require_command mvn

IFS='|' read -r TARGET LIB_PREFIX LIB_SUFFIX NATIVE_DIR NATIVE_FILE_NAME <<< "$(detect_target)"
if [[ "${BUILD_TOOL}" == "cross" ]]; then
  require_command cross
elif [[ "${BUILD_TOOL}" != "cargo" ]]; then
  echo "[qta] QTA_LONGPORT_JNI_BUILD_TOOL must be cargo or cross" >&2
  exit 1
fi

checkout_openapi
cd "${BUILD_DIR}"

echo "[qta] building LongPort Java JNI with ${BUILD_TOOL}: target=${TARGET}"
"${BUILD_TOOL}" build -p longport-java --release --target "${TARGET}"

NATIVE_SOURCE="target/${TARGET}/release/${LIB_PREFIX}longport_java${LIB_SUFFIX}"
if [[ ! -f "${NATIVE_SOURCE}" ]]; then
  echo "[qta] JNI build output not found: ${NATIVE_SOURCE}" >&2
  exit 1
fi

mkdir -p "java/javasrc/target/natives/${NATIVE_DIR}"
cp "${NATIVE_SOURCE}" "java/javasrc/target/natives/${NATIVE_DIR}/${NATIVE_FILE_NAME}"

PACKAGE_VERSION="${QTA_LONGPORT_PACKAGE_VERSION:-}"
if [[ -z "${PACKAGE_VERSION}" ]]; then
  PACKAGE_VERSION="$(sed -nE 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*"(.*?)".*/\1/p' Cargo.toml | head -n 1)"
fi
if [[ -z "${PACKAGE_VERSION}" ]]; then
  echo "[qta] unable to infer LongPort package version from Cargo.toml" >&2
  exit 1
fi

echo "[qta] packaging Java SDK version ${PACKAGE_VERSION}"
(
  cd java/javasrc
  mvn -q versions:set -DnewVersion="${PACKAGE_VERSION}"
  mvn -q -DskipTests package
  mvn -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/runtime-deps
)

SDK_JAR="$(find java/javasrc/target -maxdepth 1 -type f -name 'openapi-sdk-*.jar' \
  ! -name '*sources*' ! -name '*javadoc*' | sort | tail -n 1)"
if [[ -z "${SDK_JAR}" || ! -f "${SDK_JAR}" ]]; then
  echo "[qta] packaged SDK jar not found under java/javasrc/target" >&2
  exit 1
fi

mkdir -p "${INSTALL_DIR}"
INSTALL_SDK_JAR="${INSTALL_DIR}/$(basename "${SDK_JAR}")"
copy_if_absent_or_overwrite "${SDK_JAR}" "${INSTALL_SDK_JAR}"
echo "[qta] installed SDK jar: ${INSTALL_SDK_JAR}"

if [[ -d java/javasrc/target/runtime-deps ]]; then
  while IFS= read -r dependency_jar; do
    target_dependency="${INSTALL_DIR}/$(basename "${dependency_jar}")"
    copy_if_absent_or_overwrite "${dependency_jar}" "${target_dependency}"
    echo "[qta] installed runtime dependency: ${target_dependency}"
  done < <(find java/javasrc/target/runtime-deps -maxdepth 1 -type f -name '*.jar' | sort)
fi

echo "[qta] LongPort Java SDK build/install completed"
echo "[qta] next: run scripts/inspect-longport-runtime-libs.sh, configure LONGPORT_* credentials, rebuild app, then run scripts/verify-longport-real-sync.sh"
