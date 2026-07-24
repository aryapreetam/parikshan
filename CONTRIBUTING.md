# Contributing to Parikshan

Thank you for your interest in contributing to Parikshan! This document provides the guidelines and commands you need to build, test, and submit your changes.

---

## 🏗️ Project Structure

Parikshan is a complex multi-project build. Understanding the boundaries is critical:

- **`:parikshan-core`**: The protocol and selector resolution engine (Pure Kotlin Multiplatform, no UI dependencies).
- **`:parikshan-client`**: The developer-facing E2E DSL (`e2eTest`) and platform drivers (`DesktopDriver`, `WasmDriver`, `AndroidDriver`, `IosRemoteDriver`).
- **`:parikshan-server`**: Embedded in-app HTTP/WebSocket server running inside the Desktop, Android, and iOS application processes.
- **`:gradle-plugins`**: Automation Gradle plugin providing E2E test tasks (`e2eDesktopTest`, `e2eWasmTest`, `e2eAndroidTest`, `e2eIosTest`), simulator/emulator orchestration, and video recording.
- **`:samples:multiplatform-showcase:composeApp`**: Core Multiplatform showcase application used to dogfood and verify the framework across all targets.

---

## 🚀 Getting Started

### Prerequisites
- JDK 21 or later
- Node.js v20+ (for Wasm testing)
- macOS with Xcode 15+ (only required if running iOS tests)

### 1. Clone and Build
```bash
git clone https://github.com/aryapreetam/parikshan.git
cd parikshan
./gradlew assemble
```

### 2. Run the Unit Tests (Fast Feedback)
Before testing E2E behavior, ensure the core logic and test-isolation filters are intact:
```bash
# Run all unit tests across JVM, Wasm, iOS, and Android
./gradlew test

# Generate interactive HTML Code Coverage report (via kotlinx-kover)
./gradlew :parikshan-core:koverHtmlReport
# View report at: parikshan-core/build/reports/kover/html/index.html

# Verify API Binary Compatibility (.api dumps)
./gradlew apiCheck
```

### 3. Run the E2E Tests (Integration)
Parikshan dogfoods itself. To verify that your changes work in a real application environment, run the E2E orchestration tasks against the sample app:

```bash
# Desktop (JVM)
./gradlew :samples:multiplatform-showcase:composeApp:e2eDesktopTest

# Web (Wasm) - Requires Playwright
npx playwright install --with-deps chromium
./gradlew :samples:multiplatform-showcase:composeApp:e2eWasmTest

# Android (Requires a running Emulator/Device)
./gradlew :samples:multiplatform-showcase:composeApp:e2eAndroidTest

# iOS (Requires macOS and Simulator)
./gradlew :samples:multiplatform-showcase:composeApp:e2eIosTest
```
*Note: Video recording is disabled by default locally. Add `-Dparikshan.video.enabled=true` to your Gradle command to test the recording pipeline.*

---

## 🔧 Development Workflow

1. **Find an Issue**: Check the issue tracker for open bugs or feature requests. If proposing a major architectural change, please open an issue to discuss it first.
2. **Branch**: Create a feature branch (`git checkout -b fix/your-fix-name`).
3. **Code & Test**: Write your fix. If you change core behavior, you **must** verify it by running the E2E tests above.
4. **Format**: Ensure your code meets the styling guidelines:
   ```bash
   ./gradlew lintRelease
   ```
5. **Commit**: Write clear, descriptive commit messages outlining *what* changed and *why*.

---

## 📚 Documentation

If your PR introduces a new public API (e.g., a new selector or DSL command), you must include KDoc comments explaining its *intent*.

To verify how your documentation will look on the live site:
```bash
./gradlew :parikshan:dokkaGeneratePublicationHtml
```
Open `parikshan/build/dokka/html/index.html` in your browser.

---

## 🤝 Submitting a Pull Request

When you are ready, open a PR against the `main` branch. 

Your PR will automatically trigger the **multiplatform CI pipeline**, which runs:
1. Lint checks
2. Parallel Unit Tests
3. Parallel E2E Tests (Android, iOS, Desktop, Wasm) on headless runners.

**Expectations:**
- You must fill out the PR Template checklist.
- If CI fails, it is your responsibility to investigate the logs and push a fix.
- PRs that introduce "test-only" dependencies into the production server module will be rejected to maintain our Zero-Pollution guarantee.

Thank you for helping make Compose Multiplatform testing better!
