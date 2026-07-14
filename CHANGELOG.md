# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
