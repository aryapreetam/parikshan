package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random

class ParikshanClient(
  private val config: ParikshanClientConfig = ParikshanClientConfig()
) {
  private val httpClient =
    HttpClient(CIO) {
      install(WebSockets)
      install(HttpTimeout) {
        requestTimeoutMillis = config.requestTimeoutMs
        connectTimeoutMillis = config.requestTimeoutMs
      }
    }

  private var session: DefaultClientWebSocketSession? = null

  suspend fun connect() {
    repeat(config.connectRetries) { attempt ->
      try {
        val newSession = httpClient.webSocketSession(urlString = url())
        session = newSession
        val pingResponse = send(Command.Ping(id = nextId()))
        if (pingResponse is Response.Ok) {
          return
        }
        disconnect()
      } catch (cancellationException: CancellationException) {
        throw cancellationException
      } catch (_: Throwable) {
        disconnect()
        if (attempt < config.connectRetries - 1) {
          delay(config.connectRetryDelayMs)
        }
      }
    }

    error("Could not connect to Parikshan server at ${url()} after ${config.connectRetries} attempts")
  }

  suspend fun send(command: Command): Response {
    val activeSession = checkNotNull(session) {
      "Parikshan client is not connected. Call connect() before sending commands."
    }

    activeSession.send(Frame.Text(ProtocolJson.encodeCommand(command)))

    val frame = activeSession.incoming.receive()
    val payload =
      when (frame) {
        is Frame.Text -> frame.readText()
        is Frame.Close -> error("Server closed connection while waiting for response")
        else -> error("Unsupported websocket frame type: $frame")
      }

    return ProtocolJson.decodeResponse(payload)
  }

  suspend fun disconnect() {
    session?.close()
    session = null
    httpClient.close()
  }

  private fun url(): String =
    "ws://${config.host}:${config.port}${config.path}"

  private fun nextId(): String =
    "ping-${Random.nextLong().toString(16)}"
}
