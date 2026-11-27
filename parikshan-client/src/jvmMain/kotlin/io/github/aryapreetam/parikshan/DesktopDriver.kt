@file:OptIn(InternalParikshanApi::class)

package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * JVM-side driver that talks to the Parikshan server running inside a Desktop app.
 */
internal class DesktopDriver(
  private val host: String = "127.0.0.1",
  @Volatile private var port: Int = 9877,
  private val sessionToken: String = System.getProperty("parikshan.token") ?: "",
  val context: ParikshanTestContext? = null
) : TestDriver {

  constructor(context: ParikshanTestContext) : this(
    host = context.host,
    port = context.port,
    sessionToken = context.sessionToken,
    context = context
  )

  override val targetPlatform: String = "desktop"
  val activePort: Int get() = port

  init {
    if (context == null) {
      val explicitPortProp = System.getProperty("parikshan.port")?.toIntOrNull()
      if (explicitPortProp != null) {
        port = explicitPortProp
      } else {
        runCatching {
          val manifestPath = System.getProperty(DESKTOP_LAUNCH_MANIFEST_PROPERTY)
          if (!manifestPath.isNullOrBlank()) {
            val file = File(manifestPath)
            if (file.exists()) {
              val props = Properties().apply {
                file.inputStream().use(::load)
              }
              val activePort = props.getProperty("port")?.toIntOrNull()
              if (activePort != null) {
                port = activePort
              }
            }
          }
        }
      }
    }
  }

  override fun updateVirtualCursor(x: Double, y: Double) {
    io.github.aryapreetam.parikshan.client.ParikshanVideoSessionManager.updateVirtualCursor(x, y)
  }

  override fun validateAndNormalizePickerPaths(paths: List<String>): List<String> =
    PickerPathResolver.resolveAndValidatePaths(paths)

  override suspend fun send(command: Command): Response {
    // SECURITY: Sign the command with the global session token
    command.token = sessionToken
    
    val json = ProtocolJson.encodeCommand(command)
    
    val maxAttempts = 30
    var lastError: Exception? = null
    repeat(maxAttempts) { attempt ->
      try {
        val responseJson = httpPost(json)
        return ProtocolJson.decodeResponse(responseJson)
      } catch (e: Exception) {
        lastError = e
        if (attempt < maxAttempts - 1) kotlinx.coroutines.delay(1000)
      }
    }
    throw lastError ?: RuntimeException("Failed to send command after $maxAttempts attempts")
  }

  override suspend fun close() {
    // In Gradle-managed Desktop E2E, the app process is managed by the plugin.
    // Sending Shutdown here would kill the app after the first test finishes,
    // causing subsequent tests to fail with ConnectException.
    /*
    try {
        send(Command.Shutdown(id = "shutdown-desktop"))
    } catch (e: Exception) { // ignore // }
    */
  }

  override suspend fun relaunchApp() {
    if (context != null) {
      throw UnsupportedOperationException(
        "relaunchApp() on multi-instance Desktop actor '${context.instanceId}' is not supported in Parikshan 0.1.0. " +
          "Reset application state via UI/API, or use Web (Wasm) for per-actor relaunch."
      )
    }

    // 1. Coordinate pause
    io.github.aryapreetam.parikshan.client.ParikshanVideoSessionManager.pauseRecording()

    try {
      val manifestFile = desktopLaunchManifestFile()
      val properties = manifestFile.loadProperties()

      val oldPid = properties.getProperty("pid")?.toLongOrNull()

      // 2. Resolve a dynamic available port for the new server
      val newPort = PortAllocator.allocate()

      // 3. Kill the old process first to ensure only one window is visible at a time.
      //    Dynamic port allocation prevents port collisions regardless of kill order.
      if (oldPid != null) {
        destroyProcessByPid(oldPid)
      }

      // 4. Create a temporary properties copy with the new port
      val tempProperties = java.util.Properties().apply {
        putAll(properties)
        setProperty("port", newPort.toString())
      }

      // 5. Launch the new desktop process (with focus disabled)
      val process = launchDesktopProcess(tempProperties)

      // 6. Poll the new port until it successfully responds to ping (wait up to 10 seconds)
      var isConnected = false
      val deadline = System.currentTimeMillis() + 10_000L
      while (System.currentTimeMillis() <= deadline) {
        if (pingServer(host, newPort)) {
          isConnected = true
          break
        }
        kotlinx.coroutines.delay(200)
      }

      if (!isConnected) {
        process.destroyForcibly()
        throw IllegalStateException("Could not reconnect to new Parikshan desktop server on port $newPort after relaunch")
      }

      // 7. Update driver's active port to the new port
      port = newPort

      // 8. Commit properties (updating pid and port) to the manifest file
      properties["pid"] = process.pid().toString()
      properties["port"] = newPort.toString()
      manifestFile.storeProperties(properties)
    } finally {
      // 3. Resume recording with the active driver state
      io.github.aryapreetam.parikshan.client.ParikshanVideoSessionManager.resumeRecording(this)
    }
  }

  private fun findAvailablePort(): Int {
    return PortAllocator.allocate()
  }

  private fun pingServer(host: String, port: Int): Boolean {
    return try {
      val url = URI("http://$host:$port/").toURL()
      val conn = url.openConnection() as HttpURLConnection
      conn.requestMethod = "POST"
      conn.setRequestProperty("Content-Type", "application/json")
      conn.setRequestProperty("Connection", "close")
      conn.doOutput = true
      conn.connectTimeout = 1000
      conn.readTimeout = 1000
      val command = Command.Ping(id = "ping-temp").apply {
        token = sessionToken
      }
      val json = ProtocolJson.encodeCommand(command)
      conn.outputStream.use { it.write(json.toByteArray()) }
      val code = conn.responseCode
      conn.inputStream.readBytes()
      code == 200
    } catch (e: Exception) {
      false
    }
  }

  private fun destroyProcessByPid(pid: Long) {
    val handle = ProcessHandle.of(pid).orElse(null) ?: return
    terminateProcessTree(handle, timeoutSeconds = 5L)
  }

  private fun terminateProcessTree(handle: ProcessHandle, timeoutSeconds: Long) {
    if (!handle.isAlive) {
      return
    }
    val descendants = runCatching { handle.descendants().toList() }.getOrDefault(emptyList())
    descendants.forEach { runCatching { it.destroy() } }
    runCatching { handle.destroy() }
    runCatching {
      handle.onExit().get(timeoutSeconds, TimeUnit.SECONDS)
    }.onFailure {
      descendants.forEach { child ->
        runCatching { if (child.isAlive) child.destroyForcibly() }
      }
      runCatching { if (handle.isAlive) handle.destroyForcibly() }
    }
    descendants.forEach { child ->
      runCatching { if (child.isAlive) child.destroyForcibly() }
    }
  }

  private fun httpPost(body: String): String {
    val url = URI("http://$host:$port/").toURL()
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Connection", "close")
    conn.doOutput = true
    conn.connectTimeout = 5000
    conn.readTimeout = 30000
    
    conn.outputStream.use { it.write(body.toByteArray()) }
    
    if (conn.responseCode != 200) {
        val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
        error("Desktop server returned error: $error")
    }
    
    return conn.inputStream.bufferedReader().readText()
  }

  private fun desktopLaunchManifestFile(): File {
    val manifestPath =
      System.getProperty(DESKTOP_LAUNCH_MANIFEST_PROPERTY)?.takeIf { it.isNotBlank() }
        ?: error(
          "Missing system property '$DESKTOP_LAUNCH_MANIFEST_PROPERTY'. " +
            "The Gradle plugin must provide a desktop launch manifest before relaunchApp() can run."
        )
    return File(manifestPath)
  }

  private fun File.loadProperties(): Properties {
    check(exists()) {
      "Desktop relaunch manifest does not exist: $absolutePath"
    }
    return Properties().also { properties ->
      inputStream().use(properties::load)
    }
  }

  private fun File.storeProperties(properties: Properties) {
    parentFile?.mkdirs()
    outputStream().use { output ->
      properties.store(output, "Parikshan desktop launch state")
    }
  }

  private fun destroyProcessFromManifest(properties: Properties) {
    val pid = properties.getProperty("pid")?.toLongOrNull() ?: return
    val handle = ProcessHandle.of(pid).orElse(null) ?: return
    terminateProcessTree(handle, timeoutSeconds = 10L)
  }

  private fun launchDesktopProcess(
    properties: Properties
  ): Process {
    val javaExecutable = properties.required("javaExecutable")
    val jarPath = properties.required("jar")
    val appMainClass = properties.required("appMainClass")
    val launchHost = properties.getProperty("host") ?: host
    val launchPort = properties.getProperty("port") ?: port.toString()
    val launchToken = properties.getProperty("token") ?: sessionToken
    val windowTitle = properties.getProperty("windowTitle")?.takeIf { it.isNotBlank() }
    val background = properties.getProperty("background") ?: "false"
    val appArgs = properties.readIndexedValues("appArg")
    val logFile = File(properties.required("logFile"))
    logFile.parentFile?.mkdirs()

    val jarFile = File(jarPath)
    val hasPremain = runCatching {
      java.util.jar.JarFile(jarFile).use { jf ->
        jf.manifest?.mainAttributes?.getValue("Premain-Class") != null
      }
    }.getOrDefault(false)

    val command =
      buildList {
        add(javaExecutable)
        if (hasPremain) {
          add("-javaagent:${jarFile.absolutePath}")
        }
        add("-Dparikshan.host=$launchHost")
        add("-Dparikshan.port=$launchPort")
        add("-Dparikshan.token=$launchToken")
        add("-Dparikshan.desktop.focus=false")
        add("-Dparikshan.desktop.appMainClass=$appMainClass")
        add("-Dapple.awt.takeFocusOnShow=false")
        if (background == "true") {
            add("-Dparikshan.background=true")
        }
        val dataDir = context?.dataDir ?: System.getProperty("parikshan.data.dir")
        if (!dataDir.isNullOrBlank()) {
          add("-Dparikshan.data.dir=$dataDir")
        }
        val wX = context?.windowPosition?.first?.toString() ?: System.getProperty("parikshan.desktop.windowX")
        val wY = context?.windowPosition?.second?.toString() ?: System.getProperty("parikshan.desktop.windowY")
        val wW = System.getProperty("parikshan.desktop.windowWidth")
        val wH = System.getProperty("parikshan.desktop.windowHeight")
        if (!wX.isNullOrEmpty()) add("-Dparikshan.desktop.windowX=$wX")
        if (!wY.isNullOrEmpty()) add("-Dparikshan.desktop.windowY=$wY")
        if (!wW.isNullOrEmpty()) add("-Dparikshan.desktop.windowWidth=$wW")
        if (!wH.isNullOrEmpty()) add("-Dparikshan.desktop.windowHeight=$wH")

        windowTitle?.let { add("-Dparikshan.desktop.windowTitle=$it") }
        add("-cp")
        add(jarPath)
        add("io.github.aryapreetam.parikshan.server.DesktopAppLauncher")
        addAll(appArgs)
      }

    val pb = ProcessBuilder(command)
    pb.environment()["NSAppSleepDisabled"] = "YES"
    return pb
      .redirectErrorStream(true)
      .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
      .start()
  }

  private fun Properties.required(name: String): String =
    getProperty(name)?.takeIf { it.isNotBlank() }
      ?: error("Desktop relaunch manifest is missing required property '$name'")

  private fun Properties.readIndexedValues(prefix: String): List<String> {
    val count = getProperty("${prefix}Count")?.toIntOrNull() ?: 0
    return (0 until count).map { index -> getProperty("$prefix.$index").orEmpty() }
  }

  private companion object {
    private const val DESKTOP_LAUNCH_MANIFEST_PROPERTY = "parikshan.desktop.launchManifest"
  }
}
