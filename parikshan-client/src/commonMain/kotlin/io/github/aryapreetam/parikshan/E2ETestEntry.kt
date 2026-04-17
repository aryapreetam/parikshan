package io.github.aryapreetam.parikshan

expect fun e2eTest(
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2ETestScope.() -> Unit
)
