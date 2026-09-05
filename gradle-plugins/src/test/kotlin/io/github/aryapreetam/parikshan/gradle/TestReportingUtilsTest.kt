package io.github.aryapreetam.parikshan.gradle

import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestReportingUtilsTest {

  private lateinit var tempDir: Path

  @BeforeTest
  fun setUp() {
    tempDir = createTempDirectory("test-reporting-utils")
  }

  @AfterTest
  fun tearDown() {
    tempDir.toFile().deleteRecursively()
  }

  @Test
  fun `test format failed names pattern A for single failure`() {
    val formatted = TestReportingUtils.formatFailedNamesPatternA(listOf("testSubtextMatching"), 1)
    assertEquals("(1 test failed: testSubtextMatching)", formatted)
  }

  @Test
  fun `test format failed names pattern A for two failures`() {
    val formatted = TestReportingUtils.formatFailedNamesPatternA(listOf("testFirst", "testSecond"), 2)
    assertEquals("(2 tests failed: testFirst, testSecond)", formatted)
  }

  @Test
  fun `test format failed names pattern A for three or more failures`() {
    val formattedThree = TestReportingUtils.formatFailedNamesPatternA(listOf("testFirst", "testSecond", "testThird"), 3)
    assertEquals("(3 tests failed: testFirst, testSecond, +1 more)", formattedThree)

    val formattedFour = TestReportingUtils.formatFailedNamesPatternA(listOf("testFirst", "testSecond"), 4)
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

    val failedNames = TestReportingUtils.parseFailedTestNames(
      logsDir = logsDir,
      target = "wasm",
      classes = listOf("sample.app.AccessibilityIntegrationTest")
    )

    assertEquals(listOf("testSubtextMatching"), failedNames)
  }

  @Test
  fun `test parse failed test names fallback to simple class name when log file missing`() {
    val logsDir = tempDir.resolve("logs").createDirectory().toFile()
    val failedNames = TestReportingUtils.parseFailedTestNames(
      logsDir = logsDir,
      target = "wasm",
      classes = listOf("sample.app.MissingIntegrationTest")
    )

    assertEquals(listOf("MissingIntegrationTest"), failedNames)
  }
}
