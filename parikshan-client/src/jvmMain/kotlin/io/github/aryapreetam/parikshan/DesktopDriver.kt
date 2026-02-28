package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.client.ParikshanClient
import io.github.aryapreetam.parikshan.client.ParikshanClientConfig
import io.github.aryapreetam.parikshan.client.ParikshanVideoConfig
import io.github.aryapreetam.parikshan.client.ParikshanVideoSessionManager
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.Response
import kotlinx.coroutines.runBlocking

class DesktopDriver private constructor(
  private val client: ParikshanClient
) : TestDriver {
  override suspend fun send(command: Command): Response =
    client.send(command)

  override suspend fun close() {
    client.disconnect()
  }

  companion object {
    suspend fun connect(config: ParikshanClientConfig = ParikshanClientConfig()): DesktopDriver {
      val client = ParikshanClient(config)
      client.connect()
      return DesktopDriver(client)
    }
  }
}

fun e2eTest(
  config: E2ETestConfig = E2ETestConfig(),
  clientConfig: ParikshanClientConfig = ParikshanClientConfig(),
  block: suspend E2ETestScope.() -> Unit
) {
  runBlocking {
    val callerClassName = inferCallerClassName()
    val videoConfig = ParikshanVideoConfig.fromSystemProperties()
    val driver = DesktopDriver.connect(config = clientConfig)
    ParikshanVideoSessionManager.beforeScenario(
      driver = driver,
      clientConfig = clientConfig,
      config = videoConfig,
      className = callerClassName
    )
    val effectiveConfig =
      if (videoConfig.enabled && config.commandDelayMs == 0L) {
        config.copy(commandDelayMs = videoConfig.stepDelayMs)
      } else {
        config
      }
    e2eTest(driver = driver, config = effectiveConfig, block = block)
  }
}

private fun inferCallerClassName(): String {
  val stack =
    Throwable().stackTrace.asSequence()
      .map { it.className }
      .filterNot { className ->
        className.startsWith("io.github.aryapreetam.parikshan.") ||
          className.startsWith("kotlin.") ||
          className.startsWith("kotlinx.coroutines.") ||
          className.startsWith("org.junit.") ||
          className.startsWith("org.gradle.") ||
          className.startsWith("worker.") ||
          className.startsWith("sun.reflect.") ||
          className.startsWith("java.")
      }
      .firstOrNull()

  return stack ?: "unknown.test.Class"
}
