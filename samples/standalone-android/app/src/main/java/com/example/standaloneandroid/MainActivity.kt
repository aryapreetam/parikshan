package com.example.standaloneandroid

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.standaloneandroid.ui.theme.StandaloneAndroidTheme

class MainActivity : ComponentActivity() {
  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      StandaloneAndroidTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) {
          SumCalculatorScreen()
        }
      }
    }
  }
}

@Composable
fun SumCalculatorScreen() {
  var firstNumber by remember { mutableStateOf("") }
  var secondNumber by remember { mutableStateOf("") }
  var sum by remember { mutableStateOf<Int?>(null) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    // First Input Box
    TextField(
      value = firstNumber,
      onValueChange = { firstNumber = it },
      label = { Text("Enter First Number") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.testTag("firstNumberInput")
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Second Input Box
    TextField(
      value = secondNumber,
      onValueChange = { secondNumber = it },
      label = { Text("Enter Second Number") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.testTag("secondNumberInput")
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Calculate Button
    Button(
      onClick = {
        val first = firstNumber.toIntOrNull() ?: 0
        val second = secondNumber.toIntOrNull() ?: 0
        sum = first + second
      },
      modifier = Modifier.testTag("calculateButton")
    ) {
      Text("Calculate Sum")
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Result Text
    if (sum != null) {
      Text(
        text = "Sum: $sum",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.testTag("resultText")
      )
    }
  }
}