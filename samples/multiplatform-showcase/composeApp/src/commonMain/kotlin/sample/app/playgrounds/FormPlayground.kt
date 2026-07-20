package sample.app.playgrounds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable
fun FormPlayground(
  onFormSubmitted: (String) -> Unit
) {
  val scrollState = rememberScrollState()
  var name by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  
  var sliderValue by remember { mutableFloatStateOf(50f) }
  var switchValue by remember { mutableStateOf(false) }
  var radioSelection by remember { mutableStateOf("Option A") }
  var checkboxValue by remember { mutableStateOf(false) }
  
  var indicText by remember { mutableStateOf("") }
  
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
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(16.dp)
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

    // Slider component
    Column {
      Text("Range Selector: ${sliderValue.toInt()}%")
      Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        valueRange = 0f..100f,
        modifier = Modifier.fillMaxWidth().testTag("form_slider")
      )
    }

    // Switch component
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

    // Radio button group
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

    // International Text Input
    OutlinedTextField(
      value = indicText,
      onValueChange = { indicText = it },
      label = { Text("Indic / Multilingual Input") },
      placeholder = { Text("उदा. नमस्ते / தமிழ்") },
      modifier = Modifier.fillMaxWidth().testTag("form_indic_input")
    )
    if (indicText.isNotEmpty()) {
      Text(
        "Indic Value: $indicText",
        modifier = Modifier.testTag("indic_preview_text")
      )
    }

    // Checkbox component
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

    // Submit Button
    Button(
      onClick = {
        if (formIsValid) {
          onFormSubmitted("Successfully Submitted: $name")
        }
      },
      enabled = formIsValid,
      modifier = Modifier.fillMaxWidth().testTag("form_submit_button")
    ) {
      Text("Submit Registration")
    }
  }
}
