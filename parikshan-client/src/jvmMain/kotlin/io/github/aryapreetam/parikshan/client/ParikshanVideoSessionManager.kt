package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.TestDriver
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

internal object ParikshanVideoSessionManager {
  private val lock = Mutex()
  @Volatile private var activeClassName: String? = null
  @Volatile private var activeDriver: TestDriver? = null
  private val shutdownHookInstalled = AtomicBoolean(false)

  suspend fun beforeScenario(
    driver: TestDriver,
    clientConfig: ParikshanClientConfig,
    config: ParikshanVideoConfig,
    className: String
  ) {
    if (!config.enabled) {
      return
    }

    if (shutdownHookInstalled.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(Thread({
        val target = System.getProperty("parikshan.target")?.lowercase()

        // Wasm target manages its own video lifecycle via WasmDriver's shutdown hook.
        // Sending StopRecording to WasmDriver is a no-op, and running coroutines
        // or HTTP I/O during JVM shutdown is unreliable — skip entirely.
        if (target == "wasm" || target == "web") {
          return@Thread
        }

        val clsName = activeClassName
        val drv = activeDriver
        activeClassName = null
        activeDriver = null

        if (clsName == null || drv == null) return@Thread

        if (target == "desktop" || target == null || target == "") {
           runCatching {
              val token = System.getProperty("parikshan.token") ?: ""
              val command = Command.StopRecording(id = nextId(), sessionName = clsName).apply { this.token = token }
              val json = ProtocolJson.encodeCommand(command)
              val url = URI("http://${clientConfig.host}:${clientConfig.port}/").toURL()
              val conn = url.openConnection() as HttpURLConnection
              conn.requestMethod = "POST"
              conn.setRequestProperty("Content-Type", "application/json")
              conn.doOutput = true
              conn.connectTimeout = 5000
              conn.readTimeout = 10000
              conn.outputStream.use { it.write(json.toByteArray()) }
              conn.inputStream.readBytes()
           }
        } else {
           runBlocking {
               runCatching { drv.send(Command.StopRecording(id = nextId(), sessionName = clsName)) }
           }
        }
      }, "parikshan-video-session-shutdown"))
    }

    lock.withLock {
      activeDriver = driver
      if (activeClassName == className) {
        return
      }
      val previous = activeClassName
      if (previous != null) {
        sendStop(driver = driver, sessionName = previous)
      }
      sendStart(driver = driver, className = className, config = config)
      activeClassName = className
    }
  }

  private suspend fun sendStart(
    driver: TestDriver,
    className: String,
    config: ParikshanVideoConfig
  ) {
    val outputPath = resolveOutputPath(className = className, outputDir = config.outputDir)
    val response =
      driver.send(
        Command.StartRecording(
          id = nextId(),
          sessionName = className,
          path = outputPath,
          fps = config.fps,
          showCursor = config.showCursor
        )
      )
    checkResponse(response, action = "startRecording($className)")
  }

  private suspend fun sendStop(
    driver: TestDriver,
    sessionName: String
  ) {
    val response =
      driver.send(
        Command.StopRecording(
          id = nextId(),
          sessionName = sessionName
        )
      )
    checkResponse(response, action = "stopRecording($sessionName)")
  }

  private fun resolveOutputPath(
    className: String,
    outputDir: String
  ): String {
    val simpleName = className.substringAfterLast('.').replace('$', '_')
    val rawTarget = System.getProperty("parikshan.target")
    val target = rawTarget?.lowercase()?.trim() ?: ""
    val ext = when (target) {
      "wasm", "web" -> "webm"
      "desktop", "ios", "android", "", "null" -> "mp4"
      else -> "webm"
    }

    val file = File(outputDir, "$simpleName.$ext").absoluteFile
    file.parentFile?.mkdirs()
    return file.absolutePath
  }

  private fun checkResponse(
    response: Response,
    action: String
  ) {
    if (response is Response.Error) {
      // Don't crash the test if video recording fails, just log it.
      System.err.println("WARN: $action failed: ${response.message}")
    }
  }

  private fun nextId(): String =
    "video-${Random.nextLong().toString(16)}"
}
