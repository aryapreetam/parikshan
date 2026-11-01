# File Picker Automation

Automating file uploads and document selection in end-to-end tests typically introduces severe test flakiness. Native operating system dialogs (such as macOS `NSOpenPanel`, Windows Common Item Dialog, Linux GTK file chooser, Android DocumentsUI, and iOS document sheets) execute modal loops outside the Compose view hierarchy. These modals freeze the application UI thread on desktop or launch external system processes on mobile, causing test runners to stall or deadlock.

Parikshan provides declarative file picker automation (`withFilePicker`, `withFilePickerCancel`) that intercepts dialog invocations at the platform boundary. It supplies selected files immediately without blocking the application and without requiring test-specific modifications to your production code.

---

## How It Works

Instead of attempting to locate and click buttons inside an operating system's native file chooser window, you wrap the triggering UI action inside a `withFilePicker` block:

```kotlin
@Test
fun testSingleFileUpload() = e2eTest {
  // Parikshan automatically discovers fixtures in src/commonTest/resources/ or src/test/resources/
  withFilePicker("invoice.pdf") {
    click("select_document_button")
  }

  assertVisible("Uploaded: invoice.pdf")
}
```

### Execution Lifecycle

1. **Pre-Registration:** Before the lambda executes, `withFilePicker` resolves the test fixture paths on the host system and registers them with the active target driver.
2. **Triggering UI Action:** The enclosed block (e.g., `click("select_document_button")`) runs on the Compose UI. The action that triggers the file dialog **must** be inside this block.
3. **Synchronous Interception:** When your application invokes the file picker API, Parikshan intercepts the invocation immediately, supplies the registered paths, and returns control without opening an OS modal window.
4. **Guaranteed Teardown:** The listener unregisters in a `finally` block when the lambda exits.
5. **Fail-Fast Protection:** If the enclosed block finishes without your application opening a file dialog, Parikshan throws `PickerNotOpenedException(timeoutMs)`. This prevents tests from silently passing when a button is broken or disabled.

---

## Supported Platforms & Interception Engines

Parikshan uses target-native interception engines across all supported environments:

| Target Platform | Framework Scope | Interception Engine |
| :--- | :--- | :--- |
| **Desktop (macOS, Windows, Linux)** | Compose Multiplatform (JVM) | Java Agent (`ParikshanAgent`) bytecode instrumentation on `java.awt.Dialog.show()`. Intercepts on the Event Dispatch Thread before native modal loops (`[NSOpenPanel runModal]`) engage. |
| **Web (WasmJs)** | Compose Multiplatform | Playwright browser `page.onFileChooser` subscription injecting virtual file buffers directly into Chromium. |
| **Android** | Compose Multiplatform & Standalone Android | In-app `AndroidFilePickerWatcher` intercepting `ActivityResultContracts` (`GetContent`, `GetMultipleContents`, `OpenDocument`, `OpenMultipleDocuments`, `PickVisualMedia`, `OpenDocumentTree`) and serving synthetic `content://` URIs via `ParikshanFileProvider`. |
| **iOS** | Compose Multiplatform | In-app `IosFilePickerWatcher` swizzling `UIViewController.presentViewController:animated:completion:` to intercept `UIDocumentPickerViewController` and `PHPickerViewController` via runtime Objective-C subclassing. |

---

## Why In-Boundary Interception Instead of OS Dialog Automation?

Automating file choosers by opening the actual operating system dialog and simulating physical clicks on system buttons is a common source of test flakiness in cross-platform testing. Parikshan deliberately intercepts pickers at the platform boundary rather than driving OS dialog windows.

Here is why OS dialog automation fails across target platforms:

### 1. Desktop (macOS, Windows, Linux)
* **The AWT Event Dispatch Thread (EDT) Deadlock:** On the JVM, opening `java.awt.FileDialog.setVisible(true)` or `JFileChooser.showOpenDialog()` initiates a synchronous native modal event loop (e.g. `[NSOpenPanel runModal]` on macOS Cocoa). If a test dispatches a click on a Compose button and awaits completion before looking for the dialog window, the thread blocks synchronously on the modal loop — the click action never returns, and the test deadlocks.
* **Headless CI Crashes:** In headless Linux CI environments (such as GitHub Actions runners without an active X11 or Wayland display server), attempting to instantiate or display native AWT dialogs throws a fatal `HeadlessException`.
* **OS Focus Stealing:** Operating system dialogs steal focus away from the application window and render outside Compose Multiplatform's semantics tree, preventing element query or interaction.

