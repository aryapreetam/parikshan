---
date: 2026-10-31
description: Introducing declarative file picker automation (withFilePicker, withFilePickerCancel), native dialog interception, and stability fixes in Parikshan 0.0.11.
categories:
  - Releases
  - Features
---

# File Picker Automation and Interaction Fixes in Parikshan 0.0.11

Parikshan 0.0.11 adds native file and photo picker automation across Compose Multiplatform targets (`withFilePicker`, `withFilePickerCancel`), eliminating test stalls caused by operating system dialogs. This release also resolves click handling on disabled controls and prioritizes editable text during selector resolution.

<!-- more -->

Here is an overview of what changed, when to use the new picker API, and how it behaves across supported platforms.

---

## The Problem: Native Dialogs Block Test Runners

Testing file upload, image import, and document selection in Compose Multiplatform has historically been fragile:

* On **Desktop (macOS, Windows, Linux)**, opening `java.awt.FileDialog` or `JFileChooser` starts a native modal runloop (`[NSOpenPanel runModal]` on macOS). This synchronously blocks the AWT Event Dispatch Thread (EDT), freezing the application and causing test runners to deadlock.
* On **Web (WasmJs)**, clicking an element that opens an `<input type="file">` dialog prompts a browser file picker window that in-canvas clicks cannot control.
* On **Android**, file selection dispatches system intents (`ACTION_OPEN_DOCUMENT`, `ACTION_GET_CONTENT`, Photo Picker) that render outside the application process.
* On **iOS**, `UIDocumentPickerViewController` and `PHPickerViewController` present modal UIKit view controllers outside the Compose view hierarchy.

Because native pickers render outside Compose Multiplatform's semantics tree, standard test locators cannot inspect, tap, or dismiss them.

Instead of automating operating system windows—which leads to EDT deadlocks on Desktop, headless CI crashes, browser sandbox restrictions on Web, and OEM/locale flakiness on mobile—Parikshan intercepts dialog invocations at the platform contract boundary. For a detailed breakdown of platform constraints, see [Why In-Boundary Interception Instead of OS Dialog Automation](../../features/file-picker.md#why-in-boundary-interception-instead-of-os-dialog-automation).

---

## When to Use File Picker Automation

Use `withFilePicker` whenever a UI interaction opens a native operating system or browser file selector. Common scenarios include:

* Single document or image uploads
* Multi-file gallery selections
* Directory selection for file exports or batch workflows
* Testing cancellation when a user dismisses the file picker without selecting anything

---

## Declarative API: `withFilePicker`

Parikshan uses scoped pre-registration. You declare the file to select *before* executing the UI action that triggers the picker:

```kotlin
@Test
fun testDocumentUpload() = e2eTest {
  navigateToSection("upload_screen")

  // Automatically resolved from src/commonTest/resources/ or src/test/resources/
  withFilePicker("invoice.pdf") {
    click("select_document_button")
  }

  assertVisible("Uploaded: invoice.pdf")
}
```

The trigger block executes inside the lambda. When your application invokes the file picker API, Parikshan intercepts the call immediately and supplies the pre-registered files without displaying a native OS window.

You can supply:
* Clean filenames like `"invoice.pdf"` or `"fixtures/avatar.png"` (automatically discovered in your project's `src/commonTest/resources/` or `src/test/resources/`).
* Expanded user home paths like `"~/Downloads/invoice-new.pdf"`.
* Absolute system paths like `"/tmp/sample.png"`.

If the enclosed UI block finishes without the application opening a file dialog, Parikshan throws `PickerNotOpenedException`, preventing false-positive test runs.

---

## Multiple File Selection

To select multiple files, pass multiple filenames as arguments:

```kotlin
@Test
fun testMultiFileUpload() = e2eTest {
  withFilePicker("photo1.png", "photo2.png") {
    click("upload_gallery_button")
  }

  assertText("status_text", "2 files uploaded")
}
```

---

## Testing User Cancellation (`withFilePickerCancel`)

To test how your UI handles a user cancelling or dismissing the picker dialog:

```kotlin
@Test
fun testCancelPicker() = e2eTest {
  withFilePickerCancel {
    click("select_document_button")
  }

  assertText("status_text", "Upload cancelled")
}
```

---

## Supported Platforms

The picker API works identically across all supported platforms:

| Platform | Target Type | Interception Technique |
| :--- | :--- | :--- |
| **Desktop (macOS, Windows, Linux)** | Compose Multiplatform (JVM) | Java Agent bytecode injection on `java.awt.Dialog.show()`, bypassing native modal loops |
| **Web (WasmJs)** | Compose Multiplatform | Playwright browser `fileChooser` event hooks injecting virtual file buffers |
| **Android** | Compose Multiplatform & Standalone Android | In-app `ActivityResultContracts` interception backed by an embedded test `FileProvider` |
| **iOS** | Compose Multiplatform | Objective-C runtime swizzling of `UIViewController.presentViewController` for `UIDocumentPickerViewController` and `PHPickerViewController` |

---

## Visual Demos

### Demo 1: Multiplatform Showcase (Platform Native Pickers)

Demonstrates single-file, multi-file, directory selection, and cancellation driven across Desktop JVM, Web Wasm, Android physical device, and iOS simulator using [FilePickerIntegrationTest.kt](https://github.com/aryapreetam/parikshan/blob/main/samples/multiplatform-showcase/composeApp/src/commonTest/kotlin/sample/app/FilePickerIntegrationTest.kt):

<video controls autoplay loop muted playsinline onloadedmetadata="this.playbackRate = 2.0;" style="width: 100%; border-radius: 6px; margin: 1rem 0;">
  <source src="../../../../../assets/mshowcase-fp.mp4" type="video/mp4" />
</video>

### Demo 2: Real-World Integration with [cmp-imgcompress](https://github.com/aryapreetam/cmp-imgcompress)

Demonstrates picking a 5MB image from `src/commonTest/resources/` using [FileKit 0.14.0](https://github.com/vinceglb/FileKit) and compressing it across all targets, driven by [ImageCompressionIntegrationTest.kt](https://github.com/aryapreetam/cmp-imgcompress/blob/main/sample/composeApp/src/commonTest/kotlin/sample/app/ImageCompressionIntegrationTest.kt):


<video controls autoplay loop muted playsinline onloadedmetadata="this.playbackRate = 2.0;" style="width: 100%; border-radius: 6px; margin: 1rem 0;">
  <source src="../../../../../assets/imgcmpress-fp.mp4" type="video/mp4" />
</video>

---

## Interaction Bug Fixes

Parikshan 0.0.11 also includes two interaction fixes:

1. **Disabled Control Protection ([#25](https://github.com/aryapreetam/parikshan/issues/25))**: `click()` actions now verify element enabled state before dispatching clicks, failing fast with an explanatory error instead of silently clicking disabled buttons.
2. **Selector Text Priority ([#24](https://github.com/aryapreetam/parikshan/issues/24))**: Direct text extraction now checks `editableText` before falling back to `contentDescription`, resolving selector matching issues on text fields.

---

## Upgrading

Update the plugin version in your shared module's `build.gradle.kts`:

```kotlin
plugins {
  id("io.github.aryapreetam.parikshan") version "0.0.11"
}
```

For full setup details, test fixture path resolution, and asset staging mechanics, see the [File Picker Automation Guide](../../features/file-picker.md).

> **New to Parikshan?**  
> If you are setting up Parikshan for the first time, check out [Your First Test (Compose Multiplatform)](../../guides/your-first-test.md) or [Your First Test (Standalone Android)](../../guides/first-test-android.md) for step-by-step installation and configuration tutorials.
