# Installation

To install Parikshan, apply the Gradle plugin to your Compose Multiplatform project.

## 1. Apply the Plugin

Add the plugin to your `shared` / `composeApp` `build.gradle.kts`:

```kotlin
plugins {
  id("io.github.aryapreetam.parikshan") version "0.0.5"
}
```

=== "Old Structure"

    ```text
    OldStructure/
    ├── composeApp/
    |   └── build.gradle.kts  <- apply here
    ├── gradle/
    ├── iosApp/
    ├── build.gradle.kts
    ├── gradlew
    ├── gradlew.bat
    └── settings.gradle.kts
    ```

=== "New Structure"

    ```text
    NewStructure/
    ├── androidApp/
    ├── desktopApp/
    ├── gradle/
    ├── iosApp/
    ├── shared/
    |   └── build.gradle.kts  <- apply here
    ├── webApp/
    ├── build.gradle.kts
    ├── gradlew
    ├── gradlew.bat
    └── settings.gradle.kts
    ```

    ### `New Structure (separate shared Logic & UI)`

    ```text
    NewStructure/
    ├── ...
    ├── sharedLogic/
    ├── sharedUI/
    |   └── build.gradle.kts  <- apply here
    ├── ...
    ```

=== "New Structure(With Server)"

    ```text
    NewStructureWithServer/
    ├── app/
    │   ├── androidApp/
    │   ├── desktopApp/
    │   ├── iosApp/
    │   ├── shared/
    |   |   └── build.gradle.kts  <- apply here
    │   └── webApp/
    ├── core/
    ├── gradle/
    ├── server/
    ├── build.gradle.kts
    ├── gradlew
    ├── gradlew.bat
    └── settings.gradle.kts
    ```

=== "Standalone Android"

    ```text
    AndroidProject/
    ├── app/
    |   └── build.gradle.kts  <- apply here
    ├── gradle/
    ├── build.gradle.kts
    ├── gradle.properties
    ├── gradlew
    └── settings.gradle.kts
    ```

---

## 2. Platform Compatibility and Dependencies

The E2E test runner executes in the host JVM process, connecting to the target application via platform-specific drivers.

### Host OS and Device Prerequisites

| Test Target | Host OS Compatibility | Device Prerequisites |
| :--- | :--- | :--- |
| **Desktop JVM** | macOS, Linux, Windows | None (runs inside host JVM) |
| **Web (WasmJs)** | macOS, Linux, Windows | Node.js runtime and Playwright browser binaries |
| **Android** | macOS, Linux, Windows | Android Emulator or connected device via ADB |
| **iOS Simulator** | macOS only | Xcode developer command-line tools installed |

### Underlying Test Drivers

| Target | Driver Engine | Communication Protocol |
| :--- | :--- | :--- |
| **Desktop JVM** | Embedded Server (`:parikshan-server`) inside app | Local WebSockets / HTTP |
| **Web (WasmJs)** | Playwright (Node.js bridge) | Headless browser JS event hooks |
| **Android** | Executes inside Test APK (Compose Testing APIs + UiAutomator) | Instrumentation runner (`am instrument`) via ADB |
| **iOS Simulator** | Embedded Server (`:parikshan-server`) inside app | Local WebSockets / HTTP to simulator process |

---

## 3. Core Dependencies & Project Tasks

The plugin automatically configures your Kotlin Multiplatform project:

1. Adds the necessary test dependencies to `commonMain` and `commonTest` source sets.
2. Registers the unified multi-platform parallel orchestration task: `e2eTest`. 
3. Registers target-specific JVM-side test tasks: `e2eWasmTest`, `e2eAndroidTest`, `e2eIosTest` & for `jvm`/`desktop`:
    - `jvm("desktop")` ->  `e2eDesktopTest` OR `e2eTest --targets=desktop`
    - `jvm("custom")` -> `e2eCustomTest` OR `e2eTest --targets=custom`
    - `jvm()` ->  `e2eJvmTest` OR `e2eTest --targets=jvm`
4. You can use it as `./gradlew e2eTest --targets=jvm,android,ios,wasm`
5. If you are running tests for standalone Android project(without KMP/CMP), you can use `e2eAndroidTest` directly OR `e2eTest` without `--targets` property(target is inferred).

You write your test classes in `commonTest`, and they run across any/all selected target platform.

---

## 4. Gradle Configuration Cache Verification

To ensure compatibility with optimized Gradle environments (common in large-scale enterprise builds), the Parikshan Gradle tasks support the **Gradle Configuration Cache**.

Verify serialization configuration on your project by running a dry run execution:

```bash
./gradlew :shared:e2eTest --configuration-cache --dry-run
```

Ensure this task passes without serialization warnings or build aborts before committing your E2E suite to CI pipelines.
