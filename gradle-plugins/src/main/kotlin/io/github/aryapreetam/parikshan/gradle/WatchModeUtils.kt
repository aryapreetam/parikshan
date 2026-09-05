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
  fun parseFailedTestNames(logsDir: File, target: String, classes: List<String>): List<String> =
    TestReportingUtils.parseFailedTestNames(logsDir, target, classes)

  fun formatFailedNamesPatternA(names: List<String>, totalFailed: Int): String =
    TestReportingUtils.formatFailedNamesPatternA(names, totalFailed)


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
