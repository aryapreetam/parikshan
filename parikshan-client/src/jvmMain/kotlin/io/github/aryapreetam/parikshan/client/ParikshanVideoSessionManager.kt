package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.TestDriver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

internal object ParikshanVideoSessionManager {
  private val lock = Mutex()
  @Volatile private var activeClassName: String? = null
  @Volatile private var activeRecorder: VideoRecorder? = null
  @Volatile private var sessionRecordingStarted = false
  private val shutdownHookInstalled = AtomicBoolean(false)
  @Volatile private var activeOutputDir: String? = null

  private fun registerVideoPath(path: String) {
    println("[PARIKSHAN_VIDEO_PATH] $path")
    val outDir = activeOutputDir ?: return
    try {
      val indexFile = java.io.File(outDir, "video-index.txt")
      indexFile.parentFile?.mkdirs()
      synchronized(this) {
        indexFile.appendText("$path\n")
      }
    } catch (e: Exception) {
      System.err.println("WARN: Failed to write to video-index.txt: ${e.message}")
    }
  }

  fun updateVirtualCursor(x: Double, y: Double) {
  }

  fun pauseRecording() {
    (activeRecorder as? DesktopVideoRecorder)?.pause()
  }

  fun resumeRecording(newDriver: TestDriver) {
    val recorder = activeRecorder as? DesktopVideoRecorder ?: return
    recorder.updateDriver(newDriver)
    recorder.resume()
  }

  suspend fun beforeScenario(
    driver: TestDriver,
    clientConfig: ParikshanClientConfig,
    config: ParikshanVideoConfig,
    className: String,
    methodName: String
  ) {
    if (!config.enabled) {
      return
    }

    activeOutputDir = config.outputDir

    if (shutdownHookInstalled.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(Thread({
        val target = System.getProperty("parikshan.target")?.lowercase()
        // Wasm target manages its own video lifecycle via WasmDriver's shutdown hook.
        if (target == "wasm" || target == "web") {
          return@Thread
        }

        val recorder = activeRecorder
        activeRecorder = null
        if (recorder != null) {
          runBlocking {
            runCatching {
              val path = recorder.stop()
              if (path != null) {
                registerVideoPath(path)
              }
            }
          }
        }
      }, "parikshan-video-session-shutdown"))
    }

    lock.withLock {
      if (config.granularity == VideoGranularity.RUN) {
        if (!sessionRecordingStarted) {
          val target = System.getProperty("parikshan.target")?.lowercase() ?: "desktop"
          val recorder = VideoRecorderFactory.create(target, driver, config)
          activeRecorder = recorder
          recorder.start("e2e_session", config.outputDir)
          sessionRecordingStarted = true
        } else {
          val recorder = activeRecorder
          if (recorder is DesktopVideoRecorder) {
            recorder.updateDriver(driver)
          }
        }
        return
      }

      if (config.granularity == VideoGranularity.CLASS) {
        if (activeClassName == className) {
          val recorder = activeRecorder
          if (recorder is DesktopVideoRecorder) {
            recorder.updateDriver(driver)
          }
          return
        }
        val previousRecorder = activeRecorder
        activeRecorder = null
        if (previousRecorder != null) {
          val path = previousRecorder.stop()
          if (path != null) {
            registerVideoPath(path)
          }
        }
        val target = System.getProperty("parikshan.target")?.lowercase() ?: "desktop"
        val recorder = VideoRecorderFactory.create(target, driver, config)
        activeRecorder = recorder
        recorder.start(className, config.outputDir)
        activeClassName = className
        return
      }

      if (config.granularity == VideoGranularity.TEST) {
        val previousRecorder = activeRecorder
        activeRecorder = null
        if (previousRecorder != null) {
          val path = previousRecorder.stop()
          if (path != null) {
            registerVideoPath(path)
          }
        }
        val target = System.getProperty("parikshan.target")?.lowercase() ?: "desktop"
        val recorder = VideoRecorderFactory.create(target, driver, config)
        activeRecorder = recorder
        recorder.start("${className}_$methodName", config.outputDir)
      }
    }
  }

  suspend fun afterScenario(
    driver: TestDriver,
    config: ParikshanVideoConfig,
    className: String,
    methodName: String
  ) {
    if (!config.enabled) {
      return
    }

    lock.withLock {
      if (config.granularity == VideoGranularity.TEST) {
        val recorder = activeRecorder
        activeRecorder = null
        if (recorder != null) {
          val path = recorder.stop()
          if (path != null) {
            registerVideoPath(path)
          }
        }
      }
    }
  }
}
