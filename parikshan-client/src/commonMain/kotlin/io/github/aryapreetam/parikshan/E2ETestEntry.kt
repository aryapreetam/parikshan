package io.github.aryapreetam.parikshan

/**
 * Primary entrypoint for executing a Parikshan end-to-end (E2E) test scenario.
 *
 * This function orchestrates the complete test lifecycle—connecting to the application process,
 * executing intent-based UI commands within [E2ETestScope], managing automatic retries and video/screenshot
 * artifacts, and closing communication channels upon completion.
 *
 * ### Example Usage
 * ```kotlin
 * @Test
 * fun testLoginFlow() = e2eTest {
 *   // Write intent-based test actions inside E2ETestScope
 *   input("Username", "john.doe")
 *   input("Password", "SecretPass123!")
 *   click("Sign In")
 *   assertVisible("dashboard_header")
 * }
 * ```
 *
 * @param config Optional [E2ETestConfig] to customize timeouts, screenshot behavior, and command delays.
 * @param block The suspendable test scenario logic executed within [E2ETestScope].
 * @see E2ETestScope
 * @see E2ETestConfig
 * @see E2ETestLifecycle
 */
expect fun e2eTest(
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2ETestScope.() -> Unit
)

/**
 * Extension entrypoint for test classes implementing [E2ETestLifecycle].
 *
 * Automatically invokes [E2ETestLifecycle.beforeEach] before executing the test scenario block,
 * and guarantees invocation of [E2ETestLifecycle.afterEach] upon completion (success or failure).
 *
 * ### Example Usage
 * ```kotlin
 * class StorefrontTest : E2ETestLifecycle {
 *   override suspend fun E2ETestScope.beforeEach() {
 *     navigateToSection("storefront")
 *   }
 *
 *   @Test
 *   fun testCheckout() = e2eTest {
 *     click("Add to Cart")
 *     assertVisible("cart_badge")
 *   }
 * }
 * ```
 *
 * @param config Optional [E2ETestConfig] to customize timeouts, screenshot behavior, and command delays.
 * @param block The suspendable test scenario logic executed within [E2ETestScope].
 * @see E2ETestLifecycle
 * @see E2ETestScope
 */
fun E2ETestLifecycle.e2eTest(
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2ETestScope.() -> Unit
) {
  io.github.aryapreetam.parikshan.e2eTest(config) {
    beforeEach()
    try {
      block()
    } finally {
      afterEach()
    }
  }
}
