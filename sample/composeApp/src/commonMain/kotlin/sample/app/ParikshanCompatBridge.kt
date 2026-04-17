package sample.app

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import io.github.aryapreetam.parikshan.protocol.ScrollDirection

expect fun Modifier.parikshanCompatBridge(
  tag: String,
  text: String? = null,
  onClick: (() -> Unit)? = null,
  onInput: ((String) -> Unit)? = null,
  onScroll: ((ScrollDirection) -> Unit)? = null,
  scrollState: ScrollState? = null
): Modifier