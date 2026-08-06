package sample.app.playgrounds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import sample.app.composeapp.generated.resources.*
import org.jetbrains.compose.resources.Font

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormPlayground(
  snackbarHostState: SnackbarHostState? = null,
  onFormSubmitted: (String) -> Unit = {}
) {
  val coroutineScope = rememberCoroutineScope()
  val scrollState = rememberScrollState()
  val fontFamily = FontFamily(
    Font(Res.font.NotoSansDevanagari, FontWeight.Normal),
    Font(Res.font.NotoSansDevanagari, FontWeight.Bold)
  )
  var name by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var address by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  
  var dupInput1 by remember { mutableStateOf("") }
  var dupInput2 by remember { mutableStateOf("") }
  var actionStatus by remember { mutableStateOf("") }
  var formSubmittedMessage by remember { mutableStateOf("") }
  
  var sliderValue by remember { mutableFloatStateOf(50f) }
  var switchValue by remember { mutableStateOf(false) }
  var radioSelection by remember { mutableStateOf("Option A") }
  var checkboxValue by remember { mutableStateOf(false) }
  var indicText by remember { mutableStateOf("") }
  
  val selectedTags = remember { mutableStateListOf("Kotlin", "Compose") }
  val availableTags = listOf("Kotlin", "Compose", "Desktop", "Wasm", "Android", "iOS")

  val emailError = if (email.isNotEmpty() && !email.contains("@")) "Invalid email address" else null
  val passwordError = if (password.isNotEmpty() && password.length < 6) "Password too short" else null
  val confirmError = if (confirmPassword.isNotEmpty() && password != confirmPassword) "Passwords do not match" else null

  val formIsValid = name.isNotEmpty() &&
                    email.isNotEmpty() && emailError == null &&
                    password.isNotEmpty() && passwordError == null &&
                    confirmPassword.isNotEmpty() && confirmError == null &&
                    checkboxValue

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(scrollState)
      .testTag("form_playground_screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text("Form & State Playground", style = MaterialTheme.typography.headlineMedium)
    
    // Name Input
    OutlinedTextField(
      value = name,
      onValueChange = { name = it },
      label = { Text("Name") },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
      modifier = Modifier.fillMaxWidth().testTag("form_name_input")
    )
    
    // Email Input
    OutlinedTextField(
      value = email,
      onValueChange = { email = it },
      label = { Text("Email Address") },
      isError = emailError != null,
      supportingText = emailError?.let { { Text(it) } },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
      modifier = Modifier.fillMaxWidth().testTag("form_email_input")
    )
    
    // Phone Number
    OutlinedTextField(
      value = phone,
      onValueChange = { phone = it },
      label = { Text("Phone Number") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
      modifier = Modifier.fillMaxWidth().testTag("form_phone_input")
    )

    // Multi-line Shipping Address
    OutlinedTextField(
      value = address,
      onValueChange = { address = it },
      label = { Text("Shipping Address") },
      minLines = 3,
      modifier = Modifier.fillMaxWidth().testTag("form_address_input")
    )
    
    // Password Input
    OutlinedTextField(
      value = password,
      onValueChange = { password = it },
      label = { Text("Password") },
      isError = passwordError != null,
      supportingText = passwordError?.let { { Text(it) } },
      visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
      trailingIcon = {
        IconButton(
          onClick = { passwordVisible = !passwordVisible },
          modifier = Modifier.testTag("password_toggle_button")
        ) {
          Icon(
            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = if (passwordVisible) "Hide password" else "Show password"
          )
        }
      },
      modifier = Modifier.fillMaxWidth().testTag("form_password_input")
    )
    
    // Confirm Password Input
    OutlinedTextField(
      value = confirmPassword,
      onValueChange = { confirmPassword = it },
      label = { Text("Confirm Password") },
      isError = confirmError != null,
      supportingText = confirmError?.let { { Text(it) } },
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
      modifier = Modifier.fillMaxWidth().testTag("form_confirm_password_input")
    )

    // Filter Chips (Material 3)
    Text("Category Tags", style = MaterialTheme.typography.titleSmall)
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth().testTag("form_category_tags")
    ) {
      availableTags.take(3).forEach { tag ->
        FilterChip(
          selected = selectedTags.contains(tag),
          onClick = {
            if (selectedTags.contains(tag)) selectedTags.remove(tag) else selectedTags.add(tag)
          },
          label = { Text(tag) },
          modifier = Modifier.testTag("chip_$tag")
        )
      }
    }

    // Range Selector Slider
    Column {
      Text("Range Selector: ${sliderValue.toInt()}%")
      Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        valueRange = 0f..100f,
        modifier = Modifier.fillMaxWidth().testTag("form_slider")
      )
    }

    // Switch Component
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth()
    ) {
      Text("Enable Notifications")
      Spacer(modifier = Modifier.weight(1f))
      Switch(
        checked = switchValue,
        onCheckedChange = { switchValue = it },
        modifier = Modifier.testTag("form_switch")
      )
    }

    // Radio Button Group
    Column {
      Text("Preference Selection")
      listOf("Option A", "Option B", "Option C").forEach { option ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .toggleable(
              value = (radioSelection == option),
              onValueChange = { if (it) radioSelection = option },
              role = Role.RadioButton
            )
            .padding(vertical = 4.dp)
            .testTag("radio_$option")
        ) {
          RadioButton(
            selected = (radioSelection == option),
            onClick = null
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(option)
        }
      }
    }

    // Indic / Multilingual Input
    OutlinedTextField(
      value = indicText,
      onValueChange = { indicText = it },
      label = { Text("Indic / Multilingual Input", fontFamily = fontFamily) },
      placeholder = { Text("उदा. नमस्ते", fontFamily = fontFamily) },
      textStyle = LocalTextStyle.current.copy(fontFamily = fontFamily),
      modifier = Modifier.fillMaxWidth().testTag("form_indic_input")
    )
    if (indicText.isNotEmpty()) {
      Text(
        text = "Indic Value: $indicText",
        fontFamily = fontFamily,
        modifier = Modifier.testTag("indic_preview_text")
      )
    }


    // Checkbox Terms
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .toggleable(
          value = checkboxValue,
          onValueChange = { checkboxValue = it },
          role = Role.Checkbox
        )
        .padding(vertical = 8.dp)
        .testTag("form_agree_row")
    ) {
      Checkbox(
        checked = checkboxValue,
        onCheckedChange = null,
        modifier = Modifier.testTag("form_checkbox")
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text("I agree to the terms and conditions")
    }

    // Submit Registration / Final Submit Buttons
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      Button(
        onClick = {
          if (formIsValid) {
            formSubmittedMessage = "Successfully Submitted: $name"
            onFormSubmitted(formSubmittedMessage)
            snackbarHostState?.let {
              coroutineScope.launch {
                it.showSnackbar(formSubmittedMessage)
              }
            }
          }
        },
        enabled = formIsValid,
        modifier = Modifier.weight(1f).testTag("form_submit_button")
      ) {
        Text("Submit")
      }
    }
  }
}
