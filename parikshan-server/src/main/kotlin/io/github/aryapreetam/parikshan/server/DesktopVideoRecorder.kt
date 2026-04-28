package io.github.aryapreetam.parikshan.server

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.jcodec.api.awt.AWTSequenceEncoder

internal data class DesktopVideoSessionConfig(
  val sessionName: String,
  val outputPath: String,
  val fps: Int,
  val showCursor: Boolean
)

internal class DesktopVideoRecorder(
  private val semanticsAccessor: DesktopSemanticsAccessor
) {
  private val encoderLock = Any()
  private val robot = Robot()
  private val frameErrors = AtomicLong(0)
  private val firstFrameErrorLogged = AtomicBoolean(false)
  private val virtualCursorScreenPoint = AtomicReference<Point?>(null)
  private var executor: ScheduledExecutorService? = null
  private var encoder: AWTSequenceEncoder? = null
  private var activeConfig: DesktopVideoSessionConfig? = null
  private var stableCaptureBounds: Rectangle? = null

  val isRecording: Boolean
    get() = synchronized(encoderLock) {
      activeConfig != null && encoder != null
    }

  fun start(config: DesktopVideoSessionConfig) {
    stop()
    virtualCursorScreenPoint.set(null)

    val outputFile = File(config.outputPath)
    outputFile.parentFile?.mkdirs()
    if (outputFile.exists()) {
      outputFile.delete()
    }

    val frameRate = config.fps.coerceIn(1, 30)
    encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, frameRate)
    activeConfig = config
    stableCaptureBounds = null
    frameErrors.set(0)
    firstFrameErrorLogged.set(false)

    val frameIntervalMs = (1000L / frameRate).coerceAtLeast(16L)
    executor =
      Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "parikshan-video-${config.sessionName}").apply {
          isDaemon = true
        }
      }.also { scheduler ->
        scheduler.scheduleAtFixedRate(
          { safeCaptureFrame() },
          0L,
          frameIntervalMs,
          TimeUnit.MILLISECONDS
        )
      }
  }

  fun stop() {
    val currentExecutor = executor
    executor = null
    currentExecutor?.shutdown()
    runCatching {
      currentExecutor?.awaitTermination(2, TimeUnit.SECONDS)
    }
    currentExecutor?.shutdownNow()

    synchronized(encoderLock) {
      runCatching {
        encoder?.finish()
      }.onFailure { throwable ->
        logError("Parikshan video recorder failed to finalize video", throwable)
      }
      encoder = null
      activeConfig = null
      stableCaptureBounds = null
    }
    virtualCursorScreenPoint.set(null)
  }

  fun captureNow() {
    val isRecording =
      synchronized(encoderLock) {
        activeConfig != null && encoder != null
      }
    if (!isRecording) {
      return
    }
    safeCaptureFrame()
  }

  fun setVirtualCursor(
    xOnScreen: Int,
    yOnScreen: Int
  ) {
    virtualCursorScreenPoint.set(Point(xOnScreen, yOnScreen))
  }

  private fun safeCaptureFrame() {
    runCatching {
      captureFrame()
    }.onFailure { throwable ->
      recordFrameError(throwable)
    }
  }

  private fun captureFrame() {
    val configSnapshot =
      synchronized(encoderLock) {
        activeConfig
      } ?: return

    val bounds = resolveCaptureBounds() ?: return
    val image = runCatching { robot.createScreenCapture(bounds) }.getOrElse { throwable ->
      recordFrameError(throwable)
      return
    }
    if (configSnapshot.showCursor) {
      drawCursorOverlay(image = image, captureBounds = bounds)
    }

    synchronized(encoderLock) {
      val currentEncoder = encoder ?: return
      runCatching {
        currentEncoder.encodeImage(image)
      }.onFailure { throwable ->
        recordFrameError(throwable)
        return
      }
    }
  }

  private fun resolveCaptureBounds(): Rectangle? {
    val latestBounds = runCatching { semanticsAccessor.windowBoundsOnScreen() }.getOrElse { throwable ->
      recordFrameError(throwable)
      return null
    }
    val normalized = normalizeBounds(latestBounds)
    if (normalized.width <= 1 || normalized.height <= 1) {
      return null
    }

    return synchronized(encoderLock) {
      val existing = stableCaptureBounds
      if (existing == null) {
        stableCaptureBounds = Rectangle(normalized)
      } else {
        // Keep encoder dimensions fixed; only follow window movement on screen.
        existing.x = normalized.x
        existing.y = normalized.y
      }
      Rectangle(stableCaptureBounds)
    }
  }

  private fun normalizeBounds(bounds: Rectangle): Rectangle {
    var width = bounds.width
    var height = bounds.height
    if (width % 2 != 0) {
      width -= 1
    }
    if (height % 2 != 0) {
      height -= 1
    }
    return Rectangle(bounds.x, bounds.y, width, height)
  }

  private fun recordFrameError(throwable: Throwable) {
    frameErrors.incrementAndGet()
    if (firstFrameErrorLogged.compareAndSet(false, true)) {
      logError("Parikshan video recorder frame capture error", throwable)
    }
  }

  private fun logError(
    prefix: String,
    throwable: Throwable
  ) {
    val message = throwable.message ?: throwable::class.simpleName.orEmpty()
    System.err.println("$prefix: $message")
    throwable.printStackTrace(System.err)
  }

  private fun drawCursorOverlay(
    image: BufferedImage,
    captureBounds: Rectangle
  ) {
    val pointer = virtualCursorScreenPoint.get() ?: return
    if (!captureBounds.contains(pointer)) {
      return
    }

    val x = pointer.x - captureBounds.x
    val y = pointer.y - captureBounds.y

    val graphics = image.createGraphics()
    try {
      graphics.stroke = BasicStroke(2f)
      graphics.color = Color(255, 48, 48, 230)
      graphics.drawOval(x - 10, y - 10, 20, 20)
      graphics.drawLine(x - 14, y, x + 14, y)
      graphics.drawLine(x, y - 14, x, y + 14)
    } finally {
      graphics.dispose()
    }
  }
}
