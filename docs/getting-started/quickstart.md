# Quickstart

This guide shows how to write and run your first end-to-end test using Parikshan.

## 1. Create an E2E Test

Create a test class inside your shared library's `commonTest` directory (e.g., `composeApp/src/commonTest/kotlin/sample/app/LoginTest.kt`).

Wrap your test body inside the `e2eTest` block:

```kotlin
package sample.app

import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class LoginTest {
  @Test
  fun testSuccessfulLogin() = e2eTest {
    // Input text into fields matching Modifier.testTag
    input("username_field", "admin")
    input("password_field", "password123")

    // Click on the login button
    click("login_button")

    // Assertions
    assertVisible("dashboard_screen")
    assertText("welcome_header", "Welcome back, admin!")

    // Capture screenshot
    screenshot("dashboard-success")
  }
}
```

---

## 2. Execute Tests

### Specific Target Execution
Run dedicated tasks for individual targets:

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

### Orchestrated Suite Execution
Run tests concurrently across targets using `e2eTest`:

```bash
./gradlew :composeApp:e2eTest
```

Filter by specific target platforms or test class names:

```bash
# Target specific platforms only
./gradlew :composeApp:e2eTest --targets=desktop,wasm

# Filter by test class
./gradlew :composeApp:e2eTest --tests "sample.app.LoginTest"
```

---

## 3. View Execution Reports

When you run the unified `e2eTest` task, individual target test results are aggregated into an HTML report under:

```text
build/reports/tests/e2eTest/index.html
```

Open this file in a browser to inspect pass/fail statuses, failure stack traces, and failure screenshots across targets.

---

## 4. Test Lifecycle and State Resets

Parikshan distinguishes between **Synchronous Infrastructure Hooks** and **UI State Resets**.

### Synchronous Infrastructure Hooks (`@BeforeAll`, `@AfterAll`, `@BeforeTest`, `@AfterTest`)

Standard Kotlin/JUnit test annotations (`@BeforeAll`, `@AfterAll`, `@BeforeTest`, `@AfterTest`) execute synchronous Kotlin code outside of `e2eTest`. Use these for non-UI setup:
* Database seeding and cleanup
* Mock server initialization (e.g. configuring Ktor `MockEngine` or local HTTP endpoints)
* Resetting mock registries (e.g. `ServiceRegistry.resetForTesting()`)

```kotlin
import io.github.aryapreetam.parikshan.BeforeAll
import io.github.aryapreetam.parikshan.AfterAll
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import io.github.aryapreetam.parikshan.e2eTest

class UserRegistrationTest {
  companion object {
    @BeforeAll
    fun seedDatabaseAndStartMockServer() {
      MockDatabase.seedTestUsers()
      MockKtorServer.start(port = 8080)
    }

    @AfterAll
    fun teardownServer() {
      MockKtorServer.stop()
      MockDatabase.clear()
    }
  }

  @BeforeTest
  fun setupMockEngine() {
    MockKtorServer.resetEndpoints()
  }

  @AfterTest
  fun cleanupRegistry() {
    ServiceRegistry.resetForTesting()
  }

  @Test
  fun testRegistration() = e2eTest {
    input("email_field", "newuser@example.com")
    click("register_button")
    assertVisible("confirmation_screen")
  }
}
```

### UI Lifecycle Hooks (`E2ETestLifecycle`)

To perform UI resets before or after each test using Parikshan DSL commands, implement the `E2ETestLifecycle` interface on your test class.

Override `beforeEach()` and `afterEach()` to execute UI navigation commands inside the active `E2ETestScope` receiver context:

```kotlin
import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class SettingsFlowTest : E2ETestLifecycle {

  override suspend fun E2ETestScope.beforeEach() {
    // Executes inside E2ETestScope before each test
    click("nav_settings_tab")
    assertVisible("settings_screen")
  }

  override suspend fun E2ETestScope.afterEach() {
    // Executes inside E2ETestScope after each test
    click("nav_home_tab")
    assertVisible("home_screen")
  }

  @Test
  fun testToggleDarkMode() = e2eTest {
    click("dark_mode_switch")
    assertVisible("dark_theme_active")
  }
}
```

!!! warning "Use `relaunchApp()` sparingly on Desktop JVM"
    `relaunchApp()` destroys and re-launches the application process or Desktop window, introducing a 1 to 2-second boot latency per test execution on Desktop JVM targets (on WasmJs it is a fast browser reload). Avoid running it sequentially across many tests; use in-app UI navigation resets (`E2ETestLifecycle`) for fast test teardown under 100ms.
