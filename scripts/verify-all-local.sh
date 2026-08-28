#!/usr/bin/env bash
set -eo pipefail

PARIKSHAN_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
START_TIME=$(date +%s)

format_duration() {
  local elapsed=$(($(date +%s) - START_TIME))
  local mins=$((elapsed / 60))
  local secs=$((elapsed % 60))
  echo "${mins}m ${secs}s (${elapsed} seconds)"
}

# Parse optional flags
PUBLISH_MAVEN=false
ENABLE_VIDEO=false
for arg in "$@"; do
  if [ "${arg}" == "--publish" ]; then
    PUBLISH_MAVEN=true
  elif [ "${arg}" == "--video" ] || [ "${arg}" == "video" ]; then
    ENABLE_VIDEO=true
  fi
done

# Helper to run gradlew commands
run_gradle() {
  echo "==> Executing: gradlew $*..."
  "${PARIKSHAN_ROOT}/gradlew" "$@"
}

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

# Helper to verify recorded video artifact existence and report linking
verify_video_recorded() {
  local video_path="$1"
  local report_path="$2"
  echo "==> Verifying video generated at: ${video_path}..."
  if [ ! -f "${video_path}" ]; then
    echo "ERROR: Video file '${video_path}' was not generated!"
    exit 1
  fi
  local file_size
  file_size=$(wc -c < "${video_path}" | tr -d ' ')
  if [ "${file_size}" -lt 1000 ]; then
    echo "ERROR: Video file '${video_path}' is corrupted or empty (${file_size} bytes)!"
    exit 1
  fi
  if [ -n "${report_path}" ] && [ -f "${report_path}" ]; then
    if ! grep -Eq "\.(mp4|webm)" "${report_path}"; then
      echo "ERROR: Video not referenced in HTML report '${report_path}'!"
      exit 1
    fi
  fi
  echo "✅ Valid video verified (${file_size} bytes) and linked in report."
}

echo "============================================================="
echo " PARIKSHAN E2E VERIFICATION SUITE"
if [ "${ENABLE_VIDEO}" = true ]; then
  echo " Mode: Video Recording ENABLED"
fi
echo "============================================================="

# -------------------------------------------------------------
# 1. Framework Library Unit Tests
# -------------------------------------------------------------
echo "==> Step 1: Running unit tests across library modules..."
run_gradle :parikshan-core:jvmTest :parikshan-server:test :parikshan-client:jvmTest :gradle-plugins:test --parallel   

# -------------------------------------------------------------
# 2. Sample E2E Suites (Composite Builds - no publishToMavenLocal needed)
# -------------------------------------------------------------
echo "==> Step 2a: Testing multiplatform-showcase..."
verify_tasks_exist "" ":samples:multiplatform-showcase:composeApp" "e2eTest" "e2eJvmTest" "e2eWasmTest" "e2eAndroidTest" "e2eIosTest"

video_jvm_opt=()
if [ "${ENABLE_VIDEO}" = true ]; then
  video_jvm_opt=("-Dparikshan.video.enabled=true")
fi

echo "==> Verifying if target specific tasks work..."
run_gradle :samples:multiplatform-showcase:composeApp:e2eJvmTest --tests="sample.app.AccessibilityIntegrationTest.testSubtextMatching" "${video_jvm_opt[@]}"
if [ "${ENABLE_VIDEO}" = true ]; then
  verify_video_recorded "${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/build/parikshan/videos/jvm/AccessibilityIntegrationTest.mp4" \
    "${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/build/reports/tests/e2eTest/jvm/index.html"
fi

run_gradle :samples:multiplatform-showcase:composeApp:e2eWasmTest --tests="sample.app.AccessibilityIntegrationTest.testSubtextMatching" "${video_jvm_opt[@]}"
if [ "${ENABLE_VIDEO}" = true ]; then
  verify_video_recorded "${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/build/parikshan/videos/wasm/AccessibilityIntegrationTest.webm" \
    "${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/build/reports/tests/e2eTest/wasm/index.html"
fi

run_gradle :samples:multiplatform-showcase:composeApp:e2eAndroidTest --tests="sample.app.AccessibilityIntegrationTest.testSubtextMatching" "${video_jvm_opt[@]}"
if [ "${ENABLE_VIDEO}" = true ]; then
  verify_video_recorded "${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/build/parikshan/videos/android/AccessibilityIntegrationTest.mp4" \
    "${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/build/reports/tests/e2eTest/android/index.html"
fi

