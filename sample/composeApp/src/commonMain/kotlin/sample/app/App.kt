package sample.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
enum class SampleScreen {
  TaskList,
  InputForm,
  ScrollDemo,
  SubtextDemo
}

@Composable
fun App() {
  val activeScreen = remember { mutableStateOf(SampleScreen.TaskList) }
  val formValue = remember { mutableStateOf("") }
  val showFormSuccess = remember { mutableStateOf(false) }
  val selectorResultMessage = remember { mutableStateOf<String?>(null) }
  val showScrollSuccess = remember { mutableStateOf(false) }
  val onTaskListSelected = { activeScreen.value = SampleScreen.TaskList }
  val onInputFormSelected = {
    showFormSuccess.value = false
    selectorResultMessage.value = null
    activeScreen.value = SampleScreen.InputForm
  }
  val onScrollDemoSelected = {
    showScrollSuccess.value = false
    activeScreen.value = SampleScreen.ScrollDemo
  }
  val onSubtextDemoSelected = {
    activeScreen.value = SampleScreen.SubtextDemo
  }

  val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  MaterialTheme {
    androidx.compose.material3.Scaffold(
      snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
      BoxWithConstraints(
        modifier =
          Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F1E8))
            .padding(paddingValues)
            .padding(12.dp)
      ) {
      if (maxWidth < 700.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
          CompactNavigation(
            onTaskListSelected = onTaskListSelected,
            onInputFormSelected = onInputFormSelected,
            onScrollDemoSelected = onScrollDemoSelected,
            onSubtextDemoSelected = onSubtextDemoSelected
          )
          Spacer(modifier = Modifier.height(12.dp))
          ContentSurface(
            modifier = Modifier.fillMaxSize(),
            activeScreen = activeScreen.value,
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
              kotlinx.coroutines.GlobalScope.launch {
                snackbarHostState.showSnackbar("Final Form Submitted Successfully!")
              }
            }
          )
        }
      } else {
        Row(modifier = Modifier.fillMaxSize()) {
          SidebarNavigation(
            onTaskListSelected = onTaskListSelected,
            onInputFormSelected = onInputFormSelected,
            onScrollDemoSelected = onScrollDemoSelected,
            onSubtextDemoSelected = onSubtextDemoSelected
          )
          Spacer(modifier = Modifier.width(12.dp))
          ContentSurface(
            modifier = Modifier.fillMaxSize(),
            activeScreen = activeScreen.value,
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
              kotlinx.coroutines.GlobalScope.launch {
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
  onTaskListSelected: () -> Unit,
  onInputFormSelected: () -> Unit,
  onScrollDemoSelected: () -> Unit,
  onSubtextDemoSelected: () -> Unit
) {
  Column(
    modifier =
      Modifier
        .width(220.dp)
        .fillMaxHeight()
        .background(Color.White)
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text("Parikshan Sample", style = MaterialTheme.typography.titleMedium)
    Button(
      onClick = onTaskListSelected,
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("nav_task_list")
    ) {
      Text("Task List")
    }
    Button(
      onClick = onInputFormSelected,
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("nav_input_form")
    ) {
      Text("Input Form")
    }
    Button(
      onClick = onScrollDemoSelected,
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("nav_scroll_demo")
    ) {
      Text("Scroll Demo")
    }
    Button(
      onClick = onSubtextDemoSelected,
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("nav_subtext_demo")
    ) {
      Text("Subtext Demo")
    }
  }
}

@Composable
private fun CompactNavigation(
  onTaskListSelected: () -> Unit,
  onInputFormSelected: () -> Unit,
  onScrollDemoSelected: () -> Unit,
  onSubtextDemoSelected: () -> Unit
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(Color.White)
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text("Parikshan Sample", style = MaterialTheme.typography.titleMedium)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = onTaskListSelected,
        modifier =
          Modifier
            .weight(1f)
            .testTag("nav_task_list")
      ) {
        Text("Task List")
      }
      Button(
        onClick = onInputFormSelected,
        modifier =
          Modifier
            .weight(1f)
            .testTag("nav_input_form")
      ) {
        Text("Input Form")
      }
      Button(
        onClick = onScrollDemoSelected,
        modifier =
          Modifier
            .weight(1f)
            .testTag("nav_scroll_demo")
      ) {
        Text("Scroll Demo")
      }
      Button(
        onClick = onSubtextDemoSelected,
        modifier =
          Modifier
            .weight(1f)
            .testTag("nav_subtext_demo")
      ) {
        Text("Subtext")
      }
    }
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
    modifier =
      modifier
        .background(Color.White)
        .padding(20.dp)
  ) {
    when (activeScreen) {
      SampleScreen.TaskList ->
        TaskListScreen()

      SampleScreen.InputForm ->
        InputFormScreen(
          value = formValue,
          onValueChange = onFormValueChange,
          onSubmit = onFormSubmit,
          showSuccess = showFormSuccess,
          selectorResultMessage = selectorResultMessage,
          onTagPriorityAction = onTagPriorityAction,
          onUniqueTextAction = onUniqueTextAction,
          onFinalSubmit = onFinalSubmit
        )

      SampleScreen.ScrollDemo ->
        ScrollDemoScreen(
          showSuccess = showScrollSuccess,
          onBottomAction = onBottomAction
        )

      SampleScreen.SubtextDemo ->
        SubtextDemoScreen()
    }
  }
}

