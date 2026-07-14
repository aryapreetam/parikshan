# CI Integration

Parikshan E2E tests can be executed headlessly inside continuous integration (CI) pipelines.

---

## 📦 GitHub Actions Marketplace Actions

To simplify headless setup and dependency management, you can use the official GitHub Actions published in the Marketplace.

### 1. Wasm E2E Action

The [parikshan-e2e-wasm](https://github.com/marketplace/actions/parikshan-e2e-wasm) action (`aryapreetam/parikshan-wasm-action@v1`) sets up a headless Chromium environment and installs required Playwright browser binaries.

```yaml
- name: Run Parikshan Wasm E2E tests
  uses: aryapreetam/parikshan-wasm-action@v1
  with:
    command: './gradlew :samples:multiplatform-showcase:composeApp:e2eWasmTest --no-configuration-cache'
```

### 2. Desktop E2E Action

The [parikshan-e2e-desktop](https://github.com/marketplace/actions/parikshan-e2e-desktop) action (`aryapreetam/parikshan-desktop-action@v1`) initializes a virtual frame buffer (`Xvfb`) on Linux runners to allow the JVM Desktop application to launch and render headless windows.

```yaml
- name: Run Parikshan Desktop E2E tests
  uses: aryapreetam/parikshan-desktop-action@v1
  with:
    command: './gradlew :samples:multiplatform-showcase:composeApp:e2eDesktopTest --no-configuration-cache'
```

---

## 🛠️ Complete GitHub Actions Workflow Example

Below is a complete, concurrent workflow configuration running unit and E2E tests across targets:

```yaml
name: E2E Verification

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '21'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      # Run unit and local tests
      - name: Run Unit Tests
        run: ./gradlew test

      # Run Wasm E2E tests headlessly
      - name: Run Wasm E2E
        uses: aryapreetam/parikshan-wasm-action@v1
        with:
          command: './gradlew :samples:multiplatform-showcase:composeApp:e2eWasmTest'

      # Run Desktop E2E headlessly using Xvfb
      - name: Run Desktop E2E
        uses: aryapreetam/parikshan-desktop-action@v1
        with:
          command: './gradlew :samples:multiplatform-showcase:composeApp:e2eDesktopTest'
```

---

## 🖥️ Headless Configuration Notes

* **Desktop (JVM)**: Desktop applications require a display server to launch. Linux CI environments do not have a physical display, so you must wrap the execution command inside an `xvfb-run` utility or use our desktop action.
* **iOS Simulator**: iOS testing requires a macOS runner (`macos-latest`). The simulator runs headlessly by default inside the runner shell environment.
* **Android Emulator**: Hardware acceleration is required for reasonable emulator performance. Ensure KVM is enabled on the Linux host runner.

---

## ⚡ CI Pipeline Optimizations

To conserve CI runner resources, configure path-based triggers in your repository workflow to skip E2E test executions when only documentation, markdown files, or metadata are modified.

Example path-based configuration in `.github/workflows/ci.yml`:

```yaml
on:
  push:
    paths:
      - 'composeApp/**'
      - 'gradle/**'
      - 'build.gradle.kts'
      - 'settings.gradle.kts'
```
