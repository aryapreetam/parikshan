package sample.app.playgrounds

actual object ChatTransport {
  private val inMemoryMessages = mutableListOf<ChatMessage>()

  actual fun getCurrentInstanceId(): String = "WasmUser"

  actual fun broadcastMessage(text: String) {
    inMemoryMessages.add(ChatMessage("WasmUser", text, 0L))
  }

  actual fun getMessages(): List<ChatMessage> = inMemoryMessages.toList()

  actual fun clearMessages() {
    inMemoryMessages.clear()
  }
}
