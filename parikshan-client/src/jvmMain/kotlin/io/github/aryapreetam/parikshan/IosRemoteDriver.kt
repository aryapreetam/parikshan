package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.client.ParikshanClientConfig
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URI

/**
 * JVM-side driver that communicates with the ParikshanIosServer
 * running inside the real iOS app on the simulator.
 * Uses HTTP POST (simpler than WebSocket for cross-process communication
 * where the server is a lightweight POSIX socket server on iOS).
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
    // Send shutdown to stop the server gracefully
    runCatching {
      send(Command.Shutdown(id = "shutdown"))
    }
  }

  private fun httpPost(body: String): String {
    val url = URI(baseUrl).toURL()
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Connection", "keep-alive")
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
      // iOS server is a raw POSIX socket HTTP server — no path routing
      val baseUrl = "http://${config.host}:${config.port}/"
      val driver = IosRemoteDriver(baseUrl)

      // Wait for the iOS server to become available
      repeat(config.connectRetries) { attempt ->
        try {
          val resp = driver.send(Command.Ping(id = "ping-connect"))
          if (resp is Response.Ok) return driver
        } catch (_: Throwable) {
          if (attempt < config.connectRetries - 1) {
            delay(config.connectRetryDelayMs)
          }
        }
      }

      error("Could not connect to Parikshan iOS server at $baseUrl after ${config.connectRetries} attempts")
    }
  }
}
