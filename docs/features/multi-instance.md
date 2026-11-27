# Multi-Instance Testing

Multi-instance testing runs multiple instances of your application at the same time on a single platform within one test. Use it to verify peer-to-peer messaging, collaborative editing, inventory contention, and multi-role workflows.

---

## Declaring Multi-Instance Tests

Pass a list of actor names directly to `e2eTest`. The test lambda receives an `E2EMultiInstanceScope`, which supports Kotlin destructuring for up to 5 actors:

```kotlin
@Test
fun testTwoPartyChatCollaboration() = e2eTest(listOf("alice", "bob")) { (alice, bob) ->
  // 1. Both instances open the chat screen
  alice.click("nav_chat_playground")
  bob.click("nav_chat_playground")

  alice.assertVisible("Collaboration Chat")
  bob.assertVisible("Collaboration Chat")

  // 2. Alice sends a message
  alice.input("chat_input", "Hello Bob!")
  alice.click("chat_send_button")

  // 3. Both observe Alice's message
  alice.assertVisible("alice: Hello Bob!")
  bob.assertVisible("alice: Hello Bob!")

  // 4. Bob replies
  bob.input("chat_input", "Hey Alice, message received!")
  bob.click("chat_send_button")

  // 5. Both observe Bob's reply
  bob.assertVisible("bob: Hey Alice, message received!")
  alice.assertVisible("bob: Hey Alice, message received!")
}
```

You can also use the vararg form (`e2eTest("alice", "bob") { (alice, bob) -> ... }`).

### Actor Identifiers & Indexing

Each actor is an `Actor` (a typealias for `E2ETestScope`). Actor identifiers must match `^[a-zA-Z0-9_-]+$` (letters, digits, hyphens, and underscores only). If you have more than 5 actors or prefer named access, index the scope directly:

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

### Parallel Actor Actions (`inParallel`)

When actors do not need strict step-by-step ordering (for example, navigating or filling forms concurrently), use `inParallel`:

```kotlin
e2eTest(listOf("alice", "bob")) { scope ->
  scope.inParallel {
    click("nav_chat_playground")
    assertVisible("Collaboration Chat")
  }

  scope.inParallel(
    "alice" to { input("chat_input", "Hi from Alice") },
    "bob" to { input("chat_input", "Hi from Bob") }
  )
}
```

---

## Reading Actor Identity in Your App

If your application needs to distinguish which actor instance is running (for example, to label a sender or isolate local storage):

| Target | How to read actor identity | Isolated data directory |
| :--- | :--- | :--- |
| **Desktop (JVM)** | `System.getProperty("parikshan.instance.id")` | `System.getProperty("parikshan.data.dir")` (`build/parikshan/<target>/data/<actor>`) |
| **Web (Wasm)** | URL query parameter `?parikshan_instance=<actor>` | Isolated per Playwright `BrowserContext` (`localStorage`, `sessionStorage`, cookies) |

---

## Setup & Teardown

In `0.1.0`, `E2ETestLifecycle` (`beforeEach` / `afterEach`) applies only to single-instance `e2eTest { ... }`. For multi-instance tests:

* Use standard `kotlin.test.@BeforeTest` and `@AfterTest` for non-UI resets (backend fixtures, temp files, database state).
* Wrap repeated UI setup/teardown across actors in a `suspend` helper with `try { ... } finally { ... }`:

```kotlin
private suspend fun withSignedInActors(
  alice: Actor,
  bob: Actor,
  block: suspend () -> Unit
) {
  alice.click("nav_chat_playground")
  bob.click("nav_chat_playground")
  try {
    block()
  } finally {
    // optional UI cleanup
  }
}
```

---

## Supported Platforms (0.1.0)

| Target | Status in 0.1.0 | Behavior |
| :--- | :--- | :--- |
| **Desktop (JVM)** | Supported | Launches one JVM process per actor with an ephemeral port and separate data directory. |
| **Web (Wasm)** | Supported | Creates a fresh Playwright `BrowserContext` per actor per test with separate storage and cookies. |
| **Android** | Planned (0.2.0) | `e2eTest(listOf(...))` in `commonTest` is skipped via `TestAbortedException` during `e2eAndroidTest`. |
| **iOS** | Planned (0.2.0) | `e2eTest(listOf(...))` in `commonTest` is skipped via `TestAbortedException` during `e2eIosTest`. |

---

## Platform Execution Models

### Desktop (JVM)

On Desktop, Parikshan launches an independent JVM process for each actor:

1. **Port Isolation**: Each process allocates an ephemeral HTTP port and writes its descriptor to `build/parikshan/<target>/sessions/<actor>.json`.
2. **Window Tiling**: Desktop windows tile automatically from `(50, 50)` or from your `--desktop-window-position=X,Y` / `--layout=side-by-side` origin.
3. **Just-In-Time (JIT) Secondary Windows (`e2eTest`)**: When running `./gradlew e2eTest --targets=desktop`, single-instance classes run first with only the primary window open. Secondary windows launch immediately before multi-instance classes execute and close afterward.
4. **State Reuse Across Methods**: Desktop processes stay running between test methods in a class. Reset app and backend state explicitly at the start or end of each test.

### Web (Wasm)

On Web (Wasm), actors share the local Wasm server while staying isolated in the browser:

1. **Browser Context Isolation**: Each actor gets a fresh Playwright `BrowserContext` (`browser.newContext()`) per test method, isolating cookies, `localStorage`, and `sessionStorage`.
2. **Serialized Playwright Dispatch**: All browser commands run on a single Playwright dispatch thread so `inParallel { ... }` blocks across Wasm actors remain thread-safe.

---

## Executing Multi-Instance Tests

Both `e2eDesktopTest` and `e2eJvmTest` support `--tests` filtering on projects using `jvm()` or `jvm("desktop")`:

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

* **Literal Actor Names Required**: Gradle discovers actor names by scanning `commonTest` and `jvmTest` source files at build time. Pass string literals directly to `e2eTest(listOf("alice", "bob"))` or `e2eTest("alice", "bob")`; variables or dynamically constructed lists are not discovered in `0.1.0`.
* **Video Recording Skipped**: Video capture is skipped during multi-instance and multi-target runs in `0.1.0`. Single-instance video recording is unaffected.
* **Dedicated Test Classes**: Place multi-instance tests in their own test classes separate from single-instance `E2ETestLifecycle` classes.
* **Standalone `e2eDesktopTest` Boot Scope**: `e2eDesktopTest` boots all discovered Desktop actors in the module before filtering `--tests`. Use `e2eTest --targets=desktop --tests="..."` if you want JIT window scoping per class.
* **Desktop `relaunchApp()`**: Calling `relaunchApp()` on a multi-instance Desktop actor throws `UnsupportedOperationException` in `0.1.0`.
* **Synchronized Mode (`--sync`)**: Multi-instance tests are skipped when `--sync` is active.
