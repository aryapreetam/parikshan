# Installation

To install Parikshan, apply the Gradle plugin to your Compose Multiplatform project.

## 1. Apply the Plugin

Add the plugin to your `shared` / `composeApp` `build.gradle.kts`:

```kotlin
plugins {
  id("io.github.aryapreetam.parikshan") version "0.0.5"
}
```

=== "Old Structure"

    ```text
    OldStructure/
    ├── composeApp/
    |   └── build.gradle.kts  <- apply here
    ├── gradle/
    ├── iosApp/
    ├── build.gradle.kts
    ├── gradlew
    ├── gradlew.bat
    └── settings.gradle.kts
    ```

=== "New Structure"

    ```text
    NewStructure/
    ├── androidApp/
    ├── desktopApp/
    ├── gradle/
    ├── iosApp/
    ├── shared/
    |   └── build.gradle.kts  <- apply here
    ├── webApp/
    ├── build.gradle.kts
    ├── gradlew
    ├── gradlew.bat
    └── settings.gradle.kts
    ```

    ### `New Structure (separate shared Logic & UI)`

    ```text
    NewStructure/
    ├── ...
    ├── sharedLogic/
    ├── sharedUI/
    |   └── build.gradle.kts  <- apply here
    ├── ...
    ```

=== "New Structure(With Server)"

    ```text
    NewStructureWithServer/
    ├── app/
    │   ├── androidApp/
    │   ├── desktopApp/
    │   ├── iosApp/
    │   ├── shared/
    |   |   └── build.gradle.kts  <- apply here
    │   └── webApp/
    ├── core/
    ├── gradle/
    ├── server/
    ├── build.gradle.kts
    ├── gradlew
    ├── gradlew.bat
    └── settings.gradle.kts
    ```

=== "Standalone Android"

    ```text
    AndroidProject/
    ├── app/
    |   └── build.gradle.kts  <- apply here
    ├── gradle/
    ├── build.gradle.kts
    ├── gradle.properties
    ├── gradlew
    └── settings.gradle.kts
    ```

## 3. Core Dependencies & Project Tasks

The plugin automatically configures your Kotlin Multiplatform project:

1. Adds the necessary test dependencies to `commonMain` and `commonTest` source sets.
2. Registers the unified multi-platform parallel orchestration task: `e2eTest`. 
3. Registers target-specific JVM-side test tasks: `e2eWasmTest`, `e2eAndroidTest`, `e2eIosTest` & for `jvm`/`desktop`:
    - `jvm("desktop")` ->  `e2eDesktopTest` OR `e2eTest --targets=desktop`
    - `jvm("custom")` -> `e2eCustomTest` OR `e2eTest --targets=custom`
    - `jvm()` ->  `e2eJvmTest` OR `e2eTest --targets=jvm`
4. You can use it as `./gradlew e2eTest --targets=jvm,android,ios,wasm`
5. If you are running tests for standalone Android project(without KMP/CMP), you can use `e2eAndroidTest` directly OR `e2eTest` without `--targets` property(target is inferred).

You write your test classes in `commonTest`, and they run across any/all selected target platform.
