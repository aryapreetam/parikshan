# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.8] - 2026-08-20

### Added
- **Window Layout & Sizing**: Added runtime window positioning and sizing options (`--layout=side-by-side`, `--window-size=<w>x<h>`, `--desktop-window-size`, `--wasm-window-size`, `--app-mode`).
- **Synchronized Multi-Target Execution**: Added lockstep step-barrier test execution (`--sync`) driving multiple target platforms concurrently.
- **Continuous Watch Mode & Keep-Alive**: Added file system watcher (`--watch`) for automatic re-execution on code changes, and persistent application session preservation (`--keep-alive`).
- **Documentation Verification**: Added `scripts/verify-docs-local.sh` script automating MkDocs, Dokka API reference, and showcase Wasm distribution builds.

### Fixed & Resolved Issues
- **Issue #10**: Fixed iOS text input focus and crash issues on un-tagged input fields by adding parent label text fallback and KVC input safety.
- **Issue #12**: Resolved plugin configuration failures on mobile-only KMP projects lacking explicit Desktop/JVM targets.
- **Issue #13**: Added safe host test task resolution for `com.android.kotlin.multiplatform.library` modules without throwing `UnknownTaskException`.
- **Issue #15**: Refactored test tasks and target configurers to use Gradle `TaskProvider` APIs for full Gradle Configuration Cache compatibility.
- **Issue #16**: Prevented false-positive test passes by ignoring `e2eTest` blocks during standard host unit test tasks (e.g., `testAndroidHostTest`) and activating them strictly during Parikshan E2E tasks.

### Changes
- Improved documentation and website layout

## [0.0.6] - 2026-07-24

### Added
- Added support for all Compose Multiplatform project layouts, including standalone applications, multi-module split projects (`:composeApp`, `:androidApp`, `:iosApp`), and `:shared` module architectures.
- Added automatic target application discovery across Android, iOS, Desktop, and Wasm multi-module project structures.
- Added `cmp-latest` full-stack storefront sample to demonstrate real-world testing workflows including server mocking, database seeding, and end-to-end integration.
- Added `composables-sample` to demonstrate and verify Parikshan against custom design systems and non-Material UI primitives (e.g., `composeunstyled`).
- Extracted Gradle plugin tasks into dedicated `DefaultTask` classes with full Gradle Configuration Cache compatibility (`--configuration-cache`).
- Added dynamic port forwarding and configurable port properties (`parikshan.port`, `parikshan.android.port`, `parikshan.ios.port`) with automatic collision resolution.
- Integrated Kover code coverage tracking for JVM unit test suites with HTML and XML report generation.
- Added automatic `JAVA_HOME` and `PATH` propagation across child processes (`xcodebuild`, `jlink`, `am instrument`).

### Changed
- Updated iOS instrumentation engine to support Compose Multiplatform 1.11.0+ while maintaining full backward compatibility with Compose Multiplatform 1.10.1.
- Replaced per-class process spawning with batch suite execution to reduce multi-target test runtimes.
- Suppressed internal client, server, and driver modules from generated Dokka documentation to surface a clean public API.
- Sanitized environment variable propagation (`SDKROOT`, `ARCHS`, `PLATFORM_NAME`) when executing `xcodebuild` from Gradle.

### Fixed
- Fixed Wasm asset serving by adding missing MIME types (`.css`, `.png`, `.json`) to the local embedded test server.
- Added HTTP readiness polling (`postPing`) and automated `logcat` error dumping on Android instrumentation startup.
- Fixed optional activity launcher argument handling (`am instrument`) when `launcherActivity` is omitted or empty.


## [0.0.5] - 2026-07-15

### Changed
- Docs & Demo updated


## [0.0.4] - 2026-07-15

### Fixed
- resolved publishing pipeline for Gradle Plugin Portal and Maven Central

## [0.0.3] - 2026-07-14

### Added
- Added iOS ComposeScene remote E2E driver, zero-config standalone Android E2E support, and improved gestures (dragging/swiping) for Wasm and Desktop.
- Added parallel multi-target test execution (`e2eTest`), unified multi-platform HTML reporting, and automated video recording configuration.
- Added video file links in reports

### Fixed
- Fixed Wasm tag-stripping issues, overlay scrolling bugs on iOS, and AWT focus stealing during background tests.
- Fixed a polymorphic JSON serialization exception in `ProtocolSerializationTest` and resolved port conflicts during parallel execution.
- Removed legacy setup template scripts and consolidated the documentation website layout.


## [0.0.2] - 2026-06-02

### Fixed
- Fixed issues related to CI failure in macOS release artifact in release workflow
- Restored missing GitHub Pages deployment logic in the release workflow

### Changed
- Improved documentation

## [0.0.1] - 2026-06-02

### Added
- Initial release of Parikshan.
- Cross-platform intent-based DSL (`click`, `input`, `assertVisible`, `assertText`).
- Smart Selector engine with tag, text, and substring resolution.
- Multiplatform drivers for Android, iOS, Desktop (JVM), and Web (Wasm).
- In-app orchestration server for Compose Multiplatform.
- Parikshan Gradle Plugin for automated environment setup and execution.
- Headless CI support via XVFB and Playwright integration.
- Video recording out-of-the-box with `CLASS` and `SESSION` strategy configuration.
