package io.github.aryapreetam.parikshan.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstanceDiscoveryTest {

  private lateinit var tempDir: File

  @BeforeTest
  fun setUp() {
    tempDir = createTempDirectory("parikshan-discovery-test").toFile()
  }

  @AfterTest
  fun tearDown() {
    tempDir.deleteRecursively()
  }

  @Test
  fun testDiscoverInstancesFromListOfCall() {
    val file = File(tempDir, "CollaborationTest.kt").apply {
      writeText(
        """
        package sample.app
        import io.github.aryapreetam.parikshan.e2eTest
        import kotlin.test.Test

        class CollaborationTest {
          @Test
          fun testChat() = e2eTest(listOf("alice", "bob")) {
            val a = instance("alice")
            val b = instance("bob")
          }
        }
        """.trimIndent()
      )
    }

    val instances = discoverE2eTestInstancesInFile(file)
    assertEquals(listOf("alice", "bob"), instances)
  }

  @Test
  fun testDiscoverInstancesFromVarargCall() {
    val file = File(tempDir, "VarargTest.kt").apply {
      writeText(
        """
        package sample.app
        import io.github.aryapreetam.parikshan.e2eTest
        import kotlin.test.Test

        class VarargTest {
          @Test
          fun testPingPong() = e2eTest("rider", "driver") {
          }
        }
        """.trimIndent()
      )
    }

    val instances = discoverE2eTestInstancesInFile(file)
    assertEquals(listOf("rider", "driver"), instances)
  }

  @Test
  fun testDiscoverInstancesMultiline() {
    val file = File(tempDir, "MultilineTest.kt").apply {
      writeText(
        """
        package sample.app
        import io.github.aryapreetam.parikshan.e2eTest
        import kotlin.test.Test

        class MultilineTest {
          @Test
          fun testThreeActors() = e2eTest(
            instances = listOf(
              "user1",
              "user2",
              "user3"
            )
          ) {
          }
        }
        """.trimIndent()
      )
    }

    val instances = discoverE2eTestInstancesInFile(file)
    assertEquals(listOf("user1", "user2", "user3"), instances)
  }

  @Test
  fun testSingleInstanceReturnsEmptyList() {
    val file = File(tempDir, "SingleInstanceTest.kt").apply {
      writeText(
        """
        package sample.app
        import io.github.aryapreetam.parikshan.e2eTest
        import kotlin.test.Test

        class SingleInstanceTest {
          @Test
          fun testHome() = e2eTest {
            assertVisible("home")
          }
        }
        """.trimIndent()
      )
    }

    val instances = discoverE2eTestInstancesInFile(file)
    assertTrue(instances.isEmpty())
  }

  @Test
  fun testDiscoverMultiTargetMethodsAndClasses() {
    val file = File(tempDir, "MultiTargetCollaborationTest.kt").apply {
      writeText(
        """
        package sample.app

        import io.github.aryapreetam.parikshan.Target
        import io.github.aryapreetam.parikshan.e2eTest
        import kotlin.test.Test

        class MultiTargetCollaborationTest {
          @Test
          fun testTwoPartyChatCollaboration() = e2eTest(listOf("alice", "bob")) {
          }

          @Test
          fun testDesktopToWebChatCollaboration() = e2eTest(
            instances = mapOf("alice" to Target.Desktop, "bob" to Target.Wasm)
          ) {
          }
        }
        """.trimIndent()
      )
    }

    val methods = discoverMultiTargetTestMethodsInFile(file)
    assertEquals(listOf("sample.app.MultiTargetCollaborationTest#testDesktopToWebChatCollaboration"), methods)

    val classes = discoverMultiTargetTestClassesInFile(file)
    assertEquals(listOf("sample.app.MultiTargetCollaborationTest"), classes)

    val multiClasses = discoverMultiInstanceTestClassesInFile(file)
    assertEquals(listOf("sample.app.MultiTargetCollaborationTest"), multiClasses)
  }

  @Test
  fun testSingleInstanceClassNotDiscoveredAsMultiInstance() {
    val file = File(tempDir, "SingleInstanceTest.kt").apply {
      writeText(
        """
        package sample.app

        import io.github.aryapreetam.parikshan.e2eTest
        import kotlin.test.Test

        class SingleInstanceTest {
          @Test
          fun testHome() = e2eTest {
          }
        }
        """.trimIndent()
      )
    }

    val multiClasses = discoverMultiInstanceTestClassesInFile(file)
    assertTrue(multiClasses.isEmpty())
  }

  @Test
  fun testCommentedOutDeclarationsAreIgnored() {
    val file = File(tempDir, "CommentedTest.kt").apply {
      writeText(
        """
        package sample.app
        import io.github.aryapreetam.parikshan.Target
        import io.github.aryapreetam.parikshan.e2eTest
        import kotlin.test.Test

        class CommentedTest {
          // @Test fun ghostLine() = e2eTest(listOf("ghost1", "ghost2")) {}
          /*
          @Test
          fun ghostBlock() = e2eTest(instances = mapOf("ghost3" to Target.Desktop, "ghost4" to Target.Wasm)) {}
          /* nested comment e2eTest("ghost5", "ghost6") {} */
          */
          @Test
          fun realTest() = e2eTest(listOf("alice", "bob")) {
            val url = "http://127.0.0.1:8080/api"
          }
        }
        """.trimIndent()
      )
    }

    assertEquals(listOf("alice", "bob"), discoverE2eTestInstancesInFile(file))
    assertTrue(discoverMultiTargetTestMethodsInFile(file).isEmpty())
    assertTrue(discoverMultiTargetActorMapsByMethodInFile(file).isEmpty())
  }

  @Test
  fun testPerClassInstancesAndPerMethodTargetMapsScopedAccurately() {
    val file = File(tempDir, "ScopedSuiteTest.kt").apply {
      writeText(
        """
        package sample.app
        import io.github.aryapreetam.parikshan.Target
        import io.github.aryapreetam.parikshan.e2eTest
        import kotlin.test.Test

        class ChatSuiteTest {
          @Test
          fun testTwoUsers() = e2eTest(listOf("alice", "bob")) {}

          @Test
          fun testDesktopToWeb() = e2eTest(
            instances = mapOf("alice" to Target.Desktop, "bob" to Target.Wasm)
          ) {}

          @Test
          fun testReversedTargets() = e2eTest(
            instances = mapOf("admin" to Target.Wasm, "guest" to Target.Desktop)
          ) {}
        }
        """.trimIndent()
      )
    }

    val byClass = discoverE2eTestInstancesByClassInFile(file)
    assertEquals(listOf("alice", "bob"), byClass["sample.app.ChatSuiteTest"])

    val byMethod = discoverMultiTargetActorMapsByMethodInFile(file)
    assertEquals(
      mapOf("alice" to "desktop", "bob" to "wasm"),
      byMethod["sample.app.ChatSuiteTest#testDesktopToWeb"]
    )
    assertEquals(
      mapOf("admin" to "wasm", "guest" to "desktop"),
      byMethod["sample.app.ChatSuiteTest#testReversedTargets"]
    )
  }
}
