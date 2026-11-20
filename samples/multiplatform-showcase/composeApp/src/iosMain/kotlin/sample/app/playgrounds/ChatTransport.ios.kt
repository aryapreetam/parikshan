package sample.app.playgrounds

actual object ChatTransport {
  private val inMemoryMessages = mutableListOf<ChatMessage>()

  actual fun getCurrentInstanceId(): String = "IosUser"

  actual fun broadcastMessage(text: String, attachment: ChatFileAttachment?) {
    inMemoryMessages.add(ChatMessage("IosUser", text, 0L, attachment))
  }

  actual fun getMessages(): List<ChatMessage> = inMemoryMessages.toList()

  actual fun clearMessages() {
    inMemoryMessages.clear()
  }
}
