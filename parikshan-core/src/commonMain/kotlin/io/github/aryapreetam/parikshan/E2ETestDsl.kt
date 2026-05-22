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

interface TestDriver {
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
    waitFor(selector = selector)
    val resolved = resolveSelectorOrThrow(selector = selector, requireVisible = true)
    checkAmbiguity(resolved)
    expectOk(
      action = "click(${selector.raw})",
      response = driver.send(Command.Click(id = nextId(), tag = resolved.tag, selector = selector))
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
    waitFor(selector = selector)
    val resolved = resolveSelectorOrThrow(selector = selector, requireVisible = true)
    checkAmbiguity(resolved)
    expectOk(
      action = "input(${selector.raw})",
      response = driver.send(Command.Input(id = nextId(), tag = resolved.tag, text = text, selector = selector))
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
    waitFor(selector = selector)
    val resolved = resolveSelectorOrThrow(selector = selector, requireVisible = true)
    checkAmbiguity(resolved)
    expectOk(
      action = "scroll(${selector.raw})",
      response = driver.send(Command.Scroll(id = nextId(), tag = resolved.tag, direction = direction, selector = selector))
    )
    settleAfterCommand()
  }

  suspend fun assertVisible(tag: String) {
    assertVisible(selector = tag.asAutoSelector())
  }
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

  suspend fun assertNotVisible(tag: String, message: String? = null) {
    assertNotVisible(selector = tag.asAutoSelector(), message = message)
  }

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
    // assertText now has "waiting built-in" for the content to match,
    // which is the world-class standard for E2E testing.
    waitForVisibleText(selector = selector, expected = expected)
    settleAfterCommand()
  }

  suspend fun waitFor(
    tag: String,
    timeoutMs: Long = config.defaultWaitTimeoutMs
  ) {
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
      } catch (error: SelectorResolutionException) {
        lastError = error.message
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
      "Timeout (${timeoutMs}ms) waiting for selector ${selector.raw}. Last error: $lastError"
    )
  }

  @Deprecated("Use assertText() instead, which now has built-in waiting.", ReplaceWith("assertText(tag, expected)"))
  suspend fun waitForVisibleText(
    tag: String,
    expected: String,
    timeoutMs: Long = config.defaultWaitTimeoutMs
  ) {
    waitForVisibleText(selector = tag.asAutoSelector(), expected = expected, timeoutMs = timeoutMs)
  }

  @Deprecated("Use assertText() instead, which now has built-in waiting.", ReplaceWith("assertText(selector, expected)"))
  suspend fun waitForVisibleText(
    selector: Selector,
    expected: String,
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
        if (nativeTag != null) {
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
        if (resolved.node.text == expected) {
          settleAfterCommand()
          return
        }
        lastError = "Text mismatch: expected '$expected' actual '${resolved.node.text}'"
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
    if (resolved.allMatches.size > 1) {
      throw AssertionError(resolved.selector.ambiguousTextMessage(resolved.allMatches))
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

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class ParikshanScenario(
  val testName: String = ""
)
