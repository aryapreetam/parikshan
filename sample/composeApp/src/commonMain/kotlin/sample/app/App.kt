package sample.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch

// Import playgrounds
import sample.app.playgrounds.FormPlayground
import sample.app.playgrounds.OverlayPlayground
import sample.app.playgrounds.NavigationPlayground
import sample.app.playgrounds.ScrollPlayground
import sample.app.playgrounds.GesturePlayground
import sample.app.playgrounds.TimingPlayground
import sample.app.playgrounds.AccessibilityPlayground

enum class SampleScreen {
  TaskList,
  InputForm,
  ScrollDemo,
  SubtextDemo,
  
  // New Playgrounds
  FormPlayground,
  OverlayPlayground,
  NavigationPlayground,
  ScrollPlayground,
  GesturePlayground,
  TimingPlayground,
  AccessibilityPlayground
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
  val activeScreen = remember { mutableStateOf(SampleScreen.TaskList) }
  val formValue = remember { mutableStateOf("") }
  val showFormSuccess = remember { mutableStateOf(false) }
  val selectorResultMessage = remember { mutableStateOf<String?>(null) }
  val showScrollSuccess = remember { mutableStateOf(false) }

  // Navigation callbacks
  val onScreenSelected: (SampleScreen) -> Unit = { screen ->
    showFormSuccess.value = false
    selectorResultMessage.value = null
    showScrollSuccess.value = false
    activeScreen.value = screen
  }

  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

