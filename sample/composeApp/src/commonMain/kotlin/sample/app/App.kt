package sample.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

  MaterialTheme {
    Row(
      modifier =
        Modifier
          .fillMaxSize()
          .background(Color(0xFFF5F1E8))
          .padding(12.dp)
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
          onClick = { activeScreen.value = SampleScreen.TaskList },
          modifier = Modifier.fillMaxWidth().parikshanTag("nav_task_list")
        ) {
          Text("Task List")
        }
        Button(
          onClick = {
            showFormSuccess.value = false
            activeScreen.value = SampleScreen.InputForm
          },
          modifier = Modifier.fillMaxWidth().parikshanTag("nav_input_form")
        ) {
          Text("Input Form")
        }
        Button(
          onClick = {
            showScrollSuccess.value = false
            activeScreen.value = SampleScreen.ScrollDemo
          },
          modifier = Modifier.fillMaxWidth().parikshanTag("nav_scroll_demo")
        ) {
          Text("Scroll Demo")
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(20.dp)
      ) {
        when (activeScreen.value) {
          SampleScreen.TaskList ->
            TaskListScreen()

          SampleScreen.InputForm ->
            InputFormScreen(
              value = formValue.value,
              onValueChange = { formValue.value = it },
              onSubmit = { showFormSuccess.value = true },
              showSuccess = showFormSuccess.value
            )

          SampleScreen.ScrollDemo ->
            ScrollDemoScreen(
              showSuccess = showScrollSuccess.value,
              onBottomAction = { showScrollSuccess.value = true }
            )
        }
      }
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
      modifier = Modifier.fillMaxWidth().parikshanTag("input_name_field", text = value)
    )
    Text(
      text = value,
      modifier = Modifier.fillMaxWidth().parikshanTag("input_name_preview", text = value)
    )
    Button(
      onClick = onSubmit,
      modifier = Modifier.parikshanTag("form_submit_button")
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
        .parikshanTag("scroll_demo_screen"),
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
      modifier = Modifier.align(Alignment.Start).parikshanTag("scroll_target_button")
    ) {
      Text("Trigger Bottom Action")
    }

    if (showSuccess) {
      Text("done", modifier = Modifier.parikshanTag("scrolled_action_done", text = "done"))
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}