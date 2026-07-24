<h1 align="center">
  <img src="docs/assets/logo.png" width="64" height="64" style="vertical-align: middle; margin-right: 14px;" alt="Parikshan Logo"/>
  Parikshan
</h1>
<p align="center">End-to-End testing framework for Compose Multiplatform</p>
<p align="center">
  <a href="https://github.com/aryapreetam/parikshan/actions/workflows/release.yml">
    <img src="https://github.com/aryapreetam/parikshan/actions/workflows/release.yml/badge.svg" alt="Release status">
  </a>
  <a href="https://mvnrepository.com/artifact/io.github.aryapreetam/parikshan">
    <img src="https://img.shields.io/maven-central/v/io.github.aryapreetam/parikshan?label=Maven%20Central&color=blue" alt="Maven Central Version">
  </a>
  <a href="https://github.com/aryapreetam/parikshan/actions/workflows/push-ci.yml">
    <img src="https://img.shields.io/badge/Coverage-Kover-brightgreen" alt="Code Coverage">
  </a>
  <a href="https://kotlinlang.org/docs/components-stability.html">
    <img src="https://kotl.in/badges/experimental.svg" alt="Kotlin Experimental">
  </a>
</p>

<p align="center">
  <img src="docs/assets/demo_short.gif" width="100%" alt="Parikshan E2E Execution Demo"/>
</p>

### Features

- Write test in Kotlin, run it on Android, iOS, Desktop, and Web (Wasm).
- No test related dependencies OR code in the main app.
- Built-in screenshot capture and video recording (including headless CI).

[**📖 API Reference**](https://aryapreetam.github.io/parikshan/api/)

---

## 🛠️ Quick Start

### 1. Apply the Plugin
In your **shared library** (e.g., `:composeApp`) `build.gradle.kts`:

```kotlin
plugins {
  id("io.github.aryapreetam.parikshan") version "0.0.5"
}
```

### 2. Write your first test
Create a test in `src/commonTest/kotlin`:

```kotlin
package sample.app

import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class SimpleGreetTest {
  @Test
  fun testSimpleGreeting() = e2eTest {
    // Enter name
    input("name_input", "परिक्षण")
    
    // Click Greet button
    click("greet_button")
    
    // Check if greeting is displayed
    assertVisible("Hello, परिक्षण!")
  }
}
```

### 3. Run on any platform
```bash
./gradlew e2eAndroidTest
./gradlew e2eIosTest
./gradlew e2eDesktopTest
./gradlew e2eWasmTest

# OR many at once concurrently

./gradlew e2eTest --targets=android,desktop,wasm
```

---

## 📦 Examples

See the [Examples Page](examples.md) for working samples and video demonstrations of the Parikshan DSL across all target platforms.

---

## License

MIT License © 2026 aryapreetam. See [LICENSE](./LICENSE) for details.
