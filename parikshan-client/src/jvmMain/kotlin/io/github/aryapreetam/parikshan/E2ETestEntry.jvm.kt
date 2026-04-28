package io.github.aryapreetam.parikshan

import kotlinx.coroutines.runBlocking
import io.github.aryapreetam.parikshan.client.ParikshanVideoConfig
import io.github.aryapreetam.parikshan.client.ParikshanVideoSessionManager
import kotlin.math.max

actual fun e2eTest(
  config: E2ETestConfig,
  block: suspend E2ETestScope.() -> Unit
) {
  runBlocking {
    val driver =
      when (val target = System.getProperty("parikshan.target")?.lowercase()) {
        "android" -> AndroidRemoteDriver.connect()
        "desktop", null, "" ->
          DesktopDriver(
            host = System.getProperty("parikshan.host") ?: "127.0.0.1",
            port = System.getProperty("parikshan.port")?.toIntOrNull() ?: 9877
          )
        "ios" ->
          IosRemoteDriver.connect(
            IosDriverConfig(
              host = System.getProperty("parikshan.host") ?: "127.0.0.1",
              port = System.getProperty("parikshan.port")?.toIntOrNull() ?: 9878
            )
          )
        "wasm", "web" -> WasmDriver.connect()
        else -> error("Unsupported Parikshan target '$target'")
      }

    val callerClassName = inferCallerClassName()
    val videoConfig = ParikshanVideoConfig.fromSystemProperties()
    val clientConfig = io.github.aryapreetam.parikshan.client.ParikshanClientConfig(
      host = System.getProperty("parikshan.host") ?: "127.0.0.1",
      port = System.getProperty("parikshan.port")?.toIntOrNull() ?: 9877
    )

    ParikshanVideoSessionManager.beforeScenario(
      driver = driver,
      clientConfig = clientConfig,
      config = videoConfig,
      className = callerClassName
    )

    val effectiveConfig = if (videoConfig.enabled) {
      config.copy(commandDelayMs = max(config.commandDelayMs, videoConfig.stepDelayMs))
    } else {
      config
    }

    e2eTest(
      driver = driver,
      config = effectiveConfig,
      block = block
    )
  }
}

private fun inferCallerClassName(): String {
  val stack = Throwable().stackTrace

  for (element in stack) {
    val className = element.className
    if (!className.startsWith("io.github.aryapreetam.parikshan.") &&
      !className.startsWith("kotlin.") &&
      !className.startsWith("kotlinx.coroutines.") &&
      !className.startsWith("org.junit.") &&
      !className.startsWith("org.gradle.") &&
      !className.startsWith("worker.") &&
      !className.startsWith("sun.reflect.") &&
      !className.startsWith("java.")
    ) {
      return className.substringAfterLast('.')
    }
  }

  return "unknown_test"
}
