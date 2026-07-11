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

## Command Line Examples

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
