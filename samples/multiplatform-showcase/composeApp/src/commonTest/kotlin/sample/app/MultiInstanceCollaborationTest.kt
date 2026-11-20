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

  @Test
  fun testTwoPartyChatFileTransfer() = e2eTest(listOf("alice", "bob")) { (alice, bob) ->
    runChatFileTransferScenario(alice, bob)
  }

  @Test
  fun testDesktopToWebChatFileTransfer() = e2eTest(
    instances = mapOf("alice" to Target.Desktop, "bob" to Target.Wasm)
  ) { (alice, bob) ->
    runChatFileTransferScenario(alice, bob)
  }

  private suspend fun runChatFileTransferScenario(alice: Actor, bob: Actor) {
    // 1. Both instances navigate to the Chat Playground
    alice.navigateToSection("nav_chat_playground")
    bob.navigateToSection("nav_chat_playground")

    alice.assertVisible("Collaboration Chat")
    bob.assertVisible("Collaboration Chat")

    // 2. Alice sends a raw-byte file payload to Bob
    alice.withFilePicker("alice_payload.txt") {
      click("chat_attach_button")
    }

    // Both observe Alice's file attachment and payload
    alice.assertVisible("alice: Sent file: alice_payload.txt")
    bob.assertVisible("alice: Sent file: alice_payload.txt")
    bob.assertVisible("chat_file_payload_alice")
    bob.assertText("chat_file_payload_alice", "JetBrains Compose Multiplatform")

    // Bob extracts the text content and confirms receipt back to Alice
    val bobReceivedNode = bob.resolveVisibleNode("chat_file_payload_alice")
    val bobReceivedPayload = bobReceivedNode.text ?: ""
    bob.input("chat_input", "Confirmed receipt: $bobReceivedPayload")
    bob.click("chat_send_button")

    // Both observe Bob's confirmation containing Alice's verified file content
    bob.assertVisible("bob: Confirmed receipt: JetBrains Compose Multiplatform")
    alice.assertVisible("bob: Confirmed receipt: JetBrains Compose Multiplatform")

    // 3. Bob responds in reverse by sending a raw-byte file payload to Alice
    bob.withFilePicker("bob_payload.txt") {
      click("chat_attach_button")
    }

    // Both observe Bob's file attachment and payload
    bob.assertVisible("bob: Sent file: bob_payload.txt")
    alice.assertVisible("bob: Sent file: bob_payload.txt")
    alice.assertVisible("chat_file_payload_bob")
    alice.assertText("chat_file_payload_bob", "Parikshan Cross-Process Testing")

    // Alice extracts the text content and confirms receipt back to Bob
    val aliceReceivedNode = alice.resolveVisibleNode("chat_file_payload_bob")
    val aliceReceivedPayload = aliceReceivedNode.text ?: ""
    alice.input("chat_input", "Confirmed receipt: $aliceReceivedPayload")
    alice.click("chat_send_button")

    // Both observe Alice's confirmation containing Bob's verified file content
    alice.assertVisible("alice: Confirmed receipt: Parikshan Cross-Process Testing")
    bob.assertVisible("alice: Confirmed receipt: Parikshan Cross-Process Testing")
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
