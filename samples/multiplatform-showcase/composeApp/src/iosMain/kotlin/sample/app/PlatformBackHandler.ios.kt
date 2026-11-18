package sample.app

import androidx.compose.runtime.Composable
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
  // No-op on iOS
}

actual fun getPlatformName(): String = "iOS"

actual fun getCurrentEpochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
