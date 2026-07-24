# Frequently Asked Questions

This page contains answers to common questions regarding Parikshan's capabilities, platform compatibility, and execution environments.

---

### Can we test our iOS app which uses SwiftUI?

No. Parikshan performs UI automation by traversing and interacting with the Compose Multiplatform semantic tree. SwiftUI elements are rendered outside of Compose's layout canvas and are not represented in the semantic tree. iOS applications must build their UI layer using Compose Multiplatform to be testable.

---

### Can we use this library for standalone Android applications?

Yes, provided the UI is built entirely using Jetpack Compose. Legacy Android XML-based layouts are not supported as they do not generate Compose semantic nodes. Refer to the `standalone-android` sample configuration for details.

---

### Does Parikshan support Gradle Configuration Caching?

Yes. The custom Gradle plugin and E2E runner tasks fully support the Gradle Configuration Cache. Run verification locally using:

```bash
./gradlew :shared:e2eTest --configuration-cache --dry-run
```

---

### How is video recording implemented across different platforms?

Video recording is handled by platform-specific tools to optimize capture performance:
* **Desktop (JVM)**: Captures sequential snapshots of the window canvas and encodes them into an MP4 file using the JCodec library.
* **Web (WasmJs)**: Playwright records the browser tab execution natively to a WebM file.
* **Android**: Uses the ADB command-line tool to invoke the native `screenrecord` utility on the target device.
* **iOS**: Invokes `xcrun simctl io` on the host macOS to record the target iOS simulator.

---

### How do I handle port conflicts on shared CI agents?

Parikshan communicates with targets using local WebSocket/HTTP servers running on default ports (e.g. Android `9879`, iOS `9878`, Desktop `9877`). If a port conflict occurs on a shared build machine:

1. **Reclaim Ports**: Pass the `--reclaim-ports` task flag to force-terminate existing processes holding these target ports:
   ```bash
   ./gradlew :shared:e2eTest --reclaim-ports
   ```
2. **Dynamic Port Range Assignment**: Override default ports using system properties in your Gradle execution:
   ```bash
   ./gradlew :shared:e2eTest -Dparikshan.port=9999
   ```

---

### Why does Web (WasmJs) testing fail on headless CI runners?

Playwright requires Chromium binaries to execute WasmJs tests in a browser. If the test runner aborts with Playwright errors:

1. **Ensure Headless Mode**: Ensure the browser is running headlessly on standard CI virtual environments:
   ```bash
   ./gradlew :shared:e2eTest -Dparikshan.wasm.headless=true
   ```
2. **Install Playwright Browsers**: Run the Playwright installer script before running E2E tests:
   ```bash
   npx playwright install --with-deps chromium
   ```
