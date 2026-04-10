package io.github.aryapreetam.parikshan

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import io.github.aryapreetam.parikshan.protocol.ScrollDirection

/**
 * Compatibility bridge for targets that still need app-side Parikshan metadata/actions
 * alongside the stable selector contract provided by [Modifier.testTag].
 */
fun Modifier.parikshanBridge(
  tag: String,
  text: String? = null,
  visible: Boolean = true,
  onClick: (() -> Unit)? = null,
  onInput: ((String) -> Unit)? = null,
  onScroll: ((ScrollDirection) -> Unit)? = null,
  scrollState: ScrollState? = null
): Modifier =
  composed {
    SideEffect {
      ParikshanTagBridgeHooks.onTagMetadata(
        tag = tag,
        text = text,
        visible = visible
      )
      ParikshanTagBridgeHooks.onTagActions(
        tag = tag,
        onClick = onClick,
        onInput = onInput,
        onScroll = onScroll,
        scrollState = scrollState
      )
    }
    DisposableEffect(tag) {
      onDispose {
        ParikshanTagBridgeHooks.onTagRemoved(tag)
      }
    }
    this.onGloballyPositioned { coordinates ->
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

@Deprecated(
  message =
    "Use Modifier.testTag(...) for stable selector identity. " +
      "parikshanTag is now a compatibility wrapper around testTag + parikshanBridge.",
  replaceWith =
    ReplaceWith(
      expression = "this.testTag(tag).parikshanBridge(tag, text, visible, onClick, onInput, onScroll, scrollState)",
      imports = [
        "androidx.compose.ui.platform.testTag",
        "io.github.aryapreetam.parikshan.parikshanBridge"
      ]
    )
)
fun Modifier.parikshanTag(
  tag: String,
  text: String? = null,
  visible: Boolean = true,
  onClick: (() -> Unit)? = null,
  onInput: ((String) -> Unit)? = null,
  onScroll: ((ScrollDirection) -> Unit)? = null,
  scrollState: ScrollState? = null
): Modifier =
  testTag(tag).parikshanBridge(
    tag = tag,
    text = text,
    visible = visible,
    onClick = onClick,
    onInput = onInput,
    onScroll = onScroll,
    scrollState = scrollState
  )

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

  fun onTagActions(
    tag: String,
    onClick: (() -> Unit)?,
    onInput: ((String) -> Unit)?,
    onScroll: ((ScrollDirection) -> Unit)?,
    scrollState: ScrollState?
  )
}
