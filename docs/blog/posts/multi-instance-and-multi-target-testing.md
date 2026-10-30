---
date: 2026-10-30
description: Introducing multi-instance and multi-target test orchestration in Parikshan 0.1.0 for testing real-time collaboration across Desktop and Web.
categories:
  - Releases
  - Features
---

# Multi-Instance and Multi-Target Testing in Parikshan 0.1.0

Parikshan 0.1.0 adds support for multi-instance and multi-target test orchestration in Compose Multiplatform. You can now write tests that coordinate multiple application instances on the same platform or drive actors across different platforms within a single test scenario.

<!-- more -->

Here is how multi-instance and multi-target testing work and how to use them in your test suites.

---

## The Problem: Testing Multi-User Collaboration

Applications frequently involve real-time interactions between multiple users:
* Peer-to-peer messaging and chat streams
* Collaborative document editing and canvas synchronization
* E-commerce inventory reservation and concurrent checkouts
* Multi-role permission workflows (e.g. admin approves an action submitted by a user)

Testing these flows previously required either manual testing or running separate test processes without coordination. If Alice on Desktop sends a message to Bob on Web, standard test frameworks had no way to control both actors within the same test block or synchronize their assertions.

---

## Multi-Instance Testing: Same Target, Multiple Actors

Multi-instance tests launch two or more independent instances of your application on the same platform target.

Pass a list of actor names to `e2eTest`. The test block provides destructured `Actor` instances:

```kotlin
@Test
fun testTwoPartyChatCollaboration() = e2eTest(listOf("alice", "bob")) { (alice, bob) ->
  // 1. Both instances navigate to the chat screen
  alice.navigateToSection("nav_chat_playground")
  bob.navigateToSection("nav_chat_playground")

  // 2. Alice sends a message
  alice.input("chat_input", "Hello Bob!")
  alice.click("chat_send_button")

  // 3. Both verify receipt
  alice.assertVisible("alice: Hello Bob!")
  bob.assertVisible("alice: Hello Bob!")

  // 4. Bob replies
  bob.input("chat_input", "Hey Alice, message received!")
  bob.click("chat_send_button")

  // 5. Both verify reply
  bob.assertVisible("bob: Hey Alice, message received!")
  alice.assertVisible("bob: Hey Alice, message received!")
}
```

### Platform Isolation

* **Desktop (JVM)**: Each instance runs in an independent JVM process listening on a dynamically allocated ephemeral port. To avoid idle secondary windows during single-instance tests, Parikshan uses a Just-In-Time (JIT) window lifecycle: the primary window (`alice`) runs single-instance test suites, and secondary windows (`bob`) launch on demand when multi-instance test classes execute and terminate immediately afterward.
* **Web (Wasm)**: Each actor runs in a distinct Playwright `BrowserContext`. Local storage, cookies, and session state are isolated between actors while sharing the local application server.

Run multi-instance tests on Desktop or Web:

```bash
# Desktop
./gradlew e2eDesktopTest --tests="sample.app.MultiInstanceCollaborationTest.testTwoPartyChatCollaboration"

# Web
./gradlew e2eWasmTest --tests="sample.app.MultiInstanceCollaborationTest.testTwoPartyChatCollaboration"
```

---

## Multi-Target Testing: Cross-Platform Coordination

Multi-target tests assign actors to different platform targets. For example, Alice interacts via Desktop (JVM) while Bob interacts via Web (Wasm):

```kotlin
@Test
fun testDesktopToWebChatCollaboration() = e2eTest(
  instances = mapOf("alice" to Target.Desktop, "bob" to Target.Wasm)
) { (alice, bob) ->
  alice.navigateToSection("nav_chat_playground")
  bob.navigateToSection("nav_chat_playground")

  alice.input("chat_input", "Hello from Desktop!")
  alice.click("chat_send_button")

  bob.assertVisible("alice: Hello from Desktop!")

  bob.input("chat_input", "Hello from Web!")
  bob.click("chat_send_button")

  alice.assertVisible("bob: Hello from Web!")
}
```

### Orchestration & Selective Method Filtering

When running multi-target tests, Parikshan's Gradle test runner manages sessions under `build/parikshan/multi-target/sessions/` with selective method dispatching:
* **Selective Method Filtering**: The test runner discovers multi-target methods at build time and invokes JUnit ConsoleLauncher using `--select-method <Class>#<method>`, running only multi-target scenarios without re-executing single-instance tests in the same class.
* **Target Affinity Guards**: If a multi-target test requires targets that were not included in the CLI invocation (for instance, running `e2eDesktopTest` alone), the test is aborted with `TestAbortedException` and reported as skipped, preventing false test failures.

Run multi-target tests across both platforms:

```bash
./gradlew e2eTest --targets=jvm,wasm --tests="sample.app.MultiInstanceCollaborationTest.testDesktopToWebChatCollaboration"
```

---

## Reusable Scenario Helper Pattern

To avoid writing duplicate scenario logic between multi-instance and multi-target tests, extract interactions into a helper function accepting `Actor` parameters:

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
  }
}
```

---

## Upgrading to 0.1.0

In your `build.gradle.kts`:

```kotlin
plugins {
  id("io.github.aryapreetam.parikshan") version "0.1.0"
}
```

For detailed documentation, see the [Multi-Instance Guide](../../features/multi-instance.md) and [Multi-Target Guide](../../features/multi-target.md).
