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

private enum class SampleScreen {
  TaskList,
  InputForm,
  ScrollDemo
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

  MaterialTheme {
    BoxWithConstraints(
      modifier =
        Modifier
          .fillMaxSize()
          .background(Color(0xFFF5F1E8))
          .padding(12.dp)
    ) {
      if (maxWidth < 700.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
          CompactNavigation(
            onTaskListSelected = onTaskListSelected,
            onInputFormSelected = onInputFormSelected,
            onScrollDemoSelected = onScrollDemoSelected
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
            onBottomAction = { showScrollSuccess.value = true }
          )
        }
      } else {
        Row(modifier = Modifier.fillMaxSize()) {
          SidebarNavigation(
            onTaskListSelected = onTaskListSelected,
            onInputFormSelected = onInputFormSelected,
            onScrollDemoSelected = onScrollDemoSelected
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
            onBottomAction = { showScrollSuccess.value = true }
          )
        }
      }
    }
  }
}

@Composable
private fun SidebarNavigation(
  onTaskListSelected: () -> Unit,
  onInputFormSelected: () -> Unit,
  onScrollDemoSelected: () -> Unit
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
  }
}

@Composable
private fun CompactNavigation(
  onTaskListSelected: () -> Unit,
  onInputFormSelected: () -> Unit,
  onScrollDemoSelected: () -> Unit
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
  onBottomAction: () -> Unit
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
          onUniqueTextAction = onUniqueTextAction
        )

      SampleScreen.ScrollDemo ->
        ScrollDemoScreen(
          showSuccess = showScrollSuccess,
          onBottomAction = onBottomAction
        )
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
  onUniqueTextAction: () -> Unit
) {
  Column(
    modifier =
      Modifier
        .fillMaxSize()
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
