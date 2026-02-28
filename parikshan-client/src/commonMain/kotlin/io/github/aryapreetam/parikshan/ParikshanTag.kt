package io.github.aryapreetam.parikshan

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag

/**
 * Tracks node geometry/text for Parikshan web automation while preserving Compose testTag semantics.
 */
fun Modifier.parikshanTag(
  tag: String,
  text: String? = null,
  visible: Boolean = true
): Modifier =
  composed {
    SideEffect {
      ParikshanTagBridgeHooks.onTagMetadata(
        tag = tag,
        text = text,
        visible = visible
      )
    }
    DisposableEffect(tag) {
      onDispose {
        ParikshanTagBridgeHooks.onTagRemoved(tag)
      }
    }
    this
      .testTag(tag)
      .onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        ParikshanTagBridgeHooks.onTagBounds(
          tag = tag,
          left = bounds.left.toDouble(),
          top = bounds.top.toDouble(),
          right = bounds.right.toDouble(),
          bottom = bounds.bottom.toDouble()
        )
      }
  }

internal expect object ParikshanTagBridgeHooks {
  fun onTagMetadata(
    tag: String,
    text: String?,
    visible: Boolean
  )

  fun onTagBounds(
    tag: String,
    left: Double,
    top: Double,
    right: Double,
    bottom: Double
  )

  fun onTagRemoved(tag: String)
}
