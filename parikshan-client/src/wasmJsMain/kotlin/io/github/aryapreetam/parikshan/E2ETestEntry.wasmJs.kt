package io.github.aryapreetam.parikshan

actual fun e2eTest(
  config: E2ETestConfig,
  block: suspend E2ETestScope.() -> Unit
) {
  error("Parikshan E2E tests run via the JVM test runner. Use ./gradlew e2eWasmTest")
}
