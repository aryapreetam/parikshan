package sample.app.playgrounds

import java.io.File

actual object ChatTransport {
  private fun resolveChatFile(): File {
    val dataDirProp = System.getProperty("parikshan.data.dir")
    val dir = if (!dataDirProp.isNullOrBlank()) {
      val dataDir = File(dataDirProp)
      dataDir.parentFile ?: dataDir
    } else {
      File(System.getProperty("java.io.tmpdir"), "parikshan_chat")
    }
    dir.mkdirs()
    return File(dir, "shared_chat_messages.txt")
  }

  actual fun getCurrentInstanceId(): String {
    return System.getProperty("parikshan.instance.id")?.takeIf { it.isNotBlank() } ?: "User"
  }

  actual fun broadcastMessage(text: String) {
    val file = resolveChatFile()
    val sender = getCurrentInstanceId()
    val timestamp = System.currentTimeMillis()
    // Append in format: sender\t timestamp\t text
    synchronized(ChatTransport) {
      file.appendText("$sender\t$timestamp\t$text\n")
    }
  }

  actual fun getMessages(): List<ChatMessage> {
    val file = resolveChatFile()
    if (!file.exists()) return emptyList()
    return runCatching {
      file.readLines()
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
    val file = resolveChatFile()
    if (file.exists()) {
      runCatching { file.delete() }
    }
  }
}
