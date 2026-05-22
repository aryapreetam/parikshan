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
class DesktopDriver(
  private val host: String = "127.0.0.1",
  private val port: Int = 9877,
  private val sessionToken: String = System.getProperty("parikshan.token") ?: ""
) : TestDriver {

  override suspend fun send(command: Command): Response {
    // SECURITY: Sign the command with the global session token
    command.token = sessionToken
    
    val json = ProtocolJson.encodeCommand(command)
    
    var lastError: Exception? = null
    repeat(30) { attempt ->
      try {
        val responseJson = httpPost(json)
        return ProtocolJson.decodeResponse(responseJson)
      } catch (e: Exception) {
        lastError = e
        if (attempt < 29) kotlinx.coroutines.delay(1000)
      }
    }
    throw lastError ?: RuntimeException("Failed to send command after 30 attempts")
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
    val manifestFile = desktopLaunchManifestFile()
    val properties = manifestFile.loadProperties()

    destroyProcessFromManifest(properties)
    val process = launchDesktopProcess(properties)
    properties["pid"] = process.pid().toString()
    manifestFile.storeProperties(properties)

    val ping = send(Command.Ping(id = "desktop-relaunch-ping"))
    if (ping !is Response.Ok) {
      throw IllegalStateException("Could not reconnect to Parikshan desktop server after relaunch: $ping")
    }
  }

  private fun httpPost(body: String): String {
    val url = URI("http://$host:$port/").toURL()
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
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
    if (!handle.isAlive) {
      return
    }

    handle.destroy()
    runCatching {
      handle.onExit().get(10, TimeUnit.SECONDS)
    }.onFailure {
      if (handle.isAlive) {
        handle.destroyForcibly()
      }
    }
  }

  private fun launchDesktopProcess(properties: Properties): Process {
    val javaExecutable = properties.required("javaExecutable")
    val jarPath = properties.required("jar")
    val appMainClass = properties.required("appMainClass")
    val launchHost = properties.getProperty("host") ?: host
    val launchPort = properties.getProperty("port") ?: port.toString()
    val launchToken = properties.getProperty("token") ?: sessionToken
    val windowTitle = properties.getProperty("windowTitle")?.takeIf { it.isNotBlank() }
    val appArgs = properties.readIndexedValues("appArg")
    val logFile = File(properties.required("logFile"))
    logFile.parentFile?.mkdirs()

    val command =
      buildList {
        add(javaExecutable)
        add("-Dparikshan.host=$launchHost")
        add("-Dparikshan.port=$launchPort")
        add("-Dparikshan.token=$launchToken")
        add("-Dparikshan.desktop.appMainClass=$appMainClass")
        windowTitle?.let { add("-Dparikshan.desktop.windowTitle=$it") }
        add("-cp")
        add(jarPath)
        add("io.github.aryapreetam.parikshan.server.ParikshanDesktopLauncher")
        addAll(appArgs)
      }

    return ProcessBuilder(command)
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
