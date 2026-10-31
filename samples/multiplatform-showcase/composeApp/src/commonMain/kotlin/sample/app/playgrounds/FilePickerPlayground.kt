package sample.app.playgrounds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import sample.app.rememberFilePickerLauncher

@Composable
fun FilePickerPlayground() {
  var selectedFileStatus by remember { mutableStateOf("No file selected") }
  var selectedCount by remember { mutableStateOf(0) }
  var lastActionStatus by remember { mutableStateOf("Idle") }

  val singlePicker = rememberFilePickerLauncher(multiple = false) { paths ->
    if (paths != null && paths.isNotEmpty()) {
      selectedFileStatus = paths.first()
      selectedCount = 1
      lastActionStatus = "File selected"
    } else {
      selectedFileStatus = "No file selected"
      selectedCount = 0
      lastActionStatus = "Picker was cancelled"
    }
  }

  val multiPicker = rememberFilePickerLauncher(multiple = true) { paths ->
    if (paths != null && paths.isNotEmpty()) {
      selectedFileStatus = paths.joinToString(", ")
      selectedCount = paths.size
      lastActionStatus = "${paths.size} files selected"
    } else {
      selectedFileStatus = "No file selected"
      selectedCount = 0
      lastActionStatus = "Picker was cancelled"
    }
  }

  val directoryPicker = rememberFilePickerLauncher(multiple = false, directory = true) { paths ->
    if (paths != null && paths.isNotEmpty()) {
      selectedFileStatus = paths.first()
      selectedCount = 1
      lastActionStatus = "Directory selected"
    } else {
      selectedFileStatus = "No file selected"
      selectedCount = 0
      lastActionStatus = "Picker was cancelled"
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .testTag("file_picker_playground")
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text(
      text = "File & Directory Picker Playground",
      style = MaterialTheme.typography.headlineMedium
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Button(
        onClick = { singlePicker() },
        modifier = Modifier.testTag("choose_single_file_button")
      ) {
        Text("Choose File")
      }

      Button(
        onClick = { multiPicker() },
        modifier = Modifier.testTag("choose_multiple_files_button")
      ) {
        Text("Choose Multiple Files")
      }

      Button(
        onClick = { directoryPicker() },
        modifier = Modifier.testTag("choose_directory_button")
      ) {
        Text("Choose Directory")
      }
    }

    Card(
      modifier = Modifier.fillMaxWidth().testTag("picker_status_card")
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Status: ", style = MaterialTheme.typography.labelMedium)
        Text(
          text = lastActionStatus,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.testTag("picker_action_status")
        )

        HorizontalDivider()

        Text("Selected Path(s):", style = MaterialTheme.typography.labelMedium)
        Text(
          text = selectedFileStatus,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.testTag("selected_file_status")
        )

        HorizontalDivider()

        Text("Count:", style = MaterialTheme.typography.labelMedium)
        Text(
          text = selectedCount.toString(),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.testTag("selected_count_text")
        )
      }
    }
  }
}
