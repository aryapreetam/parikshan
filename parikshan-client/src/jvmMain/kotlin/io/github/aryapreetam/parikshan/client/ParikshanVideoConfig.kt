package io.github.aryapreetam.parikshan.client

data class ParikshanVideoConfig(
  val enabled: Boolean,
  val outputDir: String,
  val fps: Int,
  val showCursor: Boolean,
  val stepDelayMs: Long
) {
  companion object {
    private const val DEFAULT_OUTPUT_DIR = "build/parikshan/videos"
    private const val DEFAULT_FPS = 1
    private const val DEFAULT_STEP_DELAY_MS = 350L

    fun fromSystemProperties(): ParikshanVideoConfig {
      val enabled = System.getProperty("parikshan.video.enabled")?.toBooleanStrictOrNull() ?: false
      val outputDir = System.getProperty("parikshan.video.outputDir") ?: DEFAULT_OUTPUT_DIR
      val fps = System.getProperty("parikshan.video.fps")?.toIntOrNull()?.coerceIn(1, 30) ?: DEFAULT_FPS
      val showCursor = System.getProperty("parikshan.video.showCursor")?.toBooleanStrictOrNull() ?: true
      val stepDelayMs =
        System.getProperty("parikshan.video.stepDelayMs")?.toLongOrNull()?.coerceIn(0L, 5_000L)
          ?: DEFAULT_STEP_DELAY_MS
      return ParikshanVideoConfig(
        enabled = enabled,
        outputDir = outputDir,
        fps = fps,
        showCursor = showCursor,
        stepDelayMs = stepDelayMs
      )
    }
  }
}
