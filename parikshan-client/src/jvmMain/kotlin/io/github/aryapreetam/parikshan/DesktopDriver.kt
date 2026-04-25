package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import java.net.HttpURLConnection
import java.net.URI

/**
 * JVM-side driver that talks to the Parikshan server running inside a Desktop app.
 */
class DesktopDriver(
  private val host: String = "127.0.0.1",
  private val port: Int = 9877,
  private val sessionToken: String = System.getProperty("parikshan.token") ?: ""
) : TestDriver {

  override suspend fun send(command: Command): Response {
    // SECURITY: Sign the command with the global session token
    command.token = sessionToken
    
    val json = ProtocolJson.encodeCommand(command)
    
    var lastError: Exception? = null
    repeat(30) { attempt ->
      try {
        val responseJson = httpPost(json)
        return ProtocolJson.decodeResponse(responseJson)
      } catch (e: Exception) {
        lastError = e
        if (attempt < 29) kotlinx.coroutines.delay(1000)
      }
    }
    throw lastError ?: RuntimeException("Failed to send command after 30 attempts")
  }

  override suspend fun close() {
    // In Gradle-managed Desktop E2E, the app process is managed by the plugin.
    // Sending Shutdown here would kill the app after the first test finishes,
    // causing subsequent tests to fail with ConnectException.
    /*
    try {
        send(Command.Shutdown(id = "shutdown-desktop"))
    } catch (e: Exception) { // ignore // }
    */
  }

  private fun httpPost(body: String): String {
    val url = URI("http://$host:$port/").toURL()
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 30000
    
    conn.outputStream.use { it.write(body.toByteArray()) }
    
    if (conn.responseCode != 200) {
        val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
        error("Desktop server returned error: $error")
    }
    
    return conn.inputStream.bufferedReader().readText()
  }
}
