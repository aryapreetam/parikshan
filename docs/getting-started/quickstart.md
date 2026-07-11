# Quickstart

This guide shows how to write and run your first end-to-end test using Parikshan.

## 1. Create an E2E Test

Create a test class inside your shared library's `commonTest` directory (e.g. `composeApp/src/commonTest/kotlin/sample/app/LoginTest.kt`).

Wrap your test body inside the `e2eTest` block. The DSL provides simple, synchronous-looking methods to write your tests:

```kotlin
package sample.app

import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class LoginTest {

    @Test
    fun testSuccessfulLogin() = e2eTest {
        // Input text into fields by their Modifier.testTag
        input("username_field", "admin")
        input("password_field", "password123")
        
        // Click on the login button
        click("login_button")
        
        // Assertions
        assertVisible("dashboard_screen")
        assertText("welcome_header", "Welcome back, admin!")
        
        // Capture a verification screenshot
        screenshot("dashboard-success")
    }
}
```

---

## 2. Execute Tests

Parikshan supports executing tests on individual targets, or running the entire suite concurrently.

### Run on a Specific Platform
To target a single platform, run its dedicated test task:

```bash
# Run on Desktop JVM
./gradlew :composeApp:e2eDesktopTest

# Run on Web (WasmJs) via Playwright
./gradlew :composeApp:e2eWasmTest

# Run on Android Emulator or connected device
./gradlew :composeApp:e2eAndroidTest

# Run on iOS Simulator
./gradlew :composeApp:e2eIosTest
```

### Run the Orchestrated Suite
To run tests concurrently on all available local targets, use the unified `e2eTest` task:

```bash
./gradlew :composeApp:e2eTest
```

You can customize the orchestration run using command-line parameters:
```bash
# Target specific platforms only
./gradlew :composeApp:e2eTest --targets=desktop,wasm

# Filter by test name or pattern
./gradlew :composeApp:e2eTest --tests "sample.app.LoginTest"
```

---

## 3. View Execution Reports

When you run the unified `e2eTest` task, individual target test results are aggregated. When the run finishes, the `e2eTestReport` task runs automatically and generates an aggregated HTML report under:

```
build/reports/e2e/index.html
```

Open this file in a browser to inspect the consolidated pass/fail statuses, stack traces, and failure screenshots across all targets.
