# Your First Test

This guide walks through creating and running your first multiplatform UI test from scratch using Parikshan.

---

## 1. Create a Project

Generate a new Compose Multiplatform project using the official [Kotlin Multiplatform Wizard](https://kmp.jetbrains.com/).

Select your target platforms (Android, iOS, Desktop, Web Wasm) and download the generated project template.

---

## 2. Apply the Parikshan Plugin

Refer to the [Installation Page](../getting-started/installation.md) to apply the Parikshan plugin and configure dependencies in your shared application module (`app/shared/build.gradle.kts` or `shared/build.gradle.kts`).

---

## 3. Create a Simple UI

In `shared/src/commonMain/kotlin/App.kt`, create a simple greeting UI component using `Modifier.testTag` to mark interactive elements:

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun App() {
  MaterialTheme {
    var name by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Enter your name") },
        modifier = Modifier.testTag("name_input")
      )

      Button(
        onClick = { greeting = "Hello, $name!" },
        modifier = Modifier.padding(top = 8.dp).testTag("greet_button")
      ) {
        Text("Greet")
      }

      if (greeting.isNotEmpty()) {
        Text(
          text = greeting,
          modifier = Modifier.padding(top = 16.dp).testTag("greeting_text")
        )
      }
    }
  }
}
```

---

## 4. Write Your First Test

Create a new test class inside `shared/src/commonTest/kotlin/sample/app/SimpleGreetTest.kt`:

```kotlin
package sample.app

import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class SimpleGreetTest {
  @Test
  fun testSimpleGreeting() = e2eTest {
    // 1. Enter name into text field
    input("name_input", "Parikshan")

    // 2. Click Greet button
    click("greet_button")

    // 3. Verify greeting output
    assertVisible("Hello, Parikshan!")
  }
}
```

---

## 5. Run on Target Platforms

Execute the test across targets using the unified Gradle orchestration task:

```bash
# Run tests across all targets
./gradlew :shared:e2eTest

# Run tests targeting specific platforms concurrently
./gradlew :shared:e2eTest --targets=android,jvm
```

To target a single platform during development:

```bash
# Run on Desktop JVM
./gradlew :shared:e2eDesktopTest

# Run on Web (WasmJs)
./gradlew :shared:e2eWasmTest

# Run on Android Emulator
./gradlew :shared:e2eAndroidTest

# Run on iOS Simulator
./gradlew :shared:e2eIosTest
```

View aggregated execution pass/fail logs, failure screenshots, and report link will be available in the console. You can also find it under:
```text
build/reports/tests/e2eTest/index.html
```
