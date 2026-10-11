package io.github.aryapreetam.parikshan.gradle

import java.io.File

/**
 * Data structure representing an active application session running on a target platform.
 *
 * @property token Authorization token required for communicating with the target session.
 * @property port Local HTTP/CDP communication port bound by the target application process.
 * @property timestamp Unix epoch timestamp in milliseconds when the session was registered.
 * @property instanceId Actor or instance identifier (e.g. desktop, wasm, alice, bob).
 * @property target Target platform identifier (e.g. desktop, wasm, android, ios).
 * @property pid Process ID of the launched child application process, if known.
 * @property dataDir Path to the isolated application data directory, if configured.
 */
internal data class TargetSession(
  val token: String,
  val port: Int,
  val timestamp: Long,
  val instanceId: String = "",
  val target: String = "",
  val pid: Long? = null,
  val dataDir: String? = null
) {
  val sessionToken: String get() = token
}

/**
 * Persistence manager for recording and querying active target application sessions.
 *
 * Supports both unified multi-target session manifests (`active-session.json`)
 * and actor-keyed individual session descriptors (`sessions/{instanceId}.json`).
 */
internal object SessionStore {

  /**
   * Reads session metadata for a target platform or instance.
   *
   * @param sessionFile File handle pointing to the active session JSON file or directory.
   * @param target Target platform or instance identifier.
   * @return Active [TargetSession] if found, or null if the session record does not exist.
   */
  fun readSession(sessionFile: File, target: String): TargetSession? {
    if (sessionFile.exists()) {
      val text = runCatching { sessionFile.readText() }.getOrNull()
      if (!text.isNullOrBlank()) {
        val targetBlockRegex = Regex("\"$target\"\\s*:\\s*\\{([^}]+)}")
        val blockMatch = targetBlockRegex.find(text)
        if (blockMatch != null) {
          return parseSessionBlock(blockMatch.groupValues[1], defaultTarget = target)
        }

        // If sessionFile is a multi-target manifest, do not treat as standalone or fall back
        val isMultiTargetMap = listOf("desktop", "wasm", "android", "ios").any { t ->
          Regex("\"$t\"\\s*:\\s*\\{").containsMatchIn(text)
        }
        if (isMultiTargetMap) {
          return null
        }

        val standalone = parseStandaloneSession(text, fallbackTarget = target)
        if (standalone != null && (standalone.target == target || standalone.instanceId == target)) {
          return standalone
        }
      }
    }

    val parent = sessionFile.parentFile ?: return null
    val instanceFile = File(parent, "sessions/$target.json")
    if (instanceFile.exists()) {
      return readInstanceSession(instanceFile)
    }

    return null
  }

  /**
   * Writes or updates session metadata for a target platform in the specified session JSON file,
   * synchronizing actor-keyed descriptors in `sessions/{target}.json`.
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
          parseSessionBlock(blockMatch.groupValues[1], defaultTarget = t)?.let {
            sessions[t] = it
          }
        }
      }
    }
    sessions[target] = session

    val json = sessions.entries.joinToString(prefix = "{", postfix = "}") { (t, s) ->
      buildTargetBlockJson(t, s)
    }
    runCatching {
      sessionFile.parentFile?.mkdirs()
      sessionFile.writeText(json)
    }

    sessionFile.parentFile?.let { parent ->
      val sessionsDir = File(parent, "sessions")
      val effectiveSession = if (session.target.isBlank() || session.instanceId.isBlank()) {
        session.copy(target = session.target.ifBlank { target }, instanceId = session.instanceId.ifBlank { target })
      } else {
        session
      }
      writeInstanceSession(sessionsDir, effectiveSession)
    }
  }

  /**
   * Removes session metadata for a target platform from the specified session JSON file,
   * and clears the actor-keyed descriptor in `sessions/{target}.json`.
   *
   * @param sessionFile File handle pointing to the active session JSON file.
   * @param target Target platform identifier to clear.
   */
  fun clearSession(sessionFile: File, target: String) {
    if (sessionFile.exists()) {
      val sessions = mutableMapOf<String, TargetSession>()
      val text = runCatching { sessionFile.readText() }.getOrNull().orEmpty()
      listOf("desktop", "wasm", "android", "ios").forEach { t ->
        if (t == target) return@forEach
        val targetBlockRegex = Regex("\"$t\"\\s*:\\s*\\{([^}]+)}")
        val blockMatch = targetBlockRegex.find(text)
        if (blockMatch != null) {
          parseSessionBlock(blockMatch.groupValues[1], defaultTarget = t)?.let {
            sessions[t] = it
          }
        }
      }

      val json = sessions.entries.joinToString(prefix = "{", postfix = "}") { (t, s) ->
        buildTargetBlockJson(t, s)
      }
      runCatching {
        sessionFile.writeText(json)
      }
    }

    sessionFile.parentFile?.let { parent ->
      val sessionsDir = File(parent, "sessions")
      clearInstanceSession(sessionsDir, target)
    }
  }

