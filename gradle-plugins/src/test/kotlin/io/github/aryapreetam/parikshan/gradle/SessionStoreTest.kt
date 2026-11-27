package io.github.aryapreetam.parikshan.gradle

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStoreTest {

  @Test
  fun testWriteAndReadSingleSession() {
    val tempDir = createTempDirectory("parikshan-test").toFile()
    try {
      val sessionFile = File(tempDir, "parikshan-sessions.json")
      val desktopSession = TargetSession(token = "tok-12345", port = 8080, timestamp = 1700000000000L)

      SessionStore.writeSession(sessionFile, "desktop", desktopSession)

      val readSession = SessionStore.readSession(sessionFile, "desktop")
      assertNotNull(readSession)
      assertEquals("tok-12345", readSession.token)
      assertEquals("tok-12345", readSession.sessionToken)
      assertEquals(8080, readSession.port)
      assertEquals(1700000000000L, readSession.timestamp)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testWriteMultipleTargetsAndReadEachIndependently() {
    val tempDir = createTempDirectory("parikshan-test").toFile()
    try {
      val sessionFile = File(tempDir, "parikshan-sessions.json")
      val desktopSession = TargetSession(token = "desktop-tok", port = 8080, timestamp = 1000L)
      val wasmSession = TargetSession(token = "wasm-tok", port = 9090, timestamp = 2000L)
      val androidSession = TargetSession(token = "android-tok", port = 8081, timestamp = 3000L)

      SessionStore.writeSession(sessionFile, "desktop", desktopSession)
      SessionStore.writeSession(sessionFile, "wasm", wasmSession)
      SessionStore.writeSession(sessionFile, "android", androidSession)

      val readDesktop = SessionStore.readSession(sessionFile, "desktop")
      val readWasm = SessionStore.readSession(sessionFile, "wasm")
      val readAndroid = SessionStore.readSession(sessionFile, "android")
      val readIos = SessionStore.readSession(sessionFile, "ios")

      assertNotNull(readDesktop)
      assertEquals("desktop-tok", readDesktop.token)
      assertEquals(8080, readDesktop.port)

      assertNotNull(readWasm)
      assertEquals("wasm-tok", readWasm.token)
      assertEquals(9090, readWasm.port)

      assertNotNull(readAndroid)
      assertEquals("android-tok", readAndroid.token)
      assertEquals(8081, readAndroid.port)

      assertNull(readIos, "Target 'ios' was not registered and should return null")
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testClearSingleSessionPreservesOtherTargets() {
    val tempDir = createTempDirectory("parikshan-test").toFile()
    try {
      val sessionFile = File(tempDir, "parikshan-sessions.json")
      val desktopSession = TargetSession(token = "desktop-tok", port = 8080, timestamp = 1000L)
      val wasmSession = TargetSession(token = "wasm-tok", port = 9090, timestamp = 2000L)

      SessionStore.writeSession(sessionFile, "desktop", desktopSession)
      SessionStore.writeSession(sessionFile, "wasm", wasmSession)

      // Clear desktop only
      SessionStore.clearSession(sessionFile, "desktop")

      assertNull(SessionStore.readSession(sessionFile, "desktop"), "Desktop session should be cleared")

      val readWasm = SessionStore.readSession(sessionFile, "wasm")
      assertNotNull(readWasm, "Wasm session must remain intact after desktop is cleared")
      assertEquals("wasm-tok", readWasm.token)
      assertEquals(9090, readWasm.port)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testWriteAndReadActorKeyedInstanceSession() {
    val tempDir = createTempDirectory("parikshan-test").toFile()
    try {
      val sessionsDir = File(tempDir, "sessions")
      val aliceSession = TargetSession(
        token = "d4e2b810-7e1e-473d-9d41-3b7c8441951a",
        port = 9880,
        timestamp = 1725180000000L,
        instanceId = "alice",
        target = "desktop",
        pid = 48120L,
        dataDir = "build/parikshan/data/alice"
      )

      SessionStore.writeInstanceSession(sessionsDir, aliceSession)

      val readAlice = SessionStore.readInstanceSession(sessionsDir, "alice")
      assertNotNull(readAlice)
      assertEquals("alice", readAlice.instanceId)
      assertEquals("desktop", readAlice.target)
      assertEquals(9880, readAlice.port)
      assertEquals("d4e2b810-7e1e-473d-9d41-3b7c8441951a", readAlice.token)
      assertEquals("d4e2b810-7e1e-473d-9d41-3b7c8441951a", readAlice.sessionToken)
      assertEquals(48120L, readAlice.pid)
      assertEquals("build/parikshan/data/alice", readAlice.dataDir)
      assertEquals(1725180000000L, readAlice.timestamp)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testMultiInstanceSessionsWrittenConcurrentlyWithoutCollision() {
    val tempDir = createTempDirectory("parikshan-test").toFile()
    try {
      val sessionsDir = File(tempDir, "sessions")
      val actorCount = 10
      val actors = (1..actorCount).map { "actor-$it" }
      val latch = CountDownLatch(actorCount)

      val threads = actors.map { actorId ->
        thread {
          try {
            val session = TargetSession(
              token = "tok-$actorId",
              port = 10000 + actorId.substringAfter("-").toInt(),
              timestamp = System.currentTimeMillis(),
              instanceId = actorId,
              target = "desktop",
              pid = 1000L + actorId.substringAfter("-").toLong(),
              dataDir = "build/parikshan/data/$actorId"
            )
            SessionStore.writeInstanceSession(sessionsDir, session)
          } finally {
            latch.countDown()
          }
        }
      }

      assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent session writes timed out")
      threads.forEach { it.join(2000) }

      // Verify all actor files exist and are independently readable
      val listedSessions = SessionStore.listInstanceSessions(sessionsDir)
      assertEquals(actorCount, listedSessions.size, "All actor sessions must be present without collision")

      actors.forEach { actorId ->
        val session = SessionStore.readInstanceSession(sessionsDir, actorId)
        assertNotNull(session, "Session for $actorId must exist")
        assertEquals(actorId, session.instanceId)
        assertEquals("tok-$actorId", session.token)
      }
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testClearInstanceSessionRemovesOnlyTargetInstance() {
    val tempDir = createTempDirectory("parikshan-test").toFile()
    try {
      val sessionsDir = File(tempDir, "sessions")
      val alice = TargetSession("tok-alice", 9880, 1000L, instanceId = "alice", target = "desktop")
      val bob = TargetSession("tok-bob", 9881, 2000L, instanceId = "bob", target = "wasm")

      SessionStore.writeInstanceSession(sessionsDir, alice)
      SessionStore.writeInstanceSession(sessionsDir, bob)

      SessionStore.clearInstanceSession(sessionsDir, "alice")

      assertNull(SessionStore.readInstanceSession(sessionsDir, "alice"))
      val readBob = SessionStore.readInstanceSession(sessionsDir, "bob")
      assertNotNull(readBob)
      assertEquals("tok-bob", readBob.token)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testReadSessionFallbackToSessionsDirectory() {
    val tempDir = createTempDirectory("parikshan-test").toFile()
    try {
      val sessionsDir = File(tempDir, "sessions")
      val activeSessionFile = File(tempDir, "active-session.json")

      val charlie = TargetSession("tok-charlie", 9882, 3000L, instanceId = "charlie", target = "desktop")
      SessionStore.writeInstanceSession(sessionsDir, charlie)

      // active-session.json does not exist yet; reading "charlie" should fall back to sessions/charlie.json
      val readCharlie = SessionStore.readSession(activeSessionFile, "charlie")
      assertNotNull(readCharlie, "readSession must fall back to sessions/ directory")
      assertEquals("tok-charlie", readCharlie.token)
      assertEquals(9882, readCharlie.port)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testMalformedJsonReturnsNullSafely() {
    val tempDir = createTempDirectory("parikshan-test").toFile()
    try {
      val sessionFile = File(tempDir, "corrupted-sessions.json")
      sessionFile.writeText("{ corrupted_invalid_json: true }")

      val session = SessionStore.readSession(sessionFile, "desktop")
      assertNull(session, "Corrupted session file should evaluate to null fallback without crashing")

      val instanceSession = SessionStore.readInstanceSession(sessionFile)
      assertNull(instanceSession, "Corrupted instance session file should evaluate to null")
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testNonExistentFileReturnsNullSafely() {
    val tempDir = createTempDirectory("parikshan-test").toFile()
    try {
      val sessionFile = File(tempDir, "does-not-exist.json")
      val session = SessionStore.readSession(sessionFile, "desktop")
      assertNull(session)

      val instanceSession = SessionStore.readInstanceSession(sessionFile)
      assertNull(instanceSession)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun testWindowsBackslashPathsRoundTripAsValidEscapedJson() {
    val tempDir = createTempDirectory("parikshan-win-json-test").toFile()
    try {
      val sessionsDir = File(tempDir, "sessions")
      val windowsPath = """C:\Users\runneradmin\workspace\build\parikshan\desktop\data\alice"""
      val session = TargetSession(
        token = """tok\"with\slash""",
        port = 9901,
        timestamp = 1725180000123L,
        instanceId = "alice",
        target = "desktop",
        pid = 51234L,
        dataDir = windowsPath
      )

      SessionStore.writeInstanceSession(sessionsDir, session)

      val rawJson = File(sessionsDir, "alice.json").readText()
      assertTrue(
        rawJson.contains("""C:\\Users\\runneradmin\\workspace\\build\\parikshan\\desktop\\data\\alice"""),
        "Serialized JSON must escape Windows backslashes: $rawJson"
      )

      val readBack = SessionStore.readInstanceSession(sessionsDir, "alice")
      assertNotNull(readBack)
      assertEquals(windowsPath, readBack.dataDir)
      assertEquals("""tok\"with\slash""", readBack.token)
      assertEquals(9901, readBack.port)
      assertEquals(51234L, readBack.pid)
    } finally {
      tempDir.deleteRecursively()
    }
  }
}
