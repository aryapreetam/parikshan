package sample.app.playgrounds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import sample.app.readFileBytes
import sample.app.rememberFilePickerLauncher

@Composable
fun ChatPlayground() {
  val currentUserId = remember { ChatTransport.getCurrentInstanceId() }
  var messageInput by remember { mutableStateOf("") }
  var messages by remember { mutableStateOf(emptyList<ChatMessage>()) }
  val listState = rememberLazyListState()

  val filePickerLauncher = rememberFilePickerLauncher(
    multiple = false,
    directory = false
  ) { paths ->
    val path = paths?.firstOrNull()
    if (!path.isNullOrBlank()) {
      val fileName = path.substringAfterLast('/').substringAfterLast('\\')
      val bytes = readFileBytes(path)
      ChatTransport.broadcastMessage(
        text = "Sent file: $fileName",
        attachment = ChatFileAttachment(fileName = fileName, fileBytes = bytes)
      )
      messages = ChatTransport.getMessages()
    }
  }

  LaunchedEffect(Unit) {
    while (isActive) {
      messages = ChatTransport.getMessages()
      delay(100)
    }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .testTag("chat_playground_screen")
  ) {
    Text(
      text = "Collaboration Chat",
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.testTag("chat_title")
    )
    Text(
      text = "Logged in as: $currentUserId",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.testTag("chat_current_user")
    )
    Spacer(modifier = Modifier.height(16.dp))

    LazyColumn(
      state = listState,
      verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .testTag("chat_messages_list")
    ) {
      items(messages) { msg ->
        val isSelf = msg.sender == currentUserId
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
        ) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isSelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp)
          ) {
            Column(modifier = Modifier.padding(8.dp)) {
              Text(
                text = "${msg.sender}: ${msg.text}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("chat_msg_${msg.sender}")
              )
              if (msg.attachment != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("chat_attachment_${msg.sender}")
                ) {
                  Column(modifier = Modifier.padding(6.dp)) {
                    Text(
                      text = "Attachment: ${msg.attachment.fileName}",
                      style = MaterialTheme.typography.labelMedium,
                      modifier = Modifier.testTag("chat_file_name_${msg.sender}")
                    )
                    Text(
                      text = msg.attachment.fileBytes.decodeToString(),
                      style = MaterialTheme.typography.bodySmall,
                      modifier = Modifier.testTag("chat_file_payload_${msg.sender}")
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedTextField(
        value = messageInput,
        onValueChange = { messageInput = it },
        placeholder = { Text("Type a message...") },
        modifier = Modifier
          .weight(1f)
          .testTag("chat_input")
      )
      Spacer(modifier = Modifier.width(8.dp))
      Button(
        onClick = { filePickerLauncher() },
        modifier = Modifier.testTag("chat_attach_button")
      ) {
        Text("Attach")
      }
      Spacer(modifier = Modifier.width(8.dp))
      Button(
        onClick = {
          if (messageInput.isNotBlank()) {
            val textToSend = messageInput
            messageInput = ""
            ChatTransport.broadcastMessage(textToSend)
            messages = ChatTransport.getMessages()
          }
        },
        modifier = Modifier.testTag("chat_send_button")
      ) {
        Text("Send")
      }
    }
  }
}
