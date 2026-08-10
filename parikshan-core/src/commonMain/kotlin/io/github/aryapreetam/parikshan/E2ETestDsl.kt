package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.Response
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.auto
import io.github.aryapreetam.parikshan.protocol.tag
import io.github.aryapreetam.parikshan.protocol.text
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * @suppress
 */
@InternalParikshanApi
interface TestDriver {
  val targetPlatform: String
  suspend fun send(command: Command): Response

  suspend fun relaunchApp() {
    val response = send(Command.RelaunchApp(id = nextId()))
    if (response is Response.Error) {
      throw AssertionError("relaunchApp() failed: ${response.message}")
    }
    if (response !is Response.Ok) {
      throw AssertionError("relaunchApp() returned unexpected response: $response")
    }
  }

  suspend fun reset() {
    send(Command.Reset(id = nextId()))
  }

  suspend fun close()

  fun updateVirtualCursor(x: Double, y: Double) {}

  fun resolveArtifactPath(relativePath: String): String =
    "build/parikshan/${relativePath.trimStart('/', '\\')}"

  fun setRouteTarget(platform: String?) {}

  suspend fun executeParallel(block: suspend (TestDriver) -> Unit) {
    block(this)
  }
}

/**
 * Configuration options for an end-to-end test execution.
 *
 * It is recommended to use [e2eTest] with its default configuration (`defaultWaitTimeoutMs = 10_000L`).
 * Overriding `E2ETestConfig` parameters should only be done when custom timeout scaling is strictly required
 * (such as for slow CI environments or prolonged animations).
 *
 * ### Example Usage
 * ```kotlin
 * // Standard usage: stick to default e2eTest { } invocation
 * @Test
 * fun testLoginFlow() = e2eTest {
 *   click("Sign In")
 * }
 * ```
 *
 * @param defaultWaitTimeoutMs Default timeout in milliseconds for element resolution and assertion polling (default 10,000ms).
 * @param commandDelayMs Optional stabilization delay in milliseconds applied after each UI command (default 0ms).
 * @param failureScreenshotPath File path where failure screenshots will be saved if [captureScreenshotOnFailure] is true.
 * @param captureScreenshotOnFailure Automatically captures a screenshot of the app if a test block throws an error (default true).
 * @see E2ETestScope
 */
data class E2ETestConfig(
  val defaultWaitTimeoutMs: Long = 10_000L,
  val commandDelayMs: Long = 0L,
  val failureScreenshotPath: String = "build/parikshan/failures/failure-${Random.nextLong().toString(16)}.png",
  val captureScreenshotOnFailure: Boolean = true
)

/**
 * Primary execution scope for a Parikshan end-to-end test scenario.
 *
 * `E2ETestScope` provides an intent-based DSL for driving Compose Multiplatform user interfaces
 * across Android, iOS, Desktop, and Wasm. Inside this scope, you perform actions (`click`, `input`, `scroll`),
 * assert UI state (`assertVisible`, `assertText`), and handle platform-conditional logic.
 *
 * All methods automatically handle element polling, visibility verification, and virtual cursor updates.
 *
 * @see Selector
 */
