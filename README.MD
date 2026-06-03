<h1 align="center">Parikshan</h1>
<p align="center"><b>Compose Multiplatform E2E Testing Framework</b></p>

<p align="center">
  <img src="https://img.shields.io/badge/Compose%20Multiplatform-v1.10.1-4285F4?logo=jetpackcompose" alt="Compose Multiplatform Version">
  <img src="https://img.shields.io/badge/Kotlin-v2.3.21-blueviolet?logo=kotlin" alt="Kotlin Version">
</p>

<p align="center">
  <a href="https://github.com/aryapreetam/parikshan/actions/workflows/release.yml">
    <img src="https://github.com/aryapreetam/parikshan/actions/workflows/release.yml/badge.svg" alt="Release workflow status">
  </a>
  <a href="https://mvnrepository.com/artifact/io.github.aryapreetam/parikshan">
    <img src="https://img.shields.io/maven-central/v/io.github.aryapreetam/parikshan?label=Maven%20Central&color=blue" alt="Maven Central Version">
  </a>
  <a href="https://github.com/aryapreetam/parikshan/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="MIT License">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/iOS-000000?logo=apple&logoColor=white" alt="iOS">
  <img src="https://img.shields.io/badge/Desktop/JVM-0078D6?logo=desktop&logoColor=white" alt="Desktop">
  <img src="https://img.shields.io/badge/Web(wasm)-4285F4?logo=google-chrome&logoColor=white" alt="Web">
</p>

Parikshan is an E2E testing framework for **Compose Multiplatform**.

### Features

- Write one test in Kotlin, run it on Android, iOS, Desktop, and Web (Wasm).
- No testing dependencies are included in your production builds.
- Automatically handles app installation and lifecycle during test execution.
- Captures screenshots and generates videos of test runs out-of-the-box (even in headless CI).

[**📖 Full Documentation & API Reference**](https://aryapreetam.github.io/parikshan/api/) | [**🌐 Wasm Demo**](https://aryapreetam.github.io/parikshan/demo/)

---

## 🛠️ Quick Start

### 1. Apply the Plugin
In your **shared library** (e.g., `:composeApp`) `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.aryapreetam.parikshan") version "0.0.2"
}
```

### 2. Write your first test
Create a test in `src/commonTest/kotlin`:

```kotlin
class MyE2ETest {
    @Test
    fun testLoginFlow() = e2eTest {
        // Intent-based DSL
        inputText("username_field", "admin")
        click("login_button")
        assertVisible("dashboard_screen")
        screenshot("login-success")
    }
}
```

### 3. Run on any platform
```bash
./gradlew e2eAndroidTest
./gradlew e2eIosTest
./gradlew e2eDesktopTest
./gradlew e2eWasmTest
```

---

## 📦 Examples

See the [sample project tests](https://github.com/aryapreetam/parikshan/tree/main/sample/composeApp/src/commonTest/kotlin/sample/app) for working examples of how to write E2E tests using the Parikshan DSL.

---

## License

MIT License © 2026 aryapreetam. See [LICENSE](./LICENSE) for details.
