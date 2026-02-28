package sample.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fiblib.getFibonacciNumbers

@Composable
fun App() {
  Box(
    modifier = Modifier.fillMaxSize().background(Color.White),
    contentAlignment = Alignment.Center
  ) {
    FibonacciGenerator()
  }
}

@Composable
fun FibonacciGenerator() {
  var inputText by remember { mutableStateOf(TextFieldValue("")) }
  var fibonacciNumbers by remember { mutableStateOf<List<Int>>(emptyList()) }
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    BasicText("Fibonacci Generator")
    BasicTextField(
      value = inputText,
      onValueChange = { newValue ->
        if (newValue.text.all { it.isDigit() } && newValue.text.length <= 1) {
          inputText = newValue
          val number = newValue.text.toIntOrNull()
          fibonacciNumbers = if (number != null && number > 0) {
            getFibonacciNumbers(number)
          } else {
            emptyList()
          }
        }
      },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      singleLine = true,
      textStyle = TextStyle(
        fontSize = 18.sp,
        textAlign = TextAlign.Center
      ),
      modifier = Modifier
        .background(Color.White)
        .border(
          width = 1.dp,
          color = Color.Gray
        )
        .padding(horizontal = 8.dp, vertical = 8.dp),
      decorationBox = { innerTextField ->
        // Center placeholder and the input cursor/text within the field area
        Box(
          contentAlignment = Alignment.Center
        ) {
          if (inputText.text.isEmpty()) {
            BasicText(
              modifier = Modifier.testTag("placeholder").semantics { contentDescription = "Enter a number(1-9)" },
              text = "Enter a number(1-9)",
              style = TextStyle(color = Color.LightGray)
            )
          }
          innerTextField()
        }
      }
    )
    if(fibonacciNumbers.isNotEmpty()) {
      val text = if(inputText.text.toInt() == 1) "First fibonacci number" else "First ${inputText.text} fibonacci numbers"
      BasicText(
        text = "$text=${fibonacciNumbers.joinToString(", ")}",
      )
    }
  }
}