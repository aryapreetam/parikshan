# Parikshan Component Showcase Sample (`multiplatform-showcase`)

This sample demonstrates atomic Compose Multiplatform UI component playgrounds and framework selector driver validation across Desktop, Web (WasmJs), Android, and iOS targets.

---

## What This Sample Demonstrates

* **Form & State Playground (`FormPlayground.kt`)**: Text fields, validation errors, checkbox states, switch toggles, slider controls, dropdown menus, and submit triggers.
* **Overlay & Modal Playground (`OverlayPlayground.kt`)**: Dialogs, alert popups, dropdown anchors, and snackbar notifications.
* **Gesture & Pointer Playground (`GesturePlayground.kt`)**: Drag gestures, tap triggers, press actions, and canvas drawing coordinates.
* **Scroll & Viewport Playground (`ScrollPlayground.kt`)**: LazyColumn lists, nested scroll containers, and scroll-until-visible targeting.
* **Timing & Async Playground (`TimingPlayground.kt`)**: Delayed UI state transitions, loading indicators, and async assertion timeouts.
* **Accessibility Playground (`AccessibilityPlayground.kt`)**: Content descriptions, semantics tags, semantic headers, and screen-reader accessibility properties.
* **Selector Parity Playground (`SelectorParityPlayground.kt`)**: Multi-layered element resolution matching tags, text, index constraints, and content descriptions.

---

## Running E2E Tests

### 1. Concurrent E2E Test Suite (`e2eTest`)
Execute test suites across target environments. You can specify exact target platforms using `--targets=jvm,android,ios` or `-Pparikshan.targets=jvm,wasmJs`:

```bash
# Run tests across all default configured targets
./gradlew :samples:multiplatform-showcase:composeApp:e2eTest

# Run tests for specific target platforms (e.g. JVM and WasmJs)
./gradlew :samples:multiplatform-showcase:composeApp:e2eTest --targets=jvm,wasmJs

# Run tests for JVM, Android, and iOS targets
./gradlew :samples:multiplatform-showcase:composeApp:e2eTest --targets=jvm,android,ios
```

### 2. Single Target E2E Test Tasks
* **Desktop (JVM)**:
  ```bash
  ./gradlew :samples:multiplatform-showcase:composeApp:e2eDesktopTest
  ```
* **Web (WasmJs)**:
  ```bash
  ./gradlew :samples:multiplatform-showcase:composeApp:e2eWasmTest
  ```
* **Android**:
  ```bash
  ./gradlew :samples:multiplatform-showcase:composeApp:e2eAndroidTest
  ```
* **iOS**:
  ```bash
  ./gradlew :samples:multiplatform-showcase:composeApp:e2eIosTest
  ```
* **Target Specific Test Class**:
  ```bash
  ./gradlew :samples:multiplatform-showcase:composeApp:e2eDesktopTest --tests "sample.app.FormIntegrationTest"
  ```

---

## Running the Application

### 1. Desktop Application (JVM)
```bash
./gradlew :samples:multiplatform-showcase:composeApp:run
```

### 2. Web Application (WasmJs)
```bash
./gradlew :samples:multiplatform-showcase:composeApp:wasmJsBrowserDevelopmentRun
```

### 3. Android Application
```bash
./gradlew :samples:multiplatform-showcase:androidApp:assembleDebug
```

### 4. iOS Application
Open `samples/multiplatform-showcase/iosApp` in Xcode and run on simulator or device.
