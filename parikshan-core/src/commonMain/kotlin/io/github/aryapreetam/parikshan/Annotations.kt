package io.github.aryapreetam.parikshan

/**
 * Marks a function to be executed once before any test scenario in the test suite starts.
 *
 * On JVM and Android, place this annotation on static functions or functions inside a `companion object`.
 *
 * ### Example Usage
 * ```kotlin
 * class FormIntegrationTest : E2ETestLifecycle {
 *   companion object {
 *     @BeforeAll
 *     fun prepareDatabase() {
 *       println("Initializing global test suite data...")
 *     }
 *   }
 * }
 * ```
 *
 * @see AfterAll
 * @see E2ETestLifecycle
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BeforeAll

/**
 * Marks a function to be executed once after all test scenarios in the test suite complete.
 *
 * On JVM and Android, place this annotation on static functions or functions inside a `companion object`.
 *
 * ### Example Usage
 * ```kotlin
 * class FormIntegrationTest : E2ETestLifecycle {
 *   companion object {
 *     @AfterAll
 *     fun cleanupDatabase() {
 *       println("Cleaning up global test suite data...")
 *     }
 *   }
 * }
 * ```
 *
 * @see BeforeAll
 * @see E2ETestLifecycle
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AfterAll

/**
 * Interface providing per-test scenario lifecycle hooks for a test class.
 *
 * Implement this interface on your test class to automatically execute setup and teardown logic
 * before and after every [e2eTest] block in that class.
 *
 * ### Example Usage
 * ```kotlin
 * class FormIntegrationTest : E2ETestLifecycle {
 *
 *   override suspend fun E2ETestScope.beforeEach() {
 *     // Automatically executes before every test block in this class
 *     navigateToSection("form_playground")
 *     assertVisible("form_playground_screen")
 *   }
 *
 *   override suspend fun E2ETestScope.afterEach() {
 *     // Automatically executes after every test block completes (success or failure)
 *     navigateToSection("home_screen")
 *   }
 *
 *   @Test
 *   fun testFormSubmission() = e2eTest {
 *     input("form_name_input", "Alex")
 *     click("Submit")
 *     assertVisible("success_message")
 *   }
 * }
 * ```
 *
 * @see BeforeAll
 * @see AfterAll
 */
interface E2ETestLifecycle {
  /**
   * Executes inside the active [E2ETestScope] before every test scenario runs.
   */
  suspend fun E2ETestScope.beforeEach() {}

  /**
   * Executes inside the active [E2ETestScope] after every test scenario completes (success or failure).
   */
  suspend fun E2ETestScope.afterEach() {}
}
