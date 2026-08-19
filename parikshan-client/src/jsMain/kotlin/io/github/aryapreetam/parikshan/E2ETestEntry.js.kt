package io.github.aryapreetam.parikshan

/**
 * Note: Legacy Kotlin/JS (`js`) is not supported for Parikshan E2E test execution.
 * Web-based E2E tests must target WebAssembly (`wasmJs`).
 */
actual fun e2eTest(
  config: E2ETestConfig,
  block: suspend E2ETestScope.() -> Unit
) {
  error("Parikshan E2E tests run via the JVM test runner. Web testing is supported exclusively on Wasm (wasmJs). Use ./gradlew e2eTest")
}
