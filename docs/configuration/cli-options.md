# CLI Options & Configuration

Parikshan supports execution configuration via Gradle task command-line flags (`--<option>`) and system properties (`-Dparikshan.*`).

---

## Task Command-Line Flags

Pass options directly to the `e2eTest` orchestration task or target-specific tasks:

```bash
./gradlew :shared:e2eTest --targets=desktop,wasm --tests "sample.app.LoginTest" --keep-alive
```

| Flag | Type | Description | Example |
| :--- | :--- | :--- | :--- |
| `--targets` | String | Comma-separated list of targets to execute | `--targets=desktop,wasm` |
| `--tests` | String | Class or method filter pattern | `--tests "sample.app.LoginTest"` |
| `--keep-alive` | Boolean | Keeps application processes and browser instances running across test runs | `--keep-alive` |
| `--reclaim-ports` | Boolean | Force terminates conflicting active app sessions holding default ports | `--reclaim-ports` |
| `--device` | String | Target device/emulator name or serial fallback | `--device "iPhone 16"` |
| `--android-device` | String | Explicit Android device serial override | `--android-device "emulator-5554"` |
| `--ios-device` | String | Explicit iOS simulator name or UDID override | `--ios-device "iPhone 16"` |

---

## Device & Emulator Selection

When executing tests on Android or iOS targets, Parikshan allows targeting specific physical devices, emulators, or simulator UDIDs.

### Using CLI Flags (`e2eTest`)

```bash
# Target specific Android emulator by serial (adb devices)
./gradlew e2eTest --targets=android --android-device="emulator-5554"

# Target specific iOS simulator by name or UDID
./gradlew e2eTest --targets=ios --ios-device="00008101-00123456789"
./gradlew e2eTest --targets=ios --ios-device="iPhone 16 Pro"

# Generic --device flag (matches Android serials or iOS simulator names)
./gradlew e2eTest --targets=android,ios --device="emulator-5554"
```

### Using System Properties (Target-Specific Tasks)

```bash
# Android serial property
./gradlew :sample:composeApp:e2eAndroidTest -Dparikshan.android.serial="emulator-5554"

# iOS UDID or device name property
./gradlew :sample:composeApp:e2eIosTest -Dparikshan.ios.udid="YOUR_SIMULATOR_UDID"
./gradlew :sample:composeApp:e2eIosTest -Dparikshan.ios.device="iPhone 16 Pro"
```

---

## System Properties (`-Dparikshan.*`)

Configure test runner behavior, target settings, and video capture via system properties:

```bash
./gradlew :shared:e2eTest -Dparikshan.target=desktop -Dparikshan.video.enabled=true
```

### Video Recording Options

| System Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `parikshan.video.enabled` | Boolean | `false` | Enables MP4 video recording per test execution |
| `parikshan.video.outputDir` | String | `build/parikshan/videos` | Target directory where generated MP4 videos are saved |
| `parikshan.video.fps` | Int | `10` | Frame rate for encoded video capture (coerced in `1..30`) |
| `parikshan.video.showCursor` | Boolean | `true` | Renders a virtual cursor overlay in recorded videos |
| `parikshan.video.granularity` | String | `class` | Recording lifecycle scope: `session`/`run` (one video for the entire run), `class` (one video per test class), or `test` (one video per test method) |
| `parikshan.video.stepDelayMs` | Long | `0` | Artificial delay inserted between test actions for video visual clarity (coerced in `0..5000` ms) |
| `parikshan.video.postRollMs` | Long | `1000` | Post-roll pause duration in ms before closing video capture (coerced in `0..10000` ms) |
| `parikshan.video.width` | Int | Auto | Target video frame width in pixels (coerced in `100..3840`) |
| `parikshan.video.height` | Int | Auto | Target video frame height in pixels (coerced in `100..2160`) |
| `parikshan.video.deviceScaleFactor` | Double | Auto | Optional device scale factor/DPR (coerced in `0.0..4.0`) |