### 2. Web (WasmJs)
* **Browser Security Sandbox:** Browser-level file inputs (`<input type="file">`) and the File System Access API trigger native operating system file choosers. For security reasons, browser engines strictly isolate host OS windows from in-page JavaScript execution; scripts cannot query or click buttons inside the OS picker.
* **Canvas Isolation:** Compose for Web (Wasm) renders all UI components into a single HTML `<canvas>` via Skiko/WebGL. The OS file chooser exists outside the DOM and canvas coordinate space entirely.

### 3. Android (Compose Multiplatform & Standalone Android)
* **OEM UI Fragmentation:** Android file picking routes through `com.android.documentsui` (Storage Access Framework) or `com.google.android.providers.media.module` (Photo Picker). Across Android API levels (API 24 through 35) and OEM skins (Samsung OneUI, Xiaomi MIUI, Google Pixel), view hierarchies, element resource IDs, and screen layouts differ substantially.
* **Localization Fragility:** Automating system pickers using text matching (`"Open"`, `"Select"`, `"Cancel"`) breaks whenever devices or emulators run in non-English locales (such as German, Sanskrit, or Japanese).
* **Latency & System Prompts:** Out-of-process system pickers involve inter-process animations, runtime storage permissions, and navigation drawers that introduce 3–5 seconds of flakiness per picker interaction.

### 4. iOS (Simulator & Physical Device)
* **Out-of-Process SpringBoard Sheets:** `UIDocumentPickerViewController` and `PHPickerViewController` render as remote view services hosted by Apple's SpringBoard daemon outside the application's view hierarchy.
* **XCUITest Query Fragility:** Interacting with system document browser sheets via XCUITest requires coordinate taps or fragile system element queries that break across iOS major versions (iOS 16 vs 17 vs 18) and fail when iCloud Drive is unconfigured or signed out in simulators.

### Architectural Separation of Concerns
The application under test does not implement or maintain the operating system's file chooser UI. An E2E test's responsibility is to verify **how the application handles** receiving selected file paths, multi-file lists, directory tree URIs, or cancellations.

Intercepting at the API contract boundary delivers deterministic, sub-millisecond execution, zero window popping, and full headless CI compatibility across all platforms.

---

## API Reference

### Selecting a Single File

Pass a file path to `withFilePicker`:

```kotlin
@Test
fun testSingleFile() = e2eTest {
  withFilePicker("sample.png") {
    click("pick_image_button")
  }

  assertVisible("Selected: sample.png")
}
```

### Selecting Multiple Files

Pass multiple file paths as varargs or as a `List<String>`:

```kotlin
@Test
fun testMultipleFiles() = e2eTest {
  withFilePicker("photo1.png", "photo2.png") {
    click("pick_photos_button")
  }

  assertText("selected_count", "2 files selected")
}
```

### Directory Selection

Pass a directory path to pick a folder (supported via `ACTION_OPEN_DOCUMENT_TREE` on Android and folder pickers on Desktop and iOS):

```kotlin
@Test
fun testDirectorySelection() = e2eTest {
  withFilePicker("export_dir") {
    click("choose_folder_button")
  }

  assertVisible("Folder selected")
}
```

### Simulating User Cancellation (`withFilePickerCancel`)

Test how your UI behaves when a user cancels or dismisses the file picker without selecting files:

```kotlin
@Test
fun testCancellation() = e2eTest {
  withFilePickerCancel {
    click("select_document_button")
  }

  assertText("picker_status", "Picker cancelled")
}
```

### Custom Timeout Configuration

By default, Parikshan waits up to 10,000 milliseconds for the application to trigger a file dialog after executing the block. You can adjust this timeout:

```kotlin
withFilePicker("report.csv", timeoutMs = 3000L) {
  click("export_report_button")
}
```

---

## Test Fixture Placement & Resolution

Parikshan's `PickerPathResolver` automatically locates test fixtures across standard project directory structures so you do not need to place test assets in your application module root:

### Candidate Search Hierarchy

When you specify a path (e.g. `"invoice.pdf"` or `"fixtures/sample.png"`), Parikshan searches the following locations in order:

