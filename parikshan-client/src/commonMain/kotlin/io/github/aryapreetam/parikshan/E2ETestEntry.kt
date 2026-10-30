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
 * @param config Optional [E2ETestConfig] to customize timeouts, screenshot behavior, and step delays.
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
 * Executes a multi-instance end-to-end test across named application actors.
 *
 * Each instance corresponds to an isolated application process, browser context,
 * or device session, identified by a unique name (such as `"alice"` or `"bob"`).
 *
 * ### Example Usage
 * ```kotlin
 * @Test
 * fun testCollaboration() = e2eTest(instances = listOf("alice", "bob")) {
 *   val alice = instance("alice")
 *   val bob = instance("bob")
 *
 *   alice.click("create_document")
 *   bob.click("join_document")
 * }
 * ```
 *
 * @param instances List of unique instance identifiers.
 * @param config Optional [E2ETestConfig] to customize timeouts, screenshot behavior, and step delays.
 * @param block Suspending test execution block with multi-instance handle access.
 * @see E2EMultiInstanceScope
 * @see E2ETestConfig
 */
expect fun e2eTest(
  instances: List<String>,
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2EMultiInstanceScope.(E2EMultiInstanceScope) -> Unit
)

/**
 * Executes a multi-instance end-to-end test across named application actors (vararg overload).
 *
 * @param firstInstance The first unique instance identifier.
 * @param otherInstances Additional unique instance identifiers.
 * @param config Optional [E2ETestConfig] to customize timeouts, screenshot behavior, and step delays.
 * @param block Suspending test execution block with multi-instance handle access.
 */
fun e2eTest(
  firstInstance: String,
  vararg otherInstances: String,
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2EMultiInstanceScope.(E2EMultiInstanceScope) -> Unit
) {
  e2eTest(instances = listOf(firstInstance, *otherInstances), config = config, block = block)
}

/**
 * Executes a multi-target multi-instance end-to-end test across named application actors,
 * mapping each instance to its required execution target platform.
 *
 * @param instances Mapping of unique instance identifier to its target platform.
 * @param config Optional [E2ETestConfig] to customize timeouts, screenshot behavior, and step delays.
 * @param block Suspending test execution block with multi-instance handle access.
 */
expect fun e2eTest(
  instances: Map<String, Target>,
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2EMultiInstanceScope.(E2EMultiInstanceScope) -> Unit
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
 * @param config Optional [E2ETestConfig] to customize timeouts, screenshot behavior, and step delays.
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
    var primaryError: Throwable? = null
    try {
      block()
    } catch (t: Throwable) {
      primaryError = t
      throw t
    } finally {
      try {
        afterEach()
      } catch (teardownError: Throwable) {
        if (primaryError != null) {
          primaryError.addSuppressed(teardownError)
        } else {
          throw teardownError
        }
      }
    }
  }
}

fun E2ETestLifecycle.e2eTest(
  instances: List<String>,
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2EMultiInstanceScope.(E2EMultiInstanceScope) -> Unit
) {
  io.github.aryapreetam.parikshan.e2eTest(instances = instances, config = config, block = block)
}

fun E2ETestLifecycle.e2eTest(
  firstInstance: String,
  vararg otherInstances: String,
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2EMultiInstanceScope.(E2EMultiInstanceScope) -> Unit
) {
  e2eTest(instances = listOf(firstInstance, *otherInstances), config = config, block = block)
}

fun E2ETestLifecycle.e2eTest(
  instances: Map<String, Target>,
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2EMultiInstanceScope.(E2EMultiInstanceScope) -> Unit
) {
  io.github.aryapreetam.parikshan.e2eTest(instances = instances, config = config, block = block)
}


