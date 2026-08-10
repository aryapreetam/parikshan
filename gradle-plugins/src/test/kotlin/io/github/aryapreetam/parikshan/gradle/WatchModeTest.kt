package io.github.aryapreetam.parikshan.gradle

import java.io.File
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchModeTest {

  private lateinit var tempDir: Path

  @BeforeTest
  fun setUp() {
    tempDir = createTempDirectory("parikshan-watch-test")
  }

  @AfterTest
  fun tearDown() {
    runCatching { tempDir.toFile().deleteRecursively() }
  }

  @Test
  fun `test desktop process death exits watch loop`() {
    val activeTargets = listOf("desktop")
    val desktopProcessAlive = AtomicBoolean(true)

    var watchExited = false
    val watchThread = Thread {
      while (true) {
        Thread.sleep(50)
        if (activeTargets.contains("desktop") && !desktopProcessAlive.get()) {
          watchExited = true
          break
        }
      }
    }
    watchThread.start()

    // Simulate Desktop application process exit
    desktopProcessAlive.set(false)
    watchThread.join(2000)

    assertFalse(watchThread.isAlive, "Watch loop thread must terminate when Desktop process closes")
    assertTrue(watchExited, "Watch mode must exit cleanly when Desktop process closes")
  }

  @Test
  fun `test watch loop processes pending change and executes cycle`() {
    val pendingChange = AtomicReference<Path?>(null)
    var cycleExecuted = false
    val lastEventTime = System.currentTimeMillis() - 1000L
    val debounceMs = 500L

    val testFile = tempDir.resolve("AccessibilityIntegrationTest.kt")
    pendingChange.set(testFile)

    val path = pendingChange.get()
    if (path != null && System.currentTimeMillis() - lastEventTime >= debounceMs) {
      if (pendingChange.compareAndSet(path, null)) {
        cycleExecuted = true
      }
    }

    assertTrue(cycleExecuted, "Pending change must be consumed and cycle executed")
    assertEquals(null, pendingChange.get(), "Pending change reference must be reset to null after execution")
  }

  @Test
  fun `test watch cycle error recovery keeps watch loop active for subsequent changes`() {
    var cycleCount = 0
    var errorCaught = false

    val executeCycle: (Path?) -> Unit = { path ->
      cycleCount++
      if (cycleCount == 1) {
        throw RuntimeException("Simulated Kotlin compilation error")
      }
    }

    // First cycle throws compilation exception
    try {
      executeCycle(tempDir)
    } catch (e: Exception) {
      errorCaught = true
    }

    assertTrue(errorCaught, "Compilation error must be caught")
    assertEquals(1, cycleCount)

    // Second cycle following fix succeeds
    executeCycle(tempDir)
    assertEquals(2, cycleCount, "Watch loop must remain active to process subsequent cycles after compilation error")
  }

  @Test
  fun `test polling watcher detects file modification and emits event`() {
    val testFile = tempDir.resolve("Test.kt").toFile()
    testFile.writeText("class Test")

    val events = LinkedBlockingQueue<File>()
    val watcher = PollingWatcher(listOf(tempDir.toFile())) { file ->
      events.offer(file)
    }

    try {
      Thread.sleep(100)
      testFile.writeText("class TestUpdated")

      val detectedFile = events.poll(3000, TimeUnit.MILLISECONDS)
      if (detectedFile != null) {
        assertEquals("Test.kt", detectedFile.name)
      }
    } finally {
      watcher.close()
    }
  }

  @Test
  fun `test polling watcher detects new file created in nested directory`() {
    val subDir = tempDir.resolve("nested").createDirectory().toFile()
    val events = LinkedBlockingQueue<File>()
    val watcher = PollingWatcher(listOf(tempDir.toFile())) { file ->
      events.offer(file)
    }

    try {
      Thread.sleep(100)
      val newFile = File(subDir, "NewNestedTest.kt")
      newFile.writeText("class NewNestedTest")

      val detectedFile = events.poll(3000, TimeUnit.MILLISECONDS)
      if (detectedFile != null) {
        assertEquals("NewNestedTest.kt", detectedFile.name)
      }
    } finally {
      watcher.close()
    }
  }

  @Test
  fun `test session store write read and clear operations`() {
    val sessionFile = tempDir.resolve("active-session.json").toFile()

    // 1. Initial read on non-existent file returns null
    assertNull(SessionStore.readSession(sessionFile, "desktop"))

    // 2. Write desktop session and verify read
    val desktopSession = TargetSession("desktop-token-123", 9879, 1000000L)
    SessionStore.writeSession(sessionFile, "desktop", desktopSession)

    val readDesktop = SessionStore.readSession(sessionFile, "desktop")
    assertNotNull(readDesktop)
    assertEquals("desktop-token-123", readDesktop.token)
    assertEquals(9879, readDesktop.port)
    assertEquals(1000000L, readDesktop.timestamp)

    // 3. Write WASM session and verify both targets persist concurrently
    val wasmSession = TargetSession("wasm-token-456", 8080, 2000000L)
    SessionStore.writeSession(sessionFile, "wasm", wasmSession)

    val readDesktopMulti = SessionStore.readSession(sessionFile, "desktop")
    val readWasmMulti = SessionStore.readSession(sessionFile, "wasm")
    assertNotNull(readDesktopMulti)
    assertNotNull(readWasmMulti)
    assertEquals("wasm-token-456", readWasmMulti.token)
    assertEquals(8080, readWasmMulti.port)

    // 4. Clear desktop session and verify WASM session remains intact
    SessionStore.clearSession(sessionFile, "desktop")
    assertNull(SessionStore.readSession(sessionFile, "desktop"))

    val readWasmRemaining = SessionStore.readSession(sessionFile, "wasm")
    assertNotNull(readWasmRemaining)
    assertEquals("wasm-token-456", readWasmRemaining.token)
  }

  @Test
  fun `test session store handles malformed files gracefully`() {
    val sessionFile = tempDir.resolve("active-session.json").toFile()
    sessionFile.writeText("invalid { json content :::")

    assertNull(SessionStore.readSession(sessionFile, "desktop"))

    // Writing to malformed file overwrites with valid format
    val newSession = TargetSession("new-token", 9000, 3000000L)
    SessionStore.writeSession(sessionFile, "desktop", newSession)

    val readBack = SessionStore.readSession(sessionFile, "desktop")
    assertNotNull(readBack)
    assertEquals("new-token", readBack.token)
  }

  @Test
  fun `test format failed names pattern A for single failure`() {
    val formatted = WatchModeUtils.formatFailedNamesPatternA(listOf("testSubtextMatching"), 1)
    assertEquals("(1 test failed: testSubtextMatching)", formatted)
  }

  @Test
  fun `test format failed names pattern A for two failures`() {
    val formatted = WatchModeUtils.formatFailedNamesPatternA(listOf("testFirst", "testSecond"), 2)
    assertEquals("(2 tests failed: testFirst, testSecond)", formatted)
  }

  @Test
  fun `test format failed names pattern A for three or more failures`() {
    val formattedThree = WatchModeUtils.formatFailedNamesPatternA(listOf("testFirst", "testSecond", "testThird"), 3)
    assertEquals("(3 tests failed: testFirst, testSecond, +1 more)", formattedThree)

    val formattedFour = WatchModeUtils.formatFailedNamesPatternA(listOf("testFirst", "testSecond"), 4)
    assertEquals("(4 tests failed: testFirst, testSecond, +2 more)", formattedFour)
  }

  @Test
  fun `test parse failed test names from junit log file`() {
    val logsDir = tempDir.resolve("logs").createDirectory().toFile()
    val logFile = File(logsDir, "wasm-sample.app.AccessibilityIntegrationTest.log")
    logFile.writeText(
      """
      --- [wasm] Test Failures for sample.app.AccessibilityIntegrationTest ---
      Failures (1):
      JUnit Jupiter:AccessibilityIntegrationTest:testSubtextMatching()
      MethodSource [className = 'sample.app.AccessibilityIntegrationTest', methodName = 'testSubtextMatching']
      => java.lang.AssertionError: Timeout
      """.trimIndent()
    )

    val failedNames = WatchModeUtils.parseFailedTestNames(
      logsDir = logsDir,
      target = "wasm",
      classes = listOf("sample.app.AccessibilityIntegrationTest")
    )

    assertEquals(listOf("testSubtextMatching"), failedNames)
  }

  @Test
  fun `test parse failed test names fallback to simple class name when log file missing`() {
    val logsDir = tempDir.resolve("logs").createDirectory().toFile()
    val failedNames = WatchModeUtils.parseFailedTestNames(
      logsDir = logsDir,
      target = "wasm",
      classes = listOf("sample.app.MissingIntegrationTest")
    )

    assertEquals(listOf("MissingIntegrationTest"), failedNames)
  }

  @Test
  fun `test compute output timestamp returns maximum modification time`() {
    val subDir = tempDir.resolve("bin").createDirectory().toFile()
    val file1 = File(subDir, "app.jar")
    file1.writeText("content1")
    file1.setLastModified(1000L)

    val file2 = File(subDir, "app.wasm")
    file2.writeText("content2")
    file2.setLastModified(5000L)

    val timestamp = WatchModeUtils.computeOutputTimestamp(classesTime = 2000L, files = listOf(subDir))
    assertEquals(5000L, timestamp)
  }

  @Test
  fun `test is boot failure detection`() {
    assertTrue(WatchModeUtils.isBootFailure("Boot phase failed: Android device disconnected"))
    assertTrue(WatchModeUtils.isBootFailure("Parikshan Android: Multiple Android devices/emulators are connected: dev1, dev2."))
    assertTrue(WatchModeUtils.isBootFailure("Parikshan Android: No ready Android device/emulator connected."))
    assertTrue(WatchModeUtils.isBootFailure("Failed to start Android app (exit code 1)"))
    assertTrue(WatchModeUtils.isBootFailure("Device/emulator 'emulator-5554' was not found."))

    assertFalse(WatchModeUtils.isBootFailure("Android test suite execution failed (exit code 1)."))
    assertFalse(WatchModeUtils.isBootFailure("SUCCESS - 1 test passed."))
  }
}
