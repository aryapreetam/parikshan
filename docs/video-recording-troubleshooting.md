# Parikshan Video Recording Troubleshooting Guide

This guide describes how to configure, run, and troubleshoot video recording sessions in the Parikshan end-to-end (E2E) testing framework across all supported targets.

---

## 1. Video Configuration Properties

The following system and Gradle properties control the host-side and client-side video recorders:

| System Property | Default Value | Description |
|---|---|---|
| `parikshan.video.enabled` | `false` | Set to `true` to enable video recording across test sessions. |
| `parikshan.video.outputDir` | `build/reports/e2e/videos` | Target directory where the final video files will be stored. |
| `parikshan.video.granularity` | `class` | The execution scale of the recording session. Options: `run` (one video for the whole run), `class` (one video per test class), `test` (one video per test method). |
| `parikshan.video.fps` | `15` | Frame rate of captured desktop videos (1 to 30 fps). |
| `parikshan.video.showCursor` | `true` | Show mouse cursor in desktop video recordings. |
| `parikshan.video.stepDelayMs` | `0` | Artificial execution delay added after each test command when video is active. |
| `parikshan.video.postRollMs` | `1000` | Delay after test completion before finalizing video stream encoding. |

---

## 2. Platform Specific Requirements & Dependencies

### Desktop
* **Requirements:** Pure JVM runtime using AWT screen bounds.
* **Troubleshooting:**
  * Ensure the JVM process has screen capture permissions on the OS (especially on macOS under "Security & Privacy -> Screen Recording").
  * To disable focus stealing during desktop runs, set `-Dparikshan.desktop.focus=false`.

### Web (WasmJs)
* **Requirements:** Playwright browser engine.
* **Troubleshooting:**
  * Playwright automatically captures video if configured. Ensure Playwright browser binaries are correctly installed (usually handled automatically by the Gradle plugin).
  * Video files are captured as `.webm`.

### Android
* **Requirements:** Android SDK and `adb` available in system PATH.
* **Troubleshooting:**
  * The recorder uses `adb shell screenrecord` on the target emulator/device.
  * Ensure `adb` connection is stable and only one device/emulator is listed, or specify the serial using `parikshan.android.serial`.
  * The max duration of `screenrecord` is 180 seconds. For longer tests, use `run` or `class` granularity with care.

### iOS
* **Requirements:** macOS host, Xcode Command Line Tools, and `ffmpeg` (optional, for stream faststart indexing).
* **Troubleshooting:**
  * The recorder invokes `xcrun simctl io <udid> recordVideo` to record simulator screens.
  * If the video file is corrupted or unplayable in standard web browsers, ensure `ffmpeg` is installed (`brew install ffmpeg`) to allow automatic conversion to `faststart` MP4 format on stop.
  * Close any active OS simulator captures before launching tests.

---

## 3. Viewing Videos in Reports

Once E2E tests finish, a unified HTML report is generated at:
`samples/multiplatform-showcase/composeApp/build/reports/tests/e2eTest/index.html`

* In the **Tests** tab, a "Video" column will provide a "Watch Video" link next to each test method.
* In the **Failed tests** tab, an interactive `<video>` player is embedded directly below the failure stack trace for immediate visual debugging.
