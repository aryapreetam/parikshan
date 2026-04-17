package sample.app

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import io.github.aryapreetam.parikshan.protocol.ScrollDirection

actual fun Modifier.parikshanCompatBridge(
  tag: String,
  text: String?,
  onClick: (() -> Unit)?,
  onInput: ((String) -> Unit)?,
  onScroll: ((ScrollDirection) -> Unit)?,
  scrollState: ScrollState?
): Modifier = this