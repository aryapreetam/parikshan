package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.client.ParikshanClientConfig
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URI

/**
 * JVM-side driver that communicates with the ParikshanAndroidServer
 * running inside the real Android app on the emulator/device.
 * Uses HTTP POST over an adb forwarded port (e.g. 9879).
 */
class AndroidRemoteDriver private constructor(
  private val baseUrl: String,
  private val sessionToken: String = System.getProperty("parikshan.token") ?: ""
) : TestDriver {

  // State is now managed in companion object to persist across driver instances

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

  private fun startHostRecording(command: Command.StartRecording): Response {
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
        // Wait a bit to ensure it started
        Thread.sleep(1000)
        if (process.isAlive == false) {
             val error = process.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
             return Response.Error(command.id, "Failed to start adb screenrecord: $error")
        }
        return Response.Ok(command.id)
    } catch (e: Exception) {
        return Response.Error(command.id, "Exception starting adb screenrecord: ${e.message}")
    }
  }

  private fun stopHostRecording(command: Command.StopRecording): Response {
    val serial = System.getProperty("parikshan.android.serial") ?: ""
    val adbPrefix = if (serial.isNotEmpty()) listOf("adb", "-s", serial) else listOf("adb")
    val stateKey = serial.ifEmpty { "default" }
    
    val process = activeRecordingProcesses.remove(stateKey)
    if (process != null) {
        try {
            // Try to find the PID of screenrecord on device
            val pidProcess = ProcessBuilder(adbPrefix + listOf("shell", "pidof", "screenrecord")).start()
            val pid = pidProcess.inputStream.bufferedReader().readText().trim()
            
            if (pid.isNotEmpty()) {
                // Gracefully stop specifically that screenrecord process
                ProcessBuilder(adbPrefix + listOf("shell", "kill", "-2", pid)).start().waitFor()
            } else {
                // Fallback to pkill if pidof fails or returns nothing
                ProcessBuilder(adbPrefix + listOf("shell", "pkill", "-2", "screenrecord")).start().waitFor()
            }
            
            // Wait for the adb shell process to finish
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroy()
            }
        } catch (e: Exception) {
            process.destroy()
        }
    }
    
    // Give the device a moment to finalize the file
    Thread.sleep(2000)
    
    // Pull the file from device to host
    val hostPath = activeVideoPaths.remove(stateKey)
    if (hostPath != null) {
        val hostFile = java.io.File(hostPath)
        hostFile.parentFile?.mkdirs()
        try {
            // Check if file exists on device first
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
                    // Success! Now remove it from device
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
    // The server lifecycle is managed by the Gradle plugin.
    // Do not send Shutdown, otherwise subsequent tests in the suite will fail to connect.
  }

  private fun httpPost(body: String): String {
    val url = URI(baseUrl).toURL()
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Connection", "close") // Android server uses close
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

      val retries = 90 // Android instrumentation can take a long time to boot

      // Wait for the Android server to become available
      repeat(retries) { attempt ->
        try {
          val resp = driver.send(Command.Ping(id = "ping-connect"))
          if (resp is Response.Ok) return driver
        } catch (_: Throwable) {
          if (attempt < retries - 1) {
            delay(config.connectRetryDelayMs)
          }
        }
      }

      error("Could not connect to Parikshan Android server at $baseUrl after $retries attempts")
    }
  }
}
