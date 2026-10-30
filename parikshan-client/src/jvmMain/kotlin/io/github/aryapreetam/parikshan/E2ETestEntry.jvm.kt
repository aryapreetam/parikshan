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
  val executionMode = System.getProperty("parikshan.executionMode")?.lowercase()
  val targetProperty = System.getProperty("parikshan.target")?.lowercase()
  val isMultiTarget = executionMode == "multi-target" || targetProperty == "multi-target"
  if (isMultiTarget && targetProperty != "sync") {
    abortTestExecution("Single-instance test skipped during multi-target run.")
  }

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
  val executionMode = System.getProperty("parikshan.executionMode")?.lowercase()
  val targetProperty = System.getProperty("parikshan.target")?.lowercase()
  if (executionMode == "multi-target" || targetProperty == "multi-target") {
    abortTestExecution("Multi-instance test skipped during multi-target run.")
  }

  runMultiInstanceTest(instances, config, block)
}

private fun runMultiInstanceTest(
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

actual fun e2eTest(
  instances: Map<String, Target>,
  config: E2ETestConfig,
  block: suspend E2EMultiInstanceScope.(E2EMultiInstanceScope) -> Unit
) {
  require(instances.isNotEmpty()) { "Multi-target E2E test requires at least one instance mapping" }
  require(instances.keys.distinct().size == instances.size) {
    "Duplicate instance identifier detected in test declaration: ${instances.keys}"
  }

  val requiredTargets = instances.values.map { canonicalizeTarget(it.platformName) }.toSet()
  val activeTargets = resolveActiveTargets()

  if (activeTargets.isNotEmpty() && !activeTargets.containsAll(requiredTargets)) {
    val missing = requiredTargets - activeTargets
    abortTestExecution(
      "Test requires target(s) [${requiredTargets.joinToString()}], but [${missing.joinToString()}] is not active in this run (active: [${activeTargets.joinToString()}])."
    )
  }

  instances.forEach { (name, target) ->
    System.setProperty("parikshan.instance.$name.target", target.platformName)
  }

  runMultiInstanceTest(
    instances = instances.keys.toList(),
    config = config,
    block = block
  )
}

private fun canonicalizeTarget(target: String): String = when (target.trim().lowercase()) {
  "jvm" -> "desktop"
  "web" -> "wasm"
  else -> target.trim().lowercase()
}

private fun resolveActiveTargets(): Set<String> {
  val activeProp = System.getProperty("parikshan.activeTargets")
  if (!activeProp.isNullOrBlank()) {
    return activeProp.split(",").map { canonicalizeTarget(it) }.filter { it.isNotEmpty() }.toSet()
  }

  val singleTarget = System.getProperty("parikshan.target")?.trim()?.lowercase()
  if (singleTarget == "sync") {
    val syncTargets = System.getProperty("parikshan.sync.targets")
    if (!syncTargets.isNullOrBlank()) {
      return syncTargets.split(",").map { canonicalizeTarget(it) }.filter { it.isNotEmpty() }.toSet()
    }
  }

  if (!singleTarget.isNullOrBlank() && singleTarget != "sync" && singleTarget != "multi-target") {
    return setOf(canonicalizeTarget(singleTarget))
  }

  val sessionsDir = resolveSessionDirectory()
  if (sessionsDir.exists()) {
    val sessionFiles = sessionsDir.listFiles { f -> f.extension == "json" }
    if (!sessionFiles.isNullOrEmpty()) {
      val foundTargets = sessionFiles.mapNotNull { f ->
        val text = runCatching { f.readText() }.getOrNull().orEmpty()
        val parsed = runCatching { sessionJson.decodeFromString<InstanceSessionDescriptor>(text) }.getOrNull()
        parsed?.target?.let { canonicalizeTarget(it) }
      }.toSet()
      if (foundTargets.isNotEmpty()) return foundTargets
    }
  }

  return setOf("desktop")
}

private fun abortTestExecution(reason: String): Nothing {
  try {
    val clazz = Class.forName("org.opentest4j.TestAbortedException")
    val ctor = clazz.getConstructor(String::class.java)
    throw (ctor.newInstance(reason) as Throwable)
  } catch (e: Throwable) {
    if (e.javaClass.name == "org.opentest4j.TestAbortedException") throw e
  }

  try {
    val clazz = Class.forName("org.junit.AssumptionViolatedException")
    val ctor = clazz.getConstructor(String::class.java)
    throw (ctor.newInstance(reason) as Throwable)
  } catch (e: Throwable) {
    if (e.javaClass.name == "org.junit.AssumptionViolatedException") throw e
  }

  throw IllegalStateException("SKIPPED: $reason")
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
  val executionMode = System.getProperty("parikshan.executionMode")?.trim()?.lowercase()
  val activeTarget = System.getProperty("parikshan.target")?.trim()?.lowercase()

  if (executionMode == "multi-target" || activeTarget == "multi-target") {
    return java.io.File(baseDir, "multi-target/sessions")
  }
  if (activeTarget == "sync") {
    return java.io.File(baseDir, "sync/sessions")
  }
  if (!activeTarget.isNullOrBlank()) {
    val targetDir = java.io.File(baseDir, "$activeTarget/sessions")
    if (targetDir.exists()) return targetDir
    val legacyTargetDir = java.io.File(baseDir, "sessions/$activeTarget")
    if (legacyTargetDir.exists()) return legacyTargetDir
  }
  return java.io.File(baseDir, "sessions")
}

private fun resolveInstanceContext(name: String): Pair<String, ParikshanTestContext> {
  val activeRunnerTarget = System.getProperty("parikshan.target")?.trim()?.lowercase()
    ?.takeIf { it.isNotBlank() && it != "sync" && it != "multi-target" }
  val sessionsDir = resolveSessionDirectory()
  val instanceFile = java.io.File(sessionsDir, "$name.json")
  if (instanceFile.exists()) {
    val text = runCatching { instanceFile.readText() }.getOrNull().orEmpty()
    val parsed = runCatching { sessionJson.decodeFromString<InstanceSessionDescriptor>(text) }.getOrNull()
    if (parsed != null) {
      val rawTarget = parsed.target.ifBlank { "desktop" }
      val isCompatible = activeRunnerTarget == null || canonicalizeTarget(rawTarget) == canonicalizeTarget(activeRunnerTarget)
      if (isCompatible) {
        val target = if (activeRunnerTarget != null) canonicalizeTarget(activeRunnerTarget) else rawTarget
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
  }

  val target = activeRunnerTarget
    ?: System.getProperty("parikshan.instance.$name.target")
    ?: "desktop"
  val host = System.getProperty("parikshan.instance.$name.host")
    ?: System.getProperty("parikshan.host")
    ?: "127.0.0.1"
  val explicitPort = System.getProperty("parikshan.instance.$name.port")?.toIntOrNull()
  val port = explicitPort
    ?: if (name == "desktop" || canonicalizeTarget(target) == "desktop") {
      System.getProperty("parikshan.desktop.port")?.toIntOrNull()
        ?: System.getProperty("parikshan.port")?.toIntOrNull()
        ?: 9877
    } else if (canonicalizeTarget(target) == "wasm") {
      System.getProperty("parikshan.wasm.port")?.toIntOrNull()
        ?: System.getProperty("parikshan.port")?.toIntOrNull()
        ?: 8081
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

