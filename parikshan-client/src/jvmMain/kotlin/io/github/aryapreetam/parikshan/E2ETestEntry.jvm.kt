package io.github.aryapreetam.parikshan

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import io.github.aryapreetam.parikshan.client.ParikshanVideoConfig
import io.github.aryapreetam.parikshan.client.ParikshanVideoSessionManager
import io.github.aryapreetam.parikshan.client.inferCallerClassName
import io.github.aryapreetam.parikshan.client.inferCallerMethodName
import kotlin.math.max

actual fun e2eTest(
  config: E2ETestConfig,
  block: suspend E2ETestScope.() -> Unit
) {
  runBlocking {
    val targetProperty = System.getProperty("parikshan.target")?.lowercase()
    val driver =
      when (targetProperty) {
        "sync" -> {
          val activeTargets = System.getProperty("parikshan.sync.targets")
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?: listOf("desktop", "wasm")
          val drivers = withContext(Dispatchers.IO) {
            activeTargets.map { t ->
              async {
                when (t) {
                  "desktop", "jvm" -> DesktopDriver(
                    host = System.getProperty("parikshan.desktop.host") ?: System.getProperty("parikshan.host") ?: "127.0.0.1",
                    port = System.getProperty("parikshan.desktop.port")?.toIntOrNull() ?: System.getProperty("parikshan.port")?.toIntOrNull() ?: 9877
                  )
                  "wasm", "web" -> WasmDriver.connect()
                  "android" -> {
                    val androidHost = System.getProperty("parikshan.android.host") ?: System.getProperty("parikshan.host") ?: "127.0.0.1"
                    val androidPort = System.getProperty("parikshan.android.port")?.toIntOrNull() ?: System.getProperty("parikshan.port")?.toIntOrNull() ?: 9879
                    AndroidRemoteDriver.connect(
                      io.github.aryapreetam.parikshan.client.ParikshanClientConfig(
                        host = androidHost,
                        port = androidPort
                      )
                    )
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
            }.awaitAll()
          }
          BroadcastDriver(drivers)
        }
        "android" -> AndroidRemoteDriver.connect()
        "ios" ->
          IosRemoteDriver.connect(
            IosDriverConfig(
              host = System.getProperty("parikshan.host") ?: "127.0.0.1",
              port = System.getProperty("parikshan.port")?.toIntOrNull() ?: 9878
            )
          )
        "wasm", "web" -> WasmDriver.connect()
        else ->
          DesktopDriver(
            host = System.getProperty("parikshan.host") ?: "127.0.0.1",
            port = System.getProperty("parikshan.port")?.toIntOrNull() ?: 9877
          )
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

    val userConfigStepDelay = if (config.stepDelayMs > 0L) config.stepDelayMs else null
    val globalSystemStepDelay = System.getProperty("parikshan.stepDelayMs")?.toLongOrNull()

    val platformDefaultDelay = when {
      isWasmActive && videoConfig.enabled -> 10L
      isWasmActive -> 0L
      isIosActive -> 50L
      isDesktopActive && videoConfig.enabled -> 10L
      else -> 0L
    }


    val effectiveStepDelay = userConfigStepDelay
      ?: globalSystemStepDelay
      ?: platformDefaultDelay

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
}

actual fun e2eTest(
  instances: List<String>,
  config: E2ETestConfig,
  block: suspend E2EMultiInstanceScope.(E2EMultiInstanceScope) -> Unit
) {
  require(instances.isNotEmpty()) { "Multi-instance E2E test requires at least one instance identifier" }
  require(instances.distinct().size == instances.size) {
    "Duplicate instance identifier detected in test declaration: $instances"
  }

  runBlocking {
    val callerClassName = inferCallerClassName()
    val callerMethodName = inferCallerMethodName()
    val videoConfig = ParikshanVideoConfig.fromSystemProperties()

    val drivers = withContext(Dispatchers.IO) {
      instances.associateWith { name ->
        val (target, context) = resolveInstanceContext(name)
        createDriverForInstance(target, context)
      }
    }

    drivers.forEach { (name, driver) ->
      val port = (driver as? DesktopDriver)?.activePort
        ?: (driver as? IosRemoteDriver)?.context?.port
        ?: (driver as? AndroidRemoteDriver)?.context?.port
        ?: (driver as? WasmDriver)?.context?.port
        ?: 9877
      val clientConfig = io.github.aryapreetam.parikshan.client.ParikshanClientConfig(
        host = "127.0.0.1",
        port = port
      )
      ParikshanVideoSessionManager.beforeScenario(
        driver = driver,
        clientConfig = clientConfig,
        config = videoConfig,
        className = callerClassName,
        methodName = "${callerMethodName}_$name"
      )
    }

    val userConfigStepDelay = if (config.stepDelayMs > 0L) config.stepDelayMs else null
    val globalSystemStepDelay = System.getProperty("parikshan.stepDelayMs")?.toLongOrNull()
    val effectiveStepDelay = userConfigStepDelay ?: globalSystemStepDelay ?: 0L
    val effectiveConfig = config.copy(stepDelayMs = effectiveStepDelay)

    try {
      e2eTest(
        drivers = drivers,
        config = effectiveConfig,
        block = { this.block(this) }
      )
    } finally {
      drivers.forEach { (name, driver) ->
        ParikshanVideoSessionManager.afterScenario(
          driver = driver,
          config = videoConfig,
          className = callerClassName,
          methodName = "${callerMethodName}_$name"
        )
      }
    }
  }
}

private val sessionJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

@kotlinx.serialization.Serializable
private data class InstanceSessionDescriptor(
  val instanceId: String = "",
  val target: String = "desktop",
  val port: Int = 9877,
  val host: String = "127.0.0.1",
  val sessionToken: String = "",
  val dataDir: String? = null,
  val pid: Long? = null,
  val timestamp: Long = 0L
)

private fun resolveSessionDirectory(): java.io.File {
  val sessionsDirProp = System.getProperty("parikshan.sessionsDir")
  if (!sessionsDirProp.isNullOrBlank()) {
    return java.io.File(sessionsDirProp)
  }
  val projectRootDirProp = System.getProperty("parikshan.projectRootDir")
  val baseDir = if (!projectRootDirProp.isNullOrBlank()) {
    java.io.File(projectRootDirProp, "build/parikshan")
  } else {
    java.io.File(System.getProperty("user.dir"), "build/parikshan")
  }
  return java.io.File(baseDir, "sessions")
}

private fun resolveInstanceContext(name: String): Pair<String, ParikshanTestContext> {
  val sessionsDir = resolveSessionDirectory()
  val instanceFile = java.io.File(sessionsDir, "$name.json")
  if (instanceFile.exists()) {
    val text = runCatching { instanceFile.readText() }.getOrNull().orEmpty()
    val parsed = runCatching { sessionJson.decodeFromString<InstanceSessionDescriptor>(text) }.getOrNull()
    if (parsed != null) {
      val target = parsed.target.ifBlank { "desktop" }
      val ctx = ParikshanTestContext(
        instanceId = parsed.instanceId.ifBlank { name },
        host = parsed.host.ifBlank { "127.0.0.1" },
        port = parsed.port,
        sessionToken = parsed.sessionToken,
        dataDir = parsed.dataDir
      )
      return target to ctx
    }
  }

  val target = System.getProperty("parikshan.instance.$name.target")
    ?: System.getProperty("parikshan.target")
    ?: "desktop"
  val host = System.getProperty("parikshan.instance.$name.host")
    ?: System.getProperty("parikshan.host")
    ?: "127.0.0.1"
  val explicitPort = System.getProperty("parikshan.instance.$name.port")?.toIntOrNull()
  val port = explicitPort
    ?: if (name == "desktop") {
      System.getProperty("parikshan.port")?.toIntOrNull() ?: 9877
    } else if (target == "wasm" || target == "web") {
      System.getProperty("parikshan.wasm.port")?.toIntOrNull()
        ?: System.getProperty("parikshan.port")?.toIntOrNull()
        ?: 8080
    } else {
      error(
        "No session descriptor or port configuration found for actor '$name'. " +
          "Expected session descriptor at '${instanceFile.absolutePath}' or system property 'parikshan.instance.$name.port'. " +
          "Ensure the multi-instance cluster was launched via '--instances' or 'parikshan.instances'."
      )
    }
  val token = System.getProperty("parikshan.instance.$name.token")
    ?: System.getProperty("parikshan.token")
    ?: ""
  val dataDir = System.getProperty("parikshan.instance.$name.dataDir")
    ?: System.getProperty("parikshan.data.dir")

  val ctx = ParikshanTestContext(
    instanceId = name,
    host = host,
    port = port,
    sessionToken = token,
    dataDir = dataDir
  )
  return target to ctx
}

private suspend fun createDriverForInstance(target: String, context: ParikshanTestContext): TestDriver {
  return when (target.lowercase()) {
    "desktop", "jvm" -> DesktopDriver(context)
    "wasm", "web" -> WasmDriver.connect(context)
    "android" -> AndroidRemoteDriver.connect(context)
    "ios" -> IosRemoteDriver.connect(context)
    else -> error("Unsupported target '$target' for instance '${context.instanceId}'")
  }
}

