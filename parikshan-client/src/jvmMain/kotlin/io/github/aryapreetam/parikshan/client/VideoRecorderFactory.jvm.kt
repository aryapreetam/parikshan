package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.TestDriver

internal actual fun createDesktopRecorder(driver: TestDriver, videoConfig: ParikshanVideoConfig): VideoRecorder {
  return DesktopVideoRecorder(driver, videoConfig)
}

internal actual fun createWasmRecorder(driver: TestDriver): VideoRecorder {
  return WasmVideoRecorder(driver)
}

internal actual fun createIosRecorder(videoConfig: ParikshanVideoConfig): VideoRecorder {
  return IosVideoRecorder(
    udid = System.getProperty("parikshan.ios.udid") ?: "booted",
    postRollMs = videoConfig.postRollMs
  )
}
