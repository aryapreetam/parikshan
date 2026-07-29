@file:Suppress("NewApi")

package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.nio.file.*

abstract class E2ETestTask : DefaultTask() {

  init {
    outputs.upToDateWhen { false }
  }

  @get:Inject
  abstract val providers: ProviderFactory

  @get:Input
  @set:Option(option = "targets", description = "Comma-separated list of E2E targets to execute (e.g. desktop,wasm,android,ios)")
  var targets: String = "desktop,wasm,android,ios"

  @get:Input
  @set:Option(option = "keep-alive", description = "Keep application process alive to speed up subsequent E2E test runs.")
  var keepAlive: Boolean = false

  @get:Input
  @set:Option(option = "sync", description = "Execute E2E tests concurrently on all targets in synchronized lockstep mode.")
  var sync: Boolean = false

  @get:Input
  @set:Option(option = "watch", description = "Run E2E tests continuously on file changes.")
  var watch: Boolean = false

  @get:Input
  @get:Optional
  @set:Option(option = "compile-command", description = "The shell command to re-compile tests when files change in watch mode.")
  var compileCommand: String = ""

  @get:Input
  @set:Option(option = "layout", description = "Specifies the layout mode: default or side-by-side")
  var layout: String = "default"

  @get:Input
  @set:Option(option = "window-size", description = "Global window width and height override (format: <width>x<height>, e.g. 360x800)")
  var windowSize: String = ""

  @get:Input
  @set:Option(option = "desktop-window-size", description = "Desktop-specific size override (format: <width>x<height>)")
  var desktopWindowSize: String = ""

  @get:Input
  @set:Option(option = "desktop-window-position", description = "Desktop-specific screen coordinates (format: <x>,<y> or <x>x<y>)")
  var desktopWindowPosition: String = ""

  @get:Input
  @set:Option(option = "wasm-window-size", description = "Wasm-specific size override (format: <width>x<height>)")
  var wasmWindowSize: String = ""

  @get:Input
  @set:Option(option = "wasm-window-position", description = "Wasm-specific screen coordinates (format: <x>,<y> or <x>x<y>)")
  var wasmWindowPosition: String = ""

  @get:Input
  @set:Option(option = "app-mode", description = "Launches target client in standalone app mode (hiding browser chrome/toolbars for Wasm).")
  var appMode: Boolean = false


  @get:Input
  @set:Option(option = "reclaim-ports", description = "Force terminate conflicting active sessions of other applications on default ports.")
  var reclaimPorts: Boolean = false

  @get:Input
  @get:Optional
  @set:Option(option = "device", description = "Target device/emulator name or serial (convenience fallback)")
  var device: String = ""

  @get:Input
  @get:Optional
  @set:Option(option = "android-device", description = "Target Android device/emulator serial override")
  var androidDevice: String = ""

  @get:Input
  @get:Optional
  @set:Option(option = "ios-device", description = "Target iOS simulator name or UDID override")
  var iosDevice: String = ""

  @get:Input
  @get:Optional
  abstract val gradleAndroidSerial: Property<String>

  @get:Input
  @get:Optional
  abstract val gradleIosDevice: Property<String>

  @get:Input
  @get:Optional
  abstract val gradleDevice: Property<String>

  @get:Input
  @get:Optional
  abstract val gradleSerial: Property<String>

  @get:Input
  abstract val projectRootDir: Property<String>

  @get:Input
  abstract val projectPath: Property<String>

  private val projectPathPrefix: String
    get() = if (projectPath.get() == ":") "" else projectPath.get()

  @get:Input
  @get:Optional
  abstract val androidApplicationId: Property<String>

  @get:Input
  @get:Optional
  abstract val androidPort: Property<Int>

  @get:Input
  @get:Optional
  abstract val iosPort: Property<Int>

  @get:Internal
  abstract val iosBundleId: Property<String>

  @get:Input
  abstract val configurationCacheEnabled: Property<Boolean>

  @get:Input
  @set:Option(option = "tests", description = "Test class or method filter pattern (e.g. sample.app.SelectorScenarios)")
  var testsPattern: String = ""

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val hostTestClassesDirs: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val hostTestClasspath: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val junitConsoleJars: ConfigurableFileCollection

  @get:Input
  abstract val e2eTestClasses: ListProperty<String>

  @get:Input
  abstract val host: Property<String>

  @get:Input
  abstract val originalDesktopPort: Property<Int>

  @get:Input
  abstract val originalWasmPort: Property<Int>

  @get:Internal
  abstract val appJarFile: RegularFileProperty

  @get:Input
  abstract val jvmTargets: ListProperty<String>

  @get:Internal
  abstract val desktopLaunchManifestFile: RegularFileProperty

  @get:Internal
  abstract val wasmOutputDir: DirectoryProperty

  @get:Internal
  abstract val wasmPortFile: RegularFileProperty

  @get:Input
  abstract val appArgs: ListProperty<String>

  @get:Input
  abstract val token: Property<String>

  @get:Input
  @get:Optional
  abstract val title: Property<String>

  @get:Internal
  abstract val buildDir: DirectoryProperty

  @get:InputFiles
  @get:Optional
  abstract val productionSources: ConfigurableFileCollection

  @get:InputFiles
  @get:Optional
  abstract val testSources: ConfigurableFileCollection

  @get:InputFiles
  @get:Optional
  abstract val productionClassesDirs: ConfigurableFileCollection

  @get:Internal
  abstract val androidApkDir: DirectoryProperty

  @get:Internal
  abstract val iosAppDir: DirectoryProperty

