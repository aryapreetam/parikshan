package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.TestDriver

object VideoRecorderFactory {
  fun create(
    target: String,
    driver: TestDriver,
    videoConfig: ParikshanVideoConfig
  ): VideoRecorder {
    return when (target.lowercase()) {
      "desktop","jvm" -> DesktopVideoRecorder(driver, videoConfig)
      "wasm", "web" -> WasmVideoRecorder(driver)
      "android" -> AndroidVideoRecorder(
        serial = System.getProperty("parikshan.android.serial") ?: "",
        postRollMs = videoConfig.postRollMs
      )
      "ios" -> IosVideoRecorder(
        udid = System.getProperty("parikshan.ios.udid") ?: "booted",
        postRollMs = videoConfig.postRollMs
      )
      else -> throw IllegalArgumentException("Unsupported video recording target platform '$target'")
    }
  }
}
