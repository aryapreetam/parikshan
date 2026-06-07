package sample.app.playgrounds

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun AccessibilityPlayground() {
  var a11yMessage by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp)
      .testTag("accessibility_playground_screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text("Accessibility & Semantics", style = MaterialTheme.typography.headlineMedium)
    
    // Status text
    if (a11yMessage.isNotEmpty()) {
      Text(
        text = a11yMessage,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("a11y_result_message")
      )
    }

    // 1. Accessibility Description Matching (no visible text)
    Card(modifier = Modifier.fillMaxWidth().testTag("a11y_card_1")) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("1. Icon Button with Accessibility Label", style = MaterialTheme.typography.titleMedium)
        Text("The button below has no text, only a content description (accessibility label):")
        
        IconButton(
          onClick = { a11yMessage = "Clicked Settings Option" },
          modifier = Modifier
            .semantics { contentDescription = "Settings Control Button" }
            .testTag("a11y_icon_button")
        ) {
          Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("⚙", style = MaterialTheme.typography.titleLarge)
          }
        }
      }
    }

    // 2. Duplicate Text Buttons for Indexing Verification
    Card(modifier = Modifier.fillMaxWidth().testTag("a11y_card_2")) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("2. Identical Elements (Index Resolution)", style = MaterialTheme.typography.titleMedium)
        Text("These buttons share the exact same text and tag. The test must use indices to click them:")
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { a11yMessage = "Clicked Index 0" },
            modifier = Modifier.fillMaxWidth().testTag("duplicate_action_item_0")
          ) {
            Text("Duplicate Action Item")
          }
          
          Button(
            onClick = { a11yMessage = "Clicked Index 1" },
            modifier = Modifier.fillMaxWidth().testTag("duplicate_action_item_1")
          ) {
            Text("Duplicate Action Item")
          }
          
          Button(
            onClick = { a11yMessage = "Clicked Index 2" },
            modifier = Modifier.fillMaxWidth().testTag("duplicate_action_item_2")
          ) {
            Text("Duplicate Action Item")
          }
        }
      }
    }

    // 3. Hidden elements (Mounted in tree but non-interactable)
    Card(modifier = Modifier.fillMaxWidth().testTag("a11y_card_3")) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("3. Hidden Elements (Visibility Strictness)", style = MaterialTheme.typography.titleMedium)
        Text("The button below is mounted but has zero dimensions and opacity. E2E should not find it as visible:")
        
        // Zero size mounted element
        Box(modifier = Modifier.size(0.dp).alpha(0f)) {
          Button(
            onClick = { a11yMessage = "Clicked Invisible Target!" },
            modifier = Modifier.testTag("invisible_click_target")
          ) {
            Text("Hidden Click Target")
          }
        }
      }
    }
  }
}
