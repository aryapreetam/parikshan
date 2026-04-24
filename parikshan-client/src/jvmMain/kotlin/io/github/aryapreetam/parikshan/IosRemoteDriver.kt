package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.client.ParikshanClientConfig
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
  private val baseUrl: String
) : TestDriver {

  override suspend fun send(command: Command): Response {
    val json = ProtocolJson.encodeCommand(command)
    val responseJson = httpPost(json)
    return ProtocolJson.decodeResponse(responseJson)
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

    conn.outputStream.use { os ->
      os.write(body.toByteArray())
      os.flush()
    }

    val responseCode = conn.responseCode
    val responseBody = if (responseCode in 200..299) {
      conn.inputStream.use { it.readBytes().decodeToString() }
    } else {
      val error = conn.errorStream?.use { it.readBytes().decodeToString() } ?: "HTTP $responseCode"
      throw RuntimeException("iOS server returned HTTP $responseCode: $error")
    }
    return responseBody
  }

  companion object {
    private fun configFromSystemProperties(): ParikshanClientConfig {
      val host = System.getProperty("parikshan.host") ?: "127.0.0.1"
      val port = System.getProperty("parikshan.port")?.toIntOrNull() ?: 9878
      return ParikshanClientConfig(host = host, port = port)
    }

    suspend fun connect(
      config: ParikshanClientConfig = configFromSystemProperties()
    ): IosRemoteDriver {
      val baseUrl = "http://${config.host}:${config.port}/"
      val driver = IosRemoteDriver(baseUrl)
      
      // Wait for the iOS server to become available
      val retries = 60
      repeat(retries) { attempt ->
        try {
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
