# Video Recording Configuration

Parikshan allows recording E2E execution sessions across target platforms. The output is saved to the specified directory for debugging and verification.

## Video Settings Properties

Use the following system properties to configure video capture:

| Property Name | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| **`parikshan.video.enabled`** | `Boolean` | `false` | Enables video recording when set to `true`. |
| **`parikshan.video.outputDir`** | `String` | `"build/parikshan/videos/"` | The directory where output video files are written. |
| **`parikshan.video.fps`** | `Int` | `10` | The frame rate of the output video. Must be between `1` and `30`. |
| **`parikshan.video.showCursor`** | `Boolean` | `true` | Highlights the simulated cursor positions in the video. |
| **`parikshan.video.granularity`** | `String` | `"TEST"` | Video recording granularity. Options: `"RUN"`, `"CLASS"`, `"TEST"`, `"SESSION"`. |
| **`parikshan.video.stepDelayMs`** | `Long` | `null` | Optional duration in milliseconds to pause after each action step. |
| **`parikshan.video.postRollMs`** | `Long` | `null` | Optional duration in milliseconds to continue recording after the test finishes. |
| **`parikshan.video.width`** | `Int` | `null` | Width resolution override in pixels. |
| **`parikshan.video.height`** | `Int` | `null` | Height resolution override in pixels. |
| **`parikshan.video.deviceScaleFactor`** | `Double` | `null` | Scale factor for retina/high-DPI screens (e.g. `2.0`). |

### Video Granularity Levels

* **`RUN`**: Records a single continuous video covering all executed test classes and test methods in the run session.
* **`CLASS`**: Records one video per test class.
* **`TEST`**: Records one video per test method (default).
* **`SESSION`**: Records a single video for the lifetime of the application server session.

---

## Target Support Details

* **Desktop (JVM)**: Captures a sequence of buffered screenshots and encodes them into a video file using JCodec.
* **Web (WasmJs)**: Playwright records page execution natively to webm files, which are moved to the configured output directory when the test completes.
* **Android**: Leverages the system `screenrecord` CLI utility on the Android device/emulator.
* **iOS**: Leverages the `xcrun simctl io recordVideo` utility on the booted simulator context.

---

## Example Usage

Run Desktop E2E tests with video recording enabled:

```bash
./gradlew :composeApp:e2eDesktopTest -Dparikshan.video.enabled=true
```

Run Web WasmJs E2E tests with customized output paths and 24 FPS:

```bash
./gradlew :composeApp:e2eWasmTest \
  -Dparikshan.video.enabled=true \
  -Dparikshan.video.outputDir="reports/e2e/videos" \
  -Dparikshan.video.fps=24
```
