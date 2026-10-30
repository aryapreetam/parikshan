---
date: 2026-10-15
description: Introducing declarative file picker automation (withFilePicker, withFilePickerCancel), native dialog interception, and stability fixes in Parikshan 0.0.11.
categories:
  - Releases
  - Features
---

# File Picker Automation and Stability Fixes in Parikshan 0.0.11

Parikshan 0.0.11 introduces automated native file picker support across Compose Multiplatform targets (`withFilePicker`, `withFilePickerCancel`), eliminating test freezes caused by OS modal dialogs.

<!-- more -->

Here is how file picker automation works and how to use it in your test suites.

---

## The Problem: Native File Pickers Freeze Test Runners

Testing file upload workflows in Compose Multiplatform previously hit a fundamental barrier:
* On Desktop (macOS, Windows, Linux), opening `java.awt.FileDialog` or `JFileChooser` launches a native operating system modal loop (`[NSOpenPanel runModal]` on macOS). This modal blocks the AWT Event Dispatch Thread (EDT), freezing the entire application and causing test runners to time out.
* On Web (Wasm), clicking an `<input type="file">` button prompts a browser file selection dialog that cannot be driven by in-canvas click events.
* On Mobile, document pickers switch context out of the Compose canvas into system intent handlers.

Because native file pickers operate outside Compose Multiplatform's semantics tree, standard test locators cannot inspect or click dialog buttons.

---

## The Solution: Declarative Pre-Registration (`withFilePicker`)

Parikshan 0.0.11 solves this with declarative scoping. You specify the files to select *before* performing the triggering UI action:

```kotlin
@Test
fun testDocumentUpload() = e2eTest {
  navigateToSection("upload_screen")

  withFilePicker("documents/contract.pdf") {
    click("select_file_button")
  }

  assertVisible("Uploaded: contract.pdf")
}
```

### Under the Hood
* **Desktop (JVM):** A lightweight Java bytecode agent intercepts `Dialog.show()` at bytecode instruction 0, injects the selected file paths via internal AWT accessors, and returns immediately without opening a blocking native modal loop.
* **Web (Wasm):** Intercepts browser `fileChooser` events via Playwright, immediately attaching the specified file buffers.
* **Android & iOS:** Routes pre-registered paths directly through system contract launchers.

---

## Multiple File Selection

Pass multiple file paths as varargs:

```kotlin
@Test
fun testMultiFileUpload() = e2eTest {
  withFilePicker("images/photo1.png", "images/photo2.png") {
    click("upload_gallery_button")
  }

  assertText("status_text", "2 files uploaded")
}
```

---

## Testing User Cancellation (`withFilePickerCancel`)

Verify how your application handles when a user cancels or dismisses the file picker:

```kotlin
@Test
fun testCancelPicker() = e2eTest {
  withFilePickerCancel {
    click("select_file_button")
  }

  assertText("status_text", "Upload cancelled")
}
```

If the enclosed UI block fails to trigger a file picker, Parikshan throws `PickerNotOpenedException`, preventing false-positive test passes.

---

## Upgrading

Update the plugin version in your shared module's `build.gradle.kts`:

```kotlin
plugins {
  id("io.github.aryapreetam.parikshan") version "0.0.11"
}
```
