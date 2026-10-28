package sample.app.playgrounds

import kotlin.time.TimeSource

actual object ChatTransport {
  private val inMemoryMessages = mutableListOf<ChatMessage>()

  actual fun getCurrentInstanceId(): String = "IosUser"

  actual fun broadcastMessage(text: String) {
    inMemoryMessages.add(ChatMessage("IosUser", text, 0L))
  }

  actual fun getMessages(): List<ChatMessage> = inMemoryMessages.toList()

  actual fun clearMessages() {
    inMemoryMessages.clear()
  }
}
