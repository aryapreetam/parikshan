package sample.app.playgrounds

data class ChatFileAttachment(
  val fileName: String,
  val fileBytes: ByteArray
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChatFileAttachment) return false
    return fileName == other.fileName && fileBytes.contentEquals(other.fileBytes)
  }

  override fun hashCode(): Int {
    var result = fileName.hashCode()
    result = 31 * result + fileBytes.contentHashCode()
    return result
  }
}

data class ChatMessage(
  val sender: String,
  val text: String,
  val timestamp: Long,
  val attachment: ChatFileAttachment? = null
)

expect object ChatTransport {
  fun getCurrentInstanceId(): String
  fun broadcastMessage(text: String, attachment: ChatFileAttachment? = null)
  fun getMessages(): List<ChatMessage>
  fun clearMessages()
}
