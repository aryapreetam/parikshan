package sample.app

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
  // No-op on Desktop
}

actual fun getPlatformName(): String = "Desktop (JVM)"
