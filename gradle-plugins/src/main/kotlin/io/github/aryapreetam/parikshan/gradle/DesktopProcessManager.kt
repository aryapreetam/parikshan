package io.github.aryapreetam.parikshan.gradle

import java.awt.GraphicsEnvironment
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Manages concurrent Desktop application processes for multi-instance E2E testing.
 *
 * Handles:
 * - Dynamic ephemeral port allocation per instance via [PortAllocator].
 * - Unique session token generation per instance.
 * - Isolated local data directory injection (`-Dparikshan.data.dir=...`).
 * - Window coordinate tiling to avoid overlapping windows side-by-side or in a 2x2 grid.
 * - Persistent session recording in `build/parikshan/sessions/<instanceId>.json` using [SessionStore].
 * - Clean process lifecycle management and graceful shutdown in [stopAll].
 */
internal object DesktopProcessManager {

  private val logger: Logger = Logging.getLogger(DesktopProcessManager::class.java)

  /**
   * Represents an active application instance launched by the manager.
   */
  data class LaunchedInstance(
    val instanceId: String,
    val port: Int,
    val token: String,
    val process: Process,
    val dataDir: File,
    val windowPosition: Pair<Int, Int>
  )

  private val activeInstances = ConcurrentHashMap<String, LaunchedInstance>()

  /**
   * Calculates non-overlapping window coordinates for N instances.
   * If totalCount <= 2: side-by-side placement (2 columns, 1 row).
   * If totalCount > 2: 2x2 grid layout (2 columns, multiple rows).
   */
  fun calculateWindowCoordinates(
    index: Int,
    totalCount: Int,
    preferredWindowWidth: Int = 800,
    preferredWindowHeight: Int = 700,
    paddingX: Int = 20,
    paddingY: Int = 40,
    originX: Int = 50,
    originY: Int = 50
  ): Pair<Int, Int> {
    val cols = 2
    val col = index % cols
    val row = index / cols
    val x = originX + col * (preferredWindowWidth + paddingX)
    val y = originY + row * (preferredWindowHeight + paddingY)
    return Pair(x, y)
  }