  /**
   * Reads an actor-keyed session descriptor from `sessions/{instanceId}.json`.
   *
   * @param sessionsDirOrFile Directory containing session files or direct session JSON file.
   * @param instanceId Optional instance ID if [sessionsDirOrFile] is a directory.
   * @return Parsed [TargetSession] if valid, or null.
   */
  fun readInstanceSession(sessionsDirOrFile: File, instanceId: String? = null): TargetSession? {
    val file = if (sessionsDirOrFile.isDirectory) {
      if (instanceId == null) return null
      File(sessionsDirOrFile, "$instanceId.json")
    } else {
      sessionsDirOrFile
    }

    if (!file.exists()) return null
    val text = runCatching { file.readText() }.getOrNull() ?: return null
    return parseStandaloneSession(text, fallbackTarget = instanceId ?: file.nameWithoutExtension)
  }

  /**
   * Writes an actor-keyed session descriptor into `sessions/{instanceId}.json`.
   *
   * @param sessionsDir Directory to hold session JSON files (`build/parikshan/sessions`).
   * @param session Session metadata object to record.
   */
  fun writeInstanceSession(sessionsDir: File, session: TargetSession) {
    val id = session.instanceId.ifBlank { session.target.ifBlank { "default" } }
    val file = File(sessionsDir, "$id.json")
    val json = buildStandaloneJson(session.copy(instanceId = id))
    runCatching {
      sessionsDir.mkdirs()
      file.writeText(json)
    }
  }

  /**
   * Removes an actor-keyed session descriptor `sessions/{instanceId}.json`.
   *
   * @param sessionsDir Directory holding session JSON files.
   * @param instanceId Instance identifier to clear.
   */
  fun clearInstanceSession(sessionsDir: File, instanceId: String) {
    val file = File(sessionsDir, "$instanceId.json")
    runCatching {
      if (file.exists()) {
        file.delete()
      }
    }
  }

  /**
   * Lists all active actor-keyed session descriptors in [sessionsDir].
   *
   * @param sessionsDir Directory holding session JSON files.
   * @return List of parsed [TargetSession] records.
   */
  fun listInstanceSessions(sessionsDir: File): List<TargetSession> {
    if (!sessionsDir.exists() || !sessionsDir.isDirectory) return emptyList()
    return sessionsDir.listFiles { f -> f.extension == "json" }
      ?.mapNotNull { readInstanceSession(it) }
      ?.sortedBy { it.timestamp }
      ?: emptyList()
  }

  private fun parseSessionBlock(blockContent: String, defaultTarget: String): TargetSession? {
    val token = Regex("\"sessionToken\"\\s*:\\s*\"([^\"]+)\"").find(blockContent)?.groupValues?.get(1)
      ?: Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(blockContent)?.groupValues?.get(1)
      ?: return null
    val port = Regex("\"port\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    val timestamp = Regex("\"timestamp\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toLongOrNull() ?: return null
    val pid = Regex("\"pid\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toLongOrNull()
    val dataDir = Regex("\"dataDir\"\\s*:\\s*\"([^\"]+)\"").find(blockContent)?.groupValues?.get(1)
    val instanceId = Regex("\"instanceId\"\\s*:\\s*\"([^\"]+)\"").find(blockContent)?.groupValues?.get(1) ?: defaultTarget
    val target = Regex("\"target\"\\s*:\\s*\"([^\"]+)\"").find(blockContent)?.groupValues?.get(1) ?: defaultTarget

    return TargetSession(
      token = token,
      port = port,
      timestamp = timestamp,
      instanceId = instanceId,
      target = target,
      pid = pid,
      dataDir = dataDir
    )
  }

  private fun parseStandaloneSession(text: String, fallbackTarget: String): TargetSession? {
    val hasInstanceId = Regex("\"instanceId\"\\s*:\\s*\"([^\"]+)\"").find(text)
    val hasSessionToken = Regex("\"sessionToken\"\\s*:\\s*\"([^\"]+)\"").find(text)

    if (hasInstanceId == null && hasSessionToken == null) {
      val isMultiTargetMap = listOf("desktop", "wasm", "android", "ios").any { t ->
        Regex("\"$t\"\\s*:\\s*\\{").containsMatchIn(text)
      }
      if (isMultiTargetMap) {
        return null
      }
    }

    return parseSessionBlock(text, defaultTarget = fallbackTarget)
  }

  private fun buildTargetBlockJson(target: String, session: TargetSession): String {
    val parts = mutableListOf(
      "\"token\":\"${session.token}\"",
      "\"port\":${session.port}",
      "\"timestamp\":${session.timestamp}"
    )
    if (session.pid != null) parts.add("\"pid\":${session.pid}")
    if (session.dataDir != null) parts.add("\"dataDir\":\"${session.dataDir}\"")

    return "\"$target\":{${parts.joinToString(",")}}"
  }

  private fun buildStandaloneJson(session: TargetSession): String {
    val id = session.instanceId.ifBlank { session.target }
    val parts = mutableListOf(
      "\"instanceId\":\"$id\"",
      "\"target\":\"${session.target}\"",
      "\"port\":${session.port}",
      "\"sessionToken\":\"${session.token}\""
    )
    if (session.pid != null) parts.add("\"pid\":${session.pid}")
    if (!session.dataDir.isNullOrBlank()) parts.add("\"dataDir\":\"${session.dataDir}\"")
    parts.add("\"timestamp\":${session.timestamp}")

    return "{\n  ${parts.joinToString(",\n  ")}\n}\n"
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