@Composable
private fun TaskListScreen() {
  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .testTag("task_list_screen"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text("Task List", style = MaterialTheme.typography.headlineSmall)
    Text(
      "Task 1",
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("task_item_1")
    )
    Text(
      "Task 2",
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("task_item_2")
    )
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
    modifier =
      Modifier
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
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("input_name_field")
    )
    Text(
      text = value,
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("input_name_preview")
    )
    selectorResultMessage?.let { message ->
      Text(
        message,
        modifier =
          Modifier
            .testTag("selector_result_message")
      )
    }
    if (showSuccess) {
      Text(
        "Submit Successful",
        modifier =
          Modifier
            .testTag("form_success_message")
      )
    }
    Button(
      onClick = onTagPriorityAction,
      modifier =
        Modifier
          .testTag("Submit")
    ) {
      Text("Tag Selector Wins")
    }
    Button(
      onClick = onSubmit,
      modifier =
        Modifier
          .testTag("form_submit_button")
    ) {
      Text("Submit")
    }
    Button(
      onClick = onUniqueTextAction,
      modifier =
        Modifier
          .testTag("unique_text_button")
    ) {
      Text("Unique Text Action")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = {},
        modifier =
          Modifier
            .testTag("duplicate_action_primary")
      ) {
        Text("Duplicate Action")
      }
      Button(
        onClick = {},
        modifier =
          Modifier
            .testTag("duplicate_action_secondary")
      ) {
        Text("Duplicate Action")
      }
    }
    
    // Duplicate inputs for parity tests
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

    // Long form elements
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
      androidx.compose.material3.Checkbox(
        checked = agreed.value,
        onCheckedChange = null
      )
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
    modifier =
      Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .testTag("scroll_demo_screen"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text("Scroll Demo", style = MaterialTheme.typography.headlineSmall)
    repeat(30) { index ->
      val itemText = "Scrollable Item ${index + 1}"
      Text(
        itemText,
        modifier =
          Modifier
            .fillMaxWidth()
            .testTag("scroll_item_$index")
      )
    }

    Button(
      onClick = onBottomAction,
      modifier =
        Modifier
          .align(Alignment.Start)
          .testTag("scroll_target_button")
    ) {
      Text("Trigger Bottom Action")
    }

    if (showSuccess) {
      Text(
        "done",
        modifier =
          Modifier
            .testTag("scrolled_action_done")
      )
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}

@Composable
private fun SubtextDemoScreen() {
  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .testTag("subtext_demo_screen"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text("Subtext Demo", style = MaterialTheme.typography.headlineSmall)
    Text(
      "This is a sample text for testing purpose",
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("sample_large_text")
    )

    Text(
      "This is a sample",
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("sample_large_text_short")
    )

    Text(
      "This is a sample text",
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("sample_large_text_short_1")
    )

    Text(
      "This is a sample text for not testing",
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("sample_large_text__negative")
    )
  }
}
