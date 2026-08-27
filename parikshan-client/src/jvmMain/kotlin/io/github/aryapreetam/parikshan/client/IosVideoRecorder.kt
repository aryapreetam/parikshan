package io.github.aryapreetam.parikshan.client

import java.io.File
import java.util.concurrent.TimeUnit

internal class IosVideoRecorder(
  private val udid: String,
  private val postRollMs: Long
) : VideoRecorder {
  private var activePath: String? = null
  private var activeProcess: Process? = null

  override suspend fun start(sessionName: String, outputDirectory: String) {
    stop() // stop any existing session

    val simpleName = sessionName.substringAfterLast('.').replace('$', '_')
    val file = File(outputDirectory, "$simpleName.mp4").absoluteFile
    file.parentFile?.mkdirs()
    activePath = file.absolutePath

    cleanupSimctlRecording()

    val pb = ProcessBuilder(
      "xcrun", "simctl", "io", udid, "recordVideo", "--codec=h264", "--force", file.absolutePath
    )

    try {
      val process = pb.start()
      activeProcess = process
      Thread.sleep(2000)
      if (!process.isAlive) {
        val error = process.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
        if (error.contains("Resource busy", ignoreCase = true)) {
          cleanupSimctlRecording()
          Thread.sleep(1000)
          val retryProcess = pb.start()
          activeProcess = retryProcess
          Thread.sleep(2000)
          if (!retryProcess.isAlive) {
            val finalError = retryProcess.errorStream?.bufferedReader()?.readText() ?: error
            System.err.println("IosVideoRecorder: Failed to start simctl recording (udid=$udid): $finalError")
          }
        } else {
          System.err.println("IosVideoRecorder: Failed to start simctl recording (udid=$udid): $error")
        }
      }
    } catch (e: Exception) {
      System.err.println("IosVideoRecorder: Exception starting simctl recording: ${e.message}")
    }
  }

  private fun cleanupSimctlRecording() {
    try {
      ProcessBuilder("pkill", "-INT", "-f", "simctl io $udid recordVideo").start().waitFor()
      Thread.sleep(500)
      ProcessBuilder("pkill", "-9", "-f", "simctl io $udid recordVideo").start().waitFor()
    } catch (e: Exception) {
      // Ignore pkill errors
    }
  }

  override suspend fun stop(): String? {
    val path = activePath ?: return null
    activePath = null

    val process = activeProcess
    activeProcess = null

    if (process != null) {
      try {
        val pid = process.pid()
        ProcessBuilder("kill", "-2", pid.toString()).start().waitFor()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroy()
          if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
          }
        }
      } catch (e: Exception) {
        process.destroy()
      }
    }

    if (postRollMs > 0) {
      runCatching { Thread.sleep(postRollMs) }
    }

    if (path.isNotEmpty() && File(path).exists()) {
      try {
        val tempFile = File(path + ".tmp.mp4")
        val ffmpegPb = ProcessBuilder(
          "ffmpeg", "-y", "-i", path, "-c", "copy", "-movflags", "faststart", tempFile.absolutePath
        )
        val ffmpegProcess = ffmpegPb.start()
        if (ffmpegProcess.waitFor(10, TimeUnit.SECONDS) && ffmpegProcess.exitValue() == 0) {
          File(path).delete()
          tempFile.renameTo(File(path))
        } else {
          tempFile.delete()
        }
      } catch (e: Exception) {
        // ffmpeg is optional or failed
      }
    }

    return path
  }
}
