package io.github.aryapreetam.parikshan

actual fun e2eTest(
  config: E2ETestConfig,
  block: suspend E2ETestScope.() -> Unit
) {
  error("Parikshan E2E tests run via the JVM test runner. Use ./gradlew e2eIosTest")
}

actual fun e2eTest(
  instances: List<String>,
  config: E2ETestConfig,
  block: suspend E2EMultiInstanceScope.(E2EMultiInstanceScope) -> Unit
) {
  error("Parikshan multi-instance E2E tests run via the JVM test runner. Use ./gradlew e2eIosTest")
}
