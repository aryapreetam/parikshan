package sample.app.playgrounds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun SelectorParityPlayground() {
  var actionStatus by remember { mutableStateOf("") }
  var dupInput1 by remember { mutableStateOf("") }
  var dupInput2 by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .testTag("selector_parity_playground_screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text("Selector Parity & Edge Cases", style = MaterialTheme.typography.headlineMedium)
    Text("Verifies selector resolution, duplicate tag handling, and index constraints.")

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Duplicate Input Fields", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
          value = dupInput1,
          onValueChange = { dupInput1 = it },
          label = { Text("Duplicate Input") },
          modifier = Modifier.fillMaxWidth().testTag("duplicate_input_1")
        )
        OutlinedTextField(
          value = dupInput2,
          onValueChange = { dupInput2 = it },
          label = { Text("Duplicate Input") },
          modifier = Modifier.fillMaxWidth().testTag("duplicate_input_2")
        )
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ambiguous Buttons (Same Text)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { actionStatus = "Primary Clicked" },
            modifier = Modifier.testTag("duplicate_action_primary")
          ) {
            Text("Duplicate Action")
          }
          Button(
            onClick = { actionStatus = "Secondary Clicked" },
            modifier = Modifier.testTag("duplicate_action_secondary")
          ) {
            Text("Duplicate Action")
          }
        }
        if (actionStatus.isNotEmpty()) {
          Text(actionStatus, modifier = Modifier.testTag("action_status_text"))
        }
      }
    }
  }
}
