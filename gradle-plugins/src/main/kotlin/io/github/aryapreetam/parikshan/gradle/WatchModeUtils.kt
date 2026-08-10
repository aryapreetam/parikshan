package io.github.aryapreetam.parikshan.gradle

import java.io.File

/**
 * Data structure representing an active application session running on a target platform.
 *
 * @property token Authorization token required for communicating with the target session.
 * @property port Local HTTP/CDP communication port bound by the target application process.
 * @property timestamp Unix epoch timestamp in milliseconds when the session was registered.
 */
internal data class TargetSession(
  val token: String,
  val port: Int,
  val timestamp: Long
)

/**
 * Persistence manager for recording and querying active target application sessions.
 */
internal object SessionStore {

  /**
   * Reads session metadata for a target platform from the specified active session JSON file.
   *
   * @param sessionFile File handle pointing to the active session JSON file.
   * @param target Target platform identifier (e.g. desktop, wasm, android, ios).
   * @return Active [TargetSession] if found, or null if the session record does not exist.
   */
  fun readSession(sessionFile: File, target: String): TargetSession? {
    if (!sessionFile.exists()) return null
    val text = runCatching { sessionFile.readText() }.getOrNull() ?: return null
    val targetBlockRegex = Regex("\"$target\"\\s*:\\s*\\{([^}]+)}")
    val blockMatch = targetBlockRegex.find(text) ?: return null
    val blockContent = blockMatch.groupValues[1]

    val token = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(blockContent)?.groupValues?.get(1) ?: return null
    val port = Regex("\"port\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    val timestamp = Regex("\"timestamp\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toLongOrNull() ?: return null

    return TargetSession(token, port, timestamp)
  }

  /**
   * Writes or updates session metadata for a target platform in the specified session JSON file.
   *
   * @param sessionFile File handle pointing to the active session JSON file.
   * @param target Target platform identifier.
   * @param session Session metadata object to record.
   */
  fun writeSession(sessionFile: File, target: String, session: TargetSession) {
    val sessions = mutableMapOf<String, TargetSession>()
    if (sessionFile.exists()) {
      val text = runCatching { sessionFile.readText() }.getOrNull().orEmpty()
      listOf("desktop", "wasm", "android", "ios").forEach { t ->
        val targetBlockRegex = Regex("\"$t\"\\s*:\\s*\\{([^}]+)}")
        val blockMatch = targetBlockRegex.find(text)
        if (blockMatch != null) {
          val blockContent = blockMatch.groupValues[1]
          val token = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(blockContent)?.groupValues?.get(1)
          val port = Regex("\"port\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toIntOrNull()
          val timestamp = Regex("\"timestamp\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toLongOrNull()
          if (token != null && port != null && timestamp != null) {
            sessions[t] = TargetSession(token, port, timestamp)
          }
        }
      }
    }
    sessions[target] = session

    val json = sessions.entries.joinToString(prefix = "{", postfix = "}") { (t, s) ->
      "\"$t\":{\"token\":\"${s.token}\",\"port\":${s.port},\"timestamp\":${s.timestamp}}"
    }
    runCatching {
      sessionFile.parentFile?.mkdirs()
      sessionFile.writeText(json)
    }
  }

  /**
   * Removes session metadata for a target platform from the specified session JSON file.
   *
   * @param sessionFile File handle pointing to the active session JSON file.
   * @param target Target platform identifier to clear.
   */
  fun clearSession(sessionFile: File, target: String) {
    if (!sessionFile.exists()) return
    val sessions = mutableMapOf<String, TargetSession>()
    val text = runCatching { sessionFile.readText() }.getOrNull().orEmpty()
    listOf("desktop", "wasm", "android", "ios").forEach { t ->
      if (t == target) return@forEach
      val targetBlockRegex = Regex("\"$t\"\\s*:\\s*\\{([^}]+)}")
      val blockMatch = targetBlockRegex.find(text)
      if (blockMatch != null) {
        val blockContent = blockMatch.groupValues[1]
        val token = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(blockContent)?.groupValues?.get(1)
        val port = Regex("\"port\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toIntOrNull()
        val timestamp = Regex("\"timestamp\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toLongOrNull()
        if (token != null && port != null && timestamp != null) {
          sessions[t] = TargetSession(token, port, timestamp)
        }
      }
    }

    val json = sessions.entries.joinToString(prefix = "{", postfix = "}") { (t, s) ->
      "\"$t\":{\"token\":\"${s.token}\",\"port\":${s.port},\"timestamp\":${s.timestamp}}"
    }
    runCatching {
      sessionFile.writeText(json)
    }
  }
}

/**
 * Utility functions for watch mode file tracking, test log analysis, and result formatting.
 */
internal object WatchModeUtils {

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
            lines.drop(failureIndex + 1).forEach { line ->
              val trimmed = line.trim()
              if (trimmed.startsWith("JUnit Jupiter:")) {
                val method = trimmed.substringAfter("JUnit Jupiter:").substringBefore("()").substringAfterLast(":")
                if (method.isNotEmpty() && !failedNames.contains(method)) {
                  failedNames.add(method)
                }
              } else if (trimmed.contains("methodName = '")) {
                val method = trimmed.substringAfter("methodName = '").substringBefore("'")
                if (method.isNotEmpty() && !failedNames.contains(method)) {
                  failedNames.add(method)
                }
              }
            }
          }
        }
      }
      if (failedNames.isEmpty()) {
        failedNames.add(simpleName)
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

  /**
   * Computes the maximum modification timestamp across class compilation outputs and target binary files.
   *
   * @param classesTime Timestamp of compiled class outputs.
   * @param files List of target output files or directories.
   * @return Maximum last-modified epoch timestamp in milliseconds.
   */
  fun computeOutputTimestamp(classesTime: Long, files: List<File>): Long {
    if (files.isEmpty()) return classesTime
    val filesTime = files.map { file ->
      if (file.isDirectory) {
        file.walkTopDown().filter { it.isFile }.map { it.lastModified() }.maxOrNull() ?: 0L
      } else {
        file.lastModified()
      }
    }.maxOrNull() ?: 0L

    return maxOf(filesTime, classesTime)
  }

  /**
   * Evaluates whether a target result message indicates an unrecoverable boot or device configuration failure.
   *
   * @param message Result message string from a target execution.
   * @return True if the failure is an unrecoverable environment or device setup error.
   */
  fun isBootFailure(message: String): Boolean {
    return message.startsWith("Boot phase failed:") ||
        message.contains("Multiple Android devices") ||
        message.contains("No ready Android device") ||
        message.contains("Device/emulator") ||
        message.contains("Could not list Android devices") ||
        message.startsWith("Failed to start")
  }
}
