---
date: 2026-09-24
description: Supporting Compose Multiplatform 1.10, 1.11, and 1.12, Android 17 compatibility, programmatic iOS keyboard dismissal, and selector resolution improvements in Parikshan 0.0.10.
categories:
  - Releases
  - Ecosystem
---

# Supporting Compose Multiplatform 1.11–1.12, Android 17, and iOS Keyboard Control

Parikshan 0.0.10 expands ecosystem compatibility to Compose Multiplatform 1.10 through 1.12, adds Android 17 (SDK 36) support, introduces programmatic iOS keyboard dismissal (`hideKeyboard()`), and hardens selector resolution.

<!-- more -->

Here is a summary of what changed and how to update your test suites.

---

## Compose Multiplatform 1.10, 1.11, and 1.12 Compatibility

### The Problem
Compose Multiplatform releases introduced internal changes to semantics tree structure, composition lifecycle dispatch, and Wasm runtime entry points. Projects migrating to CMP 1.11 and 1.12 experienced occasional semantics node lookup failures and compilation mismatches in test runners.

### The Solution
Parikshan 0.0.10 updates internal semantics extractors across all four platform drivers:
* Supported CMP versions now span `1.10.x`, `1.11.x`, and `1.12.x`.
* Single-expression Wasm entry points (`fun main() = ComposeViewport(...)`) are supported without extra wrapper boilerplate.
* Leaf semantics extraction in Wasm is scoped strictly to interactive nodes to avoid leaking irrelevant container texts.

---

## Android 17 (SDK 36) Compatibility

Android 17 introduced stricter background execution limits and changes to instrumentation runner lifecycles. 

Parikshan 0.0.10 upgrades core Android testing dependencies:
* `androidx.test.espresso:espresso-core` upgraded to `3.7.0`.
* Test runner instrumentation now complies with Android 17 SDK requirements.
* Automatic adb reverse port forwarding adapts cleanly to Android 17 emulator and physical device connections.

---

## Programmatic iOS Keyboard Control (`hideKeyboard()`)

### The Problem
On iOS simulators, an open software keyboard frequently covers lower UI elements (such as "Submit" or "Continue" buttons). If an action tries to tap a covered node, the tap event can be swallowed by the iOS keyboard view, causing unexpected test timeouts.

### The Solution
`E2ETestScope` now includes `hideKeyboard()`:

```kotlin
@Test
fun testFormSubmission() = e2eTest {
  input("email_field", "user@example.com")
  input("password_field", "secret123")

  // Dismiss software keyboard before clicking submit
  hideKeyboard()

  click("Submit")
  assertVisible("Welcome back!")
}
```

On iOS, `hideKeyboard()` sends an end-editing signal to the active UIWindow. On platforms without a software keyboard (such as Desktop), the call is a safe no-op.

---

## Selector Resolution Priority Improvements

When resolving ambiguous string queries, Parikshan previously evaluated visible leaf text nodes alongside container nodes. In complex Material 3 components (such as buttons with both an icon and a text label), this could occasionally target the inner text span instead of the outer clickable button container.

In 0.0.10, selector resolution prioritizes container nodes possessing click or input semantics before falling back to leaf text spans.

---

## Upgrading

Update the plugin version in your shared module's `build.gradle.kts`:

```kotlin
plugins {
  id("io.github.aryapreetam.parikshan") version "0.0.10"
}
```
