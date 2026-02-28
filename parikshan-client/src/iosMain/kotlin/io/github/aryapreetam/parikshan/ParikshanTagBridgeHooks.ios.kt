package io.github.aryapreetam.parikshan

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
}
