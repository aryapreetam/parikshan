# API Overview & Selector Parity

This document outlines the core testing DSL methods provided by `E2ETestScope` and explains how Parikshan achieves selector parity and priority resolution across target platforms.

---

## Selector Parity & Priority Model

Because Compose Multiplatform renders UI elements onto a canvas rather than using platform-native view trees (like Android XML or iOS UIKit), traditional OS-specific locators do not work.

Parikshan traverses the Compose Multiplatform semantics node tree to resolve selectors uniformly on Desktop, Web WasmJs, Android, and iOS (**Selector Parity**).

### Selector Priority Sequence

When you pass a raw string query to a DSL method (such as `click("Login")`), Parikshan maps it to `Selector.Auto` and evaluates matches according to the following **Selector Priority** order:

1. **Exact Tag Match**: Looks for elements containing the exact `Modifier.testTag` matching the query.
2. **Exact Text Match**: Falls back to matching elements whose visible text exactly matches the query (case-insensitive).
3. **Prefix Text Match**: Falls back to matching elements whose visible text starts with the query (case-insensitive).
4. **Substring/Semantics Match**: Falls back to matching elements whose visible text or accessibility content description contains the query as a substring (case-insensitive).

---

## Selector Syntax Variants

You can write selectors using auto-resolution, strict typing, or helper functions depending on your verification needs:

```kotlin
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.text
import io.github.aryapreetam.parikshan.protocol.tag

// 1. Auto-resolution: Checks for tag "login_button" first, then falls back to text matching
click("login_button")

// 2. Auto-resolution: Checks for tag "Login" first, then falls back to visible text "Login"
click("Login")

// 3. Strict Text Selector: Bypasses tag matching, directly searching for visible text
click(Selector.Text("Login"))

// 4. Shorthand Text Selector: Simplified syntax using helper function
click(text("Login"))

// 5. Shorthand Tag Selector: Strict tag resolution
click(tag("login_button"))
```

### Index Constraints

When multiple elements match the same selector (such as rows in a list), apply index constraints to target a specific node instance:

```kotlin
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.atIndex

// Target the second "Add to Cart" button (0-indexed)
click(Selector.Text("Add to Cart").atIndex(1))
```

---

## Core DSL Commands

All E2E actions, assertions, and waiting methods execute inside the `e2eTest` block:

### UI Actions

* **`click(selector: Selector)`**: Triggers a click/tap event on the element matching the selector. Automatically waits for visibility.
* **`input(selector: Selector, text: String)`**: Inputs the specified text into a text field. Clears existing text contents first.
* **`drag(fromX: Double, fromY: Double, toX: Double, toY: Double, durationMs: Long)`**: Simulates a dragging gesture across custom coordinates.
* **`relaunchApp()`**: Force-terminates and relaunches the application process.  
  * *Note: This is a fast browser refresh on WasmJs, but introduces a 1–2s boot latency on Desktop JVM targets. It is recommended to avoid using this method sequentially across many tests; use in-app UI navigation resets via `E2ETestLifecycle` instead.*

### Assertions

* **`assertVisible(selector: Selector)`**: Asserts that the element matching the selector is visible in the current layout.
* **`assertNotVisible(selector: Selector)`**: Asserts that the element matching the selector is absent from the view tree or hidden.
* **`assertText(selector: Selector, expectedText: String)`**: Asserts that the targeted element contains the exact expected text.

### Navigation & Waiting

* **`scrollUntilVisible(containerSelector: Selector, targetSelector: Selector)`**: Programmatically scrolls the scrollable container matching `containerSelector` until the `targetSelector` element becomes visible in the viewport.

!!! important "No Parent Scroll Container Auto-Inference"
    `scrollUntilVisible` requires both the scroll container and the target element to be declared explicitly (e.g., `scrollUntilVisible(containerTag = "product_list", targetTag = "Submit")`).
    
    Parikshan does not auto-infer parent scroll containers to avoid fetching the entire remote semantics tree repeatedly, keeping test command execution latencies under 10ms.

!!! warning "No Auto-Scrolling"
    Unlike some testing frameworks that automatically attempt to scroll containers to locate off-screen targets, Parikshan requires explicit scrolling commands. If the element is not currently in the visible viewport, you must use `scrollUntilVisible` to bring it into view before performing actions or assertions.

---

## Complete API Reference (Dokka)

For the full class documentation, detailed type mappings, extension functions, and complete signature breakdowns, see the generated Dokka API Reference:

!!! important "Dokka API Reference"
    [**📖 Browse the Dokka API Reference**](https://aryapreetam.github.io/parikshan/api/)
