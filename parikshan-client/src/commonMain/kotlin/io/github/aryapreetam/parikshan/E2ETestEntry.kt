package io.github.aryapreetam.parikshan

/**
 * The primary entry point for writing End-to-End tests in Parikshan.
 * 
 * This function orchestrates the lifecycle of your application under test,
 * including installation, launch, and video recording.
 * 
 * @param config Optional configuration for the test execution.
 * @param block The test scenario logic, executed within the [E2ETestScope].
 */
expect fun e2eTest(
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2ETestScope.() -> Unit
)
