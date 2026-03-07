package io.github.aryapreetam.parikshan

import androidx.compose.foundation.ScrollState
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking

private data class AndroidTrackedNode(
  var onScroll: ((ScrollDirection) -> Unit)? = null,
  var scrollState: ScrollState? = null
)

private const val ANDROID_SCROLL_VIEWPORT_RATIO = 0.72f
private const val ANDROID_SCROLL_MIN_DISTANCE_PX = 220f
private const val ANDROID_SCROLL_STEP_PIXELS = 120f
private const val ANDROID_SCROLL_MIN_STEPS = 2
private const val ANDROID_SCROLL_MAX_STEPS = 6

internal actual object ParikshanTagBridgeHooks {
  private val nodes = linkedMapOf<String, AndroidTrackedNode>()

  actual fun onTagMetadata(
    tag: String,
    text: String?,
    visible: Boolean
  ) = Unit

  actual fun onTagBounds(
    tag: String,
    left: Double,
    top: Double,
    right: Double,
    bottom: Double
  ) = Unit

  actual fun onTagRemoved(tag: String) {
    synchronized(nodes) {
      nodes.remove(tag)
    }
  }

  actual fun onTagActions(
    tag: String,
    onClick: (() -> Unit)?,
    onInput: ((String) -> Unit)?,
    onScroll: ((ScrollDirection) -> Unit)?,
    scrollState: ScrollState?
  ) {
    synchronized(nodes) {
      val node = nodes.getOrPut(tag) { AndroidTrackedNode() }
      node.onScroll = onScroll
      node.scrollState = scrollState
    }
  }

  fun performScroll(
    tag: String,
    direction: ScrollDirection,
    viewportHeightPx: Float
  ): Boolean {
    val (scrollable, fallback) =
      synchronized(nodes) {
        val node = nodes[tag] ?: return false
        node.scrollState to node.onScroll
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
            ANDROID_SCROLL_MIN_DISTANCE_PX,
            viewportHeightPx.takeIf { it > 0f }?.times(ANDROID_SCROLL_VIEWPORT_RATIO) ?: 0f
          )
        val targetValue =
          (startValue + distancePx * directionMultiplier)
            .roundToInt()
            .coerceIn(0, scrollable.maxValue)
        if (targetValue == startValue) return@runBlocking

        val totalDistance = abs(targetValue - startValue)
        val stepCount =
          (totalDistance / ANDROID_SCROLL_STEP_PIXELS)
            .roundToInt()
            .coerceIn(ANDROID_SCROLL_MIN_STEPS, ANDROID_SCROLL_MAX_STEPS)
        repeat(stepCount) { index ->
          val linearProgress = (index + 1).toFloat() / stepCount
          val easedProgress = linearProgress * (2f - linearProgress)
          val intermediateValue =
            (startValue + (targetValue - startValue) * easedProgress)
              .roundToInt()
              .coerceIn(0, scrollable.maxValue)
          scrollable.scrollTo(intermediateValue)
        }
      }
      return true
    }
    val action = fallback ?: return false
    action(direction)
    return true
  }
}
