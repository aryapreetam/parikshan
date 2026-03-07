package io.github.aryapreetam.parikshan

import androidx.compose.foundation.ScrollState
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSLock

private data class IosTrackedNode(
  var left: Double = 0.0,
  var top: Double = 0.0,
  var right: Double = 0.0,
  var bottom: Double = 0.0,
  var visible: Boolean = false,
  var text: String? = null,
  var onClick: (() -> Unit)? = null,
  var onInput: ((String) -> Unit)? = null,
  var onScroll: ((ScrollDirection) -> Unit)? = null,
  var scrollState: ScrollState? = null
) {
  fun toSnapshot(tag: String): NodeSnapshot =
    NodeSnapshot(
      tag = tag,
      bounds =
        Bounds(
          left = left,
          top = top,
          right = right,
          bottom = bottom
        ),
      visible = visible && hasRenderableBounds(),
      text = text
    )

  private fun hasRenderableBounds(): Boolean = right > left && bottom > top
}

private const val IOS_SCROLL_FRAME_INTERVAL_SECONDS = 1.0 / 60.0
private const val IOS_SCROLL_MIN_DISTANCE_PX = 220f
private const val IOS_SCROLL_VIEWPORT_RATIO = 0.72f
private const val IOS_SCROLL_STEP_PIXELS = 160f
private const val IOS_SCROLL_MIN_STEPS = 3
private const val IOS_SCROLL_MAX_STEPS = 6

internal object IosBridgeState {
  private val lock = NSLock()
  private val nodes = linkedMapOf<String, IosTrackedNode>()

  fun updateMetadata(
    tag: String,
    text: String?,
    visible: Boolean
  ) {
    withLock {
      val node = nodes.getOrPut(tag) { IosTrackedNode() }
      node.text = text
      node.visible = visible
    }
  }

  fun updateBounds(
    tag: String,
    left: Double,
    top: Double,
    right: Double,
    bottom: Double
  ) {
    withLock {
      val node = nodes.getOrPut(tag) { IosTrackedNode() }
      node.left = left
      node.top = top
      node.right = right
      node.bottom = bottom
    }
  }

  fun updateActions(
    tag: String,
    onClick: (() -> Unit)?,
    onInput: ((String) -> Unit)?,
    onScroll: ((ScrollDirection) -> Unit)?,
    scrollState: ScrollState?
  ) {
    withLock {
      val node = nodes.getOrPut(tag) { IosTrackedNode() }
      node.onClick = onClick
      node.onInput = onInput
      node.onScroll = onScroll
      node.scrollState = scrollState
    }
  }

  fun removeTag(tag: String) {
    withLock {
      nodes.remove(tag)
    }
  }

  fun clear() {
    withLock {
      nodes.clear()
    }
  }

  fun snapshotNode(tag: String): NodeSnapshot? =
    withLock {
      nodes[tag]?.toSnapshot(tag)
    }

  fun snapshotTree(): List<NodeSnapshot> =
    withLock {
      nodes.entries.map { (tag, node) -> node.toSnapshot(tag) }
    }

  fun performClick(tag: String): Boolean {
    val action = withLock { nodes[tag]?.onClick } ?: return false
    action()
    return true
  }

  fun performInput(
    tag: String,
    text: String
  ): Boolean {
    val action = withLock { nodes[tag]?.onInput } ?: return false
    action(text)
    return true
  }

  fun performScroll(
    tag: String,
    direction: ScrollDirection,
    amountPx: Float = IOS_SCROLL_MIN_DISTANCE_PX
  ): Boolean {
    val (scrollable, fallback, viewportHeight) = withLock {
      val node = nodes[tag] ?: return false
      Triple(
        node.scrollState,
        node.onScroll,
        (node.bottom - node.top).toFloat()
      )
    }
    if (scrollable != null) {
      val directionMultiplier = when (direction) {
        ScrollDirection.Up -> -1
        ScrollDirection.Down -> 1
        ScrollDirection.Left, ScrollDirection.Right -> 0
      }
      if (directionMultiplier == 0) return true
      runBlocking {
        val startValue = scrollable.value
        val distancePx =
          maxOf(
            amountPx,
            viewportHeight.takeIf { it > 0f }?.times(IOS_SCROLL_VIEWPORT_RATIO) ?: 0f
          )
        val targetValue =
          (startValue + (distancePx * directionMultiplier))
            .roundToInt()
            .coerceIn(0, scrollable.maxValue)
        if (targetValue == startValue) return@runBlocking

        val totalDistance = abs(targetValue - startValue)
        val stepCount =
          (totalDistance / IOS_SCROLL_STEP_PIXELS)
            .roundToInt()
            .coerceIn(IOS_SCROLL_MIN_STEPS, IOS_SCROLL_MAX_STEPS)

        repeat(stepCount) { index ->
          val linearProgress = (index + 1).toFloat() / stepCount
          val easedProgress = linearProgress * (2f - linearProgress)
          val intermediateValue =
            (startValue + (targetValue - startValue) * easedProgress)
              .roundToInt()
              .coerceIn(0, scrollable.maxValue)
          scrollable.scrollTo(intermediateValue)
          pumpRunLoop(iterations = 1, intervalSeconds = IOS_SCROLL_FRAME_INTERVAL_SECONDS)
        }
      }
      return true
    }
    if (fallback != null) {
      fallback(direction)
      return true
    }
    return false
  }

  private inline fun <T> withLock(block: () -> T): T {
    lock.lock()
    return try {
      block()
    } finally {
      lock.unlock()
    }
  }
}

/**
 * Clears all tracked nodes from the iOS bridge state.
 * Call between tests to prevent stale node interference.
 */
fun resetIosBridgeState() {
  IosBridgeState.clear()
}


internal actual object ParikshanTagBridgeHooks {
  actual fun onTagMetadata(
    tag: String,
    text: String?,
    visible: Boolean
  ) {
    // Auto-start the in-app HTTP server on first tag registration
    io.github.aryapreetam.parikshan.server.ParikshanIosServer.startIfNeeded()

    IosBridgeState.updateMetadata(
      tag = tag,
      text = text,
      visible = visible
    )
  }

  actual fun onTagBounds(
    tag: String,
    left: Double,
    top: Double,
    right: Double,
    bottom: Double
  ) {
    IosBridgeState.updateBounds(
      tag = tag,
      left = left,
      top = top,
      right = right,
      bottom = bottom
    )
  }

  actual fun onTagRemoved(tag: String) {
    IosBridgeState.removeTag(tag)
  }

  actual fun onTagActions(
    tag: String,
    onClick: (() -> Unit)?,
    onInput: ((String) -> Unit)?,
    onScroll: ((ScrollDirection) -> Unit)?,
    scrollState: ScrollState?
  ) {
    IosBridgeState.updateActions(
      tag = tag,
      onClick = onClick,
      onInput = onInput,
      onScroll = onScroll,
      scrollState = scrollState
    )
  }
}
