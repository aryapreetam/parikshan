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

Parikshan is a testing framework designed specifically for **Compose Multiplatform**. It provides a unified, cross-platform DSL to write end-to-end (E2E) UI tests that run on Android, iOS, Desktop, and Web (Wasm) using a single codebase.

### Why Parikshan?

Writing E2E tests for multiplatform apps often requires juggling multiple tools like Appium, Maestro, and Playwright, each with its own configuration and production code "pollution." Parikshan aims to simplify this:

- **Zero-Pollution**: No testing code or "test-only" modules are included in your production builds.
- **Unified DSL**: Write once in Kotlin and run across all supported platforms.
- **Architecture-Aware**: Automatically discovers your app modules and orchestrates the installation and execution lifecycle.
- **Built-in Recording**: Automatically generates videos of your test runs (even in headless CI environments).

[**📖 Full Documentation & API Reference**](https://aryapreetam.github.io/parikshan/api/) | [**🌐 Wasm Demo**](https://aryapreetam.github.io/parikshan/demo/)

---

## 🛠️ Quick Start

### 1. Apply the Plugin
In your **shared library** (e.g., `:composeApp`) `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.aryapreetam.parikshan") version "0.0.1"
}
```

### 2. Write your first test
Create a test in `src/commonTest/kotlin`:

```kotlin
class MyE2ETest {
    @Test
    fun testLoginFlow() = e2eTest {
        // Simple, intent-based DSL
        waitFor("login_button")
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

## 📦 Try the Sample App

Validate the library on your platform by downloading the pre-built sample application:

| Platform | Download Link |
|----------|--------------|
| 🍏 **macOS (Intel)** | [Download x64 DMG](https://github.com/aryapreetam/parikshan/releases/latest/download/sample-app-desktop-x64.dmg) |
| 🍎 **macOS (Apple Silicon)** | [Download arm64 DMG](https://github.com/aryapreetam/parikshan/releases/latest/download/sample-app-desktop-aarch64.dmg) |
| 🪟 **Windows** | [Download MSI](https://github.com/aryapreetam/parikshan/releases/latest/download/sample-app-windows.msi) |
| 🤖 **Android** | [Download APK](https://github.com/aryapreetam/parikshan/releases/latest/download/sample-app-android-unsigned.apk) |
| 🌐 **Web (Wasm)** | [Download ZIP](https://github.com/aryapreetam/parikshan/releases/latest/download/sample-app-wasm.zip) |
|  **iOS Simulator** | [Download ZIP](https://github.com/aryapreetam/parikshan/releases/latest/download/sample-app-ios-simulator.zip) |

---

## License

MIT License © 2026 aryapreetam and contributors. See [LICENSE](./LICENSE) for details.

---

## 🙏 Acknowledgments

This library was built with inspiration from the excellent work of the Kotlin and Compose Multiplatform communities, including [terrakok's KMP Web Wizard](https://terrakok.github.io/kmp-web-wizard/) and the many developers pushing the boundaries of cross-platform testing.
