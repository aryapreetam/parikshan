package sample.app

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
  // No-op on Wasm
}

actual fun getPlatformName(): String = "Web (WasmJS)"

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun jsDateNow(): Double = js("Date.now()")

actual fun getCurrentEpochMillis(): Long = jsDateNow().toLong()
