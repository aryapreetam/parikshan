package sample.app.playgrounds

import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
actual object ChatTransport {
  private fun resolveChatFile(): File {
    val dir = File(System.getProperty("java.io.tmpdir"), "parikshan_chat")
    dir.mkdirs()
    return File(dir, "shared_chat_messages.txt")
  }

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
      file.appendText(line)
    }
  }

  actual fun getMessages(): List<ChatMessage> {
    val file = resolveChatFile()
    if (!file.exists()) return emptyList()
    return runCatching {
      file.readLines()
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
    if (file.exists()) {
      runCatching { file.delete() }
    }
  }
}
