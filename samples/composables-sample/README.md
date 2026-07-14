# composables-sample

## Run

From the project root:

- JVM: `./gradlew :desktopApp:hotRunJvm --auto`
- Android: open the project in Android Studio and run the `androidApp` app on a device or emulator
- Android install from terminal: `./gradlew :androidApp:installDebug`
- iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run the app on a simulator or device
- Wasm: `./gradlew :webApp:wasmJsBrowserDevelopmentRun`

## Code Formatting

This project uses ktfmt, provided via the Spotless gradle plugin.

To check for any formatting issues run:

```shell
./gradlew spotlessCheck
```

To automatically format your code run:

```shell
./gradlew spotlessApply
```