run_gradle :samples:multiplatform-showcase:composeApp:e2eIosTest --tests="sample.app.AccessibilityIntegrationTest.testSubtextMatching" "${video_jvm_opt[@]}"
if [ "${ENABLE_VIDEO}" = true ]; then
  verify_video_recorded "${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/build/parikshan/videos/ios/AccessibilityIntegrationTest.mp4" \
    "${PARIKSHAN_ROOT}/samples/multiplatform-showcase/composeApp/build/reports/tests/e2eTest/ios/index.html"
fi

echo "==> Running all tests for all target...(verify e2eTest)"
run_gradle :samples:multiplatform-showcase:composeApp:e2eTest
echo "==> Verifying sync mode..."
run_gradle :samples:multiplatform-showcase:composeApp:e2eTest --tests="sample.app.FormIntegrationTest.testSliderDrag" --sync
echo "==> Verifying watch mode..."
"${PARIKSHAN_ROOT}/scripts/verify-watch-mode.sh"
echo "==> Verifying watch mode with sync enabled..."
"${PARIKSHAN_ROOT}/scripts/verify-watch-mode.sh" --sync

run_gradle --stop

echo "==> Step 2b: Testing cmp-latest..."
verify_tasks_exist "-p samples/cmp-latest" ":app:shared" "e2eTest" "e2eJvmTest" "e2eWasmTest" "e2eAndroidTest"
run_gradle -p samples/cmp-latest :app:shared:e2eTest

echo "==> Step 2c: Testing composables-sample..."
verify_tasks_exist "-p samples/composables-sample" ":shared" "e2eTest" "e2eJvmTest" "e2eWasmTest" "e2eAndroidTest"
run_gradle -p samples/composables-sample :shared:e2eTest

echo "==> Step 2d: Testing issue-playground..."
verify_tasks_exist "-p samples/issue-playground" ":shared" "e2eTest" "e2eJvmTest" "e2eWasmTest" "e2eAndroidTest"
run_gradle -p samples/issue-playground :shared:e2eTest

run_gradle "-p samples/cmp-latest" --stop
run_gradle "-p samples/composables-sample" --stop
run_gradle "-p samples/issue-playground" --stop

echo "==> Step 2e: Testing standalone-android..."
verify_tasks_exist "-p samples/standalone-android" ":app" "e2eTest" "e2eAndroidTest"

standalone_video_opt=()
if [ "${ENABLE_VIDEO}" = true ]; then
  standalone_video_opt=("--video")
fi

run_gradle -p samples/standalone-android :app:e2eTest "${standalone_video_opt[@]}"
if [ "${ENABLE_VIDEO}" = true ]; then
  verify_video_recorded "${PARIKSHAN_ROOT}/samples/standalone-android/app/build/parikshan/videos/android/CalculatorE2ETest.mp4" \
    "${PARIKSHAN_ROOT}/samples/standalone-android/app/build/reports/tests/e2eTest/index.html"
fi

run_gradle "-p samples/standalone-android" --stop

# -------------------------------------------------------------
# 3. Publish to Maven Local (Required ONLY for external kmp-mobile)
# -------------------------------------------------------------
if [ "${PUBLISH_MAVEN}" = true ]; then
  echo "==> Step 3: Publishing Parikshan library to Maven Local..."
  run_gradle publishToMavenLocal
else
  echo "==> Step 3: Skipping publishToMavenLocal (pass --publish to enable)."
fi

# -------------------------------------------------------------                                                                                                             
# 4. External Repository Test: ~/projects/kmp-mobile                                                                                                                        
# -------------------------------------------------------------                                                                                                             
KMP_MOBILE_SCRIPT="${HOME}/projects/kmp-mobile/scripts/verify-all-cmp.sh"
if [ -x "${KMP_MOBILE_SCRIPT}" ] || [ -f "${KMP_MOBILE_SCRIPT}" ]; then
  echo "==> Step 4: Running external tests for ~/projects/kmp-mobile..."
  if [ "${ENABLE_VIDEO}" = true ]; then
    bash "${KMP_MOBILE_SCRIPT}" --video
    verify_video_recorded "${HOME}/projects/kmp-mobile/shared/build/parikshan/videos/ios/AppTest.mp4" \
      "${HOME}/projects/kmp-mobile/shared/build/reports/tests/e2eTest/index.html"
  else
    bash "${KMP_MOBILE_SCRIPT}"
  fi
fi
                                                                                                                                                                  
pkill -f '.*org.gradle.launcher.daemon.bootstrap.GradleDaemon.*' || true

echo "============================================================="
echo " SUCCESS: All library unit tests, sample project matrix E2E"
echo " tests, task existence audits, and external project tests PASSED!"
echo " Total Execution Time: $(format_duration)"
echo "============================================================="
