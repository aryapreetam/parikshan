package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.TestDriver
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.Response
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

internal object ParikshanVideoSessionManager {
  private val lock = Mutex()
  private val shutdownHookInstalled = AtomicBoolean(false)
  private var activeClassName: String? = null

  suspend fun beforeScenario(
    driver: TestDriver,
    clientConfig: ParikshanClientConfig,
    config: ParikshanVideoConfig,
    className: String
  ) {
    if (!config.enabled) {
      return
    }
    installShutdownHookIfNeeded(clientConfig = clientConfig, config = config)

    lock.withLock {
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

  private fun installShutdownHookIfNeeded(
    clientConfig: ParikshanClientConfig,
    config: ParikshanVideoConfig
  ) {
    if (!shutdownHookInstalled.compareAndSet(false, true)) {
      return
    }

    Runtime.getRuntime().addShutdownHook(
      Thread {
        val className =
          runBlocking {
            lock.withLock {
            activeClassName.also { activeClassName = null }
            }
          } ?: return@Thread

        runBlocking {
          runCatching {
            val client = ParikshanClient(clientConfig)
            client.connect()
            client.send(
              Command.StopRecording(
                id = nextId(),
                sessionName = className
              )
            )
            client.disconnect()
          }
        }
      }
    )
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
    val file = File(outputDir, "$simpleName.mp4")
    file.parentFile?.mkdirs()
    return file.path
  }

  private fun checkResponse(
    response: Response,
    action: String
  ) {
    if (response is Response.Error) {
      throw IllegalStateException("$action failed: ${response.message}")
    }
  }

  private fun nextId(): String =
    "video-${Random.nextLong().toString(16)}"
}
