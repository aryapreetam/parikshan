package sample.app.playgrounds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import sample.app.formatUtcDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayPlayground() {
  var showDialog by remember { mutableStateOf(false) }
  var showBottomSheet by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }
  var showTimePicker by remember { mutableStateOf(false) }
  
  var dropdownExpanded by remember { mutableStateOf(false) }
  var selectedDropdownOption by remember { mutableStateOf("") }
  var overlayMessage by remember { mutableStateOf("") }

  val sheetState = rememberModalBottomSheetState()
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(16.dp)
      .testTag("overlay_playground_screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    horizontalAlignment = Alignment.Start
  ) {
    Text("Overlays Playground", style = MaterialTheme.typography.titleSmall)

    // 1. Dropdown Section (Using ExposedDropdownMenuBox)
    ExposedDropdownMenuBox(
      expanded = dropdownExpanded,
      onExpandedChange = { dropdownExpanded = it },
      modifier = Modifier.testTag("exposed_dropdown_box")
    ) {
      OutlinedTextField(
        value = selectedDropdownOption.ifEmpty { "Select an option" },
        onValueChange = {},
        readOnly = true,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        modifier = Modifier.menuAnchor().fillMaxWidth().testTag("dropdown_anchor")
      )
      ExposedDropdownMenu(
        expanded = dropdownExpanded,
        onDismissRequest = { dropdownExpanded = false },
        modifier = Modifier.testTag("dropdown_menu")
      ) {
        listOf("Red", "Green", "Blue", "Yellow", "Cyan", "Magenta", "Black", "White", "Gray", "Orange", "Purple", "Brown").forEach { option ->
          DropdownMenuItem(
            text = { Text("Option $option") },
            onClick = {
              selectedDropdownOption = option
              overlayMessage = "Selected $option from Dropdown"
              dropdownExpanded = false
            },
            modifier = Modifier.testTag("dropdown_item_${option.lowercase()}")
          )
        }
      }
    }

    // 2. Alert Dialog Section
    Button(
      onClick = { showDialog = true },
      modifier = Modifier.fillMaxWidth().testTag("dialog_trigger_button")
    ) {
      Text("Open Alert Dialog")
    }

    if (showDialog) {
      AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text("Confirm Action") },
        text = { Text("Are you sure you want to perform this test action?") },
        confirmButton = {
          TextButton(
            onClick = {
              overlayMessage = "Dialog Confirmed"
              showDialog = false
            },
            modifier = Modifier.testTag("dialog_confirm_button")
          ) {
            Text("Confirm")
          }
        },
        dismissButton = {
          TextButton(
            onClick = {
              overlayMessage = "Dialog Dismissed"
              showDialog = false
            },
            modifier = Modifier.testTag("dialog_dismiss_button")
          ) {
            Text("Cancel")
          }
        },
        modifier = Modifier.testTag("alert_dialog_popup")
      )
    }

    // 3. Bottom Sheet Section
    Button(
      onClick = { showBottomSheet = true },
      modifier = Modifier.fillMaxWidth().testTag("bottom_sheet_trigger_button")
    ) {
      Text("Open Bottom Sheet")
    }

    if (showBottomSheet) {
      ModalBottomSheet(
        onDismissRequest = { showBottomSheet = false },
        sheetState = sheetState,
        modifier = Modifier.testTag("modal_bottom_sheet_popup")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("bottom_sheet_content"),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text("Modal Bottom Sheet", style = MaterialTheme.typography.titleLarge)
          Text("Select an action inside the bottom sheet:")
          
          Button(
            onClick = {
              overlayMessage = "Action A from Sheet clicked"
              showBottomSheet = false
            },
            modifier = Modifier.fillMaxWidth().testTag("sheet_action_a_button")
          ) {
            Text("Execute Action A")
          }
          
          Button(
            onClick = {
              overlayMessage = "Action B from Sheet clicked"
              showBottomSheet = false
            },
            modifier = Modifier.fillMaxWidth().testTag("sheet_action_b_button")
          ) {
            Text("Execute Action B")
          }
        }
      }
    }
    
    // 4. Date Picker Section
    Button(
      onClick = { showDatePicker = true },
      modifier = Modifier.fillMaxWidth().testTag("date_picker_trigger_button")
    ) {
      Text("Open Date Picker")
    }
    
    if (showDatePicker) {
      val datePickerState = rememberDatePickerState()
      DatePickerDialog(
        onDismissRequest = { showDatePicker = false },
        confirmButton = {
          TextButton(
            onClick = {
              val millis = datePickerState.selectedDateMillis
              val date = if (millis != null) {
                formatUtcDate(millis)
              } else "None"
              overlayMessage = "Date Selected: $date"
              showDatePicker = false
            },
            modifier = Modifier.testTag("date_picker_ok_button")
          ) {
            Text("OK")
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showDatePicker = false },
            modifier = Modifier.testTag("date_picker_dismiss_button")
          ) {
            Text("Cancel")
          }
        },
        modifier = Modifier.testTag("date_picker_dialog")
      ) {
        DatePicker(state = datePickerState)
      }
    }
    
    // 5. Time Picker Section
    var use24HourTime by remember { mutableStateOf(true) }
    Button(
      onClick = { use24HourTime = false; showTimePicker = true },
      modifier = Modifier.fillMaxWidth().testTag("time_picker_12h_trigger_button")
    ) {
      Text("Open Time Picker (12h)")
    }
    Button(
      onClick = { use24HourTime = true; showTimePicker = true },
      modifier = Modifier.fillMaxWidth().testTag("time_picker_24h_trigger_button")
    ) {
      Text("Open Time Picker (24h)")
    }
    
    if (showTimePicker) {
      val timePickerState = rememberTimePickerState(is24Hour = use24HourTime)
      var isInputMode by remember { mutableStateOf(false) }
      AlertDialog(
        onDismissRequest = { showTimePicker = false },
        modifier = Modifier.testTag("time_picker_dialog"),
        confirmButton = {
          TextButton(
            onClick = {
              val formattedHour = timePickerState.hour.toString().padStart(2, '0')
              val formattedMinute = timePickerState.minute.toString().padStart(2, '0')
              overlayMessage = "Time Selected: $formattedHour:$formattedMinute"
              showTimePicker = false
            },
            modifier = Modifier.testTag("time_picker_ok_button")
          ) {
            Text("OK")
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showTimePicker = false },
            modifier = Modifier.testTag("time_picker_dismiss_button")
          ) {
            Text("Cancel")
          }
        },
        text = {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            if (isInputMode) {
              TimeInput(state = timePickerState)
              TextButton(
                onClick = { isInputMode = false },
                modifier = Modifier.testTag("toggle_time_picker_mode_button")
              ) {
                Text("Switch to touch dial mode")
              }
            } else {
              TimePicker(state = timePickerState, modifier = Modifier.testTag("time_picker_dial"))
              TextButton(
                onClick = { isInputMode = true },
                modifier = Modifier.testTag("toggle_time_picker_mode_button")
              ) {
                Text("Switch to text input mode")
              }
            }
          }
        }
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Results Label
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
      modifier = Modifier.fillMaxWidth().testTag("overlay_result_card")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text("Last Action Output:", style = MaterialTheme.typography.labelMedium)
        Text(
          overlayMessage, 
          style = MaterialTheme.typography.bodyLarge, 
          modifier = Modifier
            .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
            .testTag("overlay_result_message")
        )
      }
    }
  }
}
