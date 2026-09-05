package io.github.aryapreetam.parikshan.gradle

import java.io.File

/**
 * Utility functions for parsing test log outputs and formatting summary reports.
 */
internal object TestReportingUtils {

  /**
   * Parses test log files in the specified logs directory to extract failing test method names.
   *
   * @param logsDir Directory containing test execution log files.
   * @param target Target platform or execution context identifier.
   * @param classes List of fully-qualified test class names executed during the run.
   * @return List of failing test method names, or class simple names as fallback.
   */
  fun parseFailedTestNames(logsDir: File, target: String, classes: List<String>): List<String> {
    val failedNames = mutableListOf<String>()
    val baseTarget = if (target.startsWith("sync")) "sync" else target
    classes.forEach { testClass ->
      val simpleName = testClass.substringAfterLast('.')
      val logFiles = listOf(
        File(logsDir, "${baseTarget}-${testClass}.log"),
        File(logsDir, "${baseTarget}-${simpleName}.log")
      )
      val logFile = logFiles.firstOrNull { it.exists() }
      if (logFile != null) {
        runCatching {
          val lines = logFile.readLines()
          val failureIndex = lines.indexOfFirst { it.trim().startsWith("Failures (") }
          if (failureIndex >= 0) {
            val classFailedMethods = mutableListOf<String>()
            lines.drop(failureIndex + 1).forEach { line ->
              val trimmed = line.trim()
              if (trimmed.startsWith("JUnit Jupiter:")) {
                val method = trimmed.substringAfter("JUnit Jupiter:").substringBefore("()").substringAfterLast(":")
                if (method.isNotEmpty() && !classFailedMethods.contains(method)) {
                  classFailedMethods.add(method)
                }
              } else if (trimmed.contains("methodName = '")) {
                val method = trimmed.substringAfter("methodName = '").substringBefore("'")
                if (method.isNotEmpty() && !classFailedMethods.contains(method)) {
                  classFailedMethods.add(method)
                }
              }
            }
            if (classFailedMethods.isNotEmpty()) {
              failedNames.addAll(classFailedMethods)
            } else if (!failedNames.contains(simpleName)) {
              failedNames.add(simpleName)
            }
          } else {
            val hasFailedTests = lines.any { line ->
              val trimmed = line.trim()
              trimmed.endsWith("tests failed ]") && !trimmed.startsWith("[ 0")
            }
            if (hasFailedTests && !failedNames.contains(simpleName)) {
              failedNames.add(simpleName)
            }
          }
        }
      } else {
        if (!failedNames.contains(simpleName)) {
          failedNames.add(simpleName)
        }
      }
    }
    return failedNames
  }

  /**
   * Formats test failure method names according to Pattern A smart concise inline formatting rules.
   *
   * @param names List of failing test method or class names.
   * @param totalFailed Total count of failed tests reported by test metrics.
   * @return Formatted summary string (e.g. "(1 test failed: testMethod)" or "(3 tests failed: test1, test2, +1 more)").
   */
  fun formatFailedNamesPatternA(names: List<String>, totalFailed: Int): String {
    val count = maxOf(names.size, totalFailed)
    val testWord = if (count == 1) "test" else "tests"
    return when {
      count == 1 && names.isNotEmpty() -> "(1 $testWord failed: ${names[0]})"
      count == 2 && names.size >= 2 -> "(2 tests failed: ${names[0]}, ${names[1]})"
      names.size >= 2 -> {
        val extra = count - 2
        if (extra > 0) {
          "($count tests failed: ${names[0]}, ${names[1]}, +$extra more)"
        } else {
          "($count tests failed: ${names[0]}, ${names[1]})"
        }
      }
      names.size == 1 -> {
        val extra = count - 1
        if (extra > 0) {
          "($count tests failed: ${names[0]}, +$extra more)"
        } else {
          "(1 $testWord failed: ${names[0]})"
        }
      }
      else -> "($count $testWord failed)"
    }
  }
}
