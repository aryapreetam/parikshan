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
}
