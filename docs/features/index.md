# Features & Capabilities

Parikshan provides an intent-based testing framework designed for Compose Multiplatform. You write tests once in Kotlin, execute them across desktop, web, and mobile, and inspect results through unified reports and recordings.

---

## Supported Features Matrix

The following table outlines platform capabilities available in Parikshan:

| Capability | Desktop (JVM) | Web (Wasm) | Android | iOS |
| :--- | :---: | :---: | :---: | :---: |
| **[Core Intent DSL](../getting-started/quickstart.md)** | Supported | Supported | Supported | Supported |
| **[File Picker Automation](file-picker.md)** | Supported | Supported | Supported | Supported |
| **[Synchronized Lockstep (`--sync`)](synchronized-execution.md)** | Supported | Supported | Supported | Supported |
| **[Window Layout & Sizing](window-layout.md)** | Supported | Supported | Fixed Viewport | Fixed Viewport |
| **[Continuous Watch Mode (`--watch`)](watch-mode.md)** | Supported | Supported | Planned | Planned |
| **[Video Recording & Reports](test-reports.md)** | Supported | Supported | Supported | Supported |

---

## Feature Highlights

### [Unified Intent DSL](../getting-started/quickstart.md)
Write UI tests in Kotlin using clean, high-level intents (`click`, `input`, `assertVisible`, `scrollUntilVisible`) that execute identically across all target platforms without target-specific selectors or boilerplate.

### [File Picker Automation](file-picker.md)
Automate native OS dialogs and browser file choosers using `withFilePicker` and `withFilePickerCancel` without blocking the application UI thread.

### [Synchronized Execution (`--sync`)](synchronized-execution.md)
Run multiple platform targets in lockstep barrier synchronization. Every action is dispatched simultaneously across active targets, allowing side-by-side visual comparison of UI transitions and layouts.

### [Window Layout & Sizing](window-layout.md)
Control window geometry (`--window-size`), screen placement coordinates (`--desktop-window-position`, `--wasm-window-position`), and auto-tiling (`--layout=side-by-side`) for Desktop JVM and Web Wasm targets.

### [Continuous Watch Mode (`--watch`)](watch-mode.md)
Run tests continuously during active development. Parikshan monitors your project source directories, automatically recompiles, and re-executes matching tests on file save.

### [Test Reports & Video Recording](test-reports.md)
Capture MP4 video recordings of test execution, full-resolution failure screenshots, and unified HTML reports aggregating test metrics across all platforms.
