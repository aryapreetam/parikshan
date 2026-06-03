# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
