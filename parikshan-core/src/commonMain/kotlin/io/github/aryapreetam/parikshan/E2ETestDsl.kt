package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.Response
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.protocol.Selector
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
}

data class E2ETestConfig(
  val defaultWaitTimeoutMs: Long = 10_000L,
  val commandDelayMs: Long = 0L,
  val failureScreenshotPath: String = "build/parikshan/failures/failure-${Random.nextLong().toString(16)}.png",
  val captureScreenshotOnFailure: Boolean = true
)

/**
 * The primary execution scope for a Parikshan end-to-end test.
 *
 * This scope provides an intent-based DSL for interacting with your Compose Multiplatform
 * application across all supported platforms.
 */
class E2ETestScope internal constructor(
  private val driver: TestDriver,
  private val config: E2ETestConfig
) {
  val targetPlatform: String get() = driver.targetPlatform

  /**
   * Executes a physical tap or click on the UI element matching the provided [tag].
   *
   * This method automatically waits for the element to become visible before attempting the click.
   * If the [tag] matches multiple visible nodes, it will pick the most specific one or the first match.
   */
  suspend fun click(tag: String) {
    click(selector = tag.asAutoSelector())
  }

  /**
   * Executes a physical tap or click on the UI element matching the [selector].
   *
   * Automatically waits for visibility.
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
   * Clears any existing text and inputs the provided [text] into the element matching the [tag].
   *
   * Automatically waits for visibility.
   */
  suspend fun input(
    tag: String,
    text: String
  ) {
    input(selector = tag.asAutoSelector(), text = text)
  }

  /**
   * Clears any existing text and inputs the provided [text] into the element matching the [selector].
   *
   * Automatically waits for visibility.
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
   * Performs a scroll action on the element matching the [tag] in the specified [direction].
   *
   * Useful for scrolling Lists or LazyColumns.
   */
  suspend fun scroll(
    tag: String,
    direction: ScrollDirection
  ) {
    scroll(selector = tag.asAutoSelector(), direction = direction)
  }

  /**
   * Performs a scroll action on the element matching the [selector] in the specified [direction].
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
   * Asserts that an element matching the [tag] is present and visible in the UI.
   *
   * This method has built-in waiting and will poll until the element appears or the timeout is reached.
   */
  suspend fun assertVisible(tag: String) {
    assertVisible(selector = tag.asAutoSelector())
  }

  /**
   * Asserts that an element matching the [selector] is present and visible in the UI.
   *
   * Polling is built-in.
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
   * Asserts that an element matching the [tag] is NOT visible in the UI.
   *
   * This is a polling assertion that waits for the element to disappear if it is currently present.
   */
  suspend fun assertNotVisible(tag: String, message: String? = null) {
    assertNotVisible(selector = tag.asAutoSelector(), message = message)
  }

  /**
   * Asserts that an element matching the [selector] is NOT visible in the UI.
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
   * Asserts that the element matching the [tag] contains the [expected] text.
   *
   * This assertion includes built-in waiting for the text to appear or match.
   */
  suspend fun assertText(
    tag: String,
    expected: String
  ) {
    assertText(selector = tag.asAutoSelector(), expected = expected)
  }

  /**
   * Asserts that the element matching the [selector] contains the [expected] text.
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
   * Asserts that the element matching the [tag] contains the provided [substring].
   */
  suspend fun assertContains(
    tag: String,
    substring: String
  ) {
    assertContains(selector = tag.asAutoSelector(), substring = substring)
  }

  /**
   * Asserts that the element matching the [selector] contains the provided [substring].
   */
  suspend fun assertContains(
    selector: Selector,
    substring: String
  ) {
    waitForVisibleText(selector = selector, expected = substring, policy = MatchPolicy.CONTAINS)
    settleAfterCommand()
  }

  /**
   * Asserts that the input element matching the [tag] has the [expected] value.
   *
   * This is a semantic alias for [assertText] that improves intent when verifying form fields.
   */
  suspend fun assertValue(
    tag: String,
    expected: String
  ) {
    assertText(tag = tag, expected = expected)
  }

  /**
   * Asserts that the input element matching the [selector] has the [expected] value.
   */
  suspend fun assertValue(
    selector: Selector,
    expected: String
  ) {
    assertText(selector = selector, expected = expected)
  }

  /**
   * Asserts that the provided [block] fails with an [AssertionError] containing the [messageContains] substring.
   *
   * Useful for verifying negative scenarios and framework behavior.
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
   * Blocks execution until an element matching the [tag] becomes visible.
   *
   * Fails with an [AssertionError] if the [timeoutMs] is reached.
   */
  suspend fun waitFor(
    tag: String,
    timeoutMs: Long = config.defaultWaitTimeoutMs
  ) {
    waitFor(selector = tag.asAutoSelector(), timeoutMs = timeoutMs)
  }

  /**
   * Blocks execution until an element matching the [selector] becomes visible.
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

  suspend fun resolveNode(
    selector: Selector,
    requireVisible: Boolean = true
  ): NodeSnapshot =
    resolveSelectorOrThrow(selector = selector, requireVisible = requireVisible).node

  suspend fun resolveNode(
    selector: String,
    requireVisible: Boolean = true
  ): NodeSnapshot = resolveNode(selector = selector.asAutoSelector(), requireVisible = requireVisible)

  suspend fun resolveVisibleNode(selector: String): NodeSnapshot =
    resolveNode(selector = selector, requireVisible = true)

  suspend fun resolveVisibleNode(selector: Selector): NodeSnapshot =
    resolveNode(selector = selector, requireVisible = true)

  suspend fun hasVisibleNode(selector: String): Boolean =
    hasVisibleNode(selector = selector.asAutoSelector())

  suspend fun hasVisibleNode(selector: Selector): Boolean =
    runCatching {
      resolveVisibleNode(selector)
    }.isSuccess

  suspend fun getTree(): List<NodeSnapshot> {
    val nodes = fetchTree()
    settleAfterCommand()
    return nodes
  }

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

  suspend fun takeScreenshot(hostPath: String) {
    screenshot(hostPath)
  }

  fun artifactPath(relativePath: String): String =
    driver.resolveArtifactPath(relativePath)

  fun screenshotPath(name: String): String =
    artifactPath("screenshots/${name.trim().ifEmpty { "unnamed" }}.png")

  /**
   * Performs a physical mouse or touch drag/swipe gesture from (fromX, fromY) to (toX, toY) over the specified duration.
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
   * Drags the UI element matching [tag] by moving it by (offsetX, offsetY) pixels.
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
   * Drags the UI element matching [selector] by moving it by (offsetX, offsetY) pixels.
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

  suspend fun pressBack() {
    expectOk(
      action = "pressBack()",
      response = driver.send(Command.PressBack(id = nextId()))
    )
    settleAfterCommand()
  }

  suspend fun pressHome() {
    expectOk(
      action = "pressHome()",
      response = driver.send(Command.PressHome(id = nextId()))
    )
    settleAfterCommand()
  }

  suspend fun relaunchApp() {
    driver.relaunchApp()
    settleAfterCommand()
  }

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
   * Retries the provided [block] up to [maxAttempts] times with a [delayMs] between attempts.
   *
   * Useful for waiting for asynchronous UI state changes that aren't covered by built-in waits.
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
    scope.block()
  } catch (throwable: Throwable) {
    if (config.captureScreenshotOnFailure) {
      runCatching {
        scope.screenshot(config.failureScreenshotPath)
      }
    }
    throw throwable
  } finally {
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

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class ParikshanScenario(
  val testName: String = ""
)

/**
 * Returns true if the current test execution target is Web (WasmJs).
 */
fun E2ETestScope.isWasm(): Boolean = targetPlatform == "wasm"

/**
 * Returns true if the current test execution target is JVM Desktop.
 */
fun E2ETestScope.isDesktop(): Boolean = targetPlatform == "desktop"

/**
 * Returns true if the current test execution target is an Android device or emulator.
 */
fun E2ETestScope.isAndroid(): Boolean = targetPlatform == "android"

/**
 * Returns true if the current test execution target is an iOS simulator or device.
 */
fun E2ETestScope.isIos(): Boolean = targetPlatform == "ios"
