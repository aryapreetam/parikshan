<p align="center">                                                                                                                                                           
      <img src="docs/assets/logo.png" width="44" height="44" alt="Parikshan Logo" align="absmiddle"/>                                                                            
      <span style="font-size: 2.2em; font-weight: bold; vertical-align: middle; margin-left: 8px;">Parikshan</span>                                                              
    </p>   

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

- Write tests in Kotlin, execute and visually watch them run across Android, iOS, Desktop, and Web (Wasm).
- Built-in [video recording](https://aryapreetam.github.io/parikshan/configuration/cli-options/#video-recording-options), screenshot capture, and [test reports](https://aryapreetam.github.io/parikshan/reports/test-reports).
- [Window layout and positioning](https://aryapreetam.github.io/parikshan/configuration/cli-options/#layout-size-position-control), [synchronized execution](https://aryapreetam.github.io/parikshan/configuration/cli-options/#synchronized-multi-target-execution-sync), and continuous [watch mode](https://aryapreetam.github.io/parikshan/configuration/cli-options/#continuous-watch-mode-watch).

[**API Reference**](https://aryapreetam.github.io/parikshan/api/) | [**Documentation**](https://aryapreetam.github.io/parikshan/)

---

## Quick Start

### 1. Apply the Plugin
In your shared library module (e.g. `:shared`/`:composeApp`) `build.gradle.kts`:

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

## Examples

See the [Examples Page](https://aryapreetam.github.io/parikshan/examples/) for working samples and video demonstrations across all supported platforms.

---

## License

MIT License © 2026 aryapreetam. See [LICENSE](./LICENSE) for details.
