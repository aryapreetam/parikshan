# CLI Options & System Overrides

You can control test execution, target platforms, and override defaults at runtime using system properties passed via command line flags (`-D<property>=<value>`).

## System Properties

The following system properties can be passed when running Parikshan test tasks:

### General Options
* **`parikshan.target`**: The target test driver execution target.
  * Supported values: `desktop`, `wasm` (or `web`), `android`, `ios`
* **`parikshan.host`**: Overrides the host IP used to connect to the target application's server.
* **`parikshan.port`**: Overrides the port used to connect to the target application's server.
* **`parikshan.token`**: Security session token applied to commands sent to target application servers.

### Android Target Options
* **`parikshan.android.serial`**: Specifies the ADB serial identifier of the target Android emulator or physical device.

### iOS Target Options
* **`parikshan.ios.udid`**: Specifies the simulator UDID to boot and deploy to. Defaults to `"booted"`.
* **`parikshan.ios.bundleId`**: Specifies the Bundle ID of the iOS application to launch.

### Desktop Target Options
* **`parikshan.desktop.launchManifest`**: The path to a custom desktop app launcher manifest configuration file.

### Wasm (Web) Target Options
* **`parikshan.wasm.url`**: Overrides the local or remote URL of the WasmJs application.
* **`parikshan.wasm.headless`**: Controls whether the Playwright browser is launched headless. Defaults to `true`.
* **`parikshan.wasm.viewportWidth`**: Width of the browser viewport.
* **`parikshan.wasm.viewportHeight`**: Height of the browser viewport.
* **`parikshan.wasm.bridgeReadyTimeoutMs`**: Time to wait for the page JS bridge to register.

---

## Task Options

The main `e2eTest` task supports task-specific CLI options:

* **`--keep-alive`**: Keeps the target application and browser instances running after the test run finishes. Subsequent test executions will perform health checks and skip recompilation and re-launching if the source code and assets are unchanged.
* **`--reclaim-ports`**: Force terminates any conflicting background applications holding default test ports (`9879` for Android, `9878` for iOS) instead of shifting to fallback ports.

### The Port Shift and Reclaim Strategy

When executing tests, Parikshan checks if the target port is occupied. If a conflict is detected:
* **Default Behavior (Port Shifting):** Parikshan automatically allocates the next available port (e.g. `9880`, `9881`) on both host and device. This allows multiple different applications to run tests concurrently on the same emulator or simulator.
* **Reclaim Behavior (with `--reclaim-ports`):** Parikshan issues a termination command (`am force-stop` on Android or `simctl terminate` on iOS) to the conflicting application's bundle identifier, frees the port, and executes the tests on the default port.

#### When to use `--reclaim-ports`
* **CI/CD Pipelines:** Use it in non-interactive builds to ensure a hermetic, clean test run on standard ports.
* **Process Cleanup:** Use it when you want to quickly kill stale background instances from previous debugging sessions without manual command-line intervention.

#### Cautions & Tradeoffs
* **Interrupts Concurrent Runs:** If you are actively running test suites for different applications concurrently on the same emulator, using `--reclaim-ports` will immediately terminate the other application, causing its tests to fail.
* **Process Termination:** It performs a hard process termination. Any unsaved diagnostic state in the conflicting application will be lost.

---

## Project Properties

You can also set the reclaim ports behavior globally in your `gradle.properties` file or pass it as a project property:

* **`parikshan.reclaimPorts`**: Set to `true` to enable reclaim behavior by default for all test runs.

Example in `gradle.properties`:
```properties
parikshan.reclaimPorts=true
```

---

## Command Line Examples

### Run Tests with Keep-Alive Enabled
To keep application and browser instances running for subsequent fast iterations:

```bash
./gradlew :composeApp:e2eTest --targets=desktop --keep-alive
```

### Run a Specific Test
Use the standard Gradle `--tests` flag to run a specific test class or method:

```bash
./gradlew :composeApp:e2eDesktopTest --tests "sample.app.LoginTest"
```

### Run Tests Headless on Wasm
Force headless mode off on the Web Wasm target:

```bash
./gradlew :composeApp:e2eWasmTest -Dparikshan.wasm.headless=false
```

### Target a Specific Android Emulator
Specify the serial of the target device when multiple emulators are running:

```bash
./gradlew :composeApp:e2eAndroidTest -Dparikshan.android.serial="emulator-5556"
```

### Reclaim Default Ports during Conflict
Force terminate any conflicting processes holding standard ports and run E2E tests:

```bash
./gradlew :composeApp:e2eTest --reclaim-ports
```

Or via project property:

```bash
./gradlew :composeApp:e2eTest -Pparikshan.reclaimPorts=true
```
