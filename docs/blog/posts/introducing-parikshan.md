---
date: 2026-07-23
description: Write E2E UI tests in Kotlin and run them across Desktop, Web (WasmJs), Android, and iOS Simulator targets with Parikshan.
categories:
  - E2E Testing
---

# Introducing Parikshan

Parikshan is an end-to-end (E2E) testing framework for Compose Multiplatform applications. It provides Kotlin DSL to write UI tests and run them across Desktop (JVM), Web (WasmJs), Android, and iOS Simulator targets.

<!-- more -->

## Unified E2E Testing for Compose Multiplatform

While Kotlin Multiplatform allows sharing business logic and UI code, verifying that UI components render and behave correctly across all target platforms remains a manual or fragmented process.

---

## The Pain Points in Compose Multiplatform Testing

Developers typically face these issues when testing Compose Multiplatform applications:

* **Platform-Specific Test Tooling**: To verify a flow on all targets, you must rewrite the same test logic multiple times using different tools (such as Jetpack Compose Testing APIs on Android, XCTest on iOS, Playwright for WasmJs, and desktop-specific test harnesses).
* **Code Duplication**: Maintaining separate test repositories or test suites for each target leads to significant code replication.
* **Production Code Pollution**: Emulating and driving UI components from external test suites often requires inserting testing hooks, test-only flags, or test dependencies directly into production application builds.

---

## How Parikshan Solves These Problems

Parikshan lets you write one end-to-end test suite in Kotlin inside your shared `commonTest` source set. The framework handles target communication under the hood.

* **Unified DSL**: You interact with your application using a single, platform-agnostic API:
  ```kotlin
  @Test
  fun testLoginFlow() = e2eTest {
    input("username_input", "user@domain.com")
    input("password_input", "securepassword")
    click("login_button")

    assertVisible("dashboard_home")
  }
  ```
* **Concurrent Execution**: Run the tests concurrently on all targeted platforms (Desktop, Web, Android, iOS Simulator) using a single command, aggregating all target logs and screenshots into a single HTML report.
* **Isolated Production Builds**: No testing infrastructure or test-specific dependencies are compiled into your final production application binary.

---

## Getting Started

To run your first test, apply the Parikshan plugin to your shared application module (`shared/build.gradle.kts`):

```kotlin
plugins {
  id("io.github.aryapreetam.parikshan") version "0.0.5"
}
```

Refer to the [Quickstart Guide](../../getting-started/quickstart.md) or explore our [Sample Applications](../../examples.md) to set up your test suite.
