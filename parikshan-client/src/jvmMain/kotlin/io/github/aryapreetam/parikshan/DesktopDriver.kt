package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.client.ParikshanClient
import io.github.aryapreetam.parikshan.client.ParikshanClientConfig
import io.github.aryapreetam.parikshan.client.ParikshanVideoConfig
import io.github.aryapreetam.parikshan.client.ParikshanVideoSessionManager
import io.github.aryapreetam.parikshan.client.ParikshanWasmConfig
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
    val target = ParikshanTarget.fromSystemProperty()

    val driver =
      when (target) {
        ParikshanTarget.Desktop -> {
          val effectiveClientConfig = clientConfig.withDesktopSystemPropertyOverrides()
          val desktopDriver = DesktopDriver.connect(config = effectiveClientConfig)
          ParikshanVideoSessionManager.beforeScenario(
            driver = desktopDriver,
            clientConfig = effectiveClientConfig,
            config = videoConfig,
            className = callerClassName
          )
          desktopDriver
        }

        ParikshanTarget.Wasm ->
          WasmDriver.connect(
            className = callerClassName,
            wasmConfig = ParikshanWasmConfig.fromSystemProperties(),
            videoConfig = videoConfig
          )

        ParikshanTarget.Ios ->
          IosRemoteDriver.connect() // Reads host/port from system properties
      }

    val effectiveConfig =
      if (videoConfig.enabled && config.commandDelayMs == 0L) {
        config.copy(commandDelayMs = videoConfig.stepDelayMs)
      } else {
        config
      }
    e2eTest(driver = driver, config = effectiveConfig, block = block)
  }
}

private fun ParikshanClientConfig.withDesktopSystemPropertyOverrides(): ParikshanClientConfig {
  val defaults = ParikshanClientConfig()
  val hostOverride =
    System.getProperty("parikshan.host")
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
  val portOverride = System.getProperty("parikshan.port")?.toIntOrNull()
  val pathOverride =
    System.getProperty("parikshan.path")
      ?.trim()
      ?.takeIf { it.isNotEmpty() }

  return copy(
    host = if (host == defaults.host) hostOverride ?: host else host,
    port = if (port == defaults.port) portOverride ?: port else port,
    path = if (path == defaults.path) pathOverride ?: path else path
  )
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

private enum class ParikshanTarget {
  Desktop,
  Wasm,
  Ios;

  companion object {
    fun fromSystemProperty(): ParikshanTarget {
      return when (System.getProperty("parikshan.target")?.trim()?.lowercase()) {
        null, "", "desktop" -> Desktop
        "wasm", "web" -> Wasm
        "ios" -> Ios
        else -> error("Unsupported parikshan.target value. Expected 'desktop', 'wasm', or 'ios'.")
      }
    }
  }
}
