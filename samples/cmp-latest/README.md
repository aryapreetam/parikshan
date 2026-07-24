# Parikshan Full-Stack E2E Showcase Sample (`cmp-latest`)

This sample demonstrates end-to-end (E2E) testing for a full-stack Compose Multiplatform application (**StorefrontApp**) communicating with an embedded Ktor backend server.

---

## What This Sample Demonstrates

* **MVVM + MVI Storefront Application**: Email authentication, asynchronous product catalog loading, modal details sheets with option toggles, delivery signature canvas, and checkout success confirmation.
* **Full-Stack Ktor Backend (`:server`)**: Local HTTP REST endpoints (`GET /api/products`, `POST /api/login`, `POST /api/orders`) providing dynamic JSON payloads over port 8080.
* **Hermetic & Live E2E Testing**: Tests run against Ktor `MockEngine` in automated CI pipelines without external server dependencies, while local runtime app builds connect live to the Ktor server with offline fallback.

---

## Running E2E Tests

### 1. Concurrent E2E Test Suite (`e2eTest`)
Execute test suites across target environments. You can specify exact target platforms using `--targets=jvm,android,ios` or `-Pparikshan.targets=jvm,wasmJs`:

```bash
# Run tests across all default configured targets
./gradlew -p samples/cmp-latest :app:shared:e2eTest

# Run tests for specific target platforms (e.g. JVM and WasmJs)
./gradlew -p samples/cmp-latest :app:shared:e2eTest --targets=jvm,wasmJs

# Run tests for JVM, Android, and iOS targets
./gradlew -p samples/cmp-latest :app:shared:e2eTest --targets=jvm,android,ios
```

### 2. Single Target E2E Test Tasks
* **Desktop (JVM)**:
  ```bash
  ./gradlew -p samples/cmp-latest :app:shared:e2eJvmTest
  ```
* **Web (WasmJs)**:
  ```bash
  ./gradlew -p samples/cmp-latest :app:shared:e2eWasmTest
  ```
* **Target Specific Test Class**:
  ```bash
  ./gradlew -p samples/cmp-latest :app:shared:e2eJvmTest --tests "org.example.project.StorefrontFlowTest"
  ```

---

## Running the Application

### 1. Ktor Backend Server (Port 8080)
```bash
./gradlew -p samples/cmp-latest :server:run
```

### 2. Desktop Application (JVM)
```bash
./gradlew -p samples/cmp-latest :app:desktopApp:run
```

### 3. Web Application (WasmJs)
```bash
./gradlew -p samples/cmp-latest :app:webApp:wasmJsBrowserDevelopmentRun
```

### 4. Android Application
```bash
./gradlew -p samples/cmp-latest :app:androidApp:assembleDebug
```

### 5. iOS Application
Open `samples/cmp-latest/app/iosApp` in Xcode and execute on simulator or device.

---

## TBD / Future Enhancements

* **Dynamic Server CRUD Persistence**: Extend `:server` with a persistent database store and real-time WebSocket event broadcasting.
* **Live Connection Status Badge**: Render a visual status badge (`Connected to Ktor Server` vs `Offline Seed Mode`) on the Storefront TopAppBar.
* **Real-time Server Mutation Tests**: Add E2E tests validating live `POST /api/products` updates reflected instantly in client view models.