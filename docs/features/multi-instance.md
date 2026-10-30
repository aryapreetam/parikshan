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

Each actor is an instance of `Actor` (a typealias for `E2ETestScope`). If you have more than 5 instances or prefer named indexing, access instances directly:

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

## Platform Execution Models

Multi-instance tests execute identically in test code, but their underlying isolation and process management adapt to each platform.

### Desktop (JVM)

On Desktop, Parikshan launches independent application processes for each actor:

1. **Port Isolation**: Each desktop process allocates an independent ephemeral HTTP/WebSocket port. Test commands are routed directly to the designated actor's port.
2. **Session Namespacing**: Session connection descriptors are written to isolated paths (`build/parikshan/desktop/sessions/<actor>.json`), preventing port or process collisions.
3. **Window Layout**: When launched, instances inherit window geometry and positioning flags. Combine with `--layout=side-by-side` or explicit coordinates (`--desktop-window-position=X,Y`) to arrange windows automatically on screen.
4. **Just-In-Time (JIT) Window Lifecycle**: The primary instance (`alice`) launches at the start of the Desktop test run to execute single-instance test suites. If multi-instance test classes are discovered, secondary window processes (`bob`) launch on demand immediately before multi-instance tests begin and terminate immediately after the batch completes. This prevents idle secondary windows from consuming screen space and memory during unrelated single-instance tests.

### Web (Wasm)

On Web (Wasm), running multiple actors in a single browser profile would share `localStorage`, `sessionStorage`, and cookies. Parikshan isolates actors at the browser engine level:

1. **Browser Context Isolation**: Parikshan provisions an independent Playwright `BrowserContext` (`browser.newContext()`) for each actor against the local web server (`http://127.0.0.1:8081`).
2. **State Separation**: Cookies, local storage, session storage, and cache partitions are isolated between actors. Logging in as `alice` in one context does not pollute authentication tokens for `bob` in the other.
3. **Page Orchestration**: Each context hosts its own active page, with JS bridge hooks and driver events dispatched independently.

---

## Executing Multi-Instance Tests

Run multi-instance tests using standard Gradle test tasks:

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
