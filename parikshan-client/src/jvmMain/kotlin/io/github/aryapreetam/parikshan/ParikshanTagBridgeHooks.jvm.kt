package io.github.aryapreetam.parikshan

import androidx.compose.foundation.ScrollState
import io.github.aryapreetam.parikshan.protocol.ScrollDirection

internal actual object ParikshanTagBridgeHooks {
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

  actual fun onTagRemoved(tag: String) = Unit

  actual fun onTagActions(
    tag: String,
    onClick: (() -> Unit)?,
    onInput: ((String) -> Unit)?,
    onScroll: ((ScrollDirection) -> Unit)?,
    scrollState: ScrollState?
  ) = Unit
}
