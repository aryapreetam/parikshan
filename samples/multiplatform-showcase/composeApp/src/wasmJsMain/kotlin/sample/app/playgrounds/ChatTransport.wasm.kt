package sample.app.playgrounds

import kotlinx.browser.window
import org.w3c.xhr.XMLHttpRequest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@JsName("Date")
private external object JsDate {
  fun now(): Double
}

@OptIn(ExperimentalEncodingApi::class)
actual object ChatTransport {
  private const val RELAY_ENDPOINT = "http://127.0.0.1:18089/parikshan-chat"

  actual fun ensureRelayStarted() {}

  actual fun getCurrentInstanceId(): String {
    val search = runCatching { window.location.search }.getOrNull().orEmpty()
    val match = Regex("parikshan_instance=([^&]+)").find(search)
    return match?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "WasmUser"
  }

  actual fun broadcastMessage(text: String, attachment: ChatFileAttachment?) {
    val sender = getCurrentInstanceId()
    val timestamp = runCatching { JsDate.now().toLong() }.getOrDefault(0L)
    val body = if (attachment != null) {
      val b64 = Base64.encode(attachment.fileBytes)
      "$sender\t$timestamp\t$text\t${attachment.fileName}\t$b64\n"
    } else {
      "$sender\t$timestamp\t$text\n"
    }
    runCatching {
      val xhr = XMLHttpRequest()
      xhr.open("POST", RELAY_ENDPOINT, false)
      xhr.setRequestHeader("Content-Type", "text/plain")
      xhr.send(body)
    }
  }

  actual fun getMessages(): List<ChatMessage> {
    return runCatching {
      val xhr = XMLHttpRequest()
      xhr.open("GET", RELAY_ENDPOINT, false)
      xhr.send()
      val response = xhr.responseText
      if (response.isBlank()) return emptyList()
      response.lines()
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
    runCatching {
      val xhr = XMLHttpRequest()
      xhr.open("DELETE", RELAY_ENDPOINT, false)
      xhr.send()
    }
  }
}
