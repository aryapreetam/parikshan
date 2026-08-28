package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.TestDriver

internal object VideoRecorderFactory {
  fun create(
    target: String,
    driver: TestDriver,
    videoConfig: ParikshanVideoConfig
  ): VideoRecorder {
    return when (target.lowercase()) {
      "desktop", "jvm" -> createDesktopRecorder(driver, videoConfig)
      "wasm", "web" -> createWasmRecorder(driver)
      "android" -> AndroidVideoRecorder(
        serial = System.getProperty("parikshan.android.serial") ?: "",
        postRollMs = videoConfig.postRollMs
      )
      "ios" -> createIosRecorder(videoConfig)
      else -> throw IllegalArgumentException("Unsupported video recording target platform '$target'")
    }
  }
}

internal expect fun createDesktopRecorder(driver: TestDriver, videoConfig: ParikshanVideoConfig): VideoRecorder
internal expect fun createWasmRecorder(driver: TestDriver): VideoRecorder
internal expect fun createIosRecorder(videoConfig: ParikshanVideoConfig): VideoRecorder
