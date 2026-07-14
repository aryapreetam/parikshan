# Frequently Asked Questions

This page contains answers to common questions regarding Parikshan's capabilities, platform compatibility, and execution environments.

---

### Can we test our iOS app which uses SwiftUI?

No. Parikshan performs UI automation by traversing and interacting with the Compose Multiplatform semantic tree. SwiftUI elements are rendered outside of Compose's layout canvas and are not represented in the semantic tree. iOS applications must build their UI layer using Compose Multiplatform to be testable.

---

### Can we use this library for standalone Android applications?

Yes, provided the UI is built entirely using Jetpack Compose. Legacy Android XML-based layouts are not supported as they do not generate Compose semantic nodes. To see how to configure a standalone Android application, refer to the [:sample:androidApp](https://github.com/aryapreetam/parikshan/tree/main/sample/androidApp) module configuration.

---

### Does Parikshan support Gradle Configuration Caching?

Yes. The custom Gradle plugin and E2E runner tasks fully support the Gradle Configuration Cache. Run verification locally using:

```bash
./gradlew :sample:composeApp:e2eDesktopTest --configuration-cache --dry-run
```

---

### How is video recording implemented across different platforms?

Video recording is handled by platform-specific tools to optimize capture performance:
* **Desktop (JVM)**: Captures sequential snapshots of the window canvas and encodes them into an MP4 file using the JCodec library.
* **Web (WasmJs)**: Playwright records the browser tab execution natively to a WebM file.
* **Android**: Uses the ADB command-line tool to invoke the native `screenrecord` utility on the target device.
* **iOS**: Invokes `xcrun simctl io` on the host macOS to record the target iOS simulator.
