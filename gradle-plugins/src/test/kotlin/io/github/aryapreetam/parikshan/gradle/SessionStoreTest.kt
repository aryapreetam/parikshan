package io.github.aryapreetam.parikshan.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
  fun testMalformedJsonReturnsNullSafely() {
    val tempDir = createTempDirectory("parikshan-test").toFile()
    try {
      val sessionFile = File(tempDir, "corrupted-sessions.json")
      sessionFile.writeText("{ corrupted_invalid_json: true }")

      val session = SessionStore.readSession(sessionFile, "desktop")
      assertNull(session, "Corrupted session file should evaluate to null fallback without crashing")
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
    } finally {
      tempDir.deleteRecursively()
    }
  }
}
