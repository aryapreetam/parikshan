# Gradle Plugin Configuration

You can configure the `parikshan` block in your application module's `build.gradle.kts` to control runtime environment variables, ports, timeouts, and launcher parameters.

## Configuration Block Options

The following properties are available inside the `parikshan` configuration block:

| Property Name | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `host` | `Property<String>` | `"127.0.0.1"` | The IP address/host that the test runner communicates with. |
| `port` | `Property<Int>` | `9877` | The port used by the embedded test servers (Desktop, iOS, Android). |
| `appJarTaskName` | `Property<String>` | `"packageUberJarForCurrentOS"` | The name of the Gradle task that packages the Desktop application into a single runnable Uber JAR. |
| `appArgs` | `ListProperty<String>` | `emptyList()` | CLI arguments to pass to the Desktop application at startup. |
| `desktopWindowTitle` | `Property<String>` | `null` | Overrides the desktop window title when launched. |
| `desktopTestTaskName` | `Property<String>` | `null` | Custom Gradle test task name for Desktop execution. |
| `wasmDistributionTaskName`| `Property<String>` | `null` | Custom task name that distributes the compiled WasmJs static website. |
| `wasmServerPort` | `Property<Int>` | `8081` | The port of the local HTTP server serving the compiled WasmJs application. |
| `androidLaunchActivityClassName` | `Property<String>` | `null` | The fully qualified activity name (e.g. `"com.example.app.MainActivity"`) that must be launched on Android. |
| `startupTimeoutMs` | `Property<Long>` | `90000L` | Maximum time in milliseconds to wait for the target application server to boot and reply. |
| `startupPollIntervalMs` | `Property<Long>` | `250L` | Polling frequency in milliseconds when checking for server readiness. |

## Example Configuration

```kotlin
parikshan {
    host.set("127.0.0.1")
    port.set(9877)
    startupTimeoutMs.set(60_000L)
    androidLaunchActivityClassName.set("sample.app.MainActivity")
    wasmServerPort.set(8099)
}
```
