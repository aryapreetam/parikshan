package sample.app

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
  androidx.activity.compose.BackHandler(enabled, onBack)
}

actual fun getPlatformName(): String = "Android"