class E2ETestScope @InternalParikshanApi constructor(
  private val driver: TestDriver,
  private val config: E2ETestConfig
) {
  /** The target execution platform string (e.g. "android", "ios", "desktop", "wasm"). */
  val targetPlatform: String get() = driver.targetPlatform

  /**
   * Executes the provided test [block] concurrently across each active target driver in parallel.
   *
   * Inside [block], `this` refers to a target-scoped [E2ETestScope] connected directly to an individual
   * target driver (such as Desktop, Wasm, Android, or iOS). Actions dispatched within [block] run
   * independently on each target without waiting for other target viewports to reach the same step.
   *
   * ### When to Use
   * Use `executeParallel` when writing navigation or layout helpers for multi-target scenarios where
   * different target viewports display different UI states (for example, a wide Desktop window displaying a
   * persistent navigation rail vs a mobile screen displaying a compact hamburger button and modal drawer).
   *
   * ### When to Avoid
   * Avoid using `executeParallel` inside standard end-to-end test scenarios. Standard test flows should use
   * the top-level unified DSL (`click`, `input`, `assertVisible`), which enforces a strict step-barrier
   * contract across all target platforms after every command.
   *
   * ### Example Usage
   * ```kotlin
   * @OptIn(InternalParikshanApi::class)
   * suspend fun E2ETestScope.openAppNavigation() {
   *   executeParallel {
   *     if (hasVisibleNode("hamburger_button")) {
   *       if (!hasVisibleNode("navigation_drawer")) {
   *         click("hamburger_button")
   *         waitFor("navigation_drawer")
   *       }
   *     }
   *   }
   * }
   * ```
   *
   * @param block The target-scoped test operations to execute independently on each active target.
   * @see TestDriver.executeParallel
   */
  @InternalParikshanApi
  suspend fun executeParallel(block: suspend E2ETestScope.() -> Unit) {
    driver.executeParallel { targetDriver ->
      val localScope = E2ETestScope(driver = targetDriver, config = config)
      localScope.block()
    }
  }

  /**
   * Executes a physical tap or click on the UI element matching the provided string [tag] or text.
   *
   * This is the primary and simplest method for interacting with UI elements. It automatically attempts to match
   * by explicit Compose `testTag` first, and falls back to matching by visible text substring.
   *
   * Automatically waits for the element to become visible before attempting the click.
   *
   * ### Example Usage
   * ```kotlin
   * click("Submit")
   * click("login_button")
   * ```
   *
   * @param tag The `testTag` string or visible text of the UI element to click.
   * @throws AssertionError If no visible element matching [tag] appears within the timeout.
   * @see click(Selector)
   * @see waitFor
   */
  suspend fun click(tag: String) {
    click(selector = tag.asAutoSelector())
  }

  /**
   * Executes a physical tap or click on the UI element matching the specified [selector].
   *
   * Use this variant when precise element targeting is required—such as matching an exact tag only (`tag()`),
   * exact text only (`text()`), or picking a specific index when multiple elements match (`atIndex()`, `first()`, `last()`).
   *
   * Automatically waits for the element to become visible before attempting the click.
   *
   * ### Example Usage
   * ```kotlin
   * // Click strictly by testTag (ignores text matching)
   * click(tag("submit_btn"))
   *
   * // Click strictly by visible text substring
   * click(text("Sign In"))
   *
   * // Click the 2nd matching element when multiple items exist
   * click(auto("Delete").atIndex(1))
   * ```
   *
   * @param selector The explicit [Selector] rule used to locate the target UI element.
   * @throws AssertionError If no visible node matches [selector] within the timeout.
   * @see Selector
   * @see auto
   * @see tag
   * @see text
   */
  suspend fun click(selector: Selector) {
    waitFor(selector = selector)
    val resolved = resolveSelectorOrThrow(selector = selector, requireVisible = true)
    checkAmbiguity(resolved)
    driver.updateVirtualCursor(resolved.node.bounds.centerX, resolved.node.bounds.centerY)
    expectOk(
      action = "click(${selector.raw})",
      response = driver.send(Command.Click(id = nextId(), tag = resolved.tag, selector = selector))
    )
    settleAfterCommand()
  }

  /**
   * Clears any existing text and inputs [text] into the UI element matching the string [tag].
   *
   * Primary method for entering text into form fields. Matches by explicit `testTag` first, then visible text substring.
   *
   * Automatically waits for the field to become visible before typing.
   *
   * ### Example Usage
   * ```kotlin
   * input("Username", "john.doe")
   * input("Email Address", "alex.smith@example.com")
   * ```
   *
   * @param tag The `testTag` string or visible placeholder text of the input field.
   * @param text The text value to input into the field.
   * @throws AssertionError If no visible input element matching [tag] appears within the timeout.
   * @see input(Selector, String)
   */
  suspend fun input(
    tag: String,
    text: String
  ) {
    input(selector = tag.asAutoSelector(), text = text)
  }

  /**
   * Clears any existing text and inputs [text] into the UI element matching the specified [selector].
   *
   * Advanced variant using explicit selectors for input field targeting.
   *
   * Automatically waits for the field to become visible before typing.
   *
   * ### Example Usage
   * ```kotlin
   * input(tag("password_input"), "SecretPass123!")
   * input(text("Search").first(), "Kotlin Multiplatform")
   * ```
   *
   * @param selector The explicit [Selector] rule used to locate the input field.
   * @param text The text value to input into the field.
   * @throws AssertionError If no visible input element matches [selector] within the timeout.
   * @see Selector
   */
  suspend fun input(
    selector: Selector,
    text: String
  ) {
    waitFor(selector = selector)
    val resolved = resolveSelectorOrThrow(selector = selector, requireVisible = true)
    checkAmbiguity(resolved)
    driver.updateVirtualCursor(resolved.node.bounds.centerX, resolved.node.bounds.centerY)
    expectOk(
      action = "input(${selector.raw})",
      response = driver.send(Command.Input(id = nextId(), tag = resolved.tag, text = text, selector = selector))
    )
    settleAfterCommand()
  }

  /**
   * Performs a single scroll gesture on the container element matching string [tag] in the specified [direction].
   *
   * Primary method for scrolling scrollable containers like `LazyColumn`, `LazyRow`, or scrollable `Column`s.
   * Applies a **200px displacement step** per invocation:
   * - **Desktop JVM, Android, iOS:** Dispatches a 200px Compose `ScrollBy` action to the scrollable container.
   * - **WasmJs:** Dispatches 10 incremental `mouse.wheel()` steps totaling 200px over 500ms to simulate smooth web scrolling.
   *
   * > **Tip:** To reveal off-screen items in a `LazyColumn` or `LazyRow` without calculating pixel distances, prefer using [scrollUntilVisible].
   *
   * ### Example Usage
   * ```kotlin
   * scroll("item_list", ScrollDirection.Down)
   * scroll("image_gallery", ScrollDirection.Right)
   * ```
   *
   * @param tag The `testTag` string or visible text of the scrollable container.
   * @param direction The [ScrollDirection] (Up, Down, Left, Right).
   * @see ScrollDirection
   * @see scrollUntilVisible
   */
  suspend fun scroll(
    tag: String,
    direction: ScrollDirection
  ) {
    scroll(selector = tag.asAutoSelector(), direction = direction)
  }

  /**
   * Performs a single scroll gesture on the container element matching [selector] in the specified [direction].
   *
   * Advanced variant using explicit selectors for scrollable container targeting.
   * Applies a **200px displacement step** per invocation.
   *
   * ### Example Usage
   * ```kotlin
   * scroll(tag("main_scroll_view"), ScrollDirection.Down)
   * ```
   *
   * @param selector The explicit [Selector] rule targeting the scrollable container.
   * @param direction The [ScrollDirection] (Up, Down, Left, Right).
   * @see ScrollDirection
   * @see scrollUntilVisible
   */
  suspend fun scroll(
    selector: Selector,
    direction: ScrollDirection
  ) {
    waitFor(selector = selector)
    val resolved = resolveSelectorOrThrow(selector = selector, requireVisible = true)
    checkAmbiguity(resolved)
    driver.updateVirtualCursor(resolved.node.bounds.centerX, resolved.node.bounds.centerY)
    expectOk(
      action = "scroll(${selector.raw})",
      response = driver.send(Command.Scroll(id = nextId(), tag = resolved.tag, direction = direction, selector = selector))
    )
    settleAfterCommand()
  }

  /**
   * Repeatedly scrolls the container matching string [containerTag] in [direction] until [targetTag] becomes visible.
   *
   * Primary method for revealing off-screen elements inside long lists or lazy layouts.
   *
   * ### Example Usage
   * ```kotlin
   * // Scroll down list until "Settings Item" becomes visible
   * scrollUntilVisible("settings_list", "Notification Settings")
   * ```
   *
   * @param containerTag The `testTag` or visible text of the scrollable container.
   * @param targetTag The `testTag` or visible text of the element to reveal.
   * @param direction The scroll direction (defaults to [ScrollDirection.Down]).
   * @param maxScrolls Maximum number of scroll attempts before failing (default 30).
   * @param stabilizationDelayMs Stabilization pause in milliseconds between scrolls (default 300ms).
   * @throws AssertionError If [targetTag] is not revealed within [maxScrolls] scroll attempts.
   */
  suspend fun scrollUntilVisible(
    containerTag: String,
    targetTag: String,
    direction: ScrollDirection = ScrollDirection.Down,
    maxScrolls: Int = 30,
    stabilizationDelayMs: Long = 300
  ) {
    scrollUntilVisible(
      containerSelector = containerTag.asAutoSelector(),
      targetSelector = targetTag.asAutoSelector(),
      direction = direction,
      maxScrolls = maxScrolls,
      stabilizationDelayMs = stabilizationDelayMs
    )
  }

  /**
   * Repeatedly scrolls the container matching [containerSelector] in [direction] until [targetSelector] becomes visible.
   *
   * Advanced variant using explicit selectors for container and target element lookup.
   *
   * ### Example Usage
   * ```kotlin
   * scrollUntilVisible(
   *   containerSelector = tag("feed_list"),
   *   targetSelector = text("Load More").first(),
   *   direction = ScrollDirection.Down
   * )
   * ```
   *
   * @param containerSelector Explicit selector targeting the scrollable container.
   * @param targetSelector Explicit selector targeting the element to reveal.
   * @param direction The scroll direction (defaults to [ScrollDirection.Down]).
   * @param maxScrolls Maximum number of scroll attempts before failing (default 30).
   * @param stabilizationDelayMs Stabilization pause in milliseconds between scrolls (default 300ms).
   * @throws AssertionError If [targetSelector] is not revealed within [maxScrolls] scroll attempts.
   */
  suspend fun scrollUntilVisible(
    containerSelector: Selector,
    targetSelector: Selector,
    direction: ScrollDirection = ScrollDirection.Down,
    maxScrolls: Int = 30,
    stabilizationDelayMs: Long = 300
  ) {
    driver.executeParallel { targetDriver ->
      val localScope = E2ETestScope(driver = targetDriver, config = config)
      for (i in 0 until maxScrolls) {
        if (localScope.hasVisibleNode(targetSelector)) {
          return@executeParallel
        }
        localScope.scroll(selector = containerSelector, direction = direction)
        delay(stabilizationDelayMs)
      }
      throw AssertionError(
        "Timed out scrolling container '${containerSelector.raw}' to locate target element: '${targetSelector.raw}' after $maxScrolls scrolls."
      )
    }
  }


  /**
   * Asserts that a UI element matching string [tag] is present and visible on screen.
   *
   * Automatically polls the UI tree until the element appears or the timeout is reached.
   *
   * ### Example Usage
   * ```kotlin
   * assertVisible("welcome_header")
   * assertVisible("Logout")
   * ```
   *
   * @param tag The `testTag` string or visible text of the element.
   * @throws AssertionError If the element is not visible within the timeout.
   * @see assertNotVisible
   * @see waitFor
   */
  suspend fun assertVisible(tag: String) {
    assertVisible(selector = tag.asAutoSelector())
  }

  /**
   * Asserts that a UI element matching [selector] is present and visible on screen.
   *
   * Advanced variant using explicit selectors. Automatically polls until visible.
   *
   * ### Example Usage
   * ```kotlin
   * assertVisible(tag("dashboard_card"))
   * assertVisible(text("Total Items").first())
   * ```
   *
   * @param selector Explicit selector targeting the UI element.
   * @throws AssertionError If no matching visible node appears within the timeout.
   */
  suspend fun assertVisible(selector: Selector) {
    waitFor(selector = selector)
    val resolved = resolveSelectorOrThrow(selector = selector, requireVisible = true)

    val response = driver.send(Command.AssertVisible(id = nextId(), tag = resolved.tag, selector = selector))
    if (response is Response.Error) {
      throw AssertionError("assertVisible(${selector.raw}) failed: ${response.message}")
    }
    if (response !is Response.NodeInfo && response !is Response.Ok) {
      throw AssertionError("assertVisible(${selector.raw}) returned unexpected response: $response")
    }
    settleAfterCommand()
  }

  /**
   * Asserts that a UI element matching string [tag] is NOT visible on screen.
   *
   * Automatically polls and waits for the element to disappear if it is currently visible.
   *
   * ### Example Usage
   * ```kotlin
   * assertNotVisible("loading_spinner")
   * ```
   *
   * @param tag The `testTag` string or visible text of the element.
   * @param message Optional custom assertion error message on failure.
   * @throws AssertionError If the element remains visible after the timeout.
   */
  suspend fun assertNotVisible(tag: String, message: String? = null) {
    assertNotVisible(selector = tag.asAutoSelector(), message = message)
  }

  /**
   * Asserts that a UI element matching [selector] is NOT visible on screen.
   *
   * Advanced variant using explicit selectors. Automatically polls until the element disappears.
   *
   * ### Example Usage
   * ```kotlin
   * assertNotVisible(tag("modal_dialog"))
   * ```
   *
   * @param selector Explicit selector targeting the UI element.
   * @param message Optional custom assertion error message.
   * @param timeoutMs Maximum polling duration in milliseconds before failing.
   * @throws AssertionError If the element remains visible after the timeout.
   */
  suspend fun assertNotVisible(
    selector: Selector,
    message: String? = null,
    timeoutMs: Long = config.defaultWaitTimeoutMs
  ) {
    val startMark = TimeSource.Monotonic.markNow()
    do {
      if (!hasVisibleNode(selector)) {
        settleAfterCommand()
        return
      }
      if (startMark.elapsedNow() >= timeoutMs.milliseconds) {
        break
      }
      delay(WAIT_POLL_INTERVAL_MS)
    } while (true)

    throw AssertionError(message ?: "Expected '${selector.raw}' to be not visible after ${timeoutMs}ms")
  }

  /**
   * Asserts that the UI element matching string [tag] displays exact text [expected].
   *
   * Includes built-in waiting until the text updates to match the expected value.
   *
   * ### Example Usage
   * ```kotlin
   * assertText("user_name_display", "Alice")
   * ```
   *
   * @param tag The `testTag` string or visible text of the element.
   * @param expected The exact expected text string.
   * @throws AssertionError If the element text does not match [expected] within the timeout.
   * @see assertContains
   */
  suspend fun assertText(
    tag: String,
    expected: String
  ) {
    assertText(selector = tag.asAutoSelector(), expected = expected)
  }

  /**
   * Asserts that the UI element matching [selector] displays exact text [expected].
   *
   * Advanced variant using explicit selectors. Includes built-in waiting.
   *
   * ### Example Usage
   * ```kotlin
   * assertText(tag("status_label"), "Connected")
   * ```
   *
   * @param selector Explicit selector targeting the UI element.
   * @param expected The exact expected text string.
   * @throws AssertionError If the element text does not match [expected] within the timeout.
   */
  suspend fun assertText(
    selector: Selector,
    expected: String
  ) {
    // assertText includes built-in waiting for the content to match.
    waitForVisibleText(selector = selector, expected = expected, policy = MatchPolicy.EXACT)
    settleAfterCommand()
  }

  /**
   * Asserts that the UI element matching string [tag] contains the [substring].
   *
   * Includes built-in waiting for the text substring to appear.
   *
   * ### Example Usage
   * ```kotlin
   * assertContains("result_summary", "3 items found")
   * ```
   *
   * @param tag The `testTag` string or visible text of the element.
   * @param substring The expected substring contained within the element's text.
   * @throws AssertionError If the element text does not contain [substring] within the timeout.
   * @see assertText
   */
  suspend fun assertContains(
    tag: String,
    substring: String
  ) {
    assertContains(selector = tag.asAutoSelector(), substring = substring)
  }

  /**
   * Asserts that the UI element matching [selector] contains the [substring].
   *
   * Advanced variant using explicit selectors. Includes built-in waiting.
   *
   * ### Example Usage
   * ```kotlin
   * assertContains(tag("description_box"), "Compose Multiplatform")
   * ```
   *
   * @param selector Explicit selector targeting the UI element.
   * @param substring The expected substring contained within the element's text.
   * @throws AssertionError If the element text does not contain [substring] within the timeout.
   */
  suspend fun assertContains(
    selector: Selector,
    substring: String
  ) {
    waitForVisibleText(selector = selector, expected = substring, policy = MatchPolicy.CONTAINS)
    settleAfterCommand()
  }

  /**
   * Asserts that an input element matching string [tag] has text value [expected].
   *
   * Semantic alias for [assertText] intended for form field validation.
   *
   * ### Example Usage
   * ```kotlin
   * assertValue("email_input", "alice@example.com")
   * ```
   *
   * @param tag The `testTag` string or visible text of the input field.
   * @param expected The expected input value.
   */
  suspend fun assertValue(
    tag: String,
    expected: String
  ) {
    assertText(tag = tag, expected = expected)
  }

  /**
   * Asserts that an input element matching [selector] has text value [expected].
   *
   * Semantic alias for [assertText] using explicit selectors.
   *
   * ### Example Usage
   * ```kotlin
   * assertValue(tag("phone_input"), "+15550199")
   * ```
   *
   * @param selector Explicit selector targeting the input element.
   * @param expected The expected input value.
   */
  suspend fun assertValue(
    selector: Selector,
    expected: String
  ) {
    assertText(selector = selector, expected = expected)
  }

  /**
   * Asserts that executing [block] fails with an [AssertionError] containing [messageContains].
   *
   * Useful for testing negative scenarios or verifying custom validation errors.
   *
   * ### Example Usage
   * ```kotlin
   * assertFailure("Expected 'Submit' to be visible") {
   *   click("Submit")
   * }
   * ```
   *
   * @param messageContains Substring expected in the thrown error message.
   * @param block Test code block expected to fail.
   */
  suspend fun assertFailure(
    messageContains: String,
    block: suspend E2ETestScope.() -> Unit
  ) {
    try {
      block()
    } catch (e: AssertionError) {
      if (e.message?.contains(messageContains) == true) {
        return
      }
      throw AssertionError("Expected failure message to contain '$messageContains' but got '${e.message}'")
    } catch (e: Throwable) {
      throw AssertionError("Expected AssertionError but got ${e::class.simpleName}: ${e.message}")
    }
    throw AssertionError("Expected block to fail with message containing '$messageContains', but it succeeded.")
  }

  /**
   * Blocks execution until an element matching string [tag] becomes visible.
   *
   * ### Example Usage
   * ```kotlin
   * waitFor("dashboard_screen", timeoutMs = 5000)
   * ```
   *
   * @param tag The `testTag` string or visible text of the element.
   * @param timeoutMs Maximum duration in milliseconds to wait (defaults to [E2ETestConfig.defaultWaitTimeoutMs]).
   * @throws AssertionError If the element is not visible within [timeoutMs].
   */
  suspend fun waitFor(
    tag: String,
    timeoutMs: Long = config.defaultWaitTimeoutMs
  ) {
    waitFor(selector = tag.asAutoSelector(), timeoutMs = timeoutMs)
  }

  /**
   * Blocks execution until an element matching [selector] becomes visible.
   *
   * Advanced variant using explicit selectors.
   *
   * ### Example Usage
   * ```kotlin
   * waitFor(tag("checkout_button"), timeoutMs = 12_000L)
   * ```
   *
   * @param selector Explicit selector targeting the element.
   * @param timeoutMs Maximum duration in milliseconds to wait.
   * @throws AssertionError If no matching element is visible within [timeoutMs].
   */
  suspend fun waitFor(
    selector: Selector,
    timeoutMs: Long = config.defaultWaitTimeoutMs
  ) {
    val startMark = TimeSource.Monotonic.markNow()
    var lastError: String? = null
    do {
      try {
        lookupSelector(selector = selector, requireVisible = true)
        settleAfterCommand()
        return
      } catch (error: SelectorResolutionException) {
        lastError = error.message
      } catch (error: IllegalArgumentException) {
        lastError = error.message
      }
      if (startMark.elapsedNow() >= timeoutMs.milliseconds) {
        println("Parikshan E2ETestDsl: Timeout waiting for selector ${selector.raw}. Last error: $lastError. Printing semantics tree nodes:")
        runCatching {
          fetchTree().forEach { node ->
            println("  Node: tag='${node.tag}', text='${node.text}', visible=${node.visible}, bounds=${node.bounds}")
          }
        }
        break
      }
      delay(WAIT_POLL_INTERVAL_MS)
    } while (true)

    if (config.captureScreenshotOnFailure) {
      runCatching {
        screenshot(config.failureScreenshotPath)
      }
    }
    throw AssertionError(
      "Timeout (${timeoutMs}ms) waiting for selector ${selector.raw}. Last error: $lastError"
    )
  }

  private suspend fun waitForVisibleText(
    tag: String,
    expected: String,
    policy: MatchPolicy,
    timeoutMs: Long = config.defaultWaitTimeoutMs
  ) {
    waitForVisibleText(selector = tag.asAutoSelector(), expected = expected, policy = policy, timeoutMs = timeoutMs)
  }

  private suspend fun waitForVisibleText(
    selector: Selector,
    expected: String,
    policy: MatchPolicy,
    timeoutMs: Long = config.defaultWaitTimeoutMs
  ) {
    val startMark = TimeSource.Monotonic.markNow()
    var lastError: String? = null

    val nativeTag =
      when (selector) {
        is Selector.Auto -> selector.raw
        is Selector.Tag -> selector.value
        is Selector.Text -> null
      }

    do {
      try {
        if (nativeTag != null && policy == MatchPolicy.EXACT) {
          val response = driver.send(Command.AssertText(id = nextId(), tag = nativeTag, expected = expected))
          if (response is Response.Ok) {
            settleAfterCommand()
            return
          }
          if (response is Response.Error) {
            lastError = response.message
          }
        }

        val resolved = selector.resolveNode(fetchTree(), requireVisible = true)
        val actualText = resolved.node.text
        val matched = when (policy) {
          MatchPolicy.EXACT -> actualText?.trim() == expected.trim()
          MatchPolicy.CONTAINS -> actualText?.contains(expected) == true
        }

        if (matched) {
          settleAfterCommand()
          return
        }
        lastError = "Text mismatch (policy=$policy): expected '$expected', actual '$actualText'"
      } catch (error: IllegalArgumentException) {
        lastError = error.message
      }
      if (startMark.elapsedNow() >= timeoutMs.milliseconds) {
        break
      }
      delay(WAIT_POLL_INTERVAL_MS)
    } while (true)


    if (config.captureScreenshotOnFailure) {
      runCatching {
        screenshot(config.failureScreenshotPath)
      }
    }
    throw AssertionError(
      "Timed out waiting for '${selector.raw}' to expose text '$expected' (policy=$policy). Last error='$lastError'."
    )
  }

  /**
   * Resolves and returns a [NodeSnapshot] for the element matching the provided [selector].
   *
   * This retrieves a point-in-time snapshot of the node's properties (such as bounds, tag, text, and visibility).
   *
   * @param selector the query matching the target element.
   * @param requireVisible if true, the resolution will only succeed if the matching node is currently visible.
   * @return the resolved [NodeSnapshot].
   * @throws IllegalArgumentException if the selector cannot be resolved to a unique node.
   */
  suspend fun resolveNode(
    selector: Selector,
    requireVisible: Boolean = true
  ): NodeSnapshot =
    resolveSelectorOrThrow(selector = selector, requireVisible = requireVisible).node

  /**
   * Resolves and returns a [NodeSnapshot] for the element matching the provided [selector] string.
   *
   * This is a convenience shortcut for [resolveNode] using [String.asAutoSelector].
   *
   * @param selector the query matching the target element.
   * @param requireVisible if true, the resolution will only succeed if the matching node is currently visible.
   * @return the resolved [NodeSnapshot].
   */
  suspend fun resolveNode(
    selector: String,
    requireVisible: Boolean = true
  ): NodeSnapshot = resolveNode(selector = selector.asAutoSelector(), requireVisible = requireVisible)

  /**
   * Resolves and returns a visible [NodeSnapshot] matching the provided [selector] string.
   *
   * This is a convenience shortcut for [resolveNode] with `requireVisible = true`.
   *
   * @param selector the query matching the target element.
   * @return the resolved visible [NodeSnapshot].
   */
  suspend fun resolveVisibleNode(selector: String): NodeSnapshot =
    resolveNode(selector = selector, requireVisible = true)

  /**
   * Resolves and returns a visible [NodeSnapshot] matching the provided [selector].
   *
   * This is a convenience shortcut for [resolveNode] with `requireVisible = true`.
   *
   * @param selector the query matching the target element.
   * @return the resolved visible [NodeSnapshot].
   */
  suspend fun resolveVisibleNode(selector: Selector): NodeSnapshot =
    resolveNode(selector = selector, requireVisible = true)

  /**
   * Checks whether a visible node matching the provided [selector] string exists in the current UI tree.
   *
   * Unlike [resolveNode], this method does not throw an exception if the node is missing or invisible,
   * making it safe for conditional branching in tests.
   *
   * @param selector the query matching the target element.
   * @return true if a visible node matches the selector, false otherwise.
   */
  suspend fun hasVisibleNode(selector: String): Boolean =
    hasVisibleNode(selector = selector.asAutoSelector())

  /**
   * Checks whether a visible node matching the provided [selector] exists in the current UI tree.
   *
   * Unlike [resolveNode], this method does not throw an exception if the node is missing or invisible,
   * making it safe for conditional branching in tests.
   *
   * @param selector the query matching the target element.
   * @return true if a visible node matches the selector, false otherwise.
   */
  suspend fun hasVisibleNode(selector: Selector): Boolean =
    runCatching {
      resolveVisibleNode(selector)
    }.isSuccess

  /**
   * Fetches the entire current UI hierarchy as a list of [NodeSnapshot]s.
   *
   * Calling this method forces the test runner to synchronize and wait for outstanding UI operations to settle.
   *
   * @return a list representing all nodes currently present in the Compose Multiplatform semantic tree.
   */
  suspend fun getTree(): List<NodeSnapshot> {
    val nodes = fetchTree()
    settleAfterCommand()
    return nodes
  }

  /**
   * Captures a screenshot of the current application screen and saves it as a PNG to [path].
   *
   * Primary canonical method for taking screenshots.
   *
   * ### Platform Screen Capture Mechanics
   * - **Desktop JVM:** Captures the active window frame bounds.
   * - **WasmJs:** Playwright captures the viewport canvas.
   * - **Android:** Triggers a device screenshot via UiAutomator / ADB screencap.
   * - **iOS:** Triggers a simulator screenshot via `xcrun simctl`.
   *
   * ### Example Usage
   * ```kotlin
   * screenshot("build/reports/login-success.png")
   * ```
   *
   * @param path File path where the screenshot PNG will be stored.
   * @see takeScreenshot
   * @see screenshotPath
   */
  suspend fun screenshot(path: String) {
    expectOk(
      action = "screenshot($path)",
      response =
        driver.send(
          Command.Screenshot(
            id = nextId(),
            devicePath = path,
            hostPath = path
          )
        )
    )
    settleAfterCommand()
  }

  /**
   * Captures a screenshot of the current application screen and saves it as a PNG to [hostPath].
   *
   * Convenience alias for [screenshot]. New code should prefer using [screenshot].
   *
   * @param hostPath File path on the host machine where the screenshot PNG will be stored.
   * @see screenshot
   */
  suspend fun takeScreenshot(hostPath: String) {
    screenshot(hostPath)
  }

  /**
   * Resolves a relative path to an absolute path inside the project's build and report output directory.
   *
   * @param relativePath the path relative to the test runner's artifact output base.
   * @return the resolved absolute path string.
   */
  fun artifactPath(relativePath: String): String =
    driver.resolveArtifactPath(relativePath)

  /**
   * Helper that resolves a logical name into a standard screenshot file path inside the build directory.
   *
   * @param name the logical name of the screenshot (e.g. "login-success").
   * @return the resolved absolute file path for the screenshot.
   */
  fun screenshotPath(name: String): String =
    artifactPath("screenshots/${name.trim().ifEmpty { "unnamed" }}.png")

  /**
   * Performs a physical touch or mouse drag gesture from ([fromX], [fromY]) to ([toX], [toY]) over [durationMs].
   *
   * ### Example Usage
   * ```kotlin
   * drag(fromX = 100.0, fromY = 500.0, toX = 100.0, toY = 100.0, durationMs = 400L)
   * ```
   *
   * @param fromX Starting X pixel coordinate.
   * @param fromY Starting Y pixel coordinate.
   * @param toX Ending X pixel coordinate.
   * @param toY Ending Y pixel coordinate.
   * @param durationMs Gesture movement duration in milliseconds (default 300ms).
   */
  suspend fun drag(
    fromX: Double,
    fromY: Double,
    toX: Double,
    toY: Double,
    durationMs: Long = 300L
  ) {
    driver.updateVirtualCursor(fromX, fromY)
    expectOk(
      action = "drag($fromX, $fromY -> $toX, $toY)",
      response = driver.send(Command.Drag(id = nextId(), fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMs = durationMs))
    )
    driver.updateVirtualCursor(toX, toY)
    settleAfterCommand()
  }

  /**
   * Drags the UI element matching string [tag] by moving it by ([offsetX], [offsetY]) pixels.
   *
   * ### Example Usage
   * ```kotlin
   * drag("volume_slider", offsetX = 50.0, offsetY = 0.0)
   * ```
   *
   * @param tag The `testTag` string or visible text of the element to drag.
   * @param offsetX Horizontal pixel movement offset.
   * @param offsetY Vertical pixel movement offset.
   * @param durationMs Gesture duration in milliseconds (default 300ms).
   */
  suspend fun drag(
    tag: String,
    offsetX: Double,
    offsetY: Double,
    durationMs: Long = 300L
  ) {
    drag(selector = tag.asAutoSelector(), offsetX = offsetX, offsetY = offsetY, durationMs = durationMs)
  }

  /**
   * Drags the UI element matching [selector] by moving it by ([offsetX], [offsetY]) pixels.
   *
   * Advanced variant using explicit selectors.
   *
   * ### Example Usage
   * ```kotlin
   * drag(tag("thumb_handle"), offsetX = 100.0, offsetY = 0.0)
   * ```
   *
   * @param selector Explicit selector targeting the element to drag.
   * @param offsetX Horizontal pixel movement offset.
   * @param offsetY Vertical pixel movement offset.
   * @param durationMs Gesture duration in milliseconds (default 300ms).
   */
  suspend fun drag(
    selector: Selector,
    offsetX: Double,
    offsetY: Double,
    durationMs: Long = 300L
  ) {
    waitFor(selector = selector)
    val resolved = resolveSelectorOrThrow(selector = selector, requireVisible = true)
    checkAmbiguity(resolved)
    val bounds = resolved.node.bounds
    val fromX = bounds.centerX
    val fromY = bounds.centerY
    val toX = fromX + offsetX
    val toY = fromY + offsetY
    drag(fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMs = durationMs)
  }

  /**
   * Triggers a system back navigation press.
   *
   * ### Platform Support
   * - **Android:** Triggers the system hardware back button.
   * - **WasmJs:** Triggers browser history back navigation (`window.history.back()`).
   * - **iOS:** Triggers top-level navigation bar back action.
   * - **Desktop JVM:** Triggers top-level back navigation if handled by the window event listener.
   *
   * ### Example Usage
   * ```kotlin
   * click("Open Details")
   * pressBack()
   * assertVisible("main_list")
   * ```
   */
  suspend fun pressBack() {
    expectOk(
      action = "pressBack()",
      response = driver.send(Command.PressBack(id = nextId()))
    )
    settleAfterCommand()
  }

  /**
   * Triggers a system home button press to send the application to the background.
   *
   * ### Platform Support
   * - **Android & iOS:** Supported (sends the application process to the background).
   * - **Desktop JVM & WasmJs:** **No-op (ignored)**, as desktop windows and browser tabs do not possess a device home button.
   *
   * ### Example Usage
   * ```kotlin
   * pressHome()
   * ```
   */
  suspend fun pressHome() {
    expectOk(
      action = "pressHome()",
      response = driver.send(Command.PressHome(id = nextId()))
    )
    settleAfterCommand()
  }

  /**
   * Force-terminates and relaunches the application process under test while maintaining the active test connection session.
   *
   * ### Execution & Performance Notes
   * - **Desktop JVM:** Destroys and re-launches the application window/process, introducing a **1 to 2-second boot latency**.
   * - **Android & iOS:** Force-terminates and restarts the native app process via driver hooks.
   * - **WasmJs:** Performs a full browser tab reload.
   *
   * > **Warning:** Use `relaunchApp()` sparingly due to the 1-2s process boot latency on Desktop JVM.
   * > For fast test teardowns under 100ms across multiple tests, prefer using [E2ETestLifecycle] (`beforeEach`/`afterEach`) with in-app UI navigation resets.
   *
   * ### Example Usage
   * ```kotlin
   * relaunchApp()
   * assertVisible("splash_screen")
   * ```
   *
   * @see E2ETestLifecycle
   * @see resetApp
   */
  suspend fun relaunchApp() {
    driver.relaunchApp()
    settleAfterCommand()
  }

  /**
   * Resets application state and clears test session caches.
   */
  suspend fun resetApp() {
    driver.reset()
    settleAfterCommand()
  }

  private suspend fun settleAfterCommand() {
    val delayMs = config.commandDelayMs
    if (delayMs > 0L) {
      delay(delayMs)
    }
  }

  private fun expectOk(
    action: String,
    response: Response
  ) {
    when (response) {
      is Response.Ok -> Unit
      is Response.Error -> throw AssertionError("$action failed: ${response.message}")
      else -> throw AssertionError("$action returned unexpected response: $response")
    }
  }

  private suspend fun fetchTree(): List<NodeSnapshot> =
    when (
      val response = driver.send(Command.GetTree(id = nextId()))
    ) {
      is Response.Tree -> response.nodes
      is Response.Error -> throw AssertionError(response.message)
      else -> throw AssertionError("Unexpected response to getTree: $response")
    }

  private suspend fun lookupSelector(
    selector: Selector,
    requireVisible: Boolean
  ): ResolvedSelector =
    selector.resolveNode(
      nodes = fetchTree(),
      requireVisible = requireVisible
    )

  private suspend fun resolveSelectorOrThrow(
    selector: Selector,
    requireVisible: Boolean
  ): ResolvedSelector =
    try {
      lookupSelector(selector = selector, requireVisible = requireVisible)
    } catch (error: IllegalArgumentException) {
      throw AssertionError(error.message ?: "Could not resolve selector ${selector.raw}")
    }

  private fun checkAmbiguity(resolved: ResolvedSelector) {
    if (resolved.selector.index != null) return
    if (resolved.allMatches.size > 1) {
      throw AssertionError(resolved.selector.ambiguousTextMessage(resolved.allMatches))
    }
  }

  /**
   * Retries executing [block] up to [maxAttempts] times with a [delayMs] pause between attempts.
   *
   * Useful for wrapping flaky assertions or waiting for asynchronous network operations.
   *
   * ### Example Usage
   * ```kotlin
   * retry(maxAttempts = 5, delayMs = 1000L) {
   *   assertVisible("async_data_card")
   * }
   * ```
   *
   * @param maxAttempts Maximum number of execution attempts (default 3).
   * @param delayMs Pause duration in milliseconds between attempts (default 500ms).
   * @param block Test code block to execute and retry on failure.
   */
  suspend fun <T> retry(
    maxAttempts: Int = 3,
    delayMs: Long = 500L,
    block: suspend E2ETestScope.() -> T
  ): T {
    var lastError: Throwable? = null
    repeat(maxAttempts) { attempt ->
      try {
        return this.block()
      } catch (e: Throwable) {
        lastError = e
        if (attempt < maxAttempts - 1) {
          delay(delayMs)
        }
      }
    }
    throw lastError ?: RuntimeException("Retry failed after $maxAttempts attempts")
  }

  /**
   * Executes the given [block] only on the specified [target] platform during synchronized execution,
   * or if the single active driver platform matches the specified [target].
   */
  suspend fun onTarget(target: Target, block: suspend E2ETestScope.() -> Unit) {
    if (driver.targetPlatform == "sync") {
      driver.setRouteTarget(target.platformName)
      try {
        block()
      } finally {
        driver.setRouteTarget(null)
      }
    } else if (driver.targetPlatform == target.platformName) {
      block()
    }
  }
}

