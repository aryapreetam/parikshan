package sample.app.playgrounds

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TimingPlayground() {
  val coroutineScope = rememberCoroutineScope()
  var isLoading by remember { mutableStateOf(false) }
  var asyncResultText by remember { mutableStateOf("") }
  
  var isExpanded by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .testTag("timing_playground_screen"),
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
    Text("Timing & Latency Playground", style = MaterialTheme.typography.headlineMedium)
    Text("Verifies E2E timeout resiliency, wait-polling, and coordinate shifts during size animations.")

    // 1. Asynchronous Delay Loader Section
    Card(modifier = Modifier.fillMaxWidth().testTag("async_loader_card")) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text("1. Asynchronous Data Load (2s Delay)", style = MaterialTheme.typography.titleMedium)
        
        Button(
          onClick = {
            isLoading = true
            asyncResultText = ""
            coroutineScope.launch {
              delay(2000)
              isLoading = false
              asyncResultText = "Asynchronous Data Loaded Successfully!"
            }
          },
          enabled = !isLoading,
          modifier = Modifier.testTag("trigger_async_load_button")
        ) {
          Text("Load Remote Data")
        }

        if (isLoading) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("loading_spinner_container")
          ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp).testTag("timing_loading_spinner"))
            Text("Connecting to mock server...")
          }
        }

        if (asyncResultText.isNotEmpty()) {
          Text(
            text = asyncResultText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("async_result_text")
          )
        }
      }
    }

    // 2. Expandable Animating Card Section
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .animateContentSize()
        .testTag("expandable_animation_card")
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text("2. Layout Dynamic Resizing", style = MaterialTheme.typography.titleMedium)
        Text("This card will animate its height when expanded, shifting the layout position of any elements below it.")

        Button(
          onClick = { isExpanded = !isExpanded },
          modifier = Modifier.testTag("expandable_toggle_button")
        ) {
          Text(if (isExpanded) "Collapse Card" else "Expand Card")
        }

        if (isExpanded) {
          Text(
            text = "This is the expanded text area. It contains detailed documentation instructions that were previously hidden. Tapping collapse will dynamically hide this node again.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
              .padding(top = 8.dp)
              .testTag("expandable_details_text")
          )
        }
      }
    }
  }
}
