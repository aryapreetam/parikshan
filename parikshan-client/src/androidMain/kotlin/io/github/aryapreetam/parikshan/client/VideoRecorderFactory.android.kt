package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.TestDriver

internal actual fun createDesktopRecorder(driver: TestDriver, videoConfig: ParikshanVideoConfig): VideoRecorder {
  throw UnsupportedOperationException("Desktop video recording is not supported on Android host test runner.")
}

internal actual fun createWasmRecorder(driver: TestDriver): VideoRecorder {
  throw UnsupportedOperationException("Wasm video recording is not supported on Android host test runner.")
}

internal actual fun createIosRecorder(videoConfig: ParikshanVideoConfig): VideoRecorder {
  return IosVideoRecorder(
    udid = System.getProperty("parikshan.ios.udid") ?: "booted",
    postRollMs = videoConfig.postRollMs
  )
}