/**
 * @suppress
 */
@InternalParikshanApi
suspend fun e2eTest(
  driver: TestDriver,
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2ETestScope.() -> Unit
) {
  val scope = E2ETestScope(driver = driver, config = config)
  try {
    val pingResponse = driver.send(Command.Ping(id = nextId()))
    if (pingResponse is Response.Error) {
      throw IllegalStateException("Failed to connect to Parikshan server: ${pingResponse.message}")
    }
    runCatching { driver.reset() }
    scope.block()
  } catch (throwable: Throwable) {
    if (config.captureScreenshotOnFailure) {
      runCatching {
        scope.screenshot(config.failureScreenshotPath)
      }
    }
    throw throwable
  } finally {
    runCatching { driver.reset() }
    driver.close()
  }
}

private fun nextId(): String {
  val high = Random.nextLong().toString(16)
  val low = Random.nextLong().toString(16)
  return "$high-$low"
}

private const val WAIT_POLL_INTERVAL_MS = 50L

private enum class MatchPolicy {
  EXACT,
  CONTAINS
}

/**
 * @suppress
 */
@kotlin.annotation.Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class ParikshanScenario(
  val testName: String = ""
)

/**
 * Returns true if the current test execution target is Web (WasmJs).
 *
 * ### Example Usage
 * ```kotlin
 * if (isWasm()) {
 *   // Perform Wasm-specific assertion or skip unsupported action
 * }
 * ```
 */
fun E2ETestScope.isWasm(): Boolean = targetPlatform == "wasm"

/**
 * Returns true if the current test execution target is JVM Desktop.
 *
 * ### Example Usage
 * ```kotlin
 * if (isDesktop()) {
 *   // Perform Desktop-specific window management assertion
 * }
 * ```
 */
fun E2ETestScope.isDesktop(): Boolean = targetPlatform == "desktop"

/**
 * Returns true if the current test execution target is an Android device or emulator.
 *
 * ### Example Usage
 * ```kotlin
 * if (isAndroid()) {
 *   pressBack()
 * }
 * ```
 */
fun E2ETestScope.isAndroid(): Boolean = targetPlatform == "android"

/**
 * Returns true if the current test execution target is an iOS simulator or device.
 *
 * ### Example Usage
 * ```kotlin
 * if (isIos()) {
 *   assertVisible("ios_nav_bar")
 * }
 * ```
 */
fun E2ETestScope.isIos(): Boolean = targetPlatform == "ios"

enum class Target(val platformName: String) {
  Desktop("desktop"),
  Wasm("wasm"),
  Android("android"),
  Ios("ios")
}
