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
        "sync" -> {
          val activeTargets = System.getProperty("parikshan.sync.targets")
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?: listOf("desktop", "wasm")
          val drivers = activeTargets.map { t ->
            when (t) {
              "desktop" -> DesktopDriver(
                host = System.getProperty("parikshan.desktop.host") ?: System.getProperty("parikshan.host") ?: "127.0.0.1",
                port = System.getProperty("parikshan.desktop.port")?.toIntOrNull() ?: System.getProperty("parikshan.port")?.toIntOrNull() ?: 9877
              )
              "wasm", "web" -> WasmDriver.connect()
              "android" -> {
                val oldHost = System.getProperty("parikshan.host")
                val oldPort = System.getProperty("parikshan.port")
                val androidHost = System.getProperty("parikshan.android.host") ?: oldHost
                val androidPort = System.getProperty("parikshan.android.port") ?: oldPort
                if (androidHost != null) System.setProperty("parikshan.host", androidHost)
                if (androidPort != null) System.setProperty("parikshan.port", androidPort)
                try {
                  AndroidRemoteDriver.connect()
                } finally {
                  if (oldHost != null) System.setProperty("parikshan.host", oldHost) else System.clearProperty("parikshan.host")
                  if (oldPort != null) System.setProperty("parikshan.port", oldPort) else System.clearProperty("parikshan.port")
                }
              }
              "ios" -> {
                IosRemoteDriver.connect(
                  IosDriverConfig(
                    host = System.getProperty("parikshan.ios.host") ?: System.getProperty("parikshan.host") ?: "127.0.0.1",
                    port = System.getProperty("parikshan.ios.port")?.toIntOrNull() ?: System.getProperty("parikshan.port")?.toIntOrNull() ?: 9878
                  )
                )
              }
              else -> error("Unsupported target in sync mode: $t")
            }
          }
          BroadcastDriver(drivers)
        }
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
    val callerMethodName = inferCallerMethodName()
    val videoConfig = ParikshanVideoConfig.fromSystemProperties()
    val clientConfig = io.github.aryapreetam.parikshan.client.ParikshanClientConfig(
      host = System.getProperty("parikshan.host") ?: "127.0.0.1",
      port = System.getProperty("parikshan.port")?.toIntOrNull() ?: 9877
    )

    ParikshanVideoSessionManager.beforeScenario(
      driver = driver,
      clientConfig = clientConfig,
      config = videoConfig,
      className = callerClassName,
      methodName = callerMethodName
    )

    val target = System.getProperty("parikshan.target")?.lowercase()
    val isWasmActive = target == "wasm" || target == "web" || (target == "sync" && System.getProperty("parikshan.sync.targets")?.contains("wasm") == true)
    val isIosActive = target == "ios" || (target == "sync" && System.getProperty("parikshan.sync.targets")?.contains("ios") == true)
    val isDesktopActive = target == "desktop" || target == null || target == "" || (target == "sync" && System.getProperty("parikshan.sync.targets")?.contains("desktop") == true)

    val defaultDelay = if (isWasmActive) {
      max(config.commandDelayMs, 150L)
    } else if (isIosActive) {
      max(config.commandDelayMs, 300L)
    } else if (isDesktopActive && videoConfig.enabled) {
      max(config.commandDelayMs, 10L)
    } else {
      config.commandDelayMs
    }

    val effectiveConfig = if (videoConfig.enabled) {
      config.copy(commandDelayMs = max(defaultDelay, videoConfig.stepDelayMs))
    } else {
      config.copy(commandDelayMs = defaultDelay)
    }

    try {
      e2eTest(
        driver = driver,
        config = effectiveConfig,
        block = block
      )
    } finally {
      ParikshanVideoSessionManager.afterScenario(
        driver = driver,
        config = videoConfig,
        className = callerClassName,
        methodName = callerMethodName
      )
    }
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

private fun inferCallerMethodName(): String {
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
      return element.methodName
    }
  }

  return "unknown_method"
}
