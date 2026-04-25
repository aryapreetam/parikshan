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

  override suspend fun send(command: Command): Response {
    command.token = sessionToken
    val json = ProtocolJson.encodeCommand(command)
    val responseJson = httpPost(json)
    return ProtocolJson.decodeResponse(responseJson)
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