  MaterialTheme {
    val activeScreenVal = activeScreen.value
    val onScreenSelectedVal = onScreenSelected
    
    BoxWithConstraints(
      modifier = Modifier.fillMaxSize().background(Color(0xFFF5F1E8))
    ) {
      val isCompact = maxWidth < 700.dp

      if (isCompact) {
        ModalNavigationDrawer(
          drawerState = drawerState,
          drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp).testTag("navigation_drawer")) {
              SidebarNavigation(
                activeScreen = activeScreenVal,
                onScreenSelected = { 
                  onScreenSelectedVal(it)
                  coroutineScope.launch { drawerState.close() }
                }
              )
            }
          }
        ) {
          Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
              TopAppBar(
                title = { Text("Parikshan Sample") },
                navigationIcon = {
                  IconButton(onClick = { coroutineScope.launch { drawerState.open() } }, modifier = Modifier.testTag("hamburger_button")) {
                    Text("☰") 
                  }
                }
              )
            }
          ) { paddingValues ->
            ContentSurface(
              modifier = Modifier.fillMaxSize().padding(paddingValues),
              activeScreen = activeScreenVal,
              formValue = formValue.value,
              onFormValueChange = { formValue.value = it },
              onFormSubmit = { showFormSuccess.value = true },
              showFormSuccess = showFormSuccess.value,
              selectorResultMessage = selectorResultMessage.value,
              onTagPriorityAction = { selectorResultMessage.value = "Tag selector won" },
              onUniqueTextAction = { selectorResultMessage.value = "Unique text clicked" },
              showScrollSuccess = showScrollSuccess.value,
              onBottomAction = { showScrollSuccess.value = true },
              onFinalSubmit = {
                coroutineScope.launch {
                  snackbarHostState.showSnackbar("Final Form Submitted Successfully!")
                }
              }
            )
          }
        }
      } else {
        Scaffold(
          snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
          Row(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(12.dp)) {
            SidebarNavigation(
              activeScreen = activeScreenVal,
              onScreenSelected = onScreenSelectedVal
            )
            Spacer(modifier = Modifier.width(12.dp))
            ContentSurface(
              modifier = Modifier.fillMaxSize().weight(1f),
              activeScreen = activeScreenVal,
              formValue = formValue.value,
              onFormValueChange = { formValue.value = it },
              onFormSubmit = { showFormSuccess.value = true },
              showFormSuccess = showFormSuccess.value,
              selectorResultMessage = selectorResultMessage.value,
              onTagPriorityAction = { selectorResultMessage.value = "Tag selector won" },
              onUniqueTextAction = { selectorResultMessage.value = "Unique text clicked" },
              showScrollSuccess = showScrollSuccess.value,
              onBottomAction = { showScrollSuccess.value = true },
              onFinalSubmit = {
                coroutineScope.launch {
                  snackbarHostState.showSnackbar("Final Form Submitted Successfully!")
                }
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SidebarNavigation(
  activeScreen: SampleScreen,
  onScreenSelected: (SampleScreen) -> Unit
) {
  val scrollState = rememberScrollState()
  Column(
   modifier = Modifier
     .width(240.dp)
     .fillMaxHeight()
     .background(Color.White)
     .testTag("nav_rail")
     .verticalScroll(scrollState)
     .padding(12.dp),
   verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text("Parikshan Sample", style = MaterialTheme.typography.titleMedium)
    
    Text("Legacy Scenarios", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    NavigationItemButton("Task List", SampleScreen.TaskList, activeScreen, onScreenSelected, "nav_task_list")
    NavigationItemButton("Input Form", SampleScreen.InputForm, activeScreen, onScreenSelected, "nav_input_form")
    NavigationItemButton("Scroll Demo", SampleScreen.ScrollDemo, activeScreen, onScreenSelected, "nav_scroll_demo")
    NavigationItemButton("Subtext Demo", SampleScreen.SubtextDemo, activeScreen, onScreenSelected, "nav_subtext_demo")
    
    Text("E2E Playgrounds", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    NavigationItemButton("Form Playground", SampleScreen.FormPlayground, activeScreen, onScreenSelected, "nav_form_playground")
    NavigationItemButton("Overlay Playground", SampleScreen.OverlayPlayground, activeScreen, onScreenSelected, "nav_overlay_playground")
    NavigationItemButton("Navigation Playground", SampleScreen.NavigationPlayground, activeScreen, onScreenSelected, "nav_navigation_playground")
    NavigationItemButton("Scroll Playground", SampleScreen.ScrollPlayground, activeScreen, onScreenSelected, "nav_scroll_playground")
    NavigationItemButton("Gesture Playground", SampleScreen.GesturePlayground, activeScreen, onScreenSelected, "nav_gesture_playground")
    NavigationItemButton("Timing Playground", SampleScreen.TimingPlayground, activeScreen, onScreenSelected, "nav_timing_playground")
    NavigationItemButton("A11y & Semantics", SampleScreen.AccessibilityPlayground, activeScreen, onScreenSelected, "nav_accessibility_playground")
  }
}

@Composable
private fun CompactNavigation(
  activeScreen: SampleScreen,
  onScreenSelected: (SampleScreen) -> Unit
) {
  val scrollState = rememberScrollState()
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color.White)
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text("Parikshan Sample", style = MaterialTheme.typography.titleMedium)
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) { NavigationItemButton("Task List", SampleScreen.TaskList, activeScreen, onScreenSelected, "nav_task_list") }
        Box(modifier = Modifier.weight(1f)) { NavigationItemButton("Input Form", SampleScreen.InputForm, activeScreen, onScreenSelected, "nav_input_form") }
        Box(modifier = Modifier.weight(1f)) { NavigationItemButton("Scroll Demo", SampleScreen.ScrollDemo, activeScreen, onScreenSelected, "nav_scroll_demo") }
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) { NavigationItemButton("Subtext Demo", SampleScreen.SubtextDemo, activeScreen, onScreenSelected, "nav_subtext_demo") }
        Box(modifier = Modifier.weight(1f)) { NavigationItemButton("Form PG", SampleScreen.FormPlayground, activeScreen, onScreenSelected, "nav_form_playground") }
        Box(modifier = Modifier.weight(1f)) { NavigationItemButton("Overlay PG", SampleScreen.OverlayPlayground, activeScreen, onScreenSelected, "nav_overlay_playground") }
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) { NavigationItemButton("Nav PG", SampleScreen.NavigationPlayground, activeScreen, onScreenSelected, "nav_navigation_playground") }
        Box(modifier = Modifier.weight(1f)) { NavigationItemButton("Scroll PG", SampleScreen.ScrollPlayground, activeScreen, onScreenSelected, "nav_scroll_playground") }
        Box(modifier = Modifier.weight(1f)) { NavigationItemButton("Gesture PG", SampleScreen.GesturePlayground, activeScreen, onScreenSelected, "nav_gesture_playground") }
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) { NavigationItemButton("Timing PG", SampleScreen.TimingPlayground, activeScreen, onScreenSelected, "nav_timing_playground") }
        Box(modifier = Modifier.weight(1f)) { NavigationItemButton("A11y PG", SampleScreen.AccessibilityPlayground, activeScreen, onScreenSelected, "nav_accessibility_playground") }
        Spacer(modifier = Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun NavigationItemButton(
  label: String,
  target: SampleScreen,
  activeScreen: SampleScreen,
  onScreenSelected: (SampleScreen) -> Unit,
  testTag: String
) {
  val isSelected = activeScreen == target
  Button(
    onClick = { onScreenSelected(target) },
    colors = ButtonDefaults.buttonColors(
      containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
      contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    ),
    modifier = Modifier.testTag(testTag)
  ) {
    Text(label)
  }
}

@Composable
private fun ContentSurface(
  modifier: Modifier,
  activeScreen: SampleScreen,
  formValue: String,
  onFormValueChange: (String) -> Unit,
  onFormSubmit: () -> Unit,
  showFormSuccess: Boolean,
  selectorResultMessage: String?,
  onTagPriorityAction: () -> Unit,
  onUniqueTextAction: () -> Unit,
  showScrollSuccess: Boolean,
  onBottomAction: () -> Unit,
  onFinalSubmit: () -> Unit
) {
  Box(
    modifier = modifier
      .background(Color.White)
  ) {
    when (activeScreen) {
      SampleScreen.TaskList -> TaskListScreen()
      SampleScreen.InputForm -> InputFormScreen(
        value = formValue,
        onValueChange = onFormValueChange,
        onSubmit = onFormSubmit,
        showSuccess = showFormSuccess,
        selectorResultMessage = selectorResultMessage,
        onTagPriorityAction = onTagPriorityAction,
        onUniqueTextAction = onUniqueTextAction,
        onFinalSubmit = onFinalSubmit
      )
      SampleScreen.ScrollDemo -> ScrollDemoScreen(
        showSuccess = showScrollSuccess,
        onBottomAction = onBottomAction
      )
      SampleScreen.SubtextDemo -> SubtextDemoScreen()
      
      // New Playgrounds
      SampleScreen.FormPlayground -> FormPlayground(
        onFormSubmitted = { /* Form submission can show a snackbar or log */ }
      )
      SampleScreen.OverlayPlayground -> OverlayPlayground()
      SampleScreen.NavigationPlayground -> NavigationPlayground()
      SampleScreen.ScrollPlayground -> ScrollPlayground()
      SampleScreen.GesturePlayground -> GesturePlayground()
      SampleScreen.TimingPlayground -> TimingPlayground()
      SampleScreen.AccessibilityPlayground -> AccessibilityPlayground()
    }
  }
}

@Composable
private fun TaskListScreen() {
  Column(
    modifier = Modifier.fillMaxSize().testTag("task_list_screen"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text("Task List", style = MaterialTheme.typography.headlineSmall)
    Text("Task 1", modifier = Modifier.fillMaxWidth().testTag("task_item_1"))
    Text("Task 2", modifier = Modifier.fillMaxWidth().testTag("task_item_2"))
  }
}

@Composable
private fun InputFormScreen(
  value: String,
  onValueChange: (String) -> Unit,
  onSubmit: () -> Unit,
  showSuccess: Boolean,
  selectorResultMessage: String?,
  onTagPriorityAction: () -> Unit,
  onUniqueTextAction: () -> Unit,
  onFinalSubmit: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .testTag("input_form_screen"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text("Input Demo", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text("Name") },
      modifier = Modifier.fillMaxWidth().testTag("input_name_field")
    )
    Text(
      text = value,
      modifier = Modifier.fillMaxWidth().testTag("input_name_preview")
    )
    selectorResultMessage?.let { message ->
      Text(message, modifier = Modifier.testTag("selector_result_message"))
    }
    if (showSuccess) {
      Text("Submit Successful", modifier = Modifier.testTag("form_success_message"))
    }
    Button(
      onClick = onTagPriorityAction,
      modifier = Modifier.testTag("Submit")
    ) {
      Text("Tag Selector Wins")
    }
    Button(
      onClick = onSubmit,
      modifier = Modifier.testTag("form_submit_button")
    ) {
      Text("Submit")
    }
    Button(
      onClick = onUniqueTextAction,
      modifier = Modifier.testTag("unique_text_button")
    ) {
      Text("Unique Text Action")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = {},
        modifier = Modifier.testTag("duplicate_action_primary")
      ) {
        Text("Duplicate Action")
      }
      Button(
        onClick = {},
        modifier = Modifier.testTag("duplicate_action_secondary")
      ) {
        Text("Duplicate Action")
      }
    }
    
    val duplicate1 = remember { mutableStateOf("") }
    OutlinedTextField(
      value = duplicate1.value,
      onValueChange = { duplicate1.value = it },
      label = { Text("Duplicate Input") },
      modifier = Modifier.fillMaxWidth().testTag("duplicate_input_1")
    )
    
    val duplicate2 = remember { mutableStateOf("") }
    OutlinedTextField(
      value = duplicate2.value,
      onValueChange = { duplicate2.value = it },
      label = { Text("Duplicate Input") },
      modifier = Modifier.fillMaxWidth().testTag("duplicate_input_2")
    )

    val email = remember { mutableStateOf("") }
    OutlinedTextField(
      value = email.value,
      onValueChange = { email.value = it },
      label = { Text("Email Address") },
      modifier = Modifier.fillMaxWidth()
    )

    val phone = remember { mutableStateOf("") }
    OutlinedTextField(
      value = phone.value,
      onValueChange = { phone.value = it },
      label = { Text("Phone Number") },
      modifier = Modifier.fillMaxWidth()
    )

    val address = remember { mutableStateOf("") }
    OutlinedTextField(
      value = address.value,
      onValueChange = { address.value = it },
      label = { Text("Shipping Address") },
      modifier = Modifier.fillMaxWidth(),
      minLines = 3
    )

    val password = remember { mutableStateOf("") }
    OutlinedTextField(
      value = password.value,
      onValueChange = { password.value = it },
      label = { Text("Password") },
      modifier = Modifier.fillMaxWidth()
    )

    val confirmPassword = remember { mutableStateOf("") }
    OutlinedTextField(
      value = confirmPassword.value,
      onValueChange = { confirmPassword.value = it },
      label = { Text("Confirm Password") },
      modifier = Modifier.fillMaxWidth()
    )

    val agreed = remember { mutableStateOf(false) }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .toggleable(
          value = agreed.value,
          onValueChange = { agreed.value = it },
          role = Role.Checkbox
        )
        .padding(vertical = 8.dp)
    ) {
      Checkbox(checked = agreed.value, onCheckedChange = null)
      Spacer(Modifier.width(8.dp))
      Text("I agree to the terms and conditions")
    }

    Button(
      onClick = onFinalSubmit,
      modifier = Modifier.fillMaxWidth().testTag("final_submit_button")
    ) {
      Text("Final Submit")
    }
    
    Spacer(modifier = Modifier.height(24.dp))
  }
}

@Composable
private fun ScrollDemoScreen(
  showSuccess: Boolean,
  onBottomAction: () -> Unit
) {
  val scrollState = rememberScrollState()
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .testTag("scroll_demo_screen"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text("Scroll Demo", style = MaterialTheme.typography.headlineSmall)
    repeat(60) { index ->
      Text(
        text = "Scrollable Item ${index + 1}",
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp)
          .testTag("scroll_item_$index")
      )
    }
    Button(
      onClick = onBottomAction,
      modifier = Modifier.align(Alignment.Start).testTag("scroll_target_button")
    ) {
      Text("Trigger Bottom Action")
    }
    if (showSuccess) {
      Text("done", modifier = Modifier.testTag("scrolled_action_done"))
    }
    Spacer(modifier = Modifier.height(24.dp))
  }
}

@Composable
private fun SubtextDemoScreen() {
  Column(
    modifier = Modifier.fillMaxSize().testTag("subtext_demo_screen"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text("Subtext Demo", style = MaterialTheme.typography.headlineSmall)
    Text("This is a sample text for testing purpose", modifier = Modifier.fillMaxWidth().testTag("sample_large_text"))
    Text("This is a sample", modifier = Modifier.fillMaxWidth().testTag("sample_large_text_short"))
    Text("This is a sample text", modifier = Modifier.fillMaxWidth().testTag("sample_large_text_short_1"))
    Text("This is a sample text for not testing", modifier = Modifier.fillMaxWidth().testTag("sample_large_text__negative"))
  }
}
