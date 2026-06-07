package sample.app.playgrounds

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

enum class SubScreen {
  ScreenA,
  ScreenB,
  ScreenC
}

@Composable
fun NavigationPlayground() {
  var currentSubScreen by remember { mutableStateOf(SubScreen.ScreenA) }
  var backInterceptEnabled by remember { mutableStateOf(false) }
  var showConfirmDialog by remember { mutableStateOf(false) }
  
  // State preservation checks
  var textScreenA by remember { mutableStateOf("") }
  var textScreenB by remember { mutableStateOf("") }

  val navigationStack = remember { mutableStateListOf(SubScreen.ScreenA) }

  fun navigateTo(screen: SubScreen) {
    navigationStack.add(screen)
    currentSubScreen = screen
  }

  fun handleBackNavigation() {
    if (navigationStack.size > 1) {
      if (backInterceptEnabled) {
        showConfirmDialog = true
      } else {
        navigationStack.removeLast()
        currentSubScreen = navigationStack.last()
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .testTag("navigation_playground_screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text("Navigation & Lifecycle Playground", style = MaterialTheme.typography.headlineMedium)
    
    // Interceptor Toggle Control
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
    ) {
      Text("Enable Back Navigation Interception")
      Spacer(modifier = Modifier.weight(1f))
      Switch(
        checked = backInterceptEnabled,
        onCheckedChange = { backInterceptEnabled = it },
        modifier = Modifier.testTag("back_intercept_switch")
      )
    }

    // Stack Breadcrumb Tracker
    Text(
      text = "Stack: " + navigationStack.joinToString(" -> "),
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.testTag("nav_stack_breadcrumb")
    )

    HorizontalDivider()

    // Back Button (Visible when stack is deeper than Screen A)
    if (navigationStack.size > 1) {
      Button(
        onClick = { handleBackNavigation() },
        modifier = Modifier.testTag("nav_back_button")
      ) {
        Text("← Go Back")
      }
    }

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (currentSubScreen) {
        SubScreen.ScreenA -> {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Screen A (Root)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("screen_a_title"))
            Text("Type something here. This text must persist when you navigate to B and return.")
            OutlinedTextField(
              value = textScreenA,
              onValueChange = { textScreenA = it },
              label = { Text("Input A") },
              modifier = Modifier.fillMaxWidth().testTag("input_screen_a")
            )
            Button(
              onClick = { navigateTo(SubScreen.ScreenB) },
              modifier = Modifier.testTag("nav_to_b_button")
            ) {
              Text("Go to Screen B")
            }
          }
        }
        SubScreen.ScreenB -> {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Screen B", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("screen_b_title"))
            OutlinedTextField(
              value = textScreenB,
              onValueChange = { textScreenB = it },
              label = { Text("Input B") },
              modifier = Modifier.fillMaxWidth().testTag("input_screen_b")
            )
            Button(
              onClick = { navigateTo(SubScreen.ScreenC) },
              modifier = Modifier.testTag("nav_to_c_button")
            ) {
              Text("Go to Screen C")
            }
          }
        }
        SubScreen.ScreenC -> {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Screen C (Terminal)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("screen_c_title"))
            Text("This is the deepest screen in our navigation playground structure.")
          }
        }
      }
    }

    if (showConfirmDialog) {
      AlertDialog(
        onDismissRequest = { showConfirmDialog = false },
        title = { Text("Confirm Navigation") },
        text = { Text("The back interceptor is enabled. Are you sure you want to go back?") },
        confirmButton = {
          TextButton(
            onClick = {
              showConfirmDialog = false
              // Force back navigation bypassing intercept
              if (navigationStack.size > 1) {
                navigationStack.removeLast()
                currentSubScreen = navigationStack.last()
              }
            },
            modifier = Modifier.testTag("confirm_back_btn")
          ) {
            Text("Yes, Go Back")
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showConfirmDialog = false },
            modifier = Modifier.testTag("cancel_back_btn")
          ) {
            Text("Cancel")
          }
        },
        modifier = Modifier.testTag("back_intercept_dialog")
      )
    }
  }
}