  /**
   * Spawns N concurrent desktop application processes with isolated ports, session tokens,
   * local data storage directories, and tiled window positions.
   *
   * @param jarFile Packaged runnable application JAR artifact.
   * @param instances List of unique actor/instance identifiers (e.g. `listOf("alice", "bob")`).
   * @param baseOutputDir Root directory for logs, data directories, and session descriptors.
   * @param preferredWindowWidth Preferred width of each desktop window.
   * @param preferredWindowHeight Preferred height of each desktop window.
   * @param appArgs Additional CLI arguments to pass to the application main method.
   * @param host Localhost binding address (default: `127.0.0.1`).
   * @param timeoutMs Maximum duration to wait for each instance's embedded server to become responsive.
   * @param pollMs Interval between health check probes.
   * @param titlePrefix Optional window title prefix.
   * @param background When true, applies focus prevention flags for background execution.
   * @return List of [LaunchedInstance] descriptors.
   */
  fun launchInstances(
    jarFile: File,
    instances: List<String>,
    baseOutputDir: File,
    preferredWindowWidth: Int = 800,
    preferredWindowHeight: Int = 700,
    appArgs: List<String> = emptyList(),
    host: String = "127.0.0.1",
    timeoutMs: Long = 15000L,
    pollMs: Long = 250L,
    titlePrefix: String? = null,
    background: Boolean = false,
    useAppLauncher: Boolean = true,
    waitForReady: Boolean = true
  ): List<LaunchedInstance> {
    require(instances.isNotEmpty()) { "Instance list must not be empty" }
    require(instances.distinct().size == instances.size) { "Duplicate instance IDs detected: $instances" }

    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val javaExecutable = System.getProperty("java.home") + "/bin/java" + (if (isWindows) ".exe" else "")
    val mainClass = java.util.jar.JarFile(jarFile).manifest?.mainAttributes?.getValue("Main-Class")
    val hasPremain = runCatching {
      java.util.jar.JarFile(jarFile).use { jf ->
        jf.manifest?.mainAttributes?.getValue("Premain-Class") != null
      }
    }.getOrDefault(false)
    val sessionsDir = File(baseOutputDir, "sessions").apply { mkdirs() }
    val logsDir = File(baseOutputDir, "logs").apply { mkdirs() }
    val sharedDataDir = File(baseOutputDir, "data").apply { mkdirs() }
    File(sharedDataDir, "shared_chat_messages.txt").delete()

    val launched = mutableListOf<LaunchedInstance>()

    instances.forEachIndexed { index, instanceId ->
      stopInstance(instanceId = instanceId, baseOutputDir = baseOutputDir, host = host)

      val port = PortAllocator.allocate()
      val token = UUID.randomUUID().toString()
      val dataDir = File(baseOutputDir, "data/$instanceId").apply { mkdirs() }
      val (windowX, windowY) = calculateWindowCoordinates(
        index = index,
        totalCount = instances.size,
        preferredWindowWidth = preferredWindowWidth,
        preferredWindowHeight = preferredWindowHeight
      )

      val logFile = File(logsDir, "desktop-$instanceId.log")

      val command = buildList {
        add(javaExecutable)
        if (hasPremain) {
          add("-javaagent:${jarFile.absolutePath}")
        }
        add("-Dparikshan.host=$host")
        add("-Dparikshan.port=$port")
        add("-Dparikshan.token=$token")
        add("-Dparikshan.session.token=$token")
        add("-Dparikshan.instance.id=$instanceId")
        add("-Dparikshan.data.dir=${dataDir.absolutePath}")
        add("-Dparikshan.desktop.windowX=$windowX")
        add("-Dparikshan.desktop.windowY=$windowY")
        add("-Dparikshan.desktop.windowWidth=$preferredWindowWidth")
        add("-Dparikshan.desktop.windowHeight=$preferredWindowHeight")
        if (!mainClass.isNullOrBlank()) {
          add("-Dparikshan.desktop.appMainClass=$mainClass")
        }
        add("-Dapple.awt.takeFocusOnShow=false")
        if (background) {
          add("-Dparikshan.background=true")
        }
        if (!titlePrefix.isNullOrBlank()) {
          add("-Dparikshan.desktop.windowTitle=$titlePrefix - $instanceId")
        }
        if (useAppLauncher) {
          add("-cp")
          add(jarFile.absolutePath)
          add("io.github.aryapreetam.parikshan.server.DesktopAppLauncher")
        } else {
          add("-jar")
          add(jarFile.absolutePath)
        }
        addAll(appArgs)
      }

      val pb = ProcessBuilder(command).apply {
        environment()["NSAppSleepDisabled"] = "YES"
        redirectErrorStream(true)
        redirectOutput(logFile)
      }

      val process = pb.start()

      val targetSession = TargetSession(
        token = token,
        port = port,
        timestamp = System.currentTimeMillis(),
        instanceId = instanceId,
        target = "desktop",
        pid = process.pid(),
        dataDir = dataDir.absolutePath
      )
      SessionStore.writeInstanceSession(sessionsDir, targetSession)

      val instance = LaunchedInstance(
        instanceId = instanceId,
        port = port,
        token = token,
        process = process,
        dataDir = dataDir,
        windowPosition = Pair(windowX, windowY)
      )
      activeInstances[instanceId] = instance
      launched.add(instance)
    }

    if (waitForReady) {
      waitForHealth(launched, baseOutputDir, host, timeoutMs, pollMs)
    }

    return launched
  }

  private fun waitForHealth(
    instances: List<LaunchedInstance>,
    baseOutputDir: File,
    host: String,
    timeoutMs: Long,
    pollMs: Long
  ) {
    val deadline = System.currentTimeMillis() + timeoutMs
    for (inst in instances) {
      var ready = false
      while (System.currentTimeMillis() < deadline) {
        if (!inst.process.isAlive) {
          val exitCode = inst.process.exitValue()
          error("Process for instance '${inst.instanceId}' terminated prematurely with exit code $exitCode")
        }
        if (isEndpointReady(host, inst.port, inst.token)) {
          ready = true
          break
        }
        try {
          Thread.sleep(pollMs)
        } catch (_: InterruptedException) {
          Thread.currentThread().interrupt()
          break
        }
      }
      if (!ready) {
        error("Parikshan: Instance '${inst.instanceId}' on port ${inst.port} did not complete readiness handshake before timeout ($timeoutMs ms). Check log at ${File(baseOutputDir, "logs/desktop-${inst.instanceId}.log").absolutePath}")
      }
    }
  }

