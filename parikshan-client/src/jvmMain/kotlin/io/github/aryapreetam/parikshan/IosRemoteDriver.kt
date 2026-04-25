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

  override suspend fun send(command: Command): Response {
    // SECURITY: Sign the command with the global session token
    command.token = sessionToken
    
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
