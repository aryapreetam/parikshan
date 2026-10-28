package sample.app.playgrounds

data class ChatMessage(
  val sender: String,
  val text: String,
  val timestamp: Long
)

expect object ChatTransport {
  fun getCurrentInstanceId(): String
  fun broadcastMessage(text: String)
  fun getMessages(): List<ChatMessage>
  fun clearMessages()
}
