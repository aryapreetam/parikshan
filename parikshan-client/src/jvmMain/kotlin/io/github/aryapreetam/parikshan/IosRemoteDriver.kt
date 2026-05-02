package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URI

/**
 * JVM-side driver that talks to the Parikshan server running inside the iOS app.
 */
class IosRemoteDriver private constructor(
  private val baseUrl: String,
  private val sessionToken: String = System.getProperty("parikshan.token") ?: ""
) : TestDriver {

  // State is now managed in companion object to persist across driver instances

  override suspend fun send(command: Command): Response {
    // SECURITY: Sign the command with the global session token
    command.token = sessionToken
    
    if (command is Command.StartRecording) {
        return startHostRecording(command)
    }
    if (command is Command.StopRecording) {
        return stopHostRecording(command)
    }

    val json = ProtocolJson.encodeCommand(command)
    val responseJson = httpPost(json)
    val response = ProtocolJson.decodeResponse(responseJson)
    
    // ARTIFACT HANDSHAKE: If this was a screenshot, save the returned bytes to the host path
    if (command is Command.Screenshot && response is Response.NodeInfo) {
        val base64 = response.text ?: ""
        if (base64.isNotEmpty()) {
            val bytes = java.util.Base64.getDecoder().decode(base64)
            val hostFile = java.io.File(command.hostPath)
            hostFile.parentFile?.mkdirs()
            hostFile.writeBytes(bytes)
            return Response.Ok(command.id)
        }
    }
    
    return response
  }

  private fun startHostRecording(command: Command.StartRecording): Response {
    val udid = System.getProperty("parikshan.ios.udid") ?: "booted"
    val videoFile = java.io.File(command.path)
    videoFile.parentFile?.mkdirs()
    
    // Stop any existing recording
    stopHostRecording(Command.StopRecording(command.id, command.sessionName))
    
    activeVideoPaths[udid] = videoFile.absolutePath
    
    // Also proactively kill any existing simctl io recordVideo processes for this device
    cleanupSimctlRecording(udid)
    
    val pb = ProcessBuilder(
        "xcrun", "simctl", "io", udid, "recordVideo", "--codec=h264", "--force", videoFile.absolutePath
    )
    
    try {
        val process = pb.start()
        activeRecordingProcesses[udid] = process
        // Wait a bit to ensure it actually started
        Thread.sleep(2000) 
        if (process.isAlive == false) {
            val error = process.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            if (error.contains("Resource busy", ignoreCase = true)) {
                cleanupSimctlRecording(udid)
                Thread.sleep(1000)
                val retryProcess = pb.start()
                activeRecordingProcesses[udid] = retryProcess
                Thread.sleep(2000)
                if (retryProcess.isAlive == true) {
                    return Response.Ok(command.id)
                }
            }
            val finalError = process.errorStream?.bufferedReader()?.readText() ?: error
            return Response.Error(command.id, "Failed to start simctl recording (udid=$udid): $finalError")
        }
        return Response.Ok(command.id)
    } catch (e: Exception) {
        return Response.Error(command.id, "Exception starting simctl recording: ${e.message}")
    }
  }

  private fun cleanupSimctlRecording(udid: String) {
    try {
        // Try SIGINT first to let them finalize nicely
        ProcessBuilder("pkill", "-INT", "-f", "simctl io $udid recordVideo").start().waitFor()
        Thread.sleep(500)
        // Then SIGKILL for any stubborn ones
        ProcessBuilder("pkill", "-9", "-f", "simctl io $udid recordVideo").start().waitFor()
    } catch (e: Exception) {
        // Ignore pkill failures
    }
  }

  private fun stopHostRecording(command: Command.StopRecording): Response {
    val udid = System.getProperty("parikshan.ios.udid") ?: "booted"
    val process = activeRecordingProcesses.remove(udid)
    if (process == null) {
        return Response.Ok(command.id)
    }
    
    val videoPath = activeVideoPaths.remove(udid)
    
    try {
        // Send SIGINT for clean finalization
        val pid = process.pid()
        ProcessBuilder("kill", "-2", pid.toString()).start().waitFor()
        
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroy()
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
        
        // Post-process with ffmpeg to fix MP4 playback (faststart)
        if (videoPath != null && java.io.File(videoPath).exists()) {
            try {
                val tempFile = java.io.File(videoPath + ".tmp.mp4")
                val ffmpegPb = ProcessBuilder(
                    "ffmpeg", "-y", "-i", videoPath, "-c", "copy", "-movflags", "faststart", tempFile.absolutePath
                )
                val ffmpegProcess = ffmpegPb.start()
                if (ffmpegProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && ffmpegProcess.exitValue() == 0) {
                    java.io.File(videoPath).delete()
                    tempFile.renameTo(java.io.File(videoPath))
                } else {
                    val err = ffmpegProcess.errorStream.bufferedReader().readText()
                    System.err.println("ffmpeg post-processing failed: $err")
                    tempFile.delete()
                }
            } catch (e: Exception) {
                System.err.println("Exception during ffmpeg post-processing: ${e.message}")
            }
        }
    } catch (e: Exception) {
        process.destroyForcibly()
    }
    
    return Response.Ok(command.id)
  }

  override suspend fun close() {
    // No-op for in-app server
  }

  private fun httpPost(body: String): String {
    val url = URI(baseUrl).toURL()
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 30000
    
    conn.outputStream.use { it.write(body.toByteArray()) }
    
    if (conn.responseCode != 200) {
        val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
        error("iOS server returned error: $error")
    }
    
    return conn.inputStream.bufferedReader().readText()
  }

  companion object {
    private val activeRecordingProcesses = java.util.concurrent.ConcurrentHashMap<String, Process>()
    private val activeVideoPaths = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Create and connect to the iOS driver, waiting for the server to be ready.
     */
    suspend fun connect(
      config: IosDriverConfig = IosDriverConfig()
    ): IosRemoteDriver {
      val baseUrl = "http://${config.host}:${config.port}/"
      val driver = IosRemoteDriver(baseUrl)
      
      // Wait for the iOS server to become available
      val retries = 300
      repeat(retries) { attempt ->
        try {
          // send() is already token-aware, ensuring secure handshake
          val resp = driver.send(Command.Ping(id = "ping-connect"))
          if (resp is Response.Ok) return driver
        } catch (_: Throwable) {
          if (attempt < retries - 1) {
            delay(1000)
          }
        }
      }

      error("Could not connect to Parikshan iOS server at $baseUrl after $retries attempts")
    }
  }
}

data class IosDriverConfig(
  val host: String = "127.0.0.1",
  val port: Int = 9878
)
