# Multi-Target Testing

Multi-target testing allows coordinating multiple application actors running on different platform targets within a single test scenario. For example, you can verify real-time collaboration where Alice operates on Desktop (JVM) while Bob interacts via Web (Wasm).

---

## Declaring Multi-Target Tests

Assign each actor name to a physical target (`Target.Desktop`, `Target.Wasm`) using `instances = mapOf(...)`. The test block receives destructured `Actor` instances:

```kotlin
@Test
fun testDesktopToWebChatCollaboration() = e2eTest(
  instances = mapOf("alice" to Target.Desktop, "bob" to Target.Wasm)
) { (alice, bob) ->
  // Alice runs on Desktop JVM, Bob runs on Web WasmJs
  alice.navigateToSection("nav_chat_playground")
  bob.navigateToSection("nav_chat_playground")

  alice.assertVisible("Collaboration Chat")
  bob.assertVisible("Collaboration Chat")

  // Alice sends a message from Desktop
  alice.input("chat_input", "Hello from Desktop!")
  alice.click("chat_send_button")

  // Bob verifies receipt on Web
  bob.assertVisible("alice: Hello from Desktop!")

  // Bob replies from Web
  bob.input("chat_input", "Hello from Web!")
  bob.click("chat_send_button")

  // Alice verifies receipt on Desktop
  alice.assertVisible("bob: Hello from Web!")
}
```

---

## Reusable Scenario Helper Pattern

To avoid duplicating test logic between multi-instance (same platform) and multi-target (cross-platform) tests, extract the scenario actions into a private helper function accepting typed `Actor` parameters:

```kotlin
class MultiInstanceCollaborationTest {

  @Test
  fun testTwoPartyChatCollaboration() = e2eTest(listOf("alice", "bob")) { (alice, bob) ->
    runChatCollaborationScenario(alice, bob)
  }

  @Test
  fun testDesktopToWebChatCollaboration() = e2eTest(
    instances = mapOf("alice" to Target.Desktop, "bob" to Target.Wasm)
  ) { (alice, bob) ->
    runChatCollaborationScenario(alice, bob)
  }

  private suspend fun runChatCollaborationScenario(alice: Actor, bob: Actor) {
    alice.navigateToSection("nav_chat_playground")
    bob.navigateToSection("nav_chat_playground")

    alice.input("chat_input", "Hello Bob!")
    alice.click("chat_send_button")

    bob.assertVisible("alice: Hello Bob!")

    bob.input("chat_input", "Hey Alice, message received!")
    bob.click("chat_send_button")

    alice.assertVisible("bob: Hey Alice, message received!")
  }
}
```

With this pattern, the scenario logic remains identical whether both participants are running on Desktop, both on Web, or one on Desktop and the other on Web.

---

## Supported Platforms (0.1.0)

| Target | Status in 0.1.0 | Behavior |
| :--- | :--- | :--- |
| **`Target.Desktop`** | Supported | Launches an isolated Desktop JVM application process for each Desktop actor. |
| **`Target.Wasm`** | Supported | Provisions an isolated Playwright `BrowserContext` for each Wasm actor. |
| **`Target.Android`** | Planned (0.2.0) | Fails fast with `UnsupportedOperationException` / `GradleException` if declared in `instances = mapOf(...)`. |
| **`Target.Ios`** | Planned (0.2.0) | Fails fast with `UnsupportedOperationException` / `GradleException` if declared in `instances = mapOf(...)`. |

---

## Orchestration & Method Filtering

When running tests across multiple targets:
```bash
./gradlew :composeApp:e2eTest --targets=jvm,wasm
```

Parikshan's Gradle orchestration pipeline coordinates multi-target execution through three mechanisms:

1. **Static Discovery**: Build-time source scanning inspects `commonTest` and `jvmTest` source files (ignoring comments) to identify `@Test` methods declaring `e2eTest(instances = mapOf(...))` and their actor-to-target assignments.
2. **Method-Level Filtering**: When executing the multi-target batch, Gradle invokes JUnit ConsoleLauncher using `--select-method <Class>#<method>`. When `--tests` matches only multi-target method(s), redundant single-target passes are skipped automatically.
3. **Runtime Target Affinity Guards**: If a multi-target test requires targets that are not currently active in the CLI command (for example, executing `./gradlew :composeApp:e2eDesktopTest` without Wasm), Parikshan aborts the test with `TestAbortedException`. The test runner marks the test as skipped rather than failing the build.

---

## Execution Command

Execute multi-target tests by specifying all required targets in the Gradle command:

```bash
./gradlew :composeApp:e2eTest --targets=jvm,wasm --tests="sample.app.MultiInstanceCollaborationTest.testDesktopToWebChatCollaboration"
```

To run with side-by-side window layout:

```bash
./gradlew :composeApp:e2eTest --targets=jvm,wasm --tests="sample.app.MultiInstanceCollaborationTest.testDesktopToWebChatCollaboration" --layout=side-by-side
```

---

## Known Limitations (0.1.0)

* **Actor-Target Uniqueness per Batch**: Within a single multi-target run, an actor name must map consistently to one target. If two methods in the same batch map the same actor name (e.g., `"alice"`) to different targets (`Target.Desktop` in one method and `Target.Wasm` in another), Parikshan fails fast before launch. Use distinct actor names (e.g., `"desktopAlice"`, `"webAlice"`) or run those methods separately with `--tests`.
* **Video Recording**: Video capture is skipped during multi-target runs in `0.1.0` (single-instance video recording is unaffected).
* **Desktop vs. Wasm State Isolation**: Desktop actors reuse their process across methods in a batch, whereas Wasm actors start with a clean `BrowserContext` per test method. Reset shared backend and Desktop UI state explicitly between scenarios.
* **Dedicated Test Classes**: Keep multi-target tests separate from single-instance `E2ETestLifecycle` classes.
* **Synchronized Mode (`--sync`)**: Multi-target tests are skipped when `--sync` is active.
