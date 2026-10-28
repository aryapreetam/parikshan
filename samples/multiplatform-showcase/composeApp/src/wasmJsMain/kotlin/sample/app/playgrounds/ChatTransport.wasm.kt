package sample.app.playgrounds

import kotlinx.browser.window
import org.w3c.xhr.XMLHttpRequest

@JsName("Date")
private external object JsDate {
  fun now(): Double
}

actual object ChatTransport {
  actual fun getCurrentInstanceId(): String {
    val search = runCatching { window.location.search }.getOrNull().orEmpty()
    val match = Regex("parikshan_instance=([^&]+)").find(search)
    return match?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "WasmUser"
  }

  actual fun broadcastMessage(text: String) {
    val sender = getCurrentInstanceId()
    val timestamp = runCatching { JsDate.now().toLong() }.getOrDefault(0L)
    val body = "$sender\t$timestamp\t$text\n"
    runCatching {
      val xhr = XMLHttpRequest()
      xhr.open("POST", "/parikshan-chat", false)
      xhr.setRequestHeader("Content-Type", "text/plain")
      xhr.send(body)
    }
  }

  actual fun getMessages(): List<ChatMessage> {
    return runCatching {
      val xhr = XMLHttpRequest()
      xhr.open("GET", "/parikshan-chat", false)
      xhr.send()
      val response = xhr.responseText
      if (response.isBlank()) return emptyList()
      response.lines()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
          val parts = line.split("\t", limit = 3)
          if (parts.size >= 3) {
            ChatMessage(
              sender = parts[0],
              timestamp = parts[1].toLongOrNull() ?: 0L,
              text = parts[2]
            )
          } else null
        }
    }.getOrDefault(emptyList())
  }

  actual fun clearMessages() {
    runCatching {
      val xhr = XMLHttpRequest()
      xhr.open("DELETE", "/parikshan-chat", false)
      xhr.send()
    }
  }
}