  @TaskAction
  fun runOrchestratedTests() {
    val logger = logger
    
    // Clear stale test results from previous runs
    val testResultsDir = File(buildDir.get().asFile, "test-results/e2eTest")
    testResultsDir.deleteRecursively()
    testResultsDir.mkdirs()

    val discoveredClasses = e2eTestClasses.get()
    
    val filteredClasses = if (testsPattern.isBlank()) {
      discoveredClasses
    } else {
      discoveredClasses.filter { clazz ->
        E2EFilterMatcher.isClassMatched(clazz, testsPattern)
      }
    }
    
    if (filteredClasses.isEmpty()) {
      throw GradleException("No E2E test classes matched the filter pattern: '$testsPattern'")
    }

    val activeTargets = targets.split(",")
      .map { it.trim().lowercase() }
      .filter { it.isNotEmpty() }
      
    if (activeTargets.isEmpty()) {
      throw GradleException("No execution targets specified in --targets")
    }

    val hasAndroid = activeTargets.contains("android")
    val hasIos = activeTargets.contains("ios")

    var resolvedAndroidSerial: String? = null
    var resolvedIosDevice: String? = null

    if (androidDevice.isNotBlank()) {
      resolvedAndroidSerial = androidDevice.trim()
    }
    if (iosDevice.isNotBlank()) {
      resolvedIosDevice = iosDevice.trim()
    }

    if (device.isNotBlank()) {
      val devTrimmed = device.trim()
      if (hasAndroid && hasIos) {
        val matchesAndroid = getConnectedAndroidSerials().contains(devTrimmed)
        val matchesIos = getAvailableIosSimulators().any { it.name == devTrimmed || it.udid == devTrimmed }

        if (matchesAndroid && matchesIos) {
          throw GradleException("Ambiguous target device '$devTrimmed': matches both a connected Android device serial and an available iOS simulator name/UDID. Please use explicit --android-device and --ios-device options instead.")
        } else if (matchesAndroid) {
          if (resolvedAndroidSerial == null) {
            resolvedAndroidSerial = devTrimmed
          }
        } else if (matchesIos) {
          if (resolvedIosDevice == null) {
            resolvedIosDevice = devTrimmed
          }
        } else {
          throw GradleException("Target device '$devTrimmed' specified via --device matches neither a connected Android device nor an available iOS simulator. Please check connected/available devices or use explicit --android-device and --ios-device options.")
        }
      } else if (hasAndroid) {
        if (resolvedAndroidSerial == null) {
          resolvedAndroidSerial = devTrimmed
        }
      } else if (hasIos) {
        if (resolvedIosDevice == null) {
          resolvedIosDevice = devTrimmed
        }
      }
    }

    val finalAndroidSerial = resolvedAndroidSerial
      ?: gradleDevice.orNull?.takeIf { it.isNotBlank() }
      ?: gradleSerial.orNull?.takeIf { it.isNotBlank() }
      ?: gradleAndroidSerial.orNull?.takeIf { it.isNotBlank() }
      ?: System.getenv("PARIKSHAN_ANDROID_SERIAL")?.takeIf { it.isNotBlank() }

    val resolvedHost = host.getOrElse("127.0.0.1")

    val finalIosDevice = resolvedIosDevice
      ?: gradleDevice.orNull?.takeIf { it.isNotBlank() }
      ?: gradleSerial.orNull?.takeIf { it.isNotBlank() }
      ?: gradleIosDevice.orNull?.takeIf { it.isNotBlank() }
      ?: System.getenv("PARIKSHAN_IOS_DEVICE")?.takeIf { it.isNotBlank() }
      ?: getAvailableIosSimulators().firstOrNull { it.isBooted }?.name
      ?: getAvailableIosSimulators().firstOrNull()?.name
      ?: "iPhone 16"

    val logDir = File(buildDir.get().asFile, "parikshan/logs")
    logDir.mkdirs()

    logger.lifecycle("Parikshan: Starting parallel E2E runs for targets: ${activeTargets.joinToString()}")
    logger.lifecycle("Parikshan: Target test classes: ${filteredClasses.joinToString()}")

    val activeProcesses = mutableListOf<Process>()
    val activeForwardedPorts = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    val shutdownHook = Thread {
      synchronized(activeProcesses) {
        activeProcesses.forEach {
          try { it.destroyForcibly() } catch (_: Exception) {}
        }
      }
      try { WasmServer.stop() } catch (_: Exception) {}
      synchronized(activeForwardedPorts) {
        activeForwardedPorts.forEach { port ->
          try {
            ProcessBuilder("adb", "forward", "--remove", "tcp:$port").start().waitFor()
          } catch (_: Exception) {}
        }
      }
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    if (watch) {
      try {
        runWatchLoop(activeTargets, filteredClasses, activeProcesses, activeForwardedPorts, resolvedHost, finalAndroidSerial, finalIosDevice)
      } finally {
        try {
          Runtime.getRuntime().removeShutdownHook(shutdownHook)
        } catch (_: Exception) {}
      }
      return
    }

    val results = if (sync) {
      val targetStartTime = System.currentTimeMillis()
      val result = try {
        executeSyncTarget(activeTargets, filteredClasses, activeProcesses, finalAndroidSerial, finalIosDevice)
      } catch (e: Exception) {
        TargetResult("sync", false, e.message ?: "Sync execution failed")
      }
      val duration = System.currentTimeMillis() - targetStartTime
      listOf(result.copy(durationMs = duration))
    } else {
      val executor = Executors.newFixedThreadPool(activeTargets.size)
      val futures = mutableListOf<Future<TargetResult>>()

      activeTargets.forEach { target ->
        val future = executor.submit<TargetResult> {
          val targetStartTime = System.currentTimeMillis()
          try {
            val result = executeTarget(target, filteredClasses, activeProcesses, activeForwardedPorts, resolvedHost, finalAndroidSerial, finalIosDevice)
            val duration = System.currentTimeMillis() - targetStartTime
            result.copy(durationMs = duration)
          } catch (e: Exception) {
            val duration = System.currentTimeMillis() - targetStartTime
            TargetResult(target, false, e.message ?: "Execution failed", duration)
          }
        }
        futures.add(future)
      }

      val res = futures.map { it.get() }
      executor.shutdown()
      res
    }

    try {
      Runtime.getRuntime().removeShutdownHook(shutdownHook)
    } catch (_: Exception) {}

    logger.lifecycle("\n========================================")
    logger.lifecycle("      Parikshan E2E Test Results        ")
    logger.lifecycle("========================================")
    results.forEach { res ->
      val status = if (res.success) "SUCCESS" else "FAILED"
      val durationStr = formatDuration(res.durationMs)
      logger.lifecycle("[${res.target.uppercase()}] $status - ${res.message} ($durationStr)")
    }
    logger.lifecycle("========================================\n")

    val anyFailure = results.any { !it.success }
    writeWatchResults(!anyFailure, results)
    
    if (anyFailure) {
      throw GradleException("Parikshan E2E test execution failed for one or more targets.")
    }
  }

  private data class TargetResult(
    val target: String,
    val success: Boolean,
    val message: String,
    val durationMs: Long = 0L
  )

  private fun executeTarget(
    target: String,
    classes: List<String>,
    activeProcesses: MutableList<Process>,
    activeForwardedPorts: MutableSet<Int>,
    resolvedHost: String,
    finalAndroidSerial: String?,
    finalIosDevice: String
  ): TargetResult {
    val logger = logger
    logger.lifecycle("Parikshan [$target]: Starting target E2E execution...")

    return withTargetLock(target) {
      val isJvmTarget = target == "desktop" || target == "jvm" || jvmTargets.get().map { it.lowercase() }.contains(target)
      when {
        isJvmTarget -> {
          val session = if (keepAlive) readSession(target) else null
          val minBinaryTimestamp = getTargetOutputTimestamp(target)
          val maxSourceTimestamp = getProductionSourceTimestamp()
          val healthy = session != null && checkTargetHealth(host.get(), session.port, session.token)
          val fresh = isTargetFresh(target)
          logger.debug("Parikshan [$target] keep-alive check: session=${session != null}, healthy=$healthy, fresh=$fresh, session.timestamp=${session?.timestamp}, minBinary=$minBinaryTimestamp, maxSource=$maxSourceTimestamp")
          val canReuse = session != null && healthy && fresh && session.timestamp >= minBinaryTimestamp

          val optDesktopSize = desktopWindowSize.takeIf { it.isNotBlank() } ?: windowSize
          val size = parseSize(optDesktopSize)
          val pos = parsePosition(desktopWindowPosition) ?: if (layout == "side-by-side") Pair(10, 50) else null
          if (size != null) {
            System.setProperty("parikshan.desktop.windowWidth", size.first.toString())
            System.setProperty("parikshan.desktop.windowHeight", size.second.toString())
          } else {
            System.clearProperty("parikshan.desktop.windowWidth")
            System.clearProperty("parikshan.desktop.windowHeight")
          }
          if (pos != null) {
            System.setProperty("parikshan.desktop.windowX", pos.first.toString())
            System.setProperty("parikshan.desktop.windowY", pos.second.toString())
          } else {
            System.clearProperty("parikshan.desktop.windowX")
            System.clearProperty("parikshan.desktop.windowY")
          }

          val jarFile = appJarFile.orNull?.asFile
          val resolvedPort = if (canReuse && session != null) {
            logger.lifecycle("Parikshan [$target]: Keeping active instance alive (skipping build/launch).")
            session.port
          } else {
            if (session != null) {
              logger.lifecycle("Parikshan [$target]: Active instance is stale or unhealthy. Relaunching...")
              DesktopProcess.stop(
                host = host.get(),
                port = session.port,
                token = session.token,
                manifestFile = desktopLaunchManifestFile.get().asFile
              )
            }
            val port = PortConflictHandler.resolvePortAndCleanStale(
              originalPort = originalDesktopPort.get(),
              host = host.get(),
              logger = logger
            )
            if (jarFile != null) {
              DesktopProcess.start(
                jar = jarFile,
                token = token.get(),
                logFile = File(buildDir.get().asFile, "parikshan/${target}-app-logs.log"),
                manifestFile = desktopLaunchManifestFile.get().asFile,
                appArgs = appArgs.get(),
                host = host.get(),
                port = port,
                timeoutMs = 15000L,
                pollMs = 250L,
                title = title.orNull,
                background = true
              )
              writeSession(target, TargetSession(token.get(), port, System.currentTimeMillis()))
            } else {
              logger.lifecycle("Parikshan [$target]: No application JAR was provided. Skipping application startup; running tests against an external or manually-started instance.")
            }
            port
          }

          val activeToken = if (canReuse && session != null) session.token else token.get()
          val systemProps = mutableMapOf(
            "parikshan.target" to target,
            "parikshan.host" to host.get(),
            "parikshan.port" to resolvedPort.toString(),
            "parikshan.token" to activeToken,
            "parikshan.desktop.launchManifest" to desktopLaunchManifestFile.get().asFile.absolutePath
          )
          System.getProperty("parikshan.desktop.windowX")?.let { systemProps["parikshan.desktop.windowX"] = it }
          System.getProperty("parikshan.desktop.windowY")?.let { systemProps["parikshan.desktop.windowY"] = it }
          System.getProperty("parikshan.desktop.windowWidth")?.let { systemProps["parikshan.desktop.windowWidth"] = it }
          System.getProperty("parikshan.desktop.windowHeight")?.let { systemProps["parikshan.desktop.windowHeight"] = it }
          if (System.getProperty("parikshan.background") == "true") {
            systemProps["parikshan.background"] = "true"
          }
          logger.lifecycle("Parikshan [$target]: Running test suite across ${classes.size} classes...")
          val exitCode = spawnTestJvmForClasses(
            target = target,
            testClasses = classes,
            systemProperties = systemProps,
            activeProcesses = activeProcesses
          )
          if (exitCode != 0) {
            classes.forEach { testClass ->
              printTestFailures(target, testClass)
            }
            if (!keepAlive) {
              DesktopProcess.stop(
                host = host.get(),
                port = resolvedPort,
                token = activeToken,
                manifestFile = desktopLaunchManifestFile.get().asFile
              )
              clearSession("desktop")
            }
            return createTargetResult("desktop", false, classes, "Test class execution failed (exit code $exitCode). Check logs at build/parikshan/logs/desktop-*.log")
          }

          if (keepAlive) {
            val manifest = desktopLaunchManifestFile.get().asFile
            if (manifest.exists() && jarFile != null) {
              val props = java.util.Properties()
              runCatching { manifest.inputStream().use { props.load(it) } }
              val finalPort = props.getProperty("port")?.toIntOrNull() ?: resolvedPort
              val finalToken = props.getProperty("token") ?: activeToken
              writeSession(target, TargetSession(finalToken, finalPort, System.currentTimeMillis()))
            } else if (jarFile != null) {
              writeSession(target, TargetSession(activeToken, resolvedPort, System.currentTimeMillis()))
            }
          } else {
            if (jarFile != null) {
              DesktopProcess.stop(
                host = host.get(),
                port = resolvedPort,
                token = activeToken,
                manifestFile = desktopLaunchManifestFile.get().asFile
              )
              clearSession(target)
            }
          }
          return@withTargetLock createTargetResult(target, true, classes)
        }

        target == "wasm" -> {
        val session = if (keepAlive) readSession("wasm") else null
        val minBinaryTimestamp = getTargetOutputTimestamp("wasm")
        val canReuse = session != null && 
                       checkTargetHealth("127.0.0.1", session.port, session.token) && 
                       isTargetFresh("wasm") && 
                       session.timestamp >= minBinaryTimestamp

        val resolvedPort = if (canReuse && session != null) {
          logger.lifecycle("Parikshan [wasm]: Keeping active instance alive (skipping build/launch).")
          session.port
        } else {
          if (session != null) {
            logger.lifecycle("Parikshan [wasm]: Active instance is stale or unhealthy. Relaunching...")
            WasmServer.stop()
          }
          val port = PortConflictHandler.resolvePortAndCleanStale(
            originalPort = originalWasmPort.get(),
            host = "127.0.0.1",
            logger = logger
          )
          val portFile = wasmPortFile.get().asFile
          portFile.parentFile.mkdirs()
          portFile.writeText(port.toString())

          WasmServer.start(port, wasmOutputDir.get().asFile)
          writeSession("wasm", TargetSession(token.get(), port, System.currentTimeMillis()))
          port
        }

        val activeToken = if (canReuse && session != null) session.token else token.get()

        val systemProps = mutableMapOf(
          "parikshan.target" to "wasm",
          "parikshan.token" to activeToken,
          "parikshan.wasm.url" to "http://127.0.0.1:$resolvedPort"
        )
        val isAppModeActive = appMode || (layout == "side-by-side")
        systemProps["parikshan.wasm.appMode"] = isAppModeActive.toString()
        val resolvedWasmSize = parseSize(wasmWindowSize.takeIf { it.isNotBlank() } ?: windowSize) ?: Pair(1280, 600)
        systemProps["parikshan.wasm.viewportWidth"] = resolvedWasmSize.first.toString()
        systemProps["parikshan.wasm.viewportHeight"] = resolvedWasmSize.second.toString()
        val resolvedWasmPos = if (layout == "side-by-side") {
          val resolvedDesktopSize = parseSize(desktopWindowSize.takeIf { it.isNotBlank() } ?: windowSize) ?: Pair(800, 600)
          val resolvedDesktopPos = parsePosition(desktopWindowPosition) ?: Pair(10, 50)
          Pair(resolvedDesktopPos.first + resolvedDesktopSize.first, resolvedDesktopPos.second)
        } else {
          parsePosition(wasmWindowPosition)
        }
        if (resolvedWasmPos != null) {
          systemProps["parikshan.wasm.windowX"] = resolvedWasmPos.first.toString()
          systemProps["parikshan.wasm.windowY"] = resolvedWasmPos.second.toString()
        }
        logger.lifecycle("Parikshan [wasm]: Running test suite across ${classes.size} classes in single browser window...")
        val exitCode = spawnTestJvmForClasses(
          target = "wasm",
          testClasses = classes,
          systemProperties = systemProps,
          activeProcesses = activeProcesses
        )
        if (exitCode != 0) {
          classes.forEach { testClass ->
            printTestFailures("wasm", testClass)
          }
          if (!keepAlive) {
            WasmServer.stop()
            clearSession("wasm")
          }
          return createTargetResult("wasm", false, classes, "Test class execution failed (exit code $exitCode). Check logs at build/parikshan/logs/wasm-*.log")
        }

        if (!keepAlive) {
          WasmServer.stop()
          clearSession("wasm")
        }
        return createTargetResult("wasm", exitCode == 0, classes)
      }

        target == "android" -> {
        val isExplicit = targets.split(",").map { it.trim().lowercase() }.contains("android")
        val isAndroidE2EExplicit = isExplicit && targets != "desktop,wasm,android,ios"
        val hasAndroidDeviceSpecified = androidDevice.isNotBlank() || device.isNotBlank() || gradleAndroidSerial.orNull?.isNotBlank() == true || gradleDevice.orNull?.isNotBlank() == true || gradleSerial.orNull?.isNotBlank() == true
        val shouldExecuteAndroid = isAndroidE2EExplicit || hasAndroidDeviceSpecified || isAndroidDeviceOnline(finalAndroidSerial)

        if (!shouldExecuteAndroid) {
          logger.lifecycle("Parikshan: Skipping target 'android' because no active emulator or device was detected.")
          return TargetResult("android", true, "Skipped (no device detected)")
        }

        val session = if (keepAlive) readSession("android") else null
        val defaultAndroidPort = androidPort.orNull ?: 9879
        val resolvedAndroidPort = session?.port ?: defaultAndroidPort
        val resolvedToken = session?.token ?: token.get()
        val isHealthy = session != null && checkTargetHealth(resolvedHost, resolvedAndroidPort, resolvedToken)
        val isFresh = isTargetFresh("android")
        val minBinaryTimestamp = getTargetOutputTimestamp("android")
        val canReuse = isHealthy && isFresh && session!!.timestamp >= minBinaryTimestamp

        val activeToken = if (canReuse && session != null) session.token else token.get()
        var activePort = if (canReuse && session != null) session.port else defaultAndroidPort

        val gradlew = getGradlewExecutable(File(projectRootDir.get()))

        if (canReuse) {
          logger.lifecycle("Parikshan [android]: Keeping active instance alive (skipping build/launch).")
        } else {
          synchronized(getLockFor("android")) {
            val serial = AndroidTargetConfigurer.AndroidRecorder.resolveDeviceSerial(logger, File(projectRootDir.get()), finalAndroidSerial)
            val reclaim = reclaimPorts || providers.gradleProperty("parikshan.reclaimPorts").orNull?.toBoolean() ?: false

            val devicePortState = checkDevicePortState(serial, defaultAndroidPort, resolvedHost)
            if (devicePortState.isBusy || !PortConflictHandler.isPortAvailable(resolvedHost, defaultAndroidPort)) {
              val activeAppId = devicePortState.parikshanAppId
              if (activeAppId != null) {
                if (activeAppId == androidApplicationId.get()) {
                  ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", androidApplicationId.get()).start().waitFor()
                  ProcessBuilder("adb", "-s", serial, "forward", "--remove", "tcp:$defaultAndroidPort").start().waitFor()
                  activeForwardedPorts.remove(defaultAndroidPort)
                  Thread.sleep(500)
                } else if (reclaim) {
                  logger.lifecycle("Parikshan [android]: Port $defaultAndroidPort was held by '${activeAppId}'. Reclaiming port via --reclaim-ports...")
                  ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", activeAppId).start().waitFor()
                  ProcessBuilder("adb", "-s", serial, "forward", "--remove", "tcp:$defaultAndroidPort").start().waitFor()
                  activeForwardedPorts.remove(defaultAndroidPort)
                  Thread.sleep(500)
                } else {
                  var fbPort = defaultAndroidPort + 1
                  while (fbPort <= 65535) {
                    if (PortConflictHandler.isPortAvailable(resolvedHost, fbPort) && !checkDevicePortState(serial, fbPort, resolvedHost).isBusy) {
                      break
                    }
                    fbPort++
                  }
                  logger.lifecycle("Parikshan [android]: Port $defaultAndroidPort is held by another active application '${activeAppId}'. Falling back to host port $fbPort.")
                  activePort = fbPort
                }
              } else {
                var fbPort = defaultAndroidPort + 1
                while (fbPort <= 65535) {
                  if (PortConflictHandler.isPortAvailable(resolvedHost, fbPort) && !checkDevicePortState(serial, fbPort, resolvedHost).isBusy) {
                    break
                  }
                  fbPort++
                }
                logger.lifecycle("Parikshan [android]: Port $defaultAndroidPort is occupied by a non-Parikshan process or busy on device. Falling back to host port $fbPort.")
                activePort = fbPort
              }
            }

            if (session != null) {
              logger.lifecycle("Parikshan [android]: Active instance is stale or unhealthy. Relaunching...")
              ProcessBuilder("adb", "-s", serial, "forward", "--remove", "tcp:${session.port}").start().waitFor()
              activeForwardedPorts.remove(session.port)
              ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", androidApplicationId.get()).start().waitFor()
            }
            logger.lifecycle("Parikshan [android]: Device detected. Starting E2E execution on port $activePort...")
            // 1. Start App
            val startArgs = mutableListOf(
              gradlew,
              "-p", projectRootDir.get(),
              "$projectPathPrefix:startParikshanAndroidApp",
              "-Pparikshan.token=${token.get()}",
              "-Pparikshan.port=$activePort",
              "-Pparikshan.e2e.active=true"
            )
            if (!configurationCacheEnabled.get()) {
              startArgs.add("--no-configuration-cache")
            }
            if (!finalAndroidSerial.isNullOrBlank()) {
              startArgs.add("-Pparikshan.android.serial=$finalAndroidSerial")
            }
            val startProcess = ProcessBuilder(startArgs).apply {
              cleanXcodeEnv(this)
              redirectErrorStream(true)
              val logF = File(buildDir.get().asFile, "parikshan/logs/android-start.log")
              logF.parentFile.mkdirs()
              redirectOutput(logF)
            }.start()
            activeForwardedPorts.add(activePort)
            synchronized(activeProcesses) { activeProcesses.add(startProcess) }
            val startExit = try {
              startProcess.waitFor()
            } finally {
              synchronized(activeProcesses) { activeProcesses.remove(startProcess) }
            }
            if (startExit != 0) {
              return TargetResult("android", false, "Failed to start Android app (exit code $startExit). Check build/parikshan/logs/android-start.log")
            }
            writeSession("android", TargetSession(token.get(), activePort, System.currentTimeMillis()))
          }
        }

        // 2. Run Tests
        logger.lifecycle("Parikshan [android]: Running test suite across ${classes.size} classes...")
        val testSystemProps = mutableMapOf(
          "parikshan.target" to "android",
          "parikshan.host" to resolvedHost,
          "parikshan.port" to activePort.toString(),
          "parikshan.token" to activeToken
        )
        if (!finalAndroidSerial.isNullOrBlank()) {
          testSystemProps["parikshan.android.serial"] = finalAndroidSerial
        }
        val exitCode = spawnTestJvmForClasses(
          target = "android",
          testClasses = classes,
          systemProperties = testSystemProps,
          activeProcesses = activeProcesses
        )
        val runSuccess = exitCode == 0
        var testFailureMessage: String? = if (!runSuccess) "Android test suite execution failed (exit code $exitCode)." else null
        if (!runSuccess) {
          classes.forEach { testClass ->
            printTestFailures("android", testClass)
          }
        }

        if (!keepAlive) {
          // 3. Stop App
          synchronized(getLockFor("android")) {
            val stopArgs = mutableListOf(
              gradlew,
              "-p", projectRootDir.get(),
              "$projectPathPrefix:stopParikshanAndroidApp"
            )
            if (!configurationCacheEnabled.get()) {
              stopArgs.add("--no-configuration-cache")
            }
            if (!finalAndroidSerial.isNullOrBlank()) {
              stopArgs.add("-Pparikshan.android.serial=$finalAndroidSerial")
            }
            val stopProcess = ProcessBuilder(stopArgs).apply {
              cleanXcodeEnv(this)
            }.start()
            stopProcess.waitFor()
            clearSession("android")
          }
        }

        if (testFailureMessage != null) {
          return createTargetResult("android", false, classes, testFailureMessage)
        }
        return createTargetResult("android", true, classes)
      }

        target == "ios" -> {
        val isExplicit = targets.split(",").map { it.trim().lowercase() }.contains("ios")
        val isIosE2EExplicit = isExplicit && targets != "desktop,wasm,android,ios"
        val hasIosDeviceSpecified = iosDevice.isNotBlank() || device.isNotBlank() || gradleIosDevice.orNull?.isNotBlank() == true || gradleDevice.orNull?.isNotBlank() == true || gradleSerial.orNull?.isNotBlank() == true
        val shouldExecuteIos = isIosE2EExplicit || hasIosDeviceSpecified || isIosSimulatorBooted(finalIosDevice)

        if (!shouldExecuteIos) {
          logger.lifecycle("Parikshan: Skipping target 'ios' because no booted iOS simulator was detected and no explicit device was targeted.")
          return TargetResult("ios", true, "Skipped (no device detected)")
        }

        val udid = getIosSimulatorUdid(finalIosDevice) ?: getBootedIosSimulatorUdid() ?: ""
        val bundleId = iosBundleId.getOrElse("")

        val session = if (keepAlive) readSession("ios") else null
        val defaultIosPort = iosPort.orNull ?: 9878
        val resolvedIosPort = session?.port ?: defaultIosPort
        val resolvedToken = session?.token ?: token.get()
        val isHealthy = session != null && checkTargetHealth(resolvedHost, resolvedIosPort, resolvedToken)
        val isFresh = isTargetFresh("ios")
        val minBinaryTimestamp = getTargetOutputTimestamp("ios")
        val canReuse = isHealthy && isFresh && session!!.timestamp >= minBinaryTimestamp

        val activeToken = if (canReuse && session != null) session.token else token.get()
        var activePort = if (canReuse && session != null) session.port else defaultIosPort

        val gradlew = getGradlewExecutable(File(projectRootDir.get()))

        if (canReuse) {
          logger.lifecycle("Parikshan [ios]: Keeping active instance alive (skipping build/launch).")
        } else {
          synchronized(getLockFor("ios")) {
            val reclaim = reclaimPorts || providers.gradleProperty("parikshan.reclaimPorts").orNull?.toBoolean() ?: false

            if (!PortConflictHandler.isPortAvailable(resolvedHost, defaultIosPort)) {
              val activeAppId = PortConflictHandler.queryApplicationId(resolvedHost, defaultIosPort)
              if (activeAppId != null) {
                if (activeAppId == bundleId) {
                  ProcessBuilder("xcrun", "simctl", "terminate", udid, bundleId).start().waitFor()
                  Thread.sleep(500)
                } else if (reclaim) {
                  logger.lifecycle("Parikshan [ios]: Port $defaultIosPort was held by '${activeAppId}'. Reclaiming port via --reclaim-ports...")
                  ProcessBuilder("xcrun", "simctl", "terminate", udid, activeAppId).start().waitFor()
                  Thread.sleep(500)
                } else {
                  var fbPort = defaultIosPort + 1
                  while (fbPort <= 65535) {
                    if (PortConflictHandler.isPortAvailable(resolvedHost, fbPort)) {
                      break
                    }
                    fbPort++
                  }
                  logger.lifecycle("Parikshan [ios]: Port $defaultIosPort is held by another active application '${activeAppId}'. Falling back to host port $fbPort.")
                  activePort = fbPort
                }
              } else {
                var fbPort = defaultIosPort + 1
                while (fbPort <= 65535) {
                  if (PortConflictHandler.isPortAvailable(resolvedHost, fbPort)) {
                    break
                  }
                  fbPort++
                }
                logger.lifecycle("Parikshan [ios]: Port $defaultIosPort is occupied by a non-Parikshan process. Falling back to host port $fbPort.")
                activePort = fbPort
              }
            }

            if (session != null) {
              logger.lifecycle("Parikshan [ios]: Active instance is stale or unhealthy. Relaunching...")
              val stopArgs = mutableListOf(
                gradlew,
                "-p", projectRootDir.get(),
                "$projectPathPrefix:stopIosApp",
                "-Pparikshan.ios.device=$finalIosDevice",
                "-Pparikshan.ios.port=${session.port}"
              )
              if (!configurationCacheEnabled.get()) {
                stopArgs.add("--no-configuration-cache")
              }
              val stopProcess = ProcessBuilder(stopArgs).apply {
                cleanXcodeEnv(this)
              }.start()
              stopProcess.waitFor()
            }
            logger.lifecycle("Parikshan [ios]: Simulator target: '$finalIosDevice' ($udid). Starting E2E execution on port $activePort...")
            // 1. Start App
            val startArgs = mutableListOf(
              gradlew, 
              "-p", projectRootDir.get(),
              "$projectPathPrefix:startIosApp", 
              "-Pparikshan.token=${token.get()}", 
              "-Pparikshan.ios.port=$activePort",
              "-Pparikshan.e2e.active=true",
              "-Pparikshan.ios.device=$finalIosDevice"
            )
            if (!configurationCacheEnabled.get()) {
              startArgs.add("--no-configuration-cache")
            }
            val startProcess = ProcessBuilder(startArgs).apply {
              cleanXcodeEnv(this)
              redirectErrorStream(true)
              val logF = File(buildDir.get().asFile, "parikshan/logs/ios-start.log")
              logF.parentFile.mkdirs()
              redirectOutput(logF)
            }.start()
            synchronized(activeProcesses) { activeProcesses.add(startProcess) }
            val startExit = try {
              startProcess.waitFor()
            } finally {
              synchronized(activeProcesses) { activeProcesses.remove(startProcess) }
            }
            if (startExit != 0) {
              return TargetResult("ios", false, "Failed to start iOS app (exit code $startExit). Check build/parikshan/logs/ios-start.log")
            }
            writeSession("ios", TargetSession(token.get(), activePort, System.currentTimeMillis()))
          }
        }

        // 2. Run Tests
        logger.lifecycle("Parikshan [ios]: Running test suite across ${classes.size} classes...")
        val exitCode = spawnTestJvmForClasses(
          target = "ios",
          testClasses = classes,
          systemProperties = mapOf(
            "parikshan.target" to "ios",
            "parikshan.host" to resolvedHost,
            "parikshan.port" to activePort.toString(),
            "parikshan.token" to activeToken,
            "parikshan.ios.bundleId" to bundleId,
            "parikshan.ios.udid" to udid,
            "parikshan.ios.device" to finalIosDevice
          ),
          activeProcesses = activeProcesses
        )
        val runSuccess = exitCode == 0
        var testFailureMessage: String? = if (!runSuccess) "iOS test suite execution failed (exit code $exitCode)." else null
        if (!runSuccess) {
          classes.forEach { testClass ->
            printTestFailures("ios", testClass)
          }
        }

        if (!keepAlive || !runSuccess) {
          // 3. Stop App
          synchronized(getLockFor("ios")) {
            val stopArgs = mutableListOf(
              gradlew, 
              "-p", projectRootDir.get(),
              "$projectPathPrefix:stopIosApp",
              "-Pparikshan.ios.device=$finalIosDevice",
              "-Pparikshan.ios.port=$activePort"
            )
            if (!configurationCacheEnabled.get()) {
              stopArgs.add("--no-configuration-cache")
            }
            val stopProcess = ProcessBuilder(stopArgs).apply {
              cleanXcodeEnv(this)
            }.start()
            stopProcess.waitFor()
            clearSession("ios")
          }
        }

        if (testFailureMessage != null) {
          return createTargetResult("ios", false, classes, testFailureMessage)
        }
        return createTargetResult("ios", true, classes)
      }

      else -> {
        return TargetResult(target, false, "Unsupported target platform: '$target'")
      }
    }
   }
  }

  private data class IosSim(val name: String, val udid: String, val isBooted: Boolean)

  private fun getConnectedAndroidSerials(): List<String> {
    return try {
      val process = ProcessBuilder("adb", "devices").start()
      val output = process.inputStream.bufferedReader().readText()
      process.waitFor()
      output.lineSequence()
        .drop(1)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
          val parts = line.split(Regex("\\s+"))
          if (parts.size >= 2 && parts[1] == "device") parts[0] else null
        }
        .toList()
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun getAvailableIosSimulators(): List<IosSim> {
    return try {
      val process = ProcessBuilder("xcrun", "simctl", "list", "devices", "available").start()
      val output = process.inputStream.bufferedReader().readText()
      process.waitFor()
      val sims = mutableListOf<IosSim>()
      output.lineSequence().forEach { line ->
        if (line.contains("(")) {
          val name = line.substringBefore("(").trim()
          val udid = line.substringAfter("(").substringBefore(")")
          val state = line.substringAfterLast("(").substringBefore(")")
          if (name.isNotEmpty() && udid.isNotEmpty()) {
            sims += IosSim(name, udid, state.contains("Booted", ignoreCase = true))
          }
        }
      }
      sims
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun isAndroidDeviceOnline(targetSerial: String? = null): Boolean {
    return try {
      val process = ProcessBuilder("adb", "devices").start()
      val output = process.inputStream.bufferedReader().readText()
      process.waitFor()
      output.lineSequence()
        .drop(1)
        .map { it.trim() }
        .any { line ->
          line.isNotEmpty() && (line.contains("device") && !line.contains("authorized")) &&
            (targetSerial == null || line.startsWith(targetSerial))
        }
    } catch (_: Exception) {
      false
    }
  }

  private fun isIosSimulatorBooted(): Boolean {
    return try {
      val process = ProcessBuilder("xcrun", "simctl", "list", "devices").start()
      val output = process.inputStream.bufferedReader().readText()
      process.waitFor()
      output.contains("(Booted)")
    } catch (_: Exception) {
      false
    }
  }

  private fun isIosSimulatorBooted(requested: String): Boolean {
    return try {
      val process = ProcessBuilder("xcrun", "simctl", "list", "devices").start()
      val output = process.inputStream.bufferedReader().readText()
      process.waitFor()
      output.lineSequence()
        .filter { it.contains("(Booted)") }
        .any { line ->
          val name = line.substringBefore("(").trim()
          val udid = line.substringAfter("(").substringBefore(")")
          requested == name || requested == udid
        }
    } catch (_: Exception) {
      false
    }
  }

  private fun getBootedIosSimulatorUdid(): String? {
    return try {
      val process = ProcessBuilder("xcrun", "simctl", "list", "devices").start()
      val output = process.inputStream.bufferedReader().readText()
      process.waitFor()
      output.lineSequence()
        .firstOrNull { it.contains("(Booted)") }
        ?.let { line ->
          val regex = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")
          regex.find(line)?.value
        }
    } catch (_: Exception) {
      null
    }
  }

  private fun getIosSimulatorUdid(requested: String): String? {
    return try {
      val process = ProcessBuilder("xcrun", "simctl", "list", "devices", "available").start()
      val output = process.inputStream.bufferedReader().readText()
      process.waitFor()
      
      val allSims = mutableListOf<IosSim>()
      output.lineSequence().forEach { line ->
        if (line.contains("(")) {
          val name = line.substringBefore("(").trim()
          val currentUdid = line.substringAfter("(").substringBefore(")")
          val state = line.substringAfterLast("(").substringBefore(")")
          if (name.isNotEmpty() && currentUdid.isNotEmpty()) {
            allSims += IosSim(name, currentUdid, state.contains("Booted", ignoreCase = true))
          }
        }
      }

      val exact = allSims.firstOrNull { requested == it.name || requested == it.udid }
      if (exact != null) return exact.udid

      val booted = allSims.firstOrNull { it.isBooted }
      if (booted != null) return booted.udid

      val iphone = allSims.firstOrNull { it.name.startsWith("iPhone", ignoreCase = true) }
      if (iphone != null) return iphone.udid

      allSims.firstOrNull()?.udid
    } catch (_: Exception) {
      null
    }
  }

  private fun getGradlewExecutable(rootDir: File): String {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val gradlewName = if (isWindows) "gradlew.bat" else "gradlew"
    return File(rootDir, gradlewName).absolutePath
  }

  internal fun cleanXcodeEnv(pb: ProcessBuilder) {
    val env = pb.environment()
    val keysToRemove = listOf(
      "PLATFORM_NAME",
      "SDK_NAME",
      "SDKROOT",
      "ARCHS",
      "ONLY_ACTIVE_ARCH",
      "TARGET_DEVICE_IDENTIFIER",
      "CONFIGURATION",
      "BUILT_PRODUCTS_DIR",
      "DERIVED_DATA_DIR",
      "BUILD_DIR",
      "TARGET_BUILD_DIR"
    )
    keysToRemove.forEach { env.remove(it) }

    val javaHomeVal = System.getProperty("java.home") ?: System.getenv("JAVA_HOME") ?: ""
    if (javaHomeVal.isNotBlank()) {
      env["JAVA_HOME"] = javaHomeVal
      val currentPath = env["PATH"] ?: System.getenv("PATH") ?: ""
      if (!currentPath.contains("$javaHomeVal/bin")) {
        env["PATH"] = "$javaHomeVal/bin:$currentPath"
      }
    }

    val essentialKeys = listOf("ANDROID_HOME", "PATH")
    essentialKeys.forEach { key ->
      val sysVal = System.getenv(key)
      if (!sysVal.isNullOrBlank() && !env.containsKey(key)) {
        env[key] = sysVal
      }
    }
  }

  private fun spawnTestJvm(
    target: String,
    testClass: String,
    systemProperties: Map<String, String>,
    activeProcesses: MutableList<Process>
  ): Int {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val javaBin = File(System.getProperty("java.home"), "bin/java" + (if (isWindows) ".exe" else "")).absolutePath

    val classpathFiles = hostTestClasspath.files + hostTestClassesDirs.files + junitConsoleJars.files + File(this.javaClass.protectionDomain.codeSource.location.toURI())
    val cpString = classpathFiles.map { it.absolutePath }.joinToString(File.pathSeparator)

    val pbArgs = mutableListOf<String>()
    pbArgs.add(javaBin)
    pbArgs.add("-cp")
    pbArgs.add(cpString)

    systemProperties.forEach { (k, v) ->
      pbArgs.add("-D$k=$v")
    }

    pbArgs.add("-Djunit.jupiter.extensions.autodetection.enabled=true")

    val propsToForward = listOf(
      "parikshan.video.enabled",
      "parikshan.video.fps",
      "parikshan.video.showCursor",
      "parikshan.video.stepDelayMs",
      "parikshan.video.postRollMs",
      "parikshan.video.granularity",
      "parikshan.video.width",
      "parikshan.video.height",
      "parikshan.wasm.headless",
      "parikshan.wasm.viewportWidth",
      "parikshan.wasm.viewportHeight",
      "parikshan.wasm.bridgeReadyTimeoutMs",
      "parikshan.wasm.windowX",
      "parikshan.wasm.windowY",
      "parikshan.wasm.appMode"
    )
    propsToForward.forEach { prop ->
      val v = System.getProperty(prop) ?: providers.gradleProperty(prop).orNull
      if (!v.isNullOrEmpty()) {
        pbArgs.add("-D$prop=$v")
      }
    }
    if (keepAlive) {
      pbArgs.add("-Dparikshan.keepAlive=true")
    }

    val reportsDir = File(buildDir.get().asFile, "test-results/e2eTest/$target/$testClass").absolutePath
    pbArgs.add("-Dparikshan.video.outputDir=" + File(buildDir.get().asFile, "parikshan/videos/$target").absolutePath)
    pbArgs.add("org.junit.platform.console.ConsoleLauncher")
    pbArgs.add("--reports-dir")
    pbArgs.add(reportsDir)
    val lastDotIdx = testsPattern.lastIndexOf('.')
    val methodPart = if (lastDotIdx >= 0) testsPattern.substring(lastDotIdx + 1) else ""
    val isMethodFilter = methodPart.isNotEmpty() && !methodPart.contains('*') && !methodPart.contains('?') && 
                         (methodPart.firstOrNull()?.isLowerCase() == true || testsPattern.length > testClass.length)
    if (isMethodFilter) {
      pbArgs.add("--select-method")
      pbArgs.add("$testClass#$methodPart")
    } else {
      pbArgs.add("--select-class")
      pbArgs.add(testClass)
    }

    val logFile = File(buildDir.get().asFile, "parikshan/logs/${target}-${testClass}.log")
    logFile.parentFile.mkdirs()

    val pb = ProcessBuilder(pbArgs)
    cleanXcodeEnv(pb)
    pb.environment()["NSAppSleepDisabled"] = "YES"
    val process = pb
      .redirectOutput(ProcessBuilder.Redirect.to(logFile))
      .redirectError(ProcessBuilder.Redirect.to(logFile))
      .start()

    synchronized(activeProcesses) {
      activeProcesses.add(process)
    }

    val exitCode = process.waitFor()

    synchronized(activeProcesses) {
      activeProcesses.remove(process)
    }

    return exitCode
  }

  private fun spawnTestJvmForClasses(
    target: String,
    testClasses: List<String>,
    systemProperties: Map<String, String>,
    activeProcesses: MutableList<Process>
  ): Int {
    if (testClasses.isEmpty()) return 0
    if (testClasses.size == 1) return spawnTestJvm(target, testClasses.first(), systemProperties, activeProcesses)

    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val javaBin = File(System.getProperty("java.home"), "bin/java" + (if (isWindows) ".exe" else "")).absolutePath

    val classpathFiles = hostTestClasspath.files + hostTestClassesDirs.files + junitConsoleJars.files + File(this.javaClass.protectionDomain.codeSource.location.toURI())
    val cpString = classpathFiles.map { it.absolutePath }.joinToString(File.pathSeparator)

    val pbArgs = mutableListOf<String>()
    pbArgs.add(javaBin)
    pbArgs.add("-cp")
    pbArgs.add(cpString)

    systemProperties.forEach { (k, v) ->
      pbArgs.add("-D$k=$v")
    }

    pbArgs.add("-Djunit.jupiter.extensions.autodetection.enabled=true")

    val propsToForward = listOf(
      "parikshan.video.enabled",
      "parikshan.video.fps",
      "parikshan.video.showCursor",
      "parikshan.video.stepDelayMs",
      "parikshan.video.postRollMs",
      "parikshan.video.granularity",
      "parikshan.video.width",
      "parikshan.video.height",
      "parikshan.wasm.headless",
      "parikshan.wasm.viewportWidth",
      "parikshan.wasm.viewportHeight",
      "parikshan.wasm.bridgeReadyTimeoutMs"
    )
    propsToForward.forEach { prop ->
      val v = System.getProperty(prop) ?: providers.gradleProperty(prop).orNull
      if (!v.isNullOrEmpty()) {
        pbArgs.add("-D$prop=$v")
      }
    }
    if (keepAlive) {
      pbArgs.add("-Dparikshan.keepAlive=true")
    }

    val reportsDir = File(buildDir.get().asFile, "test-results/e2eTest/$target").absolutePath
    pbArgs.add("-Dparikshan.video.outputDir=" + File(buildDir.get().asFile, "parikshan/videos/$target").absolutePath)
    pbArgs.add("org.junit.platform.console.ConsoleLauncher")
    pbArgs.add("--reports-dir")
    pbArgs.add(reportsDir)

    testClasses.forEach { clazz ->
      pbArgs.add("--select-class")
      pbArgs.add(clazz)
    }

    val logsDir = File(buildDir.get().asFile, "parikshan/logs")
    logsDir.mkdirs()
    logsDir.listFiles { f -> f.name.startsWith("${target}-") }?.forEach { it.delete() }

    val classWriters = testClasses.associateWith { testClass ->
      java.io.PrintWriter(File(logsDir, "${target}-${testClass}.log").bufferedWriter())
    }

    val pb = ProcessBuilder(pbArgs)
    cleanXcodeEnv(pb)
    pb.environment()["NSAppSleepDisabled"] = "YES"
    pb.redirectErrorStream(true)
    val process = pb.start()

    synchronized(activeProcesses) {
      activeProcesses.add(process)
    }

    var currentClass: String? = testClasses.firstOrNull()
    val headerLines = mutableListOf<String>()
    val summaryLines = mutableListOf<String>()
    var inSummary = false

    try {
      process.inputStream.bufferedReader().forEachLine { line ->
        var matchedClass: String? = null
        for (clazz in testClasses) {
          val simple = clazz.substringAfterLast('.')
          if (line.contains("└─  $simple") || line.contains("├─  $simple") || line.contains("Test Failures for $clazz") || line.contains("Test Failures for $simple")) {
            matchedClass = clazz
            break
          }
        }
        if (matchedClass != null) {
          currentClass = matchedClass
        }

        if (currentClass != null) {
          classWriters[currentClass]?.println(line)
        } else {
          headerLines.add(line)
        }
      }
    } catch (_: Exception) {
    } finally {
      classWriters.forEach { (_, writer) ->
        headerLines.forEach { h -> writer.println(h) }
        writer.flush()
        writer.close()
      }
    }

    val exitCode = process.waitFor()

    synchronized(activeProcesses) {
      activeProcesses.remove(process)
    }

    return exitCode
  }

  private fun printTestFailures(target: String, testClass: String) {
    val logFile = File(buildDir.get().asFile, "parikshan/logs/${target}-${testClass}.log")
    val logger = logger
    if (logFile.exists()) {
      val lines = logFile.readLines()
      val failureIndex = lines.indexOfFirst { it.trim().startsWith("Failures (") }
      if (failureIndex >= 0) {
        logger.lifecycle("\n--- [$target] Test Failures for $testClass ---")
        lines.drop(failureIndex).forEach { logger.lifecycle(it) }
        logger.lifecycle("--------------------------------------------------\n")
      } else {
        logger.lifecycle("\n--- [$target] Last 30 Log Lines for $testClass ---")
        lines.takeLast(30).forEach { logger.lifecycle(it) }
        logger.lifecycle("--------------------------------------------------\n")
      }
    }
  }

  private data class TargetMetrics(val found: Int, val started: Int, val successful: Int, val failed: Int)

  private fun parseTestMetrics(target: String, testClass: String): TargetMetrics? {
    val simpleName = testClass.substringAfterLast('.')

    val xmlReport = File(buildDir.get().asFile, "test-results/e2eTest/$target/TEST-junit-jupiter.xml")
    if (xmlReport.exists()) {
      val xmlContent = xmlReport.readText()
      val testCaseRegex = Regex("<testcase [^>]*classname=\"([^\"]+)\"[^>]*>(.*?)</testcase>", RegexOption.DOT_MATCHES_ALL)
      var found = 0
      var failed = 0
      testCaseRegex.findAll(xmlContent).forEach { match ->
        val clazz = match.groupValues[1]
        if (clazz == testClass || clazz.endsWith(".$simpleName")) {
          found++
          val body = match.groupValues[2]
          if (body.contains("<failure") || body.contains("<error")) {
            failed++
          }
        }
      }
      if (found > 0) {
        return TargetMetrics(found, found, found - failed, failed)
      }
    }

    val logFile = listOf(
      File(buildDir.get().asFile, "parikshan/logs/${target}-${simpleName}.log"),
      File(buildDir.get().asFile, "parikshan/logs/${target}-${testClass}.log")
    ).firstOrNull { it.exists() } ?: return null

    var found = 0
    var started = 0
    var successful = 0
    var failed = 0
    try {
      logFile.forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed.endsWith("tests found           ]")) {
          found = trimmed.removeSurrounding("[", "tests found           ]").trim().toIntOrNull() ?: 0
        } else if (trimmed.endsWith("tests started         ]")) {
          started = trimmed.removeSurrounding("[", "tests started         ]").trim().toIntOrNull() ?: 0
        } else if (trimmed.endsWith("tests successful      ]")) {
          successful = trimmed.removeSurrounding("[", "tests successful      ]").trim().toIntOrNull() ?: 0
        } else if (trimmed.endsWith("tests failed          ]")) {
          failed = trimmed.removeSurrounding("[", "tests failed          ]").trim().toIntOrNull() ?: 0
        }
      }
      return TargetMetrics(found, started, successful, failed)
    } catch (_: Exception) {
      return null
    }
  }

  private fun createTargetResult(target: String, success: Boolean, classes: List<String>, failureMessage: String? = null): TargetResult {
    var totalFound = 0
    var totalStarted = 0
    var totalSuccessful = 0
    var totalFailed = 0
    classes.forEach { testClass ->
      val metrics = parseTestMetrics(target, testClass)
      if (metrics != null) {
        totalFound += metrics.found
        totalStarted += metrics.started
        totalSuccessful += metrics.successful
        totalFailed += metrics.failed
      }
    }
    val detail = if (totalFound > 0) {
      if (success) {
        if (totalSuccessful == 1) {
          "1 test passed."
        } else {
          "All $totalSuccessful tests passed."
        }
      } else {
        val testWord = if (totalFound == 1) "test" else "tests"
        "$totalSuccessful/$totalFound $testWord passed ($totalFailed failed)."
      }
    } else {
      if (success) {
        if (classes.size == 1) {
          "1 test class executed successfully."
        } else {
          "All ${classes.size} test classes executed successfully."
        }
      } else {
        failureMessage ?: "Test execution failed."
      }
    }
    return TargetResult(target, success, detail)
  }

  private fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return if (mins > 0) {
      "${mins}m ${secs}s"
    } else {
      "${secs}s"
    }
  }

  private data class TargetSession(
    val token: String,
    val port: Int,
    val timestamp: Long
  )

  private fun readSession(target: String): TargetSession? {
      val sessionFile = File(buildDir.get().asFile, "parikshan/active-session.json")
      if (!sessionFile.exists()) return null
      val text = runCatching { sessionFile.readText() }.getOrNull() ?: return null
      val targetBlockRegex = Regex("\"$target\"\\s*:\\s*\\{([^}]+)}")
      val blockMatch = targetBlockRegex.find(text) ?: return null
      val blockContent = blockMatch.groupValues[1]
      
      val token = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(blockContent)?.groupValues?.get(1) ?: return null
      val port = Regex("\"port\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toIntOrNull() ?: return null
      val timestamp = Regex("\"timestamp\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toLongOrNull() ?: return null
      
      return TargetSession(token, port, timestamp)
  }

  private fun writeSession(target: String, session: TargetSession) {
      val sessionFile = File(buildDir.get().asFile, "parikshan/active-session.json")
      val sessions = mutableMapOf<String, TargetSession>()
      if (sessionFile.exists()) {
          val text = runCatching { sessionFile.readText() }.getOrNull().orEmpty()
          listOf("desktop", "wasm", "android", "ios").forEach { t ->
              val targetBlockRegex = Regex("\"$t\"\\s*:\\s*\\{([^}]+)}")
              val blockMatch = targetBlockRegex.find(text)
              if (blockMatch != null) {
                  val blockContent = blockMatch.groupValues[1]
                  val token = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(blockContent)?.groupValues?.get(1)
                  val port = Regex("\"port\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toIntOrNull()
                  val timestamp = Regex("\"timestamp\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toLongOrNull()
                  if (token != null && port != null && timestamp != null) {
                      sessions[t] = TargetSession(token, port, timestamp)
                  }
              }
          }
      }
      sessions[target] = session
      
      val json = sessions.entries.joinToString(prefix = "{", postfix = "}") { (t, s) ->
          "\"$t\":{\"token\":\"${s.token}\",\"port\":${s.port},\"timestamp\":${s.timestamp}}"
      }
      runCatching {
          sessionFile.parentFile.mkdirs()
          sessionFile.writeText(json)
      }
  }

  private fun clearSession(target: String) {
      val sessionFile = File(buildDir.get().asFile, "parikshan/active-session.json")
      if (!sessionFile.exists()) return
      val sessions = mutableMapOf<String, TargetSession>()
      val text = runCatching { sessionFile.readText() }.getOrNull().orEmpty()
      listOf("desktop", "wasm", "android", "ios").forEach { t ->
          if (t == target) return@forEach
          val targetBlockRegex = Regex("\"$t\"\\s*:\\s*\\{([^}]+)}")
          val blockMatch = targetBlockRegex.find(text)
          if (blockMatch != null) {
              val blockContent = blockMatch.groupValues[1]
              val token = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(blockContent)?.groupValues?.get(1)
              val port = Regex("\"port\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toIntOrNull()
              val timestamp = Regex("\"timestamp\"\\s*:\\s*(\\d+)").find(blockContent)?.groupValues?.get(1)?.toLongOrNull()
              if (token != null && port != null && timestamp != null) {
                  sessions[t] = TargetSession(token, port, timestamp)
              }
          }
      }
      
      val json = sessions.entries.joinToString(prefix = "{", postfix = "}") { (t, s) ->
          "\"$t\":{\"token\":\"${s.token}\",\"port\":${s.port},\"timestamp\":${s.timestamp}}"
      }
      runCatching {
          sessionFile.writeText(json)
      }
  }

  private fun checkTargetHealth(host: String, port: Int, token: String): Boolean {
      var conn: java.net.HttpURLConnection? = null
      return try {
          val url = java.net.URL("http://$host:$port/health?token=$token")
          conn = url.openConnection() as java.net.HttpURLConnection
          conn.requestMethod = "GET"
          conn.connectTimeout = 1000
          conn.readTimeout = 1000
          val code = conn.responseCode
          if (code == 200) {
              val body = conn.inputStream.bufferedReader().use { it.readText() }
              body.contains("health")
          } else {
              false
          }
      } catch (_: Exception) {
          false
      } finally {
          runCatching { conn?.disconnect() }
      }
  }

  private fun isTargetFresh(target: String): Boolean {
      val maxSourceTimestamp = getProductionSourceTimestamp()
      val minBinaryTimestamp = getTargetOutputTimestamp(target)
      if (minBinaryTimestamp == 0L) return false
      return minBinaryTimestamp >= maxSourceTimestamp
  }

  private fun getProductionSourceTimestamp(): Long {
      if (productionSources.isEmpty) return 0L
      val buildDirCanonical = runCatching { buildDir.get().asFile.canonicalPath }.getOrNull()
      val fileList = productionSources.files
          .flatMap { file ->
              if (file.isDirectory) {
                  file.walkTopDown()
                      .filter { it.isFile }
                      .filter { buildDirCanonical == null || !it.canonicalPath.startsWith(buildDirCanonical) }
                      .map { it to it.lastModified() }
                      .toList()
              } else {
                  if (buildDirCanonical != null && file.canonicalPath.startsWith(buildDirCanonical)) {
                      emptyList()
                  } else {
                      listOf(file to file.lastModified())
                  }
              }
          }
      val maxFile = fileList.maxByOrNull { it.second }
      return maxFile?.second ?: 0L
  }

  private fun getTargetOutputTimestamp(target: String): Long {
      val files = getTargetOutputFiles(target)
      val classesTime = if (target == "desktop" && !productionClassesDirs.isEmpty) {
          productionClassesDirs.files.map { classDir ->
              if (classDir.exists() && classDir.isDirectory) {
                  classDir.walkTopDown().filter { it.isFile }.map { it.lastModified() }.maxOrNull() ?: 0L
              } else 0L
          }.maxOrNull() ?: 0L
      } else 0L

      if (files.isEmpty()) return classesTime
      val filesTime = files.map { file ->
          if (file.isDirectory) {
              file.walkTopDown().filter { it.isFile }.map { it.lastModified() }.minOrNull() ?: 0L
          } else {
              file.lastModified()
          }
      }.minOrNull() ?: 0L

      return maxOf(filesTime, classesTime)
  }

  private fun getTargetOutputFiles(target: String): List<File> {
      return when (target) {
          "desktop" -> {
              if (appJarFile.isPresent) listOf(appJarFile.get().asFile).filter { it.exists() } else emptyList()
          }
          "wasm" -> {
              if (wasmOutputDir.isPresent) listOf(wasmOutputDir.get().asFile).filter { it.exists() } else emptyList()
          }
          "android" -> {
              if (androidApkDir.isPresent && androidApkDir.get().asFile.exists()) {
                  androidApkDir.get().asFile.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
              } else emptyList()
          }
          "ios" -> {
              if (iosAppDir.isPresent && iosAppDir.get().asFile.exists()) {
                  iosAppDir.get().asFile.walkTopDown().filter { it.isDirectory && it.name.endsWith(".app") }.toList()
              } else emptyList()
          }
          else -> emptyList()
      }
  }

  private inline fun withTargetLock(target: String, block: () -> TargetResult): TargetResult {
    val lockFile = File(buildDir.get().asFile, "parikshan/locks/$target.lock")
    lockFile.parentFile.mkdirs()

    while (true) {
      if (lockFile.exists()) {
        val pid = runCatching { lockFile.readText().trim().toLongOrNull() }.getOrNull()
        if (pid != null) {
          val isAlive = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
          if (!isAlive) {
            lockFile.delete()
          }
        }
      }

      val raf = java.io.RandomAccessFile(lockFile, "rw")
      val channel = raf.channel
      var lock: java.nio.channels.FileLock? = null
      val startTime = System.currentTimeMillis()
      var logged = false

      try {
        var staleBreak = false
        while (true) {
          try {
            lock = channel.tryLock()
            if (lock != null) {
              raf.setLength(0)
              raf.writeBytes(ProcessHandle.current().pid().toString())
              break
            }
          } catch (_: Exception) {}

          if (lock == null && lockFile.exists()) {
            val pid = runCatching { lockFile.readText().trim().toLongOrNull() }.getOrNull()
            if (pid != null && !ProcessHandle.of(pid).map { it.isAlive }.orElse(false)) {
              runCatching { channel.close() }
              runCatching { raf.close() }
              lockFile.delete()
              staleBreak = true
              break
            }
          }

          if (!logged) {
            logger.lifecycle("Parikshan [$target]: Waiting for target process lock (another test run is active)...")
            logged = true
          }

          if (System.currentTimeMillis() - startTime > 300000) {
            return TargetResult(target, false, "Timeout waiting for target process lock. Another process is executing tests on target '$target'.")
          }
          Thread.sleep(1000)
        }

        if (staleBreak) {
          continue
        }

        return block()
      } finally {
        runCatching { lock?.release() }
        runCatching { channel.close() }
        runCatching { raf.close() }
      }
    }
  }

  private data class DevicePortState(val isBusy: Boolean, val parikshanAppId: String?)

  private fun checkDevicePortState(serial: String, devicePort: Int, hostAddress: String = "127.0.0.1"): DevicePortState {
      var tempHostPort = 12000
      while (tempHostPort <= 20000) {
          if (PortConflictHandler.isPortAvailable(hostAddress, tempHostPort)) {
              break
          }
          tempHostPort++
      }
      if (tempHostPort > 20000) return DevicePortState(true, null)

      val forwardProcess = ProcessBuilder("adb", "-s", serial, "forward", "tcp:$tempHostPort", "tcp:$devicePort").start()
      val exit = forwardProcess.waitFor()
      if (exit != 0) return DevicePortState(true, null)

      val isBusy = try {
          java.net.Socket().use { socket ->
              socket.connect(java.net.InetSocketAddress(hostAddress, tempHostPort), 250)
              socket.soTimeout = 250
              val readVal = socket.inputStream.read()
              readVal != -1
          }
      } catch (_: java.net.SocketTimeoutException) {
          true
      } catch (_: Exception) {
          false
      }
      val appId = if (isBusy) PortConflictHandler.queryApplicationId(hostAddress, tempHostPort) else null

      ProcessBuilder("adb", "-s", serial, "forward", "--remove", "tcp:$tempHostPort").start().waitFor()
      return DevicePortState(isBusy, appId)
  }

  private fun executeSyncTarget(
    activeTargets: List<String>,
    classes: List<String>,
    activeProcesses: MutableList<Process>,
    finalAndroidSerial: String?,
    finalIosDevice: String
  ): TargetResult {
    val logger = logger
    logger.lifecycle("Parikshan [sync]: Starting synchronized E2E target execution for ${activeTargets.joinToString()}...")

    return withTargetsLock(activeTargets) {
      val desktopPort = java.util.concurrent.atomic.AtomicInteger(0)
      val desktopToken = java.util.concurrent.atomic.AtomicReference("")
      val wasmPort = java.util.concurrent.atomic.AtomicInteger(0)
      val wasmToken = java.util.concurrent.atomic.AtomicReference("")
      val androidPort = java.util.concurrent.atomic.AtomicInteger(0)
      val androidToken = java.util.concurrent.atomic.AtomicReference("")
      val androidSerial = java.util.concurrent.atomic.AtomicReference<String?>(null)
      val iosPortVal = java.util.concurrent.atomic.AtomicInteger(0)
      val iosTokenVal = java.util.concurrent.atomic.AtomicReference("")
      val iosUdidVal = java.util.concurrent.atomic.AtomicReference("")
      val bundleId = iosBundleId.orNull ?: ""

      var bootSuccess = false
      try {
        val gradleReclaimPorts = providers.gradleProperty("parikshan.reclaimPorts").orNull?.toBoolean() ?: false
        val threads = mutableListOf<Thread>()
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()

        if (activeTargets.contains("desktop") || activeTargets.contains("jvm")) {
          threads.add(Thread {
            try {
              val session = if (keepAlive) readSession("desktop") else null
              val minBinaryTimestamp = getTargetOutputTimestamp("desktop")
              val healthy = session != null && checkTargetHealth(host.get(), session.port, session.token)
              val fresh = isTargetFresh("desktop")
              val canReuse = session != null && healthy && fresh && session.timestamp >= minBinaryTimestamp

              val optDesktopSize = desktopWindowSize.takeIf { it.isNotBlank() } ?: windowSize
              val size = parseSize(optDesktopSize)
              val pos = parsePosition(desktopWindowPosition) ?: if (layout == "side-by-side") Pair(10, 50) else null
              if (size != null) {
                System.setProperty("parikshan.desktop.windowWidth", size.first.toString())
                System.setProperty("parikshan.desktop.windowHeight", size.second.toString())
              } else {
                System.clearProperty("parikshan.desktop.windowWidth")
                System.clearProperty("parikshan.desktop.windowHeight")
              }
              if (pos != null) {
                System.setProperty("parikshan.desktop.windowX", pos.first.toString())
                System.setProperty("parikshan.desktop.windowY", pos.second.toString())
              } else {
                System.clearProperty("parikshan.desktop.windowX")
                System.clearProperty("parikshan.desktop.windowY")
              }

              val resolvedPort = if (canReuse && session != null) {
                logger.lifecycle("Parikshan [desktop]: Keeping active instance alive (skipping build/launch).")
                session.port
              } else {
                if (session != null) {
                  logger.lifecycle("Parikshan [desktop]: Active instance is stale or unhealthy. Relaunching...")
                  DesktopProcess.stop(
                    host = host.get(),
                    port = session.port,
                    token = session.token,
                    manifestFile = desktopLaunchManifestFile.get().asFile
                  )
                }
                val port = PortConflictHandler.resolvePortAndCleanStale(
                  originalPort = originalDesktopPort.get(),
                  host = host.get(),
                  logger = logger
                )
                DesktopProcess.start(
                  jar = appJarFile.get().asFile,
                  token = token.get(),
                  logFile = File(buildDir.get().asFile, "parikshan/desktop-app-logs.log"),
                  manifestFile = desktopLaunchManifestFile.get().asFile,
                  appArgs = appArgs.get(),
                  host = host.get(),
                  port = port,
                  timeoutMs = 15000L,
                  pollMs = 250L,
                  title = title.orNull,
                  background = true
                )
                writeSession("desktop", TargetSession(token.get(), port, System.currentTimeMillis()))
                port
              }

              desktopToken.set(if (canReuse && session != null) session.token else token.get())
              desktopPort.set(resolvedPort)
            } catch (e: Throwable) {
              errors.add(e)
            }
          }.apply { name = "parikshan-boot-desktop"; start() })
        }

        if (activeTargets.contains("wasm")) {
          threads.add(Thread {
            try {
              val session = if (keepAlive) readSession("wasm") else null
              val minBinaryTimestamp = getTargetOutputTimestamp("wasm")
              val canReuse = session != null && 
                             checkTargetHealth("127.0.0.1", session.port, session.token) && 
                             isTargetFresh("wasm") && 
                             session.timestamp >= minBinaryTimestamp

              val resolvedPort = if (canReuse && session != null) {
                logger.lifecycle("Parikshan [wasm]: Keeping active instance alive (skipping build/launch).")
                session.port
              } else {
                if (session != null) {
                  logger.lifecycle("Parikshan [wasm]: Active instance is stale or unhealthy. Relaunching...")
                  WasmServer.stop()
                }
                val port = PortConflictHandler.resolvePortAndCleanStale(
                  originalPort = originalWasmPort.get(),
                  host = "127.0.0.1",
                  logger = logger
                )
                val portFile = wasmPortFile.get().asFile
                portFile.parentFile.mkdirs()
                portFile.writeText(port.toString())

                WasmServer.start(port, wasmOutputDir.get().asFile)
                writeSession("wasm", TargetSession(token.get(), port, System.currentTimeMillis()))
                port
              }

              wasmToken.set(if (canReuse && session != null) session.token else token.get())
              wasmPort.set(resolvedPort)
            } catch (e: Throwable) {
              errors.add(e)
            }
          }.apply { name = "parikshan-boot-wasm"; start() })
        }

        if (activeTargets.contains("android")) {
          threads.add(Thread {
            try {
              val serial = AndroidTargetConfigurer.AndroidRecorder.resolveDeviceSerial(logger, File(projectRootDir.get()), finalAndroidSerial)
              androidSerial.set(serial)
              val session = if (keepAlive) readSession("android") else null
              val resolvedAndroidPort = session?.port ?: 9879
              val resolvedToken = session?.token ?: token.get()
              val isHealthy = session != null && checkTargetHealth("127.0.0.1", resolvedAndroidPort, resolvedToken)
              val isFresh = isTargetFresh("android")
              val minBinaryTimestamp = getTargetOutputTimestamp("android")
              val canReuse = isHealthy && isFresh && session!!.timestamp >= minBinaryTimestamp

              val activeToken = if (canReuse && session != null) session.token else token.get()
              var activePort = if (canReuse && session != null) session.port else 9879
              val gradlew = getGradlewExecutable(File(projectRootDir.get()))

              androidPort.set(activePort)
              androidToken.set(activeToken)

              if (canReuse) {
                logger.lifecycle("Parikshan [android]: Keeping active instance alive (skipping build/launch).")
              } else {
                val reclaim = reclaimPorts || gradleReclaimPorts
                val devicePortState = checkDevicePortState(serial, 9879)
                if (devicePortState.isBusy || !PortConflictHandler.isPortAvailable("127.0.0.1", 9879)) {
                  val activeAppId = devicePortState.parikshanAppId
                  if (activeAppId != null) {
                    if (activeAppId == androidApplicationId.get()) {
                      ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", androidApplicationId.get()).start().waitFor()
                      ProcessBuilder("adb", "-s", serial, "forward", "--remove", "tcp:9879").start().waitFor()
                      Thread.sleep(500)
                    } else if (reclaim) {
                      logger.lifecycle("Parikshan [android]: Port 9879 was held by '${activeAppId}'. Reclaiming port via --reclaim-ports...")
                      ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", activeAppId).start().waitFor()
                      ProcessBuilder("adb", "-s", serial, "forward", "--remove", "tcp:9879").start().waitFor()
                      Thread.sleep(500)
                    } else {
                      var fbPort = 9880
                      while (fbPort <= 65535) {
                        if (PortConflictHandler.isPortAvailable("127.0.0.1", fbPort) && !checkDevicePortState(serial, fbPort).isBusy) {
                          break
                        }
                        fbPort++
                      }
                      logger.lifecycle("Parikshan [android]: Port 9879 is held by another active application '${activeAppId}'. Falling back to host port $fbPort.")
                      activePort = fbPort
                    }
                  } else {
                    var fbPort = 9880
                    while (fbPort <= 65535) {
                      if (PortConflictHandler.isPortAvailable("127.0.0.1", fbPort) && !checkDevicePortState(serial, fbPort).isBusy) {
                        break
                      }
                      fbPort++
                    }
                    logger.lifecycle("Parikshan [android]: Port 9879 is occupied by a non-Parikshan process or busy on device. Falling back to host port $fbPort.")
                    activePort = fbPort
                  }
                }

                androidPort.set(activePort)

                if (session != null) {
                  logger.lifecycle("Parikshan [android]: Active instance is stale or unhealthy. Relaunching...")
                  ProcessBuilder("adb", "-s", serial, "forward", "--remove", "tcp:${session.port}").start().waitFor()
                  ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", androidApplicationId.get()).start().waitFor()
                }
                logger.lifecycle("Parikshan [android]: Device detected. Starting E2E execution on port $activePort...")
                // 1. Start App
                val startArgs = mutableListOf(
                  gradlew, "$projectPathPrefix:startParikshanAndroidApp",
                  "-Pparikshan.token=${token.get()}",
                  "-Pparikshan.port=$activePort",
                  "-Pparikshan.e2e.active=true"
                )
                if (!serial.isNullOrBlank()) {
                  startArgs.add("-Pparikshan.android.serial=$serial")
                }
                val startProcess = ProcessBuilder(startArgs).apply {
                  cleanXcodeEnv(this)
                  redirectErrorStream(true)
                  val logF = File(buildDir.get().asFile, "parikshan/logs/android-start.log")
                  logF.parentFile.mkdirs()
                  redirectOutput(logF)
                }.start()
                val startExit = startProcess.waitFor()
                if (startExit != 0) {
                  throw GradleException("Failed to start Android app (exit code $startExit). Check build/parikshan/logs/android-start.log")
                }
                writeSession("android", TargetSession(token.get(), activePort, System.currentTimeMillis()))
              }
            } catch (e: Throwable) {
              errors.add(e)
            }
          }.apply { name = "parikshan-boot-android"; start() })
        }

        if (activeTargets.contains("ios")) {
          threads.add(Thread {
            try {
              val udid = getIosSimulatorUdid(finalIosDevice) ?: getBootedIosSimulatorUdid() ?: ""
              iosUdidVal.set(udid)
              val session = if (keepAlive) readSession("ios") else null
              val resolvedIosPort = session?.port ?: iosPort.get()
              val resolvedToken = session?.token ?: token.get()
              val isHealthy = session != null && checkTargetHealth("127.0.0.1", resolvedIosPort, resolvedToken)
              val isFresh = isTargetFresh("ios")
              val minBinaryTimestamp = getTargetOutputTimestamp("ios")
              val canReuse = isHealthy && isFresh && session!!.timestamp >= minBinaryTimestamp

              val activeToken = if (canReuse && session != null) session.token else token.get()
              var activePort = if (canReuse && session != null) session.port else iosPort.get()
              val gradlew = getGradlewExecutable(File(projectRootDir.get()))

              iosPortVal.set(activePort)
              iosTokenVal.set(activeToken)

              if (canReuse) {
                logger.lifecycle("Parikshan [ios]: Keeping active instance alive (skipping build/launch).")
              } else {
                val reclaim = reclaimPorts || gradleReclaimPorts
                val defaultIosPort = iosPort.get()

                if (!PortConflictHandler.isPortAvailable("127.0.0.1", defaultIosPort)) {
                  val activeAppId = PortConflictHandler.queryApplicationId("127.0.0.1", defaultIosPort)
                  if (activeAppId != null) {
                    if (activeAppId == bundleId) {
                      ProcessBuilder("xcrun", "simctl", "terminate", udid, bundleId).start().waitFor()
                      Thread.sleep(500)
                    } else if (reclaim) {
                      logger.lifecycle("Parikshan [ios]: Port $defaultIosPort was held by '${activeAppId}'. Reclaiming port via --reclaim-ports...")
                      ProcessBuilder("xcrun", "simctl", "terminate", udid, activeAppId).start().waitFor()
                      Thread.sleep(500)
                    } else {
                      var fbPort = defaultIosPort + 3
                      while (fbPort <= 65535) {
                        if (PortConflictHandler.isPortAvailable("127.0.0.1", fbPort)) {
                          break
                        }
                        fbPort++
                      }
                      logger.lifecycle("Parikshan [ios]: Port $defaultIosPort is held by another active application '${activeAppId}'. Falling back to host port $fbPort.")
                      activePort = fbPort
                    }
                  } else {
                    var fbPort = defaultIosPort + 3
                    while (fbPort <= 65535) {
                      if (PortConflictHandler.isPortAvailable("127.0.0.1", fbPort)) {
                        break
                      }
                      fbPort++
                    }
                    logger.lifecycle("Parikshan [ios]: Port $defaultIosPort is occupied by a non-Parikshan process. Falling back to host port $fbPort.")
                    activePort = fbPort
                  }
                }

                iosPortVal.set(activePort)

                if (session != null) {
                  logger.lifecycle("Parikshan [ios]: Active instance is stale or unhealthy. Relaunching...")
                  val stopProcess = ProcessBuilder(
                    gradlew, 
                    "$projectPathPrefix:stopIosApp",
                    "-Pparikshan.ios.device=$finalIosDevice",
                    "-Pparikshan.ios.port=${session.port}"
                  ).apply {
                    cleanXcodeEnv(this)
                  }.start()
                  stopProcess.waitFor()
                }
                logger.lifecycle("Parikshan [ios]: Simulator target: '$finalIosDevice' ($udid). Starting E2E execution on port $activePort...")
                // 1. Start App
                val startProcess = ProcessBuilder(
                  gradlew, 
                  "$projectPathPrefix:startIosApp", 
                  "-Pparikshan.token=${token.get()}", 
                  "-Pparikshan.ios.port=$activePort",
                  "-Pparikshan.e2e.active=true",
                  "-Pparikshan.ios.device=$finalIosDevice"
                ).apply {
                  cleanXcodeEnv(this)
                  redirectErrorStream(true)
                  val logF = File(buildDir.get().asFile, "parikshan/logs/ios-start.log")
                  logF.parentFile.mkdirs()
                  redirectOutput(logF)
                }.start()
                val startExit = startProcess.waitFor()
                if (startExit != 0) {
                  throw GradleException("Failed to start iOS app (exit code $startExit). Check build/parikshan/logs/ios-start.log")
                }
                writeSession("ios", TargetSession(token.get(), activePort, System.currentTimeMillis()))
              }
            } catch (e: Throwable) {
              errors.add(e)
            }
          }.apply { name = "parikshan-boot-ios"; start() })
        }

        threads.filter { it.name == "parikshan-boot-desktop" || it.name == "parikshan-boot-wasm" }.forEach { it.join() }
        
        while (activeTargets.contains("android") && androidPort.get() == 0) {
          if (errors.isNotEmpty()) {
            throw errors.first()
          }
          Thread.sleep(10)
        }
        while (activeTargets.contains("ios") && iosPortVal.get() == 0) {
          if (errors.isNotEmpty()) {
            throw errors.first()
          }
          Thread.sleep(10)
        }

        if (errors.isNotEmpty()) {
          throw errors.first()
        }

        bootSuccess = true
      } catch (e: Throwable) {
        logger.error("Parikshan [sync]: Error during synchronization boot phase: ${e.message}", e)
        cleanupSyncTargets(
          activeTargets = activeTargets,
          keepAlive = false,
          runSuccess = false,
          desktopPort = desktopPort.get(),
          desktopToken = desktopToken.get(),
          wasmPort = wasmPort.get(),
          androidSerial = androidSerial.get(),
          iosPortVal = iosPortVal.get(),
          finalIosDevice = finalIosDevice
        )
        return@withTargetsLock TargetResult("sync", false, "Boot phase failed: ${e.message}")
      }

      var runSuccess = true
      var testFailureMessage: String? = null

      try {
        val systemProps = mutableMapOf<String, String>()
        systemProps["parikshan.target"] = "sync"
        systemProps["parikshan.sync.targets"] = activeTargets.joinToString(",")
        systemProps["parikshan.token"] = token.get()

        if (activeTargets.contains("desktop") || activeTargets.contains("jvm")) {
          systemProps["parikshan.desktop.host"] = host.get()
          systemProps["parikshan.desktop.port"] = desktopPort.get().toString()
          systemProps["parikshan.desktop.launchManifest"] = desktopLaunchManifestFile.get().asFile.absolutePath

          System.getProperty("parikshan.desktop.windowX")?.let { systemProps["parikshan.desktop.windowX"] = it }
          System.getProperty("parikshan.desktop.windowY")?.let { systemProps["parikshan.desktop.windowY"] = it }
          System.getProperty("parikshan.desktop.windowWidth")?.let { systemProps["parikshan.desktop.windowWidth"] = it }
          System.getProperty("parikshan.desktop.windowHeight")?.let { systemProps["parikshan.desktop.windowHeight"] = it }
        }
        if (activeTargets.contains("wasm")) {
          systemProps["parikshan.wasm.url"] = "http://127.0.0.1:${wasmPort.get()}"

          val isAppModeActive = appMode || (layout == "side-by-side")
          systemProps["parikshan.wasm.appMode"] = isAppModeActive.toString()

          val resolvedWasmSize = parseSize(wasmWindowSize.takeIf { it.isNotBlank() } ?: windowSize) ?: Pair(1280, 600)
          systemProps["parikshan.wasm.viewportWidth"] = resolvedWasmSize.first.toString()
          systemProps["parikshan.wasm.viewportHeight"] = resolvedWasmSize.second.toString()

          val resolvedWasmPos = if (layout == "side-by-side") {
            val resolvedDesktopSize = parseSize(desktopWindowSize.takeIf { it.isNotBlank() } ?: windowSize) ?: Pair(800, 600)
            val resolvedDesktopPos = parsePosition(desktopWindowPosition) ?: Pair(10, 50)
            Pair(resolvedDesktopPos.first + resolvedDesktopSize.first, resolvedDesktopPos.second)
          } else {
            parsePosition(wasmWindowPosition)
          }
          if (resolvedWasmPos != null) {
            systemProps["parikshan.wasm.windowX"] = resolvedWasmPos.first.toString()
            systemProps["parikshan.wasm.windowY"] = resolvedWasmPos.second.toString()
          }
        }
        if (activeTargets.contains("android")) {
          systemProps["parikshan.android.host"] = "127.0.0.1"
          systemProps["parikshan.android.port"] = androidPort.get().toString()
          val serialVal = androidSerial.get()
          if (!serialVal.isNullOrBlank()) {
            systemProps["parikshan.android.serial"] = serialVal
          }
        }
        if (activeTargets.contains("ios")) {
          systemProps["parikshan.ios.host"] = "127.0.0.1"
          systemProps["parikshan.ios.port"] = iosPortVal.get().toString()
          systemProps["parikshan.ios.udid"] = iosUdidVal.get()
          systemProps["parikshan.ios.bundleId"] = bundleId
          systemProps["parikshan.ios.device"] = finalIosDevice
        }

        classes.forEach { testClass ->
          logger.lifecycle("Parikshan [sync]: Running $testClass...")
          val exitCode = spawnTestJvm(
            target = "sync",
            testClass = testClass,
            systemProperties = systemProps,
            activeProcesses = activeProcesses
          )
          if (exitCode != 0) {
            runSuccess = false
            printTestFailures("sync", testClass)
            testFailureMessage = "Test class $testClass failed (exit code $exitCode). Check logs at build/parikshan/logs/sync-${testClass.substringAfterLast('.')}.log"
          }
        }
      } finally {
        cleanupSyncTargets(
          activeTargets = activeTargets,
          keepAlive = keepAlive,
          runSuccess = runSuccess,
          desktopPort = desktopPort.get(),
          desktopToken = desktopToken.get(),
          wasmPort = wasmPort.get(),
          androidSerial = androidSerial.get(),
          iosPortVal = iosPortVal.get(),
          finalIosDevice = finalIosDevice
        )
      }

      if (testFailureMessage != null) {
        TargetResult("sync", false, testFailureMessage!!)
      } else {
        TargetResult("sync", true, "All test classes passed")
      }
    }
  }

  private fun cleanupSyncTargets(
    activeTargets: List<String>,
    keepAlive: Boolean,
    runSuccess: Boolean,
    desktopPort: Int,
    desktopToken: String,
    wasmPort: Int,
    androidSerial: String?,
    iosPortVal: Int,
    finalIosDevice: String
  ) {
    val logger = logger
    if (activeTargets.contains("desktop") || activeTargets.contains("jvm")) {
      if (keepAlive && runSuccess) {
        val manifest = desktopLaunchManifestFile.get().asFile
        if (manifest.exists()) {
          val props = java.util.Properties()
          runCatching { manifest.inputStream().use { props.load(it) } }
          val finalPort = props.getProperty("port")?.toIntOrNull() ?: desktopPort
          val finalToken = props.getProperty("token") ?: desktopToken
          writeSession("desktop", TargetSession(finalToken, finalPort, System.currentTimeMillis()))
        } else {
          writeSession("desktop", TargetSession(desktopToken, desktopPort, System.currentTimeMillis()))
        }
      } else {
        DesktopProcess.stop(
          host = host.get(),
          port = desktopPort,
          token = desktopToken,
          manifestFile = desktopLaunchManifestFile.get().asFile
        )
        clearSession("desktop")
      }
    }
    if (activeTargets.contains("wasm")) {
      if (!keepAlive || !runSuccess) {
        WasmServer.stop()
        clearSession("wasm")
      }
    }
    if (activeTargets.contains("android")) {
      if (!keepAlive || !runSuccess) {
        val gradlew = getGradlewExecutable(File(projectRootDir.get()))
        val stopArgs = mutableListOf(gradlew, "$projectPathPrefix:stopParikshanAndroidApp")
        if (!androidSerial.isNullOrBlank()) {
          stopArgs.add("-Pparikshan.android.serial=$androidSerial")
        }
        ProcessBuilder(stopArgs).apply { cleanXcodeEnv(this) }.start().waitFor()
        clearSession("android")
      }
    }
    if (activeTargets.contains("ios")) {
      if (!keepAlive || !runSuccess) {
        val gradlew = getGradlewExecutable(File(projectRootDir.get()))
        ProcessBuilder(
          gradlew, 
          "$projectPathPrefix:stopIosApp",
          "-Pparikshan.ios.device=$finalIosDevice",
          "-Pparikshan.ios.port=$iosPortVal"
        ).apply { cleanXcodeEnv(this) }.start().waitFor()
        clearSession("ios")
      }
    }
  }

  private fun <T> withTargetsLock(targets: List<String>, index: Int = 0, block: () -> T): T {
    val sortedTargets = targets.distinct().sorted()
    return withTargetsLockSorted(sortedTargets, index, block)
  }

  private fun <T> withTargetsLockSorted(targets: List<String>, index: Int = 0, block: () -> T): T {
    if (index >= targets.size) {
      return block()
    }
    val target = targets[index]
    val lockFile = File(buildDir.get().asFile, "parikshan/locks/$target.lock")
    lockFile.parentFile.mkdirs()
    val raf = java.io.RandomAccessFile(lockFile, "rw")
    val channel = raf.channel
    var lock: java.nio.channels.FileLock? = null
    try {
      while (true) {
        try {
          lock = channel.tryLock()
          if (lock != null) break
        } catch (_: java.nio.channels.OverlappingFileLockException) {}
        Thread.sleep(100)
      }
      return withTargetsLockSorted(targets, index + 1, block)
    } finally {
      try { lock?.release() } catch (_: Exception) {}
      try { channel.close() } catch (_: Exception) {}
      try { raf.close() } catch (_: Exception) {}
    }
  }

  private fun runWatchLoop(
    activeTargets: List<String>,
    filteredClasses: List<String>,
    activeProcesses: MutableList<Process>,
    activeForwardedPorts: MutableSet<Int>,
    resolvedHost: String,
    finalAndroidSerial: String?,
    finalIosDevice: String
  ) {
      val logger = logger
      logger.lifecycle("\n========================================")
      logger.lifecycle("   Parikshan WATCH Mode Initialized     ")
      logger.lifecycle("========================================")

      val originalKeepAlive = keepAlive
      keepAlive = true

      var totalPassed = 0
      var totalFailed = 0

      var compileJob: Process? = null
      val runLock = Any()

      fun executeCycle(modifiedFile: Path?) {
          synchronized(runLock) {
              val startTime = System.currentTimeMillis()
              logger.lifecycle("\n========================================")
              if (modifiedFile != null) {
                  logger.lifecycle("[WATCH] Change detected: ${modifiedFile.fileName}")
              } else {
                  logger.lifecycle("[WATCH] Running initial test suite...")
              }

              // On source change (not initial run), rebuild the project
              if (modifiedFile != null) {
                  val buildCommand = if (!compileCommand.isBlank()) {
                      compileCommand
                  } else {
                      val gradlew = getGradlewExecutable(File(projectRootDir.get()))
                      val packageTask = activeTargets.mapNotNull { target ->
                          when (target) {
                              "desktop" -> "${projectPath.get()}:packageUberJarForCurrentOS"
                              "wasm" -> "${projectPath.get()}:compileKotlinWasmJs"
                              else -> null
                          }
                      }.joinToString(" ")
                      val testCompileTask = "${projectPath.get()}:compileTestKotlinJvm"
                      "$gradlew $packageTask $testCompileTask -Pparikshan.e2e.active=true --no-daemon -q"
                  }

                  logger.lifecycle("Rebuilding... ($buildCommand)")
                  val pb = if (System.getProperty("os.name").lowercase().contains("win")) {
                      ProcessBuilder("cmd.exe", "/c", buildCommand)
                  } else {
                      ProcessBuilder("sh", "-c", buildCommand)
                  }
                  pb.directory(File(projectRootDir.get()))
                  pb.redirectErrorStream(true)
                  pb.inheritIO()
                  
                  val process = pb.start()
                  compileJob = process
                  val exitCode = process.waitFor()
                  compileJob = null

                  if (exitCode != 0) {
                      logger.lifecycle("[ERROR] Build failed (Exit code: $exitCode)")
                      logger.lifecycle("========================================")
                      logger.lifecycle("WATCHING: Waiting for file changes... (Total: $totalPassed PASSED, $totalFailed FAILED)")
                      writeWatchResults(false, listOf(TargetResult("build", false, "Build failed (exit code $exitCode)")))
                      return
                  }
                  logger.lifecycle("Build successful.")
              }

              logger.lifecycle("Executing E2E tests for targets: ${activeTargets.joinToString()}...")
              
              val results = mutableListOf<TargetResult>()
              
              if (sync) {
                  val targetStartTime = System.currentTimeMillis()
                  val result = try {
                      executeSyncTarget(activeTargets, filteredClasses, activeProcesses, finalAndroidSerial, finalIosDevice)
                  } catch (e: Exception) {
                      TargetResult("sync", false, e.message ?: "Sync execution failed")
                  }
                  val duration = System.currentTimeMillis() - targetStartTime
                  results.add(result.copy(durationMs = duration))
              } else {
                  activeTargets.forEach { target ->
                      val targetStartTime = System.currentTimeMillis()
                      val result = try {
                          executeTarget(target, filteredClasses, activeProcesses, activeForwardedPorts, resolvedHost, finalAndroidSerial, finalIosDevice)
                      } catch (e: Exception) {
                          TargetResult(target, false, e.message ?: "Execution failed")
                      }
                      val duration = System.currentTimeMillis() - targetStartTime
                      results.add(result.copy(durationMs = duration))
                  }
              }

              val anyFailure = results.any { !it.success }
              val durationStr = formatDuration(System.currentTimeMillis() - startTime)

              logger.lifecycle("----------------------------------------")
              results.forEach { res ->
                  val status = if (res.success) "SUCCESS" else "FAILED"
                  logger.lifecycle("[${res.target.uppercase()}] $status - ${res.message}")
              }
              logger.lifecycle("========================================")
              
              val passedCount = results.count { it.success }
              val failedCount = results.count { !it.success }
              
              if (anyFailure) {
                  logger.lifecycle("WATCHING: Waiting for file changes... (Latest: $passedCount PASSED, $failedCount FAILED) - Last run failed after $durationStr")
                  writeWatchResults(false, results)
              } else {
                  logger.lifecycle("WATCHING: Waiting for file changes... (Latest: $passedCount PASSED, $failedCount FAILED) - Last run succeeded in $durationStr")
                  writeWatchResults(true, results)
              }
          }
      }

      executeCycle(null)

      val watchRoots = mutableListOf<File>()
      
      // Always watch source files — compile externally on change
      watchRoots.addAll(productionSources.files)
      watchRoots.addAll(testSources.files)
      logger.lifecycle("[WATCH] Monitoring source files for changes...")

      var lastEventTime = 0L
      val debounceMs = 500L
      val pendingChange = java.util.concurrent.atomic.AtomicReference<Path?>(null)

      val watcher = PollingWatcher(watchRoots) { file ->
          val now = System.currentTimeMillis()
          pendingChange.set(file.toPath())
          
          synchronized(activeProcesses) {
              try { compileJob?.destroy() } catch (_: Exception) {}
              activeProcesses.forEach {
                  try { it.destroy() } catch (_: Exception) {}
              }
          }

          lastEventTime = now
      }

      try {
          while (true) {
              Thread.sleep(100)
              val path = pendingChange.get()
              if (path != null && System.currentTimeMillis() - lastEventTime >= debounceMs) {
                  pendingChange.compareAndSet(path, null)
                  try {
                      executeCycle(path)
                  } catch (e: Exception) {
                      logger.lifecycle("[WATCH] Error in watch cycle: ${e.message}")
                  }
              }
          }
      } catch (e: InterruptedException) {
          logger.lifecycle("Watch mode interrupted.")
      } finally {
          watcher.close()
          keepAlive = originalKeepAlive
          
          if (!keepAlive) {
              logger.lifecycle("Watch mode shutting down. Stopping target applications...")
              activeTargets.forEach { target ->
                  when (target) {
                      "desktop" -> {
                          val session = readSession("desktop")
                          if (session != null) {
                              DesktopProcess.stop(host.get(), session.port, session.token, desktopLaunchManifestFile.get().asFile)
                              clearSession("desktop")
                          }
                      }
                      "wasm" -> {
                          WasmServer.stop()
                          clearSession("wasm")
                      }
                      "android" -> {
                          val serial = AndroidTargetConfigurer.AndroidRecorder.resolveDeviceSerial(logger, File(projectRootDir.get()), finalAndroidSerial)
                          ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", androidApplicationId.get()).start().waitFor()
                          clearSession("android")
                      }
                      "ios" -> {
                          val udid = getIosSimulatorUdid(finalIosDevice) ?: getBootedIosSimulatorUdid() ?: ""
                          ProcessBuilder("xcrun", "simctl", "terminate", udid, iosBundleId.getOrElse("")).start().waitFor()
                          clearSession("ios")
                      }
                  }
              }
          }
      }
  }

  private fun writeWatchResults(success: Boolean, targets: List<TargetResult>) {
      val resultsFile = File(buildDir.get().asFile, "parikshan/watch-results.json")
      resultsFile.parentFile.mkdirs()
      val targetsJson = targets.joinToString(prefix = "[", postfix = "]") { res ->
          "{\"target\":\"${res.target}\",\"success\":${res.success},\"message\":\"${res.message.replace("\"", "\\\"")}\",\"durationMs\":${res.durationMs}}"
      }
      val json = "{\"timestamp\":${System.currentTimeMillis()},\"success\":$success,\"targets\":$targetsJson}"
      runCatching {
          resultsFile.writeText(json)
      }
  }

  private companion object {
      val targetLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

      fun getLockFor(target: String): Any {
          return targetLocks.computeIfAbsent(target) { Any() }
      }
  }
}

private class PollingWatcher(
    val roots: List<File>,
    val onEvent: (File) -> Unit
) : AutoCloseable {
    private var closed = false
    private val thread: Thread

    init {
        thread = Thread {
            var lastMaxTimestamp = getCurrentMaxTimestamp()

            try {
                while (!closed) {
                    Thread.sleep(500)
                    val current = getCurrentMaxTimestamp()
                    if (current.second > lastMaxTimestamp.second) {
                        lastMaxTimestamp = current
                        if (current.first != null) {
                            onEvent(current.first!!)
                        }
                    }
                }
            } catch (_: InterruptedException) {
            } catch (_: Exception) {
            }
        }
        thread.isDaemon = true
        thread.name = "Parikshan-PollingWatcher"
        thread.start()
    }

    private fun getCurrentMaxTimestamp(): Pair<File?, Long> {
        var maxTime = 0L
        var maxFile: File? = null
        roots.forEach { root ->
            if (root.exists()) {
                if (root.isDirectory) {
                    root.walkTopDown().forEach { file ->
                        if (file.isFile && isInteresting(file)) {
                            val t = file.lastModified()
                            if (t > maxTime) {
                                maxTime = t
                                maxFile = file
                            }
                        }
                    }
                } else {
                    if (isInteresting(root)) {
                        val t = root.lastModified()
                        if (t > maxTime) {
                            maxTime = t
                            maxFile = root
                        }
                    }
                }
            }
        }
        return Pair(maxFile, maxTime)
    }

    private fun isInteresting(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in setOf("kt", "kts")
    }

    override fun close() {
        closed = true
        thread.interrupt()
    }
}
