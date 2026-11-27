package sample.app.playgrounds

actual object ChatTransport {
  private val inMemoryMessages = mutableListOf<ChatMessage>()

  actual fun ensureRelayStarted() {}

  actual fun getCurrentInstanceId(): String = "AndroidUser"

  actual fun broadcastMessage(text: String, attachment: ChatFileAttachment?) {
    inMemoryMessages.add(ChatMessage("AndroidUser", text, System.currentTimeMillis(), attachment))
  }

  actual fun getMessages(): List<ChatMessage> = inMemoryMessages.toList()

  actual fun clearMessages() {
    inMemoryMessages.clear()
  }
}
