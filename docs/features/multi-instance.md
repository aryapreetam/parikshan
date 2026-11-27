# Multi-Instance Testing

Multi-instance testing allows running multiple instances of your application simultaneously on the same platform target within a single test scenario. This is useful for verifying peer-to-peer messaging, collaborative editing, multi-user permissions, and real-time synchronization workflows.

---

## Declaring Multi-Instance Tests

Pass a list of unique actor names to `e2eTest`. The test lambda provides an `E2EMultiInstanceScope` which supports Kotlin destructuring up to 5 instances:

```kotlin
@Test
fun testTwoPartyChatCollaboration() = e2eTest(listOf("alice", "bob")) { (alice, bob) ->
  // 1. Both instances navigate to the chat screen
  alice.navigateToSection("nav_chat_playground")
  bob.navigateToSection("nav_chat_playground")

  alice.assertVisible("Collaboration Chat")
  bob.assertVisible("Collaboration Chat")

  // 2. Alice sends a message
  alice.input("chat_input", "Hello Bob!")
  alice.click("chat_send_button")

  // 3. Both observe Alice's message in their chat streams
  alice.assertVisible("alice: Hello Bob!")
  bob.assertVisible("alice: Hello Bob!")

  // 4. Bob sends a reply
  bob.input("chat_input", "Hey Alice, message received!")
  bob.click("chat_send_button")

  // 5. Both observe Bob's reply
  bob.assertVisible("bob: Hey Alice, message received!")
  alice.assertVisible("bob: Hey Alice, message received!")
}
```

### Actor Identification & Indexing

Each actor is an instance of `Actor` (a typealias for `E2ETestScope`). Actor identifiers must match `^[a-zA-Z0-9_-]+$` (letters, digits, hyphens, and underscores only). If you have more than 5 instances or prefer named indexing, access instances directly:

```kotlin
e2eTest(listOf("alice", "bob", "charlie")) { scope ->
  val alice = scope["alice"]
  val bob = scope.instance("bob")
  val charlie = scope["charlie"]

  alice.click("action")
  bob.assertVisible("status")
  charlie.assertVisible("status")
}
```

---

## Supported Platforms (0.1.0)

| Target | Status in 0.1.0 | Behavior |
| :--- | :--- | :--- |
| **Desktop (JVM)** | Supported | Launches one isolated JVM process per actor with an ephemeral port and dedicated data directory. |
| **Web (Wasm)** | Supported | Provisions one isolated Playwright `BrowserContext` per actor with separate storage and cookies. |
| **Android** | Planned (0.2.0) | `e2eTest(listOf(...))` tests in `commonTest` are skipped via `TestAbortedException` during `e2eAndroidTest`. |
| **iOS** | Planned (0.2.0) | `e2eTest(listOf(...))` tests in `commonTest` are skipped via `TestAbortedException` during `e2eIosTest`. |

---

## Platform Execution Models

Multi-instance tests execute identically in test code, but their underlying isolation and process management adapt to each platform.

### Desktop (JVM)

On Desktop, Parikshan launches independent application processes for each actor:

1. **Port Isolation**: Each desktop process allocates an independent ephemeral HTTP/WebSocket port. Test commands are routed directly to the designated actor's port.
2. **Session Namespacing**: Session connection descriptors are written to isolated paths (`build/parikshan/desktop/sessions/<actor>.json`), preventing port or process collisions.
3. **Window Layout**: When launched, instances inherit window geometry and positioning flags. Combine with `--layout=side-by-side` or explicit coordinates (`--desktop-window-position=X,Y`) to arrange windows automatically on screen.
4. **Just-In-Time (JIT) Window Lifecycle**: The primary instance launches at the start of the Desktop test run to execute single-instance test suites. When multi-instance test classes execute, secondary window processes launch on demand immediately before that class begins and terminate immediately after it completes.
5. **State Reuse Across Methods**: Within a Desktop test class, application processes persist between test methods. Reset app state explicitly (for example, signing out or clearing backend fixtures) at the start and end of each scenario.

### Web (Wasm)

On Web (Wasm), running multiple actors in a single browser profile would share `localStorage`, `sessionStorage`, and cookies. Parikshan isolates actors at the browser engine level:

1. **Browser Context Isolation**: Parikshan provisions a fresh Playwright `BrowserContext` (`browser.newContext()`) per actor per test against the local web server.
2. **State Separation**: Cookies, local storage, session storage, and cache partitions are isolated between actors and cleared when each test completes.
3. **Page Orchestration**: Each context hosts its own active page, with JS bridge hooks and driver events serialized through a single Playwright dispatch thread so concurrent `inParallel { ... }` blocks execute safely.

---

## Executing Multi-Instance Tests

Run multi-instance tests using standard Gradle test tasks. Both `e2eDesktopTest` and `e2eJvmTest` support `--tests` filtering on projects using `jvm()` or `jvm("desktop")`:

=== "Desktop"
    ```bash
    ./gradlew :composeApp:e2eDesktopTest --tests="sample.app.MultiInstanceCollaborationTest.testTwoPartyChatCollaboration"
    ```

=== "Web (Wasm)"
    ```bash
    ./gradlew :composeApp:e2eWasmTest --tests="sample.app.MultiInstanceCollaborationTest.testTwoPartyChatCollaboration"
    ```

=== "Both Targets"
    ```bash
    ./gradlew :composeApp:e2eTest --targets=jvm,wasm --tests="sample.app.MultiInstanceCollaborationTest.testTwoPartyChatCollaboration"
    ```

---

## Known Limitations (0.1.0)

* **Video Recording**: Video capture is skipped during multi-instance and multi-target runs in `0.1.0` (single-instance video recording is unaffected).
* **Dedicated Test Classes**: Place multi-instance and multi-target tests in dedicated test classes separate from single-instance `E2ETestLifecycle` classes. `E2ETestLifecycle` (`beforeEach` / `afterEach`) applies to single-instance `e2eTest { ... }` only in `0.1.0`.
* **Static Actor Discovery**: Gradle discovers actor names from literal `e2eTest(listOf("a", "b"))` and `e2eTest("a", "b")` calls in `commonTest` / `jvmTest`. If your test constructs actor lists dynamically at runtime, pass `--instances=a,b` on the CLI or configure `parikshan { instances = listOf("a", "b") }`.
* **Desktop `relaunchApp()`**: Calling `relaunchApp()` on a multi-instance Desktop actor throws `UnsupportedOperationException` in `0.1.0`.
* **Synchronized Mode (`--sync`)**: Multi-instance tests are skipped when `--sync` is active.
