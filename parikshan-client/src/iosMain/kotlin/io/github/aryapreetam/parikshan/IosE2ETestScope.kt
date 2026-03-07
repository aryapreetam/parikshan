package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Synchronous iOS E2E test scope that drives waits via NSRunLoop pumping
 * instead of coroutine delay. This keeps the main thread available for
 * UIKit/Compose rendering between polls.
 */
class IosE2ETestScope(
  private val defaultWaitTimeoutMs: Long = 10_000L,
  private val commandDelayMs: Long = 0L,
  private val failureScreenshotPath: String = "build/parikshan/failures/failure-${Random.nextLong().toString(16)}.png",
  private val captureScreenshotOnFailure: Boolean = true
) {

  fun click(tag: String) {
    check(IosBridgeState.performClick(tag)) {
      "No clickable node found for tag '$tag'"
    }
    settleAfterCommand()
  }

  fun input(tag: String, text: String) {
    check(IosBridgeState.performInput(tag, text)) {
      "No input node found for tag '$tag'"
    }
    settleAfterCommand()
  }

  fun scroll(tag: String, direction: ScrollDirection) {
    check(IosBridgeState.performScroll(tag, direction)) {
      "No scroll node found for tag '$tag'"
    }
    settleAfterCommand()
  }

  fun assertVisible(tag: String) {
    val node = IosBridgeState.snapshotNode(tag)
      ?: throw AssertionError("assertVisible($tag) failed: no node found")
    if (!node.visible) {
      throw AssertionError("assertVisible($tag) failed: node exists but is not visible")
    }
    settleAfterCommand()
  }

  fun assertText(tag: String, expected: String) {
    val node = IosBridgeState.snapshotNode(tag)
      ?: throw AssertionError("assertText($tag) failed: no node found")
    val actual = node.text.orEmpty()
    if (actual != expected) {
      throw AssertionError("assertText($tag) failed: expected='$expected' actual='$actual'")
    }
    settleAfterCommand()
  }

  fun waitFor(tag: String, timeoutMs: Long = defaultWaitTimeoutMs) {
    val node = pollForNode(tag, timeoutMs)
      ?: throw AssertionError("waitFor($tag) failed: timed out after ${timeoutMs}ms")
    if (!node.visible) {
      throw AssertionError("waitFor($tag) failed: node found but not visible")
    }
    settleAfterCommand()
  }

  fun getTree(): List<NodeSnapshot> {
    settleAfterCommand()
    return IosBridgeState.snapshotTree()
  }

  fun pressBack() {
    // v1 no-op on iOS
    settleAfterCommand()
  }

  fun pressHome() {
    // v1 no-op on iOS
    settleAfterCommand()
  }

  fun scrollUntilVisible(
    containerTag: String,
    targetTag: String,
    maxScrolls: Int = 40
  ) {
    repeat(maxScrolls + 1) { attempt ->
      val visible = IosBridgeState.snapshotTree().any { it.tag == targetTag && it.visible }
      if (visible) return
      if (attempt == maxScrolls) {
        throw AssertionError("Could not make '$targetTag' visible after $maxScrolls scrolls")
      }
      scroll(containerTag, ScrollDirection.Down)
    }
  }

  // ── private helpers ──

  private fun pollForNode(tag: String, timeoutMs: Long): NodeSnapshot? {
    if (timeoutMs <= 0L) {
      pumpRunLoop()
      return IosBridgeState.snapshotNode(tag)?.takeIf { it.visible }
    }
    val timeout = timeoutMs.milliseconds
    val mark = TimeSource.Monotonic.markNow()
    while (mark.elapsedNow() <= timeout) {
      pumpRunLoop()
      val node = IosBridgeState.snapshotNode(tag)
      if (node != null && node.visible) return node
    }
    return null
  }

  private fun settleAfterCommand() {
    // Pump run loop to let Compose process recomposition/layout
    pumpRunLoop(iterations = 3)
    if (commandDelayMs > 0L) {
      // Additional visible delay (pumped via NSRunLoop so rendering continues)
      val steps = (commandDelayMs / 50).coerceAtLeast(1)
      repeat(steps.toInt()) {
        pumpRunLoop(iterations = 1, intervalSeconds = 0.05)
      }
    }
  }
}

/**
 * Pumps the NSRunLoop to allow UIKit and Compose to process pending
 * layout, rendering, and recomposition work.
 */
fun pumpRunLoop(iterations: Int = 5, intervalSeconds: Double = 0.05) {
  repeat(iterations) {
    NSRunLoop.mainRunLoop.runUntilDate(
      NSDate.dateWithTimeIntervalSinceNow(intervalSeconds)
    )
  }
}

/**
 * Runs an iOS E2E test block synchronously on the main thread,
 * using NSRunLoop pumping for Compose recomposition.
 */
fun iosE2ETest(
  defaultWaitTimeoutMs: Long = 10_000L,
  commandDelayMs: Long = 300L,
  block: IosE2ETestScope.() -> Unit
) {
  val scope = IosE2ETestScope(
    defaultWaitTimeoutMs = defaultWaitTimeoutMs,
    commandDelayMs = commandDelayMs
  )
  scope.block()
}