1. **Exact Path:** Absolute path or path relative to the current working directory.
2. **User Home Expansion:** Paths starting with `~` or `~/` expand to the current user home directory (e.g. `"~/Downloads/sample.png"`).
3. **Module Root:** Relative to the Gradle project directory (`parikshan.projectDir`).
4. **KMP Common Test Resources:** `<projectDir>/src/commonTest/resources/<path>`
5. **Standalone Android & JVM Test Resources:** `<projectDir>/src/test/resources/<path>`
6. **Device Instrumentation Resources:** `<projectDir>/src/androidTest/resources/<path>` and `<projectDir>/src/androidTest/assets/<path>`
7. **Android Unit Test Resources:** `<projectDir>/src/androidUnitTest/resources/<path>`

If a file exists at any candidate location, Parikshan resolves its canonical path. If the file is not found anywhere in the candidate hierarchy, it throws `FileNotFoundException` before initiating the UI interaction.

---

## Host-to-Device Asset Staging

Mobile emulators and physical devices run in sandboxed environments and cannot read files directly from the host filesystem. Parikshan handles file staging automatically:

* **Android:** Before executing the test step, the driver stages resolved files to the target device via ADB:
  ```bash
  adb push <hostPath> /data/local/tmp/parikshan_fixtures/<fileName>
  adb push <hostPath> /sdcard/Download/parikshan_fixtures/<fileName>
  ```
  The embedded `ParikshanFileProvider` creates a readable `content://` URI with read permissions granted to the application under test.
* **iOS:** The driver copies resolved fixtures into the simulator app's sandboxed container or imports media assets via `xcrun simctl addmedia`.

---

## Platform Gotchas & Best Practices

### Android: Avoid `singleInstance` Launch Mode

If the activity that launches the file picker uses `android:launchMode="singleInstance"`, Android's `ActivityTaskManager` cancels the pending activity result (`RESULT_CANCELED`) whenever an intent launches as a new task.

**Fix:** Use `standard` (the default) or `singleTop` for activities that launch `rememberLauncherForActivityResult`.

### iOS: Retain Delegate References in Compose State

UIKit's `UIDocumentPickerViewController.delegate` property is `@property (nullable, weak)`. If an anonymous delegate object is instantiated inside a button callback without a strong reference, Automatic Reference Counting (ARC) deallocates the delegate before the document picker returns.

**Fix:** Hold a strong reference to the delegate across the presentation lifecycle:

```kotlin
val delegateRef = remember { mutableStateOf<UIDocumentPickerDelegateProtocol?>(null) }
```

---

## Visual Demos

### Demo 1: Multiplatform Showcase (Compose Multiplatform)

Demonstrates native platform file and directory picker automation across Desktop JVM, Web Wasm, Android physical device, and iOS simulator. Driven by [FilePickerIntegrationTest.kt](https://github.com/aryapreetam/parikshan/blob/main/samples/multiplatform-showcase/composeApp/src/commonTest/kotlin/sample/app/FilePickerIntegrationTest.kt):

```bash
./gradlew :samples:multiplatform-showcase:composeApp:e2eTest --tests="sample.app.FilePickerIntegrationTest.testFilePickerEndToEndFlow" \
  # optional
  --window-size=830x545 \
  --desktop-window-position=0x0 \
  --wasm-window-position=0x600 \
  --app-mode --sync
```
<video controls autoplay loop muted playsinline onloadedmetadata="this.playbackRate = 2.0;" style="width: 100%; border-radius: 6px; margin: 1rem 0;">
  <source src="../../assets/mshowcase-fp.mp4" type="video/mp4" />
</video>

### Demo 2: Real-World Integration with [cmp-imgcompress](https://github.com/aryapreetam/cmp-imgcompress)

Demonstrates end-to-end integration with [FileKit 0.14.0](https://github.com/vinceglb/FileKit), selecting a large image from `src/commonTest/resources/large_sample.png` and executing compression pipelines across all 4 targets. Driven by [ImageCompressionIntegrationTest.kt](https://github.com/aryapreetam/cmp-imgcompress/blob/main/sample/composeApp/src/commonTest/kotlin/sample/app/ImageCompressionIntegrationTest.kt):

```bash
./gradlew :sample:composeApp:e2eTest --tests="sample.app.ImageCompressionIntegrationTest" \
  # optional
  --window-size=830x545  \
  --desktop-window-position=0x0 \
  --wasm-window-position=0x600 \
  --app-mode --sync
```

<video controls autoplay loop muted playsinline onloadedmetadata="this.playbackRate = 2.0;" style="width: 100%; border-radius: 6px; margin: 1rem 0;">
  <source src="../../assets/imgcmpress-fp.mp4" type="video/mp4" />
</video>