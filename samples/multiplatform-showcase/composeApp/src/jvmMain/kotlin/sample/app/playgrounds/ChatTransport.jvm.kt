package sample.app.playgrounds

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.BindException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Executors
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
actual object ChatTransport {
  private const val RELAY_PORT = 18089
  private val relayLock = Any()
  @Volatile
  private var relayServer: HttpServer? = null

  private fun resolveChatFile(): File {
    val dir = File(System.getProperty("java.io.tmpdir"), "parikshan_chat")
    dir.mkdirs()
    return File(dir, "shared_chat_messages.txt")
  }

  actual fun ensureRelayStarted() {
    synchronized(relayLock) {
      if (relayServer != null) return
      val server = try {
        HttpServer.create(InetSocketAddress("127.0.0.1", RELAY_PORT), 0)
      } catch (e: BindException) {
        if (isRelayHealthy()) return
        throw e
      }
      server.createContext("/parikshan-chat") { exchange ->
        try {
          val headers = exchange.responseHeaders
          headers.set("Access-Control-Allow-Origin", "*")
          headers.set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
          headers.set("Access-Control-Allow-Headers", "*")
          headers.set("Cross-Origin-Resource-Policy", "cross-origin")
          headers.set("Cache-Control", "no-cache")

          when (exchange.requestMethod.uppercase()) {
            "OPTIONS" -> {
              exchange.sendResponseHeaders(204, -1)
            }
            "GET" -> {
              val file = resolveChatFile()
              val body = synchronized(ChatTransport) {
                if (file.exists()) file.readBytes() else ByteArray(0)
              }
              headers.set("Content-Type", "text/plain; charset=utf-8")
              exchange.sendResponseHeaders(200, body.size.toLong())
              exchange.responseBody.use { it.write(body) }
            }
            "POST" -> {
              val payload = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
              if (payload.isNotEmpty()) {
                val file = resolveChatFile()
                val normalized = if (payload.endsWith("\n")) payload else "$payload\n"
                synchronized(ChatTransport) {
                  file.appendText(normalized, Charsets.UTF_8)
                }
              }
              val ok = "OK".toByteArray(Charsets.UTF_8)
              headers.set("Content-Type", "text/plain; charset=utf-8")
              exchange.sendResponseHeaders(200, ok.size.toLong())
              exchange.responseBody.use { it.write(ok) }
            }
            "DELETE" -> {
              clearMessages()
              val ok = "CLEARED".toByteArray(Charsets.UTF_8)
              headers.set("Content-Type", "text/plain; charset=utf-8")
              exchange.sendResponseHeaders(200, ok.size.toLong())
              exchange.responseBody.use { it.write(ok) }
            }
            else -> {
              exchange.sendResponseHeaders(405, -1)
            }
          }
        } finally {
          exchange.close()
        }
      }
      val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "sample-chat-relay").apply { isDaemon = true }
      }
      server.executor = executor
      server.start()
      relayServer = server
      Runtime.getRuntime().addShutdownHook(Thread {
        runCatching { server.stop(0) }
        runCatching { executor.shutdownNow() }
      })
    }
  }

  private fun isRelayHealthy(): Boolean = runCatching {
    val conn = URI("http://127.0.0.1:$RELAY_PORT/parikshan-chat").toURL().openConnection() as HttpURLConnection
    conn.connectTimeout = 500
    conn.readTimeout = 500
    conn.requestMethod = "GET"
    try {
      conn.responseCode == 200
    } finally {
      conn.disconnect()
    }
  }.getOrDefault(false)

  actual fun getCurrentInstanceId(): String {
    return System.getProperty("parikshan.instance.id")?.takeIf { it.isNotBlank() } ?: "User"
  }

  actual fun broadcastMessage(text: String, attachment: ChatFileAttachment?) {
    val file = resolveChatFile()
    val sender = getCurrentInstanceId()
    val timestamp = System.currentTimeMillis()
    val line = if (attachment != null) {
      val b64 = Base64.encode(attachment.fileBytes)
      "$sender\t$timestamp\t$text\t${attachment.fileName}\t$b64\n"
    } else {
      "$sender\t$timestamp\t$text\n"
    }
    synchronized(ChatTransport) {
      file.appendText(line, Charsets.UTF_8)
    }
  }

  actual fun getMessages(): List<ChatMessage> {
    val file = resolveChatFile()
    if (!file.exists()) return emptyList()
    return runCatching {
      synchronized(ChatTransport) {
        file.readLines(Charsets.UTF_8)
      }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
          val parts = line.split("\t")
          if (parts.size >= 3) {
            val attachment = if (parts.size >= 5 && parts[3].isNotBlank() && parts[4].isNotBlank()) {
              val bytes = runCatching { Base64.decode(parts[4]) }.getOrNull()
              if (bytes != null) ChatFileAttachment(fileName = parts[3], fileBytes = bytes) else null
            } else null
            ChatMessage(
              sender = parts[0],
              timestamp = parts[1].toLongOrNull() ?: 0L,
              text = parts[2],
              attachment = attachment
            )
          } else null
        }
    }.getOrDefault(emptyList())
  }

  actual fun clearMessages() {
    val file = resolveChatFile()
    synchronized(ChatTransport) {
      if (file.exists()) {
        runCatching { file.delete() }
      }
    }
  }
}
