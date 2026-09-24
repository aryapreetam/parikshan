package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.client.ParikshanClientConfig
import io.github.aryapreetam.parikshan.client.ParikshanVideoConfig
import io.github.aryapreetam.parikshan.client.ParikshanVideoSessionManager
import io.github.aryapreetam.parikshan.client.inferCallerClassName
import io.github.aryapreetam.parikshan.client.inferCallerMethodName
import kotlinx.coroutines.runBlocking

actual fun e2eTest(
  config: E2ETestConfig,
  block: suspend E2ETestScope.() -> Unit
) {
  val target = System.getProperty("parikshan.target")?.lowercase()
  if (!target.isNullOrBlank()) {
    runBlocking {
      val host = System.getProperty("parikshan.android.host") ?: System.getProperty("parikshan.host") ?: "127.0.0.1"
      val port = System.getProperty("parikshan.android.port")?.toIntOrNull() ?: System.getProperty("parikshan.port")?.toIntOrNull() ?: 9879
      val driver: TestDriver = AndroidRemoteDriver.connect(
        ParikshanClientConfig(
          host = host,
          port = port
        )
      )

      val callerClassName = inferCallerClassName()
      val callerMethodName = inferCallerMethodName()
      val videoConfig = ParikshanVideoConfig.fromSystemProperties()
      val clientConfig = ParikshanClientConfig(
        host = System.getProperty("parikshan.host") ?: "127.0.0.1",
        port = System.getProperty("parikshan.port")?.toIntOrNull() ?: 9879
      )

      ParikshanVideoSessionManager.beforeScenario(
        driver = driver,
        clientConfig = clientConfig,
        config = videoConfig,
        className = callerClassName,
        methodName = callerMethodName
      )

      val userConfigStepDelay = if (config.stepDelayMs > 0L) config.stepDelayMs else null
      val globalSystemStepDelay = System.getProperty("parikshan.stepDelayMs")?.toLongOrNull()

      val effectiveStepDelay = userConfigStepDelay
        ?: globalSystemStepDelay
        ?: 0L

      val effectiveConfig = config.copy(stepDelayMs = effectiveStepDelay)

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
  } else {
    error("Parikshan E2E tests run via the JVM test runner. Use ./gradlew e2eAndroidTest")
  }
}
