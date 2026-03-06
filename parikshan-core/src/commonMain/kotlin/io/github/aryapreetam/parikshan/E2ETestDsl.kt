package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.Response
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlin.random.Random
import kotlinx.coroutines.delay

interface TestDriver {
  suspend fun send(command: Command): Response

  suspend fun close()
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
    expectOk(
      action = "click($tag)",
      response = driver.send(Command.Click(id = nextId(), tag = tag))
    )
    settleAfterCommand()
  }

  suspend fun input(
    tag: String,
    text: String
  ) {
    expectOk(
      action = "input($tag)",
      response = driver.send(Command.Input(id = nextId(), tag = tag, text = text))
    )
    settleAfterCommand()
  }

  suspend fun scroll(
    tag: String,
    direction: ScrollDirection
  ) {
    expectOk(
      action = "scroll($tag)",
      response = driver.send(Command.Scroll(id = nextId(), tag = tag, direction = direction))
    )
    settleAfterCommand()
  }

  suspend fun assertVisible(tag: String) {
    expectNode(
      action = "assertVisible($tag)",
      response = driver.send(Command.AssertVisible(id = nextId(), tag = tag))
    )
    settleAfterCommand()
  }

  suspend fun assertText(
    tag: String,
    expected: String
  ) {
    expectOk(
      action = "assertText($tag)",
      response =
        driver.send(
          Command.AssertText(
            id = nextId(),
            tag = tag,
            expected = expected
          )
        )
    )
    settleAfterCommand()
  }

  suspend fun waitFor(
    tag: String,
    timeoutMs: Long = config.defaultWaitTimeoutMs
  ) {
    expectNode(
      action = "waitFor($tag)",
      response =
        driver.send(
          Command.WaitFor(
            id = nextId(),
            tag = tag,
            timeoutMs = timeoutMs
          )
        )
    )
    settleAfterCommand()
  }

  suspend fun screenshot(path: String) {
    expectOk(
      action = "screenshot($path)",
      response = driver.send(Command.Screenshot(id = nextId(), path = path))
    )
    settleAfterCommand()
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

  suspend fun getTree(): List<NodeSnapshot> {
    val nodes =
      when (
      val response = driver.send(Command.GetTree(id = nextId()))
    ) {
      is Response.Tree -> response.nodes
      is Response.Error -> throw AssertionError(response.message)
      else -> throw AssertionError("Unexpected response to getTree: $response")
    }
    settleAfterCommand()
    return nodes
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
