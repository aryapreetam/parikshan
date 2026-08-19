<h1 align="center" style="border-bottom: none; margin-top: 0;">
  <img src="assets/logo.png" width="44" height="44" alt="Parikshan Logo" align="absmiddle"/>
  <span style="vertical-align: middle; margin-left: 8px;">Parikshan</span>
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
  <img src="assets/demo_short.gif" width="100%" alt="Parikshan E2E Execution Demo"/>
</p>

### Features

- Write tests in Kotlin, execute and visually watch them run across Android, iOS, Desktop, and Web (Wasm).
- Built-in [video recording](configuration/cli-options.md#video-recording-options), screenshot capture, and [test reports](reports/test-reports.md).
- [Window layout and positioning](configuration/cli-options.md#layout-size-position-control), [synchronized execution](configuration/cli-options.md#synchronized-multi-target-execution-sync), and continuous [watch mode](configuration/cli-options.md#continuous-watch-mode-watch).

[**API Reference**](https://aryapreetam.github.io/parikshan/api/) | [**Quickstart Guide**](getting-started/quickstart.md) | [**Examples**](examples.md)

---

## Quick Start

### 1. Apply the Plugin
In your shared library module (e.g. `:composeApp`) `build.gradle.kts`:

```kotlin
plugins {
  id("io.github.aryapreetam.parikshan") version "0.0.8"
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
    // enter in the input component tagged name_input
    input("name_input", "परिक्षण")
    
    // click button with text 'Greet!'
    click("Greet!")
    
    // check if greeting is displayed
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

# Run across multiple targets simultaneously
./gradlew e2eTest --targets=desktop,wasm,android,ios
```

---

## Examples & Demos

Explore working integration samples and video recordings across all platforms in the [Examples](examples.md) section.
