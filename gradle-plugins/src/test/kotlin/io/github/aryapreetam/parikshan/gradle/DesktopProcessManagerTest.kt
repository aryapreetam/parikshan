package io.github.aryapreetam.parikshan.gradle

import java.io.File
import java.io.FileOutputStream
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopProcessManagerTest {

  private lateinit var tempDir: File

  @BeforeTest
  fun setUp() {
    tempDir = createTempDirectory("parikshan-desktop-mgr-test").toFile()
    DesktopProcessManager.resetForTesting()
    PortAllocator.resetForTesting()
  }

  @AfterTest
  fun tearDown() {
    DesktopProcessManager.stopAll(tempDir)
    DesktopProcessManager.resetForTesting()
    tempDir.deleteRecursively()
  }

  @Test
  fun testCalculateWindowCoordinatesSideBySideForTwoInstances() {
    val pos0 = DesktopProcessManager.calculateWindowCoordinates(
      index = 0,
      totalCount = 2,
      preferredWindowWidth = 800,
      preferredWindowHeight = 700,
      paddingX = 20,
      paddingY = 40,
      originX = 50,
      originY = 50
    )
    val pos1 = DesktopProcessManager.calculateWindowCoordinates(
      index = 1,
      totalCount = 2,
      preferredWindowWidth = 800,
      preferredWindowHeight = 700,
      paddingX = 20,
      paddingY = 40,
      originX = 50,
      originY = 50
    )

    assertEquals(50, pos0.first)
    assertEquals(50, pos0.second)

    // Side-by-side offset: 50 + 1 * (800 + 20) = 870
    assertEquals(870, pos1.first)
    assertEquals(50, pos1.second)
  }

  @Test
  fun testCalculateWindowCoordinatesGridForFourInstances() {
    val pos0 = DesktopProcessManager.calculateWindowCoordinates(index = 0, totalCount = 4, preferredWindowWidth = 600, preferredWindowHeight = 500)
    val pos1 = DesktopProcessManager.calculateWindowCoordinates(index = 1, totalCount = 4, preferredWindowWidth = 600, preferredWindowHeight = 500)
    val pos2 = DesktopProcessManager.calculateWindowCoordinates(index = 2, totalCount = 4, preferredWindowWidth = 600, preferredWindowHeight = 500)
    val pos3 = DesktopProcessManager.calculateWindowCoordinates(index = 3, totalCount = 4, preferredWindowWidth = 600, preferredWindowHeight = 500)

    // Row 0
    assertEquals(Pair(50, 50), pos0)
    assertEquals(Pair(50 + 600 + 20, 50), pos1)

    // Row 1
    assertEquals(Pair(50, 50 + 500 + 40), pos2)
    assertEquals(Pair(50 + 600 + 20, 50 + 500 + 40), pos3)
  }

  @Test
  fun testLaunchAndStopAllLifecycleWithMockJar() {
    val dummyJar = createDummyExecutableJar(tempDir)
    val instances = listOf("alice", "bob")

    val launched = DesktopProcessManager.launchInstances(
      jarFile = dummyJar,
      instances = instances,
      baseOutputDir = tempDir,
      timeoutMs = 1000L,
      pollMs = 100L,
      useAppLauncher = false,
      waitForReady = false
    )

    assertEquals(2, launched.size)
    assertEquals("alice", launched[0].instanceId)
    assertEquals("bob", launched[1].instanceId)

    assertTrue(launched[0].port > 0)
    assertTrue(launched[1].port > 0)
    assertTrue(launched[0].port != launched[1].port, "Instance ports must be distinct")

    assertTrue(launched[0].token.isNotBlank())
    assertTrue(launched[1].token.isNotBlank())
    assertTrue(launched[0].token != launched[1].token, "Instance tokens must be distinct")

    // Verify session files written to disk
    val sessionsDir = File(tempDir, "sessions")
    val aliceSession = SessionStore.readInstanceSession(sessionsDir, "alice")
    val bobSession = SessionStore.readInstanceSession(sessionsDir, "bob")

    assertNotNull(aliceSession)
    assertEquals("alice", aliceSession.instanceId)
    assertEquals(launched[0].port, aliceSession.port)
    assertEquals(launched[0].token, aliceSession.token)

    assertNotNull(bobSession)
    assertEquals("bob", bobSession.instanceId)
    assertEquals(launched[1].port, bobSession.port)
    assertEquals(launched[1].token, bobSession.token)

    // Verify isolated data dirs
    assertTrue(launched[0].dataDir.exists())
    assertTrue(launched[1].dataDir.exists())
    assertTrue(launched[0].dataDir.absolutePath.endsWith("data/alice"))
    assertTrue(launched[1].dataDir.absolutePath.endsWith("data/bob"))

    // Verify processes are alive
    assertTrue(DesktopProcessManager.isInstanceAlive("alice"))
    assertTrue(DesktopProcessManager.isInstanceAlive("bob"))

    // Stop all
    DesktopProcessManager.stopAll(tempDir)

    // Verify session files cleared
    assertNull(SessionStore.readInstanceSession(sessionsDir, "alice"))
    assertNull(SessionStore.readInstanceSession(sessionsDir, "bob"))
    assertFalse(DesktopProcessManager.isInstanceAlive("alice"))
    assertFalse(DesktopProcessManager.isInstanceAlive("bob"))
  }

  @Test
  fun testStopInstanceRemovesOnlyTargetInstance() {
    val dummyJar = createDummyExecutableJar(tempDir)
    val launched = DesktopProcessManager.launchInstances(
      jarFile = dummyJar,
      instances = listOf("user1", "user2"),
      baseOutputDir = tempDir,
      timeoutMs = 1000L,
      pollMs = 100L,
      useAppLauncher = false,
      waitForReady = false
    )

    assertEquals(2, launched.size)
    val sessionsDir = File(tempDir, "sessions")

    assertNotNull(SessionStore.readInstanceSession(sessionsDir, "user1"))
    assertNotNull(SessionStore.readInstanceSession(sessionsDir, "user2"))

    DesktopProcessManager.stopInstance("user1", tempDir)

    assertNull(SessionStore.readInstanceSession(sessionsDir, "user1"))
    assertNotNull(SessionStore.readInstanceSession(sessionsDir, "user2"))
    assertFalse(DesktopProcessManager.isInstanceAlive("user1"))
    assertTrue(DesktopProcessManager.isInstanceAlive("user2"))

    DesktopProcessManager.stopAll(tempDir)
    assertNull(SessionStore.readInstanceSession(sessionsDir, "user2"))
    assertFalse(DesktopProcessManager.isInstanceAlive("user2"))
  }

  @Test
  fun testInvalidActorIdRejectedBeforeProcessLaunch() {
    val dummyJar = createDummyExecutableJar(tempDir)
    kotlin.test.assertFailsWith<IllegalArgumentException> {
      DesktopProcessManager.launchInstances(
        jarFile = dummyJar,
        instances = listOf("alice", "../evil"),
        baseOutputDir = tempDir,
        useAppLauncher = false,
        waitForReady = false
      )
    }
    assertFalse(DesktopProcessManager.isInstanceAlive("alice"))
  }

  @Test
  fun testStartIndexOffsetsJitSecondaryWindowCoordinates() {
    val pos = DesktopProcessManager.calculateWindowCoordinates(
      index = 1,
      totalCount = 2,
      preferredWindowWidth = 800,
      preferredWindowHeight = 700,
      paddingX = 20,
      paddingY = 40,
      originX = 50,
      originY = 50
    )
    assertEquals(Pair(870, 50), pos, "Secondary JIT window (startIndex=1) must tile beside primary (50, 50)")
  }

  @Test
  fun testIsEndpointReadyRequiresHttp200Not401() {
    val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/") { exchange ->
        val body = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
        val status = if (body.contains("\"token\":\"valid-token\"")) 200 else 401
        exchange.sendResponseHeaders(status, -1)
        exchange.close()
      }
      server.start()
      val port = server.address.port

      assertTrue(DesktopProcessManager.isEndpointReady("127.0.0.1", port, "valid-token"))
      assertFalse(DesktopProcessManager.isEndpointReady("127.0.0.1", port, "wrong-token"))
    } finally {
      server.stop(0)
    }
  }

  @Test
  fun testActiveInstancesAreScopedPerOutputDirectory() {
    val dirA = File(tempDir, "projectA").apply { mkdirs() }
    val dirB = File(tempDir, "projectB").apply { mkdirs() }
    val dummyJar = createDummyExecutableJar(tempDir)

    try {
      DesktopProcessManager.launchInstances(
        jarFile = dummyJar,
        instances = listOf("alice"),
        baseOutputDir = dirA,
        useAppLauncher = false,
        waitForReady = false
      )
      DesktopProcessManager.launchInstances(
        jarFile = dummyJar,
        instances = listOf("alice"),
        baseOutputDir = dirB,
        useAppLauncher = false,
        waitForReady = false
      )

      assertTrue(DesktopProcessManager.isInstanceAlive("alice", dirA))
      assertTrue(DesktopProcessManager.isInstanceAlive("alice", dirB))

      DesktopProcessManager.stopAll(dirA)
      assertFalse(DesktopProcessManager.isInstanceAlive("alice", dirA))
      assertTrue(DesktopProcessManager.isInstanceAlive("alice", dirB), "Stopping dirA must not stop dirB's alice")
    } finally {
      DesktopProcessManager.stopAll(dirA)
      DesktopProcessManager.stopAll(dirB)
    }
  }

  private fun createDummyExecutableJar(dir: File): File {
    val srcDir = File(dir, "src").apply { mkdirs() }
    val binDir = File(dir, "bin").apply { mkdirs() }
    val javaFile = File(srcDir, "DummyMain.java")
    javaFile.writeText(
      """
      public class DummyMain {
        public static void main(String[] args) throws Exception {
          Thread.sleep(60000);
        }
      }
      """.trimIndent()
    )
    val compiler = ToolProvider.getSystemJavaCompiler()
    compiler.run(null, null, null, "-d", binDir.absolutePath, javaFile.absolutePath)

    val jarFile = File(dir, "dummy-app.jar")
    val manifest = Manifest().apply {
      mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
      mainAttributes[Attributes.Name.MAIN_CLASS] = "DummyMain"
    }

    JarOutputStream(FileOutputStream(jarFile), manifest).use { jos ->
      val classBytes = File(binDir, "DummyMain.class").readBytes()
      jos.putNextEntry(JarEntry("DummyMain.class"))
      jos.write(classBytes)
      jos.closeEntry()
    }
    return jarFile
  }
}
