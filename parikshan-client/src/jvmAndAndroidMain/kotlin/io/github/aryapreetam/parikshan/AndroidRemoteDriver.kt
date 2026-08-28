package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.client.ParikshanClientConfig
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URI

/**
 * JVM/Android-side driver that communicates with the ParikshanAndroidServer
 * running inside the real Android app on the emulator/device.
 * Uses HTTP POST over an adb forwarded port (e.g. 9879).
 */
internal class AndroidRemoteDriver private constructor(
  private val baseUrl: String,
  private val sessionToken: String = System.getProperty("parikshan.token") ?: ""
) : TestDriver {
  override val targetPlatform: String = "android"

  override suspend fun send(command: Command): Response {
    command.token = sessionToken

    if (command is Command.StartRecording) {
      return startHostRecording(command)
    }
    if (command is Command.StopRecording) {
      return stopHostRecording(command)
    }

    val json = ProtocolJson.encodeCommand(command)
    val responseJson = httpPost(json)
    return ProtocolJson.decodeResponse(responseJson)
  }

  private suspend fun startHostRecording(command: Command.StartRecording): Response {
    val serial = System.getProperty("parikshan.android.serial") ?: ""
    val adbPrefix = if (serial.isNotEmpty()) listOf("adb", "-s", serial) else listOf("adb")
    val stateKey = serial.ifEmpty { "default" }

    // Stop any existing recording
    stopHostRecording(Command.StopRecording(command.id, command.sessionName))

    activeVideoPaths[stateKey] = command.path

    // Clean up any existing file on device
    ProcessBuilder(adbPrefix + listOf("shell", "rm", "/data/local/tmp/parikshan_video.mp4")).start().waitFor()

    val pb = ProcessBuilder(adbPrefix + listOf("shell", "screenrecord", "/data/local/tmp/parikshan_video.mp4"))
    try {
      val process = pb.start()
      activeRecordingProcesses[stateKey] = process
      // Check if process failed to start within a brief latch (100ms)
      delay(100)
      if (!process.isAlive) {
        val error = process.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
        return Response.Error(command.id, "Failed to start adb screenrecord: $error")
      }
      return Response.Ok(command.id)
    } catch (e: Exception) {
      return Response.Error(command.id, "Exception starting adb screenrecord: ${e.message}")
    }
  }

  private suspend fun stopHostRecording(command: Command.StopRecording): Response {
    val serial = System.getProperty("parikshan.android.serial") ?: ""
    val adbPrefix = if (serial.isNotEmpty()) listOf("adb", "-s", serial) else listOf("adb")
    val stateKey = serial.ifEmpty { "default" }

    val process = activeRecordingProcesses.remove(stateKey)
    if (process != null) {
      try {
        val pidProcess = ProcessBuilder(adbPrefix + listOf("shell", "pidof", "screenrecord")).start()
        val pidOutput = pidProcess.inputStream.bufferedReader().readText().trim()

        if (pidOutput.isNotEmpty()) {
          pidOutput.split("\\s+".toRegex()).filter { it.isNotEmpty() }.forEach { pid ->
            ProcessBuilder(adbPrefix + listOf("shell", "kill", "-2", pid)).start().waitFor()
          }
        } else {
          ProcessBuilder(adbPrefix + listOf("shell", "pkill", "-2", "screenrecord")).start().waitFor()
        }

        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
          process.destroy()
        }

        // Wait until screenrecord terminates on device (up to 3 seconds in 50ms intervals)
        var remainingAttempts = 60
        while (remainingAttempts > 0) {
          val checkPid = ProcessBuilder(adbPrefix + listOf("shell", "pidof", "screenrecord")).start()
          val runningPids = checkPid.inputStream.bufferedReader().readText().trim()
          if (runningPids.isBlank()) break
          delay(50)
          remainingAttempts--
        }
      } catch (e: Exception) {
        process.destroy()
      }
    }

    val hostPath = activeVideoPaths.remove(stateKey)
    if (hostPath != null) {
      val hostFile = java.io.File(hostPath)
      hostFile.parentFile?.mkdirs()
      try {
        val checkFile = ProcessBuilder(adbPrefix + listOf("shell", "ls", "/data/local/tmp/parikshan_video.mp4"))
          .start()
          .waitFor()

        if (checkFile == 0) {
          val pullPb = ProcessBuilder(adbPrefix + listOf("pull", "/data/local/tmp/parikshan_video.mp4", hostFile.absolutePath))
          val pullProcess = pullPb.start()
          val pullResult = pullProcess.waitFor()
          if (pullResult != 0) {
            val error = pullProcess.errorStream.bufferedReader().readText()
            System.err.println("Failed to pull video from Android device: exit code $pullResult. Error: $error")
          } else {
            ProcessBuilder(adbPrefix + listOf("shell", "rm", "/data/local/tmp/parikshan_video.mp4")).start().waitFor()
          }
        } else {
          System.err.println("Video file /data/local/tmp/parikshan_video.mp4 not found on Android device.")
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }

    return Response.Ok(command.id)
  }

  override suspend fun close() {
  }

  private fun httpPost(body: String): String {
    val url = URI(baseUrl).toURL()
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Connection", "close")
    conn.doOutput = true
    conn.connectTimeout = 10_000
    conn.readTimeout = 30_000

    conn.outputStream.use { os ->
      os.write(body.toByteArray())
      os.flush()
    }

    val responseCode = conn.responseCode
    val responseBody = if (responseCode in 200..299) {
      conn.inputStream.use { it.readBytes().decodeToString() }
    } else {
      val error = conn.errorStream?.use { it.readBytes().decodeToString() } ?: "HTTP $responseCode"
      throw RuntimeException("Android server returned HTTP $responseCode: $error")
    }
    return responseBody
  }

  companion object {
    private val activeRecordingProcesses = java.util.concurrent.ConcurrentHashMap<String, Process>()
    private val activeVideoPaths = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun configFromSystemProperties(): ParikshanClientConfig {
      val host = System.getProperty("parikshan.host") ?: "127.0.0.1"
      val port = System.getProperty("parikshan.port")?.toIntOrNull() ?: 9879
      return ParikshanClientConfig(host = host, port = port)
    }

    suspend fun connect(
      config: ParikshanClientConfig = configFromSystemProperties()
    ): AndroidRemoteDriver {
      val baseUrl = "http://${config.host}:${config.port}/"
      val driver = AndroidRemoteDriver(baseUrl)

      val retries = 300

      repeat(retries) { attempt ->
        try {
          val resp = driver.send(Command.Ping(id = "ping-connect"))
          if (resp is Response.Ok) {
            println("Parikshan: Connected to Android server at $baseUrl successfully after ${attempt + 1} attempts.")
            return driver
          }
        } catch (_: Throwable) {
          if ((attempt + 1) % 15 == 0) {
            println("Parikshan: Still waiting for Android server to start at $baseUrl (attempt ${attempt + 1}/$retries)...")
          }
          if (attempt < retries - 1) {
            delay(config.connectRetryDelayMs)
          }
        }
      }

      error("Could not connect to Parikshan Android server at $baseUrl after $retries attempts")
    }
  }
}