  private fun isEndpointReady(host: String, port: Int, token: String): Boolean {
    var conn: HttpURLConnection? = null
    return try {
      val url = URL("http://$host:$port/")
      conn = url.openConnection() as HttpURLConnection
      conn.requestMethod = "POST"
      conn.setRequestProperty("Content-Type", "application/json")
      conn.setRequestProperty("Connection", "close")
      conn.doOutput = true
      conn.connectTimeout = 300
      conn.readTimeout = 300
      val probePayload = """{"type":"ping","id":"probe","token":"$token"}"""
      conn.outputStream.use { it.write(probePayload.toByteArray()) }
      val code = conn.responseCode
      code in 200..401
    } catch (_: Exception) {
      false
    } finally {
      runCatching { conn?.disconnect() }
    }
  }

  /**
   * Gracefully requests termination of a specific desktop instance, followed by process destruction.
   */
  fun stopInstance(
    instanceId: String,
    baseOutputDir: File? = null,
    host: String = "127.0.0.1"
  ) {
    val inMem = activeInstances.remove(instanceId)
    val sessionsDir = baseOutputDir?.let { File(it, "sessions") }
    val diskSession = sessionsDir?.let { SessionStore.readInstanceSession(it, instanceId) }

    val port = inMem?.port ?: diskSession?.port
    val token = inMem?.token ?: diskSession?.token ?: ""
    val pid = inMem?.process?.pid() ?: diskSession?.pid

    if (port != null) {
      runCatching {
        val json = """{"type":"stopRecording","id":"desktop-stop","sessionName":"any","token":"$token"}"""
        val url = URL("http://$host:$port/")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 500
        conn.readTimeout = 500
        conn.outputStream.use { it.write(json.toByteArray()) }
        conn.responseCode
      }
    }

    inMem?.process?.let { proc ->
      if (proc.isAlive) {
        proc.destroy()
        try {
          proc.waitFor(500, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {}
        if (proc.isAlive) {
          proc.destroyForcibly()
        }
      }
    }

    if (pid != null) {
      ProcessHandle.of(pid).ifPresent { handle ->
        if (handle.isAlive) {
          handle.destroy()
          runCatching { handle.onExit().get(2, TimeUnit.SECONDS) }.onFailure {
            if (handle.isAlive) handle.destroyForcibly()
          }
        }
      }
    }

    if (sessionsDir != null) {
      SessionStore.clearInstanceSession(sessionsDir, instanceId)
    }
  }

  /**
   * Terminates all active desktop application processes (in-memory and on-disk)
   * and clears session records.
   */
  fun stopAll(baseOutputDir: File? = null, host: String = "127.0.0.1") {
    val inMemoryIds = activeInstances.keys.toList()
    for (id in inMemoryIds) {
      stopInstance(id, baseOutputDir, host)
    }

    if (baseOutputDir != null) {
      val sessionsDir = File(baseOutputDir, "sessions")
      if (sessionsDir.exists() && sessionsDir.isDirectory) {
        val diskSessions = SessionStore.listInstanceSessions(sessionsDir)
        for (session in diskSessions) {
          val id = session.instanceId.ifBlank { session.target }
          if (id.isNotBlank()) {
            stopInstance(id, baseOutputDir, host)
          }
        }
      }
    }
  }

  fun getActiveInstances(): Map<String, LaunchedInstance> = activeInstances.toMap()

  fun isInstanceAlive(instanceId: String): Boolean {
    val instance = activeInstances[instanceId] ?: return false
    return instance.process.isAlive
  }

  fun resetForTesting() {
    activeInstances.clear()
  }
}
