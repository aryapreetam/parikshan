package io.github.aryapreetam.parikshan.client

data class ParikshanVideoConfig(
  val enabled: Boolean,
  val outputDir: String,
  val fps: Int,
  val showCursor: Boolean,
  val postRollMs: Long,
  val granularity: VideoGranularity = VideoGranularity.CLASS,
  /** Explicit video width override. When null, Playwright infers from the viewport size. */
  val videoWidth: Int? = null,
  /** Explicit video height override. When null, Playwright infers from the viewport size. */
  val videoHeight: Int? = null,
  /** Optional device scale factor (DPR). When null, Playwright uses the default/device DPR. */
  val deviceScaleFactor: Double? = null
) {
  companion object {
    private const val DEFAULT_OUTPUT_DIR = "build/parikshan/videos"
    private const val DEFAULT_FPS = 10
    private const val DEFAULT_POST_ROLL_MS = 1000L
    private const val DEFAULT_GRANULARITY = "class"

    fun fromSystemProperties(): ParikshanVideoConfig {
      val enabled = System.getProperty("parikshan.video.enabled")?.toBoolean() ?: false
      val outputDir = System.getProperty("parikshan.video.outputDir") ?: DEFAULT_OUTPUT_DIR
      val fps = System.getProperty("parikshan.video.fps")?.toIntOrNull()?.coerceIn(1, 30) ?: DEFAULT_FPS
      val showCursor = System.getProperty("parikshan.video.showCursor")?.toBoolean() ?: true
      val granularityStr = System.getProperty("parikshan.video.granularity")
        ?: DEFAULT_GRANULARITY
      val granularity = when (granularityStr.lowercase()) {
        "session", "run" -> VideoGranularity.RUN
        "test" -> VideoGranularity.TEST
        else -> VideoGranularity.CLASS
      }
      val postRollMs =
        System.getProperty("parikshan.video.postRollMs")?.toLongOrNull()?.coerceIn(0L, 10_000L)
          ?: DEFAULT_POST_ROLL_MS
      val videoWidth = System.getProperty("parikshan.video.width")?.toIntOrNull()?.coerceIn(100, 3840)
      val videoHeight = System.getProperty("parikshan.video.height")?.toIntOrNull()?.coerceIn(100, 2160)
      val deviceScaleFactor = System.getProperty("parikshan.video.deviceScaleFactor")?.toDoubleOrNull()?.takeIf { it > 0.0 && it <= 4.0 }
      
      return ParikshanVideoConfig(
        enabled = enabled,
        outputDir = outputDir,
        fps = fps,
        showCursor = showCursor,
        postRollMs = postRollMs,
        granularity = granularity,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        deviceScaleFactor = deviceScaleFactor
      )
    }
  }
}

enum class VideoGranularity {
    RUN, CLASS, TEST
}
