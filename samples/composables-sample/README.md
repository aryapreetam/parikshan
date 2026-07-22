# Parikshan Composables UI Sample (`composables-sample`)

This sample demonstrates using the Parikshan E2E testing framework with custom third-party UI component libraries beyond standard Material Design, specifically using **Composables UI** built with **Kotlin 2.4.0** and **Compose Multiplatform 1.11.0+**.

---

## What This Sample Demonstrates

* **Third-Party UI Library Integration**: E2E element selection and gesture interactions on custom Composables UI components (custom dialogs, sheets, buttons, and navigation elements).
* **Modern Toolchain Compatibility**: Validates Parikshan testing APIs against Kotlin 2.4.0 and Compose Multiplatform 1.11.0+.
* **Cross-Platform Target Execution**: Identical test assertions running across Desktop (JVM), Web (WasmJs), Android, and iOS targets.

---

## Running E2E Tests

### 1. Concurrent E2E Test Suite (`e2eTest`)
Execute test suites across target environments. You can specify exact target platforms using `--targets=jvm,android,ios` or `-Pparikshan.targets=jvm,wasmJs`:

```bash
# Run tests across all default configured targets
./gradlew -p samples/composables-sample :shared:e2eTest

# Run tests for specific target platforms (e.g. JVM and WasmJs)
./gradlew -p samples/composables-sample :shared:e2eTest --targets=jvm,wasmJs

# Run tests for JVM, Android, and iOS targets
./gradlew -p samples/composables-sample :shared:e2eTest --targets=jvm,android,ios
```

### 2. Single Target E2E Test Tasks
* **Desktop (JVM)**:
  ```bash
  ./gradlew -p samples/composables-sample :shared:e2eJvmTest
  ```
* **Web (WasmJs)**:
  ```bash
  ./gradlew -p samples/composables-sample :shared:e2eWasmTest
  ```

---

## Running the Application

### 1. Desktop Application (JVM)
```bash
./gradlew -p samples/composables-sample :desktopApp:run
```

### 2. Web Application (WasmJs)
```bash
./gradlew -p samples/composables-sample :webApp:wasmJsBrowserDevelopmentRun
```

### 3. Android Application
```bash
./gradlew -p samples/composables-sample :androidApp:installDebug
```

### 4. iOS Application
Open `samples/composables-sample/iosApp/iosApp.xcodeproj` in Xcode and execute on simulator or device.

---

## Code Formatting

This project uses `ktfmt` via the Spotless Gradle plugin:

```bash
# Check code formatting
./gradlew -p samples/composables-sample spotlessCheck

# Apply code formatting
./gradlew -p samples/composables-sample spotlessApply
```
