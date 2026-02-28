package io.github.aryapreetam.parikshan.client

data class ParikshanWasmConfig(
  val appUrl: String,
  val headless: Boolean,
  val viewportWidth: Int,
  val viewportHeight: Int,
  val bridgeReadyTimeoutMs: Long
) {
  companion object {
    private const val DEFAULT_URL = "http://127.0.0.1:8081"
    private const val DEFAULT_HEADLESS = false
    private const val DEFAULT_VIEWPORT_WIDTH = 1280
    private const val DEFAULT_VIEWPORT_HEIGHT = 720
    private const val DEFAULT_BRIDGE_READY_TIMEOUT_MS = 30_000L

    fun fromSystemProperties(): ParikshanWasmConfig {
      val appUrl = System.getProperty("parikshan.wasm.url") ?: DEFAULT_URL
      val headless =
        System.getProperty("parikshan.wasm.headless")?.toBooleanStrictOrNull() ?: DEFAULT_HEADLESS
      val viewportWidth =
        System.getProperty("parikshan.wasm.viewportWidth")?.toIntOrNull()?.coerceIn(320, 8192)
          ?: DEFAULT_VIEWPORT_WIDTH
      val viewportHeight =
        System.getProperty("parikshan.wasm.viewportHeight")?.toIntOrNull()?.coerceIn(240, 8192)
          ?: DEFAULT_VIEWPORT_HEIGHT
      val bridgeReadyTimeoutMs =
        System.getProperty("parikshan.wasm.bridgeReadyTimeoutMs")
          ?.toLongOrNull()
          ?.coerceIn(1_000L, 180_000L)
          ?: DEFAULT_BRIDGE_READY_TIMEOUT_MS

      return ParikshanWasmConfig(
        appUrl = appUrl,
        headless = headless,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        bridgeReadyTimeoutMs = bridgeReadyTimeoutMs
      )
    }
  }
}
