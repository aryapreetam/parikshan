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
import androidx.compose.ui.unit.dp
import io.github.aryapreetam.parikshan.parikshanTag

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
  val showScrollSuccess = remember { mutableStateOf(false) }
  val onTaskListSelected = { activeScreen.value = SampleScreen.TaskList }
  val onInputFormSelected = {
    showFormSuccess.value = false
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
      modifier = Modifier.fillMaxWidth().parikshanTag(
        tag = "nav_task_list",
        onClick = onTaskListSelected
      )
    ) {
      Text("Task List")
    }
    Button(
      onClick = onInputFormSelected,
      modifier = Modifier.fillMaxWidth().parikshanTag(
        tag = "nav_input_form",
        onClick = onInputFormSelected
      )
    ) {
      Text("Input Form")
    }
    Button(
      onClick = onScrollDemoSelected,
      modifier = Modifier.fillMaxWidth().parikshanTag(
        tag = "nav_scroll_demo",
        onClick = onScrollDemoSelected
      )
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
    Button(
      onClick = onTaskListSelected,
      modifier = Modifier.fillMaxWidth().parikshanTag(
        tag = "nav_task_list",
        onClick = onTaskListSelected
      )
    ) {
      Text("Task List")
    }
    Button(
      onClick = onInputFormSelected,
      modifier = Modifier.fillMaxWidth().parikshanTag(
        tag = "nav_input_form",
        onClick = onInputFormSelected
      )
    ) {
      Text("Input Form")
    }
    Button(
      onClick = onScrollDemoSelected,
      modifier = Modifier.fillMaxWidth().parikshanTag(
        tag = "nav_scroll_demo",
        onClick = onScrollDemoSelected
      )
    ) {
      Text("Scroll Demo")
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
          showSuccess = showFormSuccess
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
    modifier = Modifier.fillMaxSize().parikshanTag("task_list_screen"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text("Task List", style = MaterialTheme.typography.headlineSmall)
    Text("Task 1", modifier = Modifier.fillMaxWidth().parikshanTag("task_item_1", text = "Task 1"))
    Text("Task 2", modifier = Modifier.fillMaxWidth().parikshanTag("task_item_2", text = "Task 2"))
  }
}

@Composable
private fun InputFormScreen(
  value: String,
  onValueChange: (String) -> Unit,
  onSubmit: () -> Unit,
  showSuccess: Boolean
) {
  Column(
    modifier = Modifier.fillMaxSize().parikshanTag("input_form_screen"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text("Input Demo", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text("Name") },
      modifier =
        Modifier.fillMaxWidth().parikshanTag(
          tag = "input_name_field",
          text = value,
          onInput = onValueChange
        )
    )
    Text(
      text = value,
      modifier = Modifier.fillMaxWidth().parikshanTag("input_name_preview", text = value)
    )
    Button(
      onClick = onSubmit,
      modifier = Modifier.parikshanTag(
        tag = "form_submit_button",
        onClick = onSubmit
      )
    ) {
      Text("Submit")
    }
    if (showSuccess) {
      Text("Submit Successful", modifier = Modifier.parikshanTag("form_success_message", text = "Submit Successful"))
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
        .parikshanTag(
          tag = "scroll_demo_screen",
          scrollState = scrollState
        ),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text("Scroll Demo", style = MaterialTheme.typography.headlineSmall)
    repeat(30) { index ->
      val itemText = "Scrollable Item ${index + 1}"
      Text(
        itemText,
        modifier = Modifier.fillMaxWidth().parikshanTag("scroll_item_$index", text = itemText)
      )
    }

    Button(
      onClick = onBottomAction,
      modifier =
        Modifier.align(Alignment.Start).parikshanTag(
          tag = "scroll_target_button",
          onClick = onBottomAction
        )
    ) {
      Text("Trigger Bottom Action")
    }

    if (showSuccess) {
      Text("done", modifier = Modifier.parikshanTag("scrolled_action_done", text = "done"))
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}
