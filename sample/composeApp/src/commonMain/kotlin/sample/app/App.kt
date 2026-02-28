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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private enum class SampleScreen {
  ParimandalList,
  InputForm,
  ScrollDemo
}

@Composable
fun App() {
  val activeScreen = remember { mutableStateOf(SampleScreen.ParimandalList) }
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
          onClick = { activeScreen.value = SampleScreen.ParimandalList },
          modifier = Modifier.fillMaxWidth().testTag("nav_parimandal")
        ) {
          Text("Parimandal List")
        }
        Button(
          onClick = {
            showFormSuccess.value = false
            activeScreen.value = SampleScreen.InputForm
          },
          modifier = Modifier.fillMaxWidth().testTag("nav_input_form")
        ) {
          Text("Input Form")
        }
        Button(
          onClick = {
            showScrollSuccess.value = false
            activeScreen.value = SampleScreen.ScrollDemo
          },
          modifier = Modifier.fillMaxWidth().testTag("nav_scroll_demo")
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
          SampleScreen.ParimandalList ->
            ParimandalListScreen()

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
private fun ParimandalListScreen() {
  Column(
    modifier = Modifier.fillMaxSize().testTag("parimandal_list_screen"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text("Parimandal List", style = MaterialTheme.typography.headlineSmall)
    Text("परिमंडल १", modifier = Modifier.fillMaxWidth().testTag("parimandal_item_1"))
    Text("परिमंडल २", modifier = Modifier.fillMaxWidth().testTag("parimandal_item_2"))
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
    modifier = Modifier.fillMaxSize().testTag("input_form_screen"),
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
    Button(
      onClick = onSubmit,
      modifier = Modifier.testTag("form_submit_button")
    ) {
      Text("Submit")
    }
    if (showSuccess) {
      Text("सबमिट सफल", modifier = Modifier.testTag("form_success_message"))
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
      Text("Scrollable Item ${index + 1}", modifier = Modifier.fillMaxWidth().testTag("scroll_item_$index"))
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
