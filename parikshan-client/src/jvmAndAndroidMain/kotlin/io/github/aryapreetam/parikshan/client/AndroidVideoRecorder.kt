package io.github.aryapreetam.parikshan.client

import java.io.File
import java.util.concurrent.TimeUnit

internal class AndroidVideoRecorder(
  private val serial: String,
  private val postRollMs: Long
) : VideoRecorder {
  private var activePath: String? = null
  private var activeProcess: Process? = null

  private val adbPrefix: List<String>
    get() = if (serial.isNotEmpty()) listOf("adb", "-s", serial) else listOf("adb")

  override suspend fun start(sessionName: String, outputDirectory: String) {
    stop() // stop any existing active recording session

    val simpleName = sessionName.substringAfterLast('.').replace('$', '_')
    val file = File(outputDirectory, "$simpleName.mp4").absoluteFile
    file.parentFile?.mkdirs()
    activePath = file.absolutePath

    // Clean up any existing file on device to avoid resource conflicts
    runCatching {
      ProcessBuilder(adbPrefix + listOf("shell", "rm", "-f", "/data/local/tmp/parikshan_video.mp4")).start().waitFor()
    }

    val pb = ProcessBuilder(adbPrefix + listOf("shell", "screenrecord", "/data/local/tmp/parikshan_video.mp4"))
    try {
      val process = pb.start()
      activeProcess = process
      // Check if process failed to start within a brief latch (100ms)
      kotlinx.coroutines.delay(100)
      if (!process.isAlive) {
        val error = process.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
        System.err.println("AndroidVideoRecorder: Failed to start adb screenrecord: $error")
      }
    } catch (e: Exception) {
      System.err.println("AndroidVideoRecorder: Exception starting adb screenrecord: ${e.message}")
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
      val pidProcess = ProcessBuilder(adbPrefix + listOf("shell", "pidof", "screenrecord")).start()
      val pidOutput = pidProcess.inputStream.bufferedReader().readText().trim()
      val pids = pidOutput.split(Regex("\\s+")).filter { it.isNotBlank() }

      if (pids.isNotEmpty()) {
        for (pid in pids) {
          ProcessBuilder(adbPrefix + listOf("shell", "kill", "-2", pid)).start().waitFor()
        }
      } else {
        ProcessBuilder(adbPrefix + listOf("shell", "pkill", "-2", "screenrecord")).start().waitFor()
      }
      if (process != null) {
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroy()
        }
      }

      // Wait until screenrecord terminates on device (up to 3 seconds in 50ms intervals)
      var remainingAttempts = 60
      while (remainingAttempts > 0) {
        val checkPid = ProcessBuilder(adbPrefix + listOf("shell", "pidof", "screenrecord")).start()
        val runningPids = checkPid.inputStream.bufferedReader().readText().trim()
        if (runningPids.isBlank()) break
        kotlinx.coroutines.delay(50)
        remainingAttempts--
      }
    } catch (e: Exception) {
      process?.destroy()
    }

    val hostFile = File(path)
    hostFile.parentFile?.mkdirs()
    try {
      val checkFile = ProcessBuilder(adbPrefix + listOf("shell", "ls", "-l", "/data/local/tmp/parikshan_video.mp4"))
        .start()
        .waitFor()

      if (checkFile == 0) {
        val pullProcess = ProcessBuilder(adbPrefix + listOf("pull", "/data/local/tmp/parikshan_video.mp4", hostFile.absolutePath)).start()
        val pullResult = pullProcess.waitFor()
        if (pullResult != 0) {
          val error = pullProcess.errorStream.bufferedReader().readText()
          System.err.println("AndroidVideoRecorder: Failed to pull video: $error")
        } else {
          ProcessBuilder(adbPrefix + listOf("shell", "rm", "-f", "/data/local/tmp/parikshan_video.mp4")).start().waitFor()
        }
      } else {
        System.err.println("AndroidVideoRecorder: Video file /data/local/tmp/parikshan_video.mp4 not found on device.")
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    return path
  }
}
