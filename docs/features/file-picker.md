# File Picker Automation

Automating file uploads and document attachments in end-to-end tests typically introduces severe test flakiness. Native OS dialogs (such as macOS Cocoa `NSOpenPanel`, Windows Common Item Dialog, Linux GTK file chooser, and browser `<input type="file">` popups) execute modal event loops that block the application UI thread, freezing standard test automation runners.

Parikshan provides declarative file picker automation (`withFilePicker`, `withFilePickerCancel`) that intercepts dialog invocations at the bytecode and browser bridge layers, populating selected files in under a millisecond without blocking the application.

---

## How It Works

Instead of attempting to locate and click buttons inside an operating system's native file chooser window, you wrap the triggering UI action inside a `withFilePicker` block:

1. **Pre-Registration:** `withFilePicker` registers the desired file paths with the active test driver.
2. **UI Action:** The enclosed block (e.g., `click("Upload File")`) executes on the Compose UI.
3. **Instant Interception:** When your application invokes the file picker API (`FileDialog`, `JFileChooser`, or browser file chooser), Parikshan intercepts the call immediately, injects the registered paths, and returns without opening a blocking native modal.
4. **Verification:** If the enclosed block completes without the application ever triggering a file picker, Parikshan throws `PickerNotOpenedException` to prevent false-positive test passes.

---

## Usage Examples

### Selecting a Single File

Pass a relative or absolute file path to `withFilePicker`:

```kotlin
@Test
fun testSingleFileUpload() = e2eTest {
  navigateToSection("document_upload_screen")

  withFilePicker("documents/invoice.pdf") {
    click("select_document_button")
  }

  assertVisible("Uploaded: invoice.pdf")
}
```

Relative paths resolve against your project root directory.

---

### Selecting Multiple Files

Pass multiple file paths as varargs or a `List<String>`:

```kotlin
@Test
fun testMultipleFilesSelection() = e2eTest {
  withFilePicker("images/photo1.png", "images/photo2.png") {
    click("choose_photos_button")
  }

  assertText("selected_files_count", "2 files selected")
}
```

---

### Simulating User Cancellation (`withFilePickerCancel`)

Test how your UI handles when a user dismisses the file picker without selecting any files:

```kotlin
@Test
fun testCancelFileSelection() = e2eTest {
  withFilePickerCancel {
    click("select_document_button")
  }

  assertText("picker_status", "Selection cancelled")
}
```

---

### Custom Trigger Timeout

By default, Parikshan waits up to 10,000 milliseconds for the application to trigger a file dialog after executing the block. You can customize this timeout:

```kotlin
withFilePicker("report.csv", timeoutMs = 3000L) {
  click("export_report_button")
}
```

---

## Supported Targets

| Target | Underlying Interception Engine |
| :--- | :--- |
| **Desktop (JVM)** | Bytecode agent intercepting `java.awt.FileDialog` and `javax.swing.JFileChooser` |
| **Web (Wasm)** | Playwright browser `fileChooser` event interception |
| **Android** | Activity Result Contract (`ActivityResultLauncher`) interception |
| **iOS** | `UIDocumentPickerViewController` controller bypass |
