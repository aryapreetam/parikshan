package sample.app.playgrounds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings

@Composable
fun AccessibilityPlayground() {
  var a11yMessage by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
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
        
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          IconButton(
            onClick = { a11yMessage = "Clicked Settings Option" },
            modifier = Modifier
              .semantics { contentDescription = "Settings Control Button" }
              .testTag("a11y_icon_button")
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = null
            )
          }
          if (a11yMessage.isNotEmpty()) {
            Text(
              text = a11yMessage,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.testTag("a11y_result_message")
            )
          }
        }
      }
    }

    // 2. Ambiguity & Indexing Verification
    Card(modifier = Modifier.fillMaxWidth().testTag("a11y_card_2")) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("2. Identical Elements (Ambiguity & Indexing)", style = MaterialTheme.typography.titleMedium)
        
        // This Row creates an ambiguity trap: two nodes with the same tag.
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
           Box(modifier = Modifier.size(40.dp).testTag("ambiguity_trap"))
           Box(modifier = Modifier.size(40.dp).testTag("ambiguity_trap"))
        }

        Text("Buttons with same text for index resolution:")
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
          if (a11yMessage.isNotEmpty()) {
            Text(
              text = a11yMessage,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.testTag("a11y_result_message")
            )
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

    // 4. Subtext Partial Text Matching
    Card(modifier = Modifier.fillMaxWidth().testTag("a11y_card_4")) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("4. Subtext Matching", style = MaterialTheme.typography.titleMedium)
        Text("This is a sample text for testing purpose", modifier = Modifier.testTag("subtext_sample_target"))
      }
    }
  }
}
