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
    stop() // stop any existing active recording session

    val simpleName = sessionName.substringAfterLast('.').replace('$', '_')
    val file = File(outputDirectory, "$simpleName.mp4").absoluteFile
    file.parentFile?.mkdirs()
    activePath = file.absolutePath

    cleanupSimctlRecording(udid)

    val pb = ProcessBuilder(
      "xcrun", "simctl", "io", udid, "recordVideo", "--codec=h264", "--force", file.absolutePath
    )

    try {
      var process = pb.start()
      activeProcess = process
      kotlinx.coroutines.delay(150)
      if (!process.isAlive) {
        val error = process.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
        if (error.contains("Resource busy", ignoreCase = true)) {
          cleanupSimctlRecording(udid)
          kotlinx.coroutines.delay(150)
          process = pb.start()
          activeProcess = process
          kotlinx.coroutines.delay(150)
        }
        if (!process.isAlive) {
          val finalError = process.errorStream?.bufferedReader()?.readText() ?: error
          System.err.println("IosVideoRecorder: Failed to start simctl recording (udid=$udid): $finalError")
        }
      }
    } catch (e: Exception) {
      System.err.println("IosVideoRecorder: Exception starting simctl recording: ${e.message}")
    }
  }

  private suspend fun cleanupSimctlRecording(udid: String) {
    try {
      ProcessBuilder("pkill", "-INT", "-f", "simctl io $udid recordVideo").start().waitFor()
      kotlinx.coroutines.delay(100)
      ProcessBuilder("pkill", "-9", "-f", "simctl io $udid recordVideo").start().waitFor()
    } catch (_: Exception) {
    }
  }

  override suspend fun stop(): String? {
    val path = activePath ?: return null
    activePath = null

    val process = activeProcess
    activeProcess = null

    if (postRollMs > 0) {
      kotlinx.coroutines.delay(postRollMs)
    } else {
      kotlinx.coroutines.delay(1000)
    }

    try {
      cleanupSimctlRecording(udid)
      if (process != null) {
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroy()
        }
      }
    } catch (e: Exception) {
      process?.destroy()
    }

    val hostFile = File(path)
    if (hostFile.exists()) {
      try {
        val tempFile = File(path + ".tmp.mp4")
        val ffmpegPb = ProcessBuilder(
          "ffmpeg", "-y", "-i", path, "-c", "copy", "-movflags", "faststart", tempFile.absolutePath
        )
        val ffmpegProcess = ffmpegPb.start()
        if (ffmpegProcess.waitFor(10, TimeUnit.SECONDS) && ffmpegProcess.exitValue() == 0) {
          hostFile.delete()
          tempFile.renameTo(hostFile)
        } else {
          tempFile.delete()
        }
      } catch (_: Exception) {
      }
    }

    return path
  }
}
