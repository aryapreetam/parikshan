# Installation

To install Parikshan, apply the Gradle plugin to your Compose Multiplatform project.

## 1. Apply the Plugin

Add the plugin to your root `settings.gradle.kts` or `build.gradle.kts`:

```kotlin
plugins {
  id("io.github.aryapreetam.parikshan") version "0.0.5"
}
```

Then apply the plugin to your target multiplatform application module (typically `:composeApp`):

```kotlin
plugins {
  id("io.github.aryapreetam.parikshan")
}
```

---

## 2. Configure Plugin Extension

Configure target settings in the application module's `build.gradle.kts`. The configuration block controls ports, timeouts, and launcher parameters:

```kotlin
parikshan {
  // Port used by the embedded test servers (defaults to 9877)
  port.set(9877)
    
  // Fully qualified activity name to launch on Android targets
  androidLaunchActivityClassName.set("com.example.app.MainActivity")
    
  // Local server port for WasmJs web distribution (defaults to 8081)
  wasmServerPort.set(8081)
    
  // IP address/host that the test runner communicates with (defaults to "127.0.0.1")
  host.set("127.0.0.1")
    
  // Maximum wait time in milliseconds for the target application to boot (defaults to 90000)
  startupTimeoutMs.set(90_000L)
    
  // Polling frequency in milliseconds when checking for server readiness (defaults to 250)
  startupPollIntervalMs.set(250L)
    
  // Overrides the desktop window title when launched
  desktopWindowTitle.set("Parikshan E2E Playground")
}
```

---

## 3. Core Dependencies & Project Tasks

The plugin automatically configures your Kotlin Multiplatform project:
1. Adds the necessary test dependencies to `commonMain` and `commonTest` source sets.
2. Registers target-specific JVM-side test tasks: `e2eDesktopTest`, `e2eWasmTest`, `e2eAndroidTest`, and `e2eIosTest`.
3. Registers the unified multi-platform parallel orchestration task: `e2eTest`.

You write your test classes in `commonTest`, and they run across any selected target platform.
