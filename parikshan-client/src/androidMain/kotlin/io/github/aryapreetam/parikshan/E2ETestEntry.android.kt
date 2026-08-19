package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI

actual fun e2eTest(
  config: E2ETestConfig,
  block: suspend E2ETestScope.() -> Unit
) {
  val target = System.getProperty("parikshan.target")?.lowercase()
  if (!target.isNullOrBlank()) {
    runBlocking {
      val driver: TestDriver = when (target) {
        "ios" -> {
          val host = System.getProperty("parikshan.ios.host") ?: System.getProperty("parikshan.host") ?: "127.0.0.1"
          val port = System.getProperty("parikshan.ios.port")?.toIntOrNull() ?: System.getProperty("parikshan.port")?.toIntOrNull() ?: 9878
          RemoteHttpDriver("android-ios-bridge", "http://$host:$port/")
        }
        else -> {
          val host = System.getProperty("parikshan.android.host") ?: System.getProperty("parikshan.host") ?: "127.0.0.1"
          val port = System.getProperty("parikshan.android.port")?.toIntOrNull() ?: System.getProperty("parikshan.port")?.toIntOrNull() ?: 9879
          RemoteHttpDriver("android", "http://$host:$port/")
        }
      }
      e2eTest(driver = driver, config = config, block = block)
    }
  } else {
    error("Parikshan E2E tests run via the JVM test runner. Use ./gradlew e2eAndroidTest")
  }
}

private class RemoteHttpDriver(
  override val targetPlatform: String,
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
    // No-op
  }

  private fun httpPost(json: String): String {
    val url = URI.create(baseUrl).toURL()
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 60000

    conn.outputStream.use { os ->
      os.write(json.toByteArray(Charsets.UTF_8))
      os.flush()
    }

    val responseCode = conn.responseCode
    if (responseCode in 200..299) {
      return conn.inputStream.use { it.readBytes().decodeToString() }
    } else {
      val error = conn.errorStream?.use { it.readBytes().decodeToString() } ?: "HTTP $responseCode"
      throw RuntimeException("Parikshan server returned HTTP $responseCode: $error")
    }
  }
}
