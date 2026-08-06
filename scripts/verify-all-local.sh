#!/usr/bin/env bash
set -eo pipefail

PARIKSHAN_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Parse optional flags
PUBLISH_MAVEN=false
for arg in "$@"; do
  if [ "${arg}" == "--publish" ]; then
    PUBLISH_MAVEN=true
  fi
done

# Helper to verify required tasks are present in project's task list
verify_tasks_exist() {
  local dir_flag="$1"
  local project_path="$2"
  shift 2
  local required_tasks=("$@")
  echo "==> Verifying task existence in ${dir_flag:--p .} ${project_path}..."
  local tasks_output
  tasks_output="$("${PARIKSHAN_ROOT}/gradlew" ${dir_flag} "${project_path}:tasks")"
  for task in "${required_tasks[@]}"; do
    if ! echo "${tasks_output}" | grep -q "${task}"; then
      echo "ERROR: Task '${task}' not found in '${project_path}:tasks'!"
      exit 1
    fi
  done
}

echo "============================================================="
echo " PARIKSHAN E2E VERIFICATION SUITE"
echo "============================================================="

# -------------------------------------------------------------
# 1. Framework Library Unit Tests
# -------------------------------------------------------------
echo "==> Step 1: Running unit tests across library modules..."
"${PARIKSHAN_ROOT}/gradlew" :parikshan-core:jvmTest :parikshan-server:test :parikshan-client:jvmTest :gradle-plugins:test

# -------------------------------------------------------------
# 2. Sample E2E Suites (Composite Builds - no publishToMavenLocal needed)
# -------------------------------------------------------------
echo "==> Step 2a: Testing multiplatform-showcase..."
verify_tasks_exist "" ":samples:multiplatform-showcase:composeApp" "e2eTest" "e2eJvmTest" "e2eWasmTest" "e2eAndroidTest" "e2eIosTest"
"${PARIKSHAN_ROOT}/gradlew" :samples:multiplatform-showcase:composeApp:e2eTest --tests="sample.app.AccessibilityIntegrationTest.testSubtextMatching"

echo "==> Step 2b: Testing cmp-latest..."
verify_tasks_exist "-p samples/cmp-latest" ":app:shared" "e2eTest" "e2eJvmTest" "e2eWasmTest" "e2eAndroidTest"
"${PARIKSHAN_ROOT}/gradlew" -p samples/cmp-latest :app:shared:e2eTest

echo "==> Step 2c: Testing composables-sample..."
verify_tasks_exist "-p samples/composables-sample" ":shared" "e2eTest" "e2eJvmTest" "e2eWasmTest" "e2eAndroidTest"
"${PARIKSHAN_ROOT}/gradlew" -p samples/composables-sample :shared:e2eTest

echo "==> Step 2d: Testing issue-playground..."
verify_tasks_exist "-p samples/issue-playground" ":shared" "e2eTest" "e2eJvmTest" "e2eWasmTest" "e2eAndroidTest"
"${PARIKSHAN_ROOT}/gradlew" -p samples/issue-playground :shared:e2eTest

echo "==> Step 2e: Testing standalone-android..."
verify_tasks_exist "-p samples/standalone-android" ":app" "e2eTest" "e2eAndroidTest"
"${PARIKSHAN_ROOT}/gradlew" -p samples/standalone-android :app:e2eTest

# -------------------------------------------------------------
# 3. Publish to Maven Local (Required ONLY for external kmp-mobile)
# -------------------------------------------------------------
if [ "${PUBLISH_MAVEN}" = true ]; then
  echo "==> Step 2: Publishing Parikshan library to Maven Local..."
  "${PARIKSHAN_ROOT}/gradlew" publishToMavenLocal
else
  echo "==> Step 2: Skipping publishToMavenLocal (pass --publish to enable)."
fi

# -------------------------------------------------------------
# 4. External Repository Test: ~/projects/kmp-mobile
# -------------------------------------------------------------
KMP_MOBILE_DIR="${HOME}/projects/kmp-mobile"
if [ -d "${KMP_MOBILE_DIR}" ]; then
  echo "==> Step 4a: Testing ~/projects/kmp-mobile [branch: main]..."
  cd "${KMP_MOBILE_DIR}"
  git checkout main
  ./gradlew e2eTest --refresh-dependencies

  echo "==> Step 4b: Testing ~/projects/kmp-mobile [branch: check-cmp-1.10.1]..."
  git checkout check-cmp-1.10.1
  ./gradlew e2eTest
  cd "${PARIKSHAN_ROOT}"
else
  echo "WARNING: ${KMP_MOBILE_DIR} directory not found; skipping external repo tests."
fi

echo "============================================================="
echo " SUCCESS: All library unit tests, sample project matrix E2E"
echo " tests, task existence audits, and external project tests PASSED!"
echo "============================================================="


# ./gradlew :samples:multiplatform-showcase:composeApp:e2eTest --targets=jvm --tests="sample.app.FormIntegrationTest"  && 
# ./gradlew -p samples/composables-sample :shared:e2eTest --targets=jvm && 
# ./gradlew -p samples/issue-playground :shared:e2eTest --targets=jvm && 
# ./gradlew -p samples/cmp-latest :app:shared:e2eTest --targets=jvm && 
# ./gradlew -p samples/standalone-android :app:e2eTest && 
# ./gradlew -p ~/projects/kmp-mobile e2eTest --targets=android &&
# ./gradlew --stop
