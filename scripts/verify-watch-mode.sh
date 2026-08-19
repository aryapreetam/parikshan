#!/usr/bin/env bash
set -eo pipefail

PARIKSHAN_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_FILE="${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/src/commonTest/kotlin/sample/app/AccessibilityIntegrationTest.kt"
LOG_DIR="${PARIKSHAN_ROOT}/build/parikshan/logs"
LOG_FILE="${LOG_DIR}/verify-watch-script.log"

mkdir -p "${LOG_DIR}"
rm -f "${LOG_FILE}"

echo "============================================================="
echo " PARIKSHAN WATCH MODE AUTOMATED VERIFICATION SCRIPT"
echo "============================================================="

# Ensure clean state on exit
WATCH_PID=""
cleanup() {
  echo ""
  echo "==> Cleaning up..."
  if [ -n "${WATCH_PID}" ] && kill -0 "${WATCH_PID}" 2>/dev/null; then
    echo "Stopping background watch process (PID: ${WATCH_PID})..."
    kill -TERM "${WATCH_PID}" 2>/dev/null || true
    wait "${WATCH_PID}" 2>/dev/null || true
  fi
  rm -rf "${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/build/parikshan/active-session.json"
  rm -rf "${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/build/parikshan/sessions"
  if [ -f "${TEST_FILE}" ]; then
    echo "Reverting test file to original state..."
    git checkout -- "${TEST_FILE}" 2>/dev/null || true
  fi
  echo "Cleanup complete."
}
trap cleanup EXIT

# Pre-clean any stale session files before running watch verification
rm -rf "${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/build/parikshan/active-session.json"
rm -rf "${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/build/parikshan/sessions"

ENVIRONMENT="local"
TARGETS_ARG=""
EXTRA_ARGS=()

for arg in "$@"; do
  case "$arg" in
    --environment=ci|--ci)
      ENVIRONMENT="ci"
      ;;
    --environment=local|--local)
      ENVIRONMENT="local"
      ;;
    --targets=*)
      TARGETS_ARG="$arg"
      ;;
    *)
      EXTRA_ARGS+=("$arg")
      ;;
  esac
done

if [ -n "${TARGETS_ARG}" ]; then
  FINAL_TARGETS="${TARGETS_ARG}"
elif [ "${ENVIRONMENT}" == "ci" ] || [ "${CI}" == "true" ]; then
  FINAL_TARGETS="--targets=jvm,wasm"
else
  FINAL_TARGETS=""
fi

# 1. Start Watch Mode Process
echo "==> Step 1: Starting watch mode process (environment: ${ENVIRONMENT}, targets: ${FINAL_TARGETS:-all}, extra args: ${EXTRA_ARGS[*]:-none})..."
"${PARIKSHAN_ROOT}/gradlew" :samples:multiplatform-showcase:composeApp:e2eTest \
  --tests=sample.app.AccessibilityIntegrationTest.testSubtextMatching \
  --watch \
  --window-size=360x720 \
  --layout=side-by-side \
  ${FINAL_TARGETS} \
  ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"} > "${LOG_FILE}" 2>&1 &
WATCH_PID=$!
echo "   Watch process started with PID: ${WATCH_PID}"

# Function to poll log file for expected string
wait_for_log() {
  local pattern="$1"
  local description="$2"
  local timeout="${3:-180}"
  local start_line=0
  if [ -f "${LOG_FILE}" ]; then
    start_line=$(wc -l < "${LOG_FILE}" | tr -d ' ')
  fi
  local elapsed=0

  echo "   Waiting for: ${description} (timeout: ${timeout}s)..."
  until tail -n "+$((start_line + 1))" "${LOG_FILE}" 2>/dev/null | grep -E "${pattern}" >/dev/null 2>&1; do
    if ! kill -0 "${WATCH_PID}" 2>/dev/null; then
      echo "ERROR: Watch process died unexpectedly! Log output:"
      tail -n 30 "${LOG_FILE}"
      exit 1
    fi
    if [ "${elapsed}" -ge "${timeout}" ]; then
      echo "ERROR: Timeout waiting for '${description}'!"
      echo "Last 30 lines of log:"
      tail -n 30 "${LOG_FILE}"
      exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo "   [SUCCESS] Detected: ${description} (in ${elapsed}s)"
}

# 2. Wait for Initial PASS
wait_for_log "Latest: ([1-9][0-9]*|2) PASSED, 0 FAILED" "Initial run PASS status" 900

# 3. Inject Failure into Test File
echo "==> Step 3: Injecting failing assertion on line 68 of test file..."
if [[ "$OSTYPE" == "darwin"* ]]; then
  sed -i '' 's/assertVisible("This is a sample text")/assertVisible("This text does not exist anywhere")/' "${TEST_FILE}"
else
  sed -i 's/assertVisible("This is a sample text")/assertVisible("This text does not exist anywhere")/' "${TEST_FILE}"
fi

# 4. Wait for FAIL Detection
wait_for_log "Latest: 0 PASSED, ([1-9][0-9]*|2) FAILED" "Watch mode FAIL detection" 360

# 5. Revert Test Code
echo "==> Step 5: Reverting test code back to clean state..."
git checkout -- "${TEST_FILE}"

# 6. Wait for Recovery PASS
wait_for_log "Latest: ([1-9][0-9]*|2) PASSED, 0 FAILED" "Watch mode recovery PASS" 360

echo "============================================================="
echo " SUCCESS: Watch mode verification script completed cleanly!"
echo "============================================================="
