package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.Response
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

interface TestDriver {
  suspend fun send(command: Command): Response

  suspend fun close()

  fun resolveArtifactPath(relativePath: String): String =
    "build/parikshan/${relativePath.trimStart('/', '\\')}"
}

data class E2ETestConfig(
  val defaultWaitTimeoutMs: Long = 10_000L,
  val commandDelayMs: Long = 0L,
  val failureScreenshotPath: String = "build/parikshan/failures/failure-${Random.nextLong().toString(16)}.png",
  val captureScreenshotOnFailure: Boolean = true
)

class E2ETestScope internal constructor(
  private val driver: TestDriver,
  private val config: E2ETestConfig
) {
  suspend fun click(tag: String) {
    click(selector = tag.asAutoSelector())
  }

  suspend fun click(selector: Selector) {
    val resolved = resolveSelectorOrThrow(selector = selector, requireVisible = true)
    expectOk(
      action = "click(${selector.raw})",
      response = driver.send(Command.Click(id = nextId(), tag = resolved.tag))
    )
    settleAfterCommand()
  }

  suspend fun input(
    tag: String,
    text: String
  ) {
    input(selector = Selector.Tag(tag), text = text)
  }

  suspend fun input(
    selector: Selector,
    text: String
  ) {
    val resolved = resolveSelectorOrThrow(selector = selector, requireVisible = true)
    expectOk(
      action = "input(${selector.raw})",
      response = driver.send(Command.Input(id = nextId(), tag = resolved.tag, text = text))
    )
    settleAfterCommand()
  }

  suspend fun scroll(
    tag: String,
    direction: ScrollDirection
  ) {
    scroll(selector = Selector.Tag(tag), direction = direction)
  }

  suspend fun scroll(
    selector: Selector,
    direction: ScrollDirection
  ) {
    val resolved = resolveSelectorOrThrow(selector = selector, requireVisible = true)
    expectOk(
      action = "scroll(${selector.raw})",
      response = driver.send(Command.Scroll(id = nextId(), tag = resolved.tag, direction = direction))
    )
    settleAfterCommand()
  }

  suspend fun assertVisible(tag: String) {
    assertVisible(selector = tag.asAutoSelector())
  }

  suspend fun assertVisible(selector: Selector) {
    resolveSelectorOrThrow(selector = selector, requireVisible = true)
    settleAfterCommand()
  }

  suspend fun assertText(
    tag: String,
    expected: String
  ) {
    assertText(selector = tag.asAutoSelector(), expected = expected)
  }

  suspend fun assertText(
    selector: Selector,
    expected: String
  ) {
    // Try native assertion first to avoid getTree
    val tagValue = if (selector is Selector.Tag) selector.value else (selector as Selector.Auto).raw
    val response = driver.send(
      Command.AssertText(
        id = nextId(),
        tag = tagValue,
        expected = expected
      )
    )
    if (response is Response.Ok) {
      settleAfterCommand()
      return
    }
    // Fallback
    val resolved = resolveSelectorOrThrow(selector = selector, requireVisible = true)
    if (resolved.node.text != expected) {
      throw AssertionError("assertText(${selector.raw}) failed: expected '$expected', got '${resolved.node.text}'")
    }
    settleAfterCommand()
  }

  suspend fun waitFor(
    tag: String,
    timeoutMs: Long = config.defaultWaitTimeoutMs
  ) {
    // Rely on local polling loop to handle both text and exact tag matching seamlessly
    // instead of sending Command.WaitFor which can block the server thread incorrectly on text selectors
    waitFor(selector = tag.asAutoSelector(), timeoutMs = timeoutMs)
  }

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
      } catch (error: IllegalArgumentException) {
        lastError = error.message
      }
      if (startMark.elapsedNow() >= timeoutMs.milliseconds) {
        break
      }
      delay(200) // Poll interval
    } while (true)

    // Capture failure screenshot before throwing
    if (config.captureScreenshotOnFailure) {
      runCatching {
        driver.send(Command.Screenshot(id = nextId(), path = config.failureScreenshotPath))
      }
    }
    throw AssertionError(
      "Timeout (${timeoutMs}ms) waiting for selector ${selector.raw}. Last error: $lastError"
    )
  }

  suspend fun waitForVisibleText(
    tag: String,
    expected: String,
    timeoutMs: Long = config.defaultWaitTimeoutMs
  ) {
    waitForVisibleText(selector = tag.asAutoSelector(), expected = expected, timeoutMs = timeoutMs)
  }

  suspend fun waitForVisibleText(
    selector: Selector,
    expected: String,
    timeoutMs: Long = config.defaultWaitTimeoutMs
  ) {
    val startMark = TimeSource.Monotonic.markNow()
    var lastError: String? = null
    val tagValue = if (selector is Selector.Tag) selector.value else (selector as Selector.Auto).raw
    do {
      try {
        val response = driver.send(Command.AssertText(id = nextId(), tag = tagValue, expected = expected))
        if (response is Response.Ok) {
          settleAfterCommand()
          return
        }
        if (response is Response.Error) {
          lastError = response.message
        }
      } catch (error: IllegalArgumentException) {
        lastError = error.message
      }
      if (startMark.elapsedNow() >= timeoutMs.milliseconds) {
        break
      }
      delay(200) // Poll interval
    } while (true)

    // Capture failure screenshot before throwing
    if (config.captureScreenshotOnFailure) {
      runCatching {
        driver.send(Command.Screenshot(id = nextId(), path = config.failureScreenshotPath))
      }
    }
    throw AssertionError(
      "Timed out waiting for '${selector.raw}' to expose text '$expected'. Last error='$lastError'."
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
      response = driver.send(Command.Screenshot(id = nextId(), path = path))
    )
    settleAfterCommand()
  }

  fun artifactPath(relativePath: String): String =
    driver.resolveArtifactPath(relativePath)

  fun screenshotPath(name: String): String =
    artifactPath("screenshots/${name.trim().ifEmpty { "unnamed" }}.png")

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

  private fun expectNode(
    action: String,
    response: Response
  ) {
    when (response) {
      is Response.NodeInfo -> {
        if (!response.visible) {
          throw AssertionError("$action failed: node is not visible")
        }
      }

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
  ): ResolvedSelector {
    return selector.resolveNode(
      nodes = fetchTree(),
      requireVisible = requireVisible
    )
  }

  private suspend fun resolveSelectorOrThrow(
    selector: Selector,
    requireVisible: Boolean
  ): ResolvedSelector {
    // Optimization: For Tag selectors only, skip fetching the entire tree.
    // The native driver's puppet can find elements by accessibilityIdentifier directly.
    // Auto selectors must NOT use this shortcut because they may resolve by text,
    // and text-based resolution requires the full tree for ambiguity detection
    // (e.g. multiple buttons with the same label).
    if (selector is Selector.Tag) {
      return ResolvedSelector(
        selector = selector,
        matchType = ResolvedSelector.MatchType.Tag,
        node = NodeSnapshot(
          tag = selector.value,
          text = null,
          visible = requireVisible,
          bounds = io.github.aryapreetam.parikshan.protocol.Bounds(0.0, 0.0, 0.0, 0.0)
        )
      )
    }

    return try {
      lookupSelector(selector = selector, requireVisible = requireVisible)
    } catch (error: IllegalArgumentException) {
      throw AssertionError(error.message ?: "Could not resolve selector ${selector.raw}")
    }
  }
}

suspend fun e2eTest(
  driver: TestDriver,
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2ETestScope.() -> Unit
) {
  val scope = E2ETestScope(driver = driver, config = config)
  try {
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

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class ParikshanScenario(
  val testName: String = ""
)
