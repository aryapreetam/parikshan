package sample.app

import io.github.aryapreetam.parikshan.Actor
import io.github.aryapreetam.parikshan.Target
import io.github.aryapreetam.parikshan.e2eTest
import sample.app.playgrounds.ChatTransport
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MultiInstanceCollaborationTest {

  @BeforeTest
  fun setup() {
    ChatTransport.clearMessages()
  }

  @AfterTest
  fun tearDown() {
    ChatTransport.clearMessages()
  }

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
    // 1. Both instances navigate to the Chat Playground
    alice.navigateToSection("nav_chat_playground")
    bob.navigateToSection("nav_chat_playground")

    alice.assertVisible("Collaboration Chat")
    bob.assertVisible("Collaboration Chat")

    // 2. Alice sends a message to Bob
    alice.input("chat_input", "Hello Bob!")
    alice.click("chat_send_button")

    // Both observe Alice's message in their chat streams
    alice.assertVisible("alice: Hello Bob!")
    bob.assertVisible("alice: Hello Bob!")

    // 3. Bob sends a reply to Alice
    bob.input("chat_input", "Hey Alice, message received!")
    bob.click("chat_send_button")

    // Both observe Bob's reply in their chat streams
    bob.assertVisible("bob: Hey Alice, message received!")
    alice.assertVisible("bob: Hey Alice, message received!")

    alice.input("chat_input", "How are you?")
    alice.click("chat_send_button")

    alice.assertVisible("alice: How are you?")
    bob.assertVisible("alice: How are you?")

    bob.input("chat_input", "I'm good, thanks!")
    bob.click("chat_send_button")

    bob.assertVisible("bob: I'm good, thanks!")
    alice.assertVisible("bob: I'm good, thanks!")
  }
}

