# Parikshan API Reference

Parikshan is an end-to-end (E2E) testing framework for Compose Multiplatform applications targeting Android, iOS, Desktop, and Web (WasmJs).

```kotlin
@Test
fun testUserLogin() = e2eTest {
  input("Username", "john.doe")
  input("Password", "SecretPass123!")
  click("Sign In")
  assertVisible("dashboard_screen")
}
```

## Modules

| Module | Purpose |
| :--- | :--- |
| <a href="parikshan-client/index.html"><strong>parikshan-client</strong></a> | Public test entrypoint (`e2eTest`), lifecycle annotations (`BeforeAll`, `AfterAll`). |
| <a href="parikshan-core/index.html"><strong>parikshan-core</strong></a> | Test DSL scope (`E2ETestScope`), selectors (`Selector`, `auto`, `tag`, `text`), and scroll types (`ScrollDirection`). |

## Platform Drivers

Each target platform uses a different mechanism to execute test commands:

- **Android:** Runs inside the test process using Jetpack Compose testing APIs and UiAutomator.
- **iOS:** Host JVM communicates via HTTP/WebSocket to an embedded server inside the iOS Simulator process.
- **Desktop (JVM):** Host JVM communicates via HTTP/WebSocket to an embedded server inside the Desktop application process.
- **Web (WasmJs):** Host JVM controls the browser context via Playwright and JS bridge hooks.

Platform drivers are resolved automatically by the Gradle plugin. Test code is identical across all targets.
