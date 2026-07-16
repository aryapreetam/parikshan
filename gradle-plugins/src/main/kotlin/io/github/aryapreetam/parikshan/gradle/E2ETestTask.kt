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

abstract class E2ETestTask : DefaultTask() {

  @get:Inject
  abstract val providers: ProviderFactory

  @get:Input
  @set:Option(option = "targets", description = "Comma-separated list of E2E targets to execute (e.g. desktop,wasm,android,ios)")
  var targets: String = "desktop,wasm,android,ios"

  @get:Input
  @set:Option(option = "keep-alive", description = "Keep application process alive to speed up subsequent E2E test runs.")
  var keepAlive: Boolean = false

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
  abstract val iosPort: Property<Int>

  @get:Internal
  abstract val iosBundleId: Property<String>

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

    val finalIosDevice = resolvedIosDevice
      ?: gradleDevice.orNull?.takeIf { it.isNotBlank() }
      ?: gradleSerial.orNull?.takeIf { it.isNotBlank() }
      ?: gradleIosDevice.orNull?.takeIf { it.isNotBlank() }
      ?: System.getenv("PARIKSHAN_IOS_DEVICE")?.takeIf { it.isNotBlank() }
      ?: "iPhone 16"

    val logDir = File(buildDir.get().asFile, "parikshan/logs")
    logDir.mkdirs()

    logger.lifecycle("Parikshan: Starting parallel E2E runs for targets: ${activeTargets.joinToString()}")
    logger.lifecycle("Parikshan: Target test classes: ${filteredClasses.joinToString()}")

    val activeProcesses = mutableListOf<Process>()
    val shutdownHook = Thread {
      synchronized(activeProcesses) {
        activeProcesses.forEach {
          try { it.destroy() } catch (_: Exception) {}
        }
      }
      try { WasmServer.stop() } catch (_: Exception) {}
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    val executor = Executors.newFixedThreadPool(activeTargets.size)
    val futures = mutableListOf<Future<TargetResult>>()

    activeTargets.forEach { target ->
      val future = executor.submit<TargetResult> {
        val targetStartTime = System.currentTimeMillis()
        try {
          val result = executeTarget(target, filteredClasses, activeProcesses, finalAndroidSerial, finalIosDevice)
          val duration = System.currentTimeMillis() - targetStartTime
          result.copy(durationMs = duration)
        } catch (e: Exception) {
          val duration = System.currentTimeMillis() - targetStartTime
          TargetResult(target, false, e.message ?: "Execution failed", duration)
        }
      }
      futures.add(future)
    }

    val results = futures.map { it.get() }
    executor.shutdown()
    
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

          classes.forEach { testClass ->
            logger.lifecycle("Parikshan [$target]: Running $testClass...")
            val exitCode = spawnTestJvm(
              target = target,
              testClass = testClass,
              systemProperties = mapOf(
                "parikshan.target" to target,
                "parikshan.host" to host.get(),
                "parikshan.port" to resolvedPort.toString(),
                "parikshan.token" to activeToken,
                "parikshan.desktop.launchManifest" to desktopLaunchManifestFile.get().asFile.absolutePath
              ),
              activeProcesses = activeProcesses
            )
            if (exitCode != 0) {
              printTestFailures(target, testClass)
              if (jarFile != null) {
                DesktopProcess.stop(
                  host = host.get(),
                  port = resolvedPort,
                  token = activeToken,
                  manifestFile = desktopLaunchManifestFile.get().asFile
                )
                clearSession(target)
              }
              return@withTargetLock createTargetResult(target, false, classes, "Test class $testClass failed (exit code $exitCode). Check logs at build/parikshan/logs/${target}-${testClass.substringAfterLast('.')}.log")
            }
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

        classes.forEach { testClass ->
          logger.lifecycle("Parikshan [wasm]: Running $testClass...")
          val exitCode = spawnTestJvm(
            target = "wasm",
            testClass = testClass,
            systemProperties = mapOf(
              "parikshan.target" to "wasm",
              "parikshan.token" to activeToken,
              "parikshan.wasm.url" to "http://127.0.0.1:$resolvedPort"
            ),
            activeProcesses = activeProcesses
          )
          if (exitCode != 0) {
            printTestFailures("wasm", testClass)
            WasmServer.stop()
            clearSession("wasm")
            return createTargetResult("wasm", false, classes, "Test class $testClass failed (exit code $exitCode). Check logs at build/parikshan/logs/wasm-${testClass.substringAfterLast('.')}.log")
          }
        }

        if (!keepAlive) {
          WasmServer.stop()
          clearSession("wasm")
        }
        return createTargetResult("wasm", true, classes)
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
        val resolvedAndroidPort = session?.port ?: 9879
        val resolvedToken = session?.token ?: token.get()
        val isHealthy = session != null && checkTargetHealth("127.0.0.1", resolvedAndroidPort, resolvedToken)
        val isFresh = isTargetFresh("android")
        val minBinaryTimestamp = getTargetOutputTimestamp("android")
        val canReuse = isHealthy && isFresh && session!!.timestamp >= minBinaryTimestamp

        val activeToken = if (canReuse && session != null) session.token else token.get()
        var activePort = if (canReuse && session != null) session.port else 9879

        val gradlew = getGradlewExecutable(File(projectRootDir.get()))

        if (canReuse) {
          logger.lifecycle("Parikshan [android]: Keeping active instance alive (skipping build/launch).")
        } else {
          synchronized(getLockFor("android")) {
            val serial = AndroidTargetConfigurer.AndroidRecorder.resolveDeviceSerial(logger, File(projectRootDir.get()), finalAndroidSerial)
            val reclaim = reclaimPorts || providers.gradleProperty("parikshan.reclaimPorts").orNull?.toBoolean() ?: false

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
            val startExit = startProcess.waitFor()
            if (startExit != 0) {
              return TargetResult("android", false, "Failed to start Android app (exit code $startExit). Check build/parikshan/logs/android-start.log")
            }
            writeSession("android", TargetSession(token.get(), activePort, System.currentTimeMillis()))
          }
        }

        // 2. Run Tests
        var testFailureMessage: String? = null
        var runSuccess = true
        classes.forEach { testClass ->
          logger.lifecycle("Parikshan [android]: Running $testClass...")
          val testSystemProps = mutableMapOf(
            "parikshan.target" to "android",
            "parikshan.host" to "127.0.0.1",
            "parikshan.port" to activePort.toString(),
            "parikshan.token" to activeToken
          )
          if (!finalAndroidSerial.isNullOrBlank()) {
            testSystemProps["parikshan.android.serial"] = finalAndroidSerial
          }
          val exitCode = spawnTestJvm(
            target = "android",
            testClass = testClass,
            systemProperties = testSystemProps,
            activeProcesses = activeProcesses
          )
          if (exitCode != 0) {
            runSuccess = false
            printTestFailures("android", testClass)
            testFailureMessage = "Test class $testClass failed (exit code $exitCode). Check logs at build/parikshan/logs/android-${testClass.substringAfterLast('.')}.log"
          }
        }

        if (!keepAlive || !runSuccess) {
          // 3. Stop App
          synchronized(getLockFor("android")) {
            val stopArgs = mutableListOf(gradlew, "$projectPathPrefix:stopParikshanAndroidApp")
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
        val resolvedIosPort = session?.port ?: iosPort.get()
        val resolvedToken = session?.token ?: token.get()
        val isHealthy = session != null && checkTargetHealth("127.0.0.1", resolvedIosPort, resolvedToken)
        val isFresh = isTargetFresh("ios")
        val minBinaryTimestamp = getTargetOutputTimestamp("ios")
        val canReuse = isHealthy && isFresh && session!!.timestamp >= minBinaryTimestamp

        val activeToken = if (canReuse && session != null) session.token else token.get()
        var activePort = if (canReuse && session != null) session.port else iosPort.get()

        val gradlew = getGradlewExecutable(File(projectRootDir.get()))

        if (canReuse) {
          logger.lifecycle("Parikshan [ios]: Keeping active instance alive (skipping build/launch).")
        } else {
          synchronized(getLockFor("ios")) {
            val reclaim = reclaimPorts || providers.gradleProperty("parikshan.reclaimPorts").orNull?.toBoolean() ?: false
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
              return TargetResult("ios", false, "Failed to start iOS app (exit code $startExit). Check build/parikshan/logs/ios-start.log")
            }
            writeSession("ios", TargetSession(token.get(), activePort, System.currentTimeMillis()))
          }
        }

        // 2. Run Tests
        var testFailureMessage: String? = null
        var runSuccess = true
        classes.forEach { testClass ->
          logger.lifecycle("Parikshan [ios]: Running $testClass...")
          val exitCode = spawnTestJvm(
            target = "ios",
            testClass = testClass,
            systemProperties = mapOf(
              "parikshan.target" to "ios",
              "parikshan.host" to "127.0.0.1",
              "parikshan.port" to activePort.toString(),
              "parikshan.token" to activeToken,
              "parikshan.ios.bundleId" to bundleId,
              "parikshan.ios.udid" to udid,
              "parikshan.ios.device" to finalIosDevice
            ),
            activeProcesses = activeProcesses
          )
          if (exitCode != 0) {
            runSuccess = false
            printTestFailures("ios", testClass)
            testFailureMessage = "Test class $testClass failed (exit code $exitCode). Check logs at build/parikshan/logs/ios-${testClass.substringAfterLast('.')}.log"
          }
        }

        if (!keepAlive || !runSuccess) {
          // 3. Stop App
          synchronized(getLockFor("ios")) {
            val stopProcess = ProcessBuilder(
              gradlew, 
              "$projectPathPrefix:stopIosApp",
              "-Pparikshan.ios.device=$finalIosDevice",
              "-Pparikshan.ios.port=$activePort"
            ).apply {
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

  private fun cleanXcodeEnv(pb: ProcessBuilder) {
    val env = pb.environment()
    val keysToRemove = listOf(
      "PLATFORM_NAME",
      "SDK_NAME",
      "SDKROOT",
      "ARCHS",
      "ONLY_ACTIVE_ARCH",
      "TARGET_DEVICE_IDENTIFIER",
      "CONFIGURATION",
      "BUILT_PRODUCTS_DIR"
    )
    keysToRemove.forEach { env.remove(it) }
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
    pbArgs.add("-Djunit.jupiter.testmethod.order.default=io.github.aryapreetam.parikshan.client.ParikshanMethodOrderer")

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
    val logFile = File(buildDir.get().asFile, "parikshan/logs/${target}-${testClass}.log")
    if (!logFile.exists()) return null
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
      if (files.isEmpty()) return 0L
      return files.map { file ->
          if (file.isDirectory) {
              file.walkTopDown().filter { it.isFile }.map { it.lastModified() }.minOrNull() ?: 0L
          } else {
              file.lastModified()
          }
      }.minOrNull() ?: 0L
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
      val raf = java.io.RandomAccessFile(lockFile, "rw")
      val channel = raf.channel
      var lock: java.nio.channels.FileLock? = null
      val startTime = System.currentTimeMillis()
      var logged = false

      try {
          while (true) {
              try {
                  lock = channel.tryLock()
                  if (lock != null) {
                      break
                  }
              } catch (_: Exception) {}

              if (!logged) {
                  logger.lifecycle("Parikshan [$target]: Waiting for target process lock (another test run is active)...")
                  logged = true
              }

              if (System.currentTimeMillis() - startTime > 300000) { // 5 minutes timeout
                  return TargetResult(target, false, "Timeout waiting for target process lock. Another process is executing tests on target '$target'.")
              }
              Thread.sleep(1000)
          }

          return block()
      } finally {
          runCatching { lock?.release() }
          runCatching { channel.close() }
          runCatching { raf.close() }
      }
  }

  private data class DevicePortState(val isBusy: Boolean, val parikshanAppId: String?)

  private fun checkDevicePortState(serial: String, devicePort: Int): DevicePortState {
      var tempHostPort = 12000
      while (tempHostPort <= 20000) {
          if (PortConflictHandler.isPortAvailable("127.0.0.1", tempHostPort)) {
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
              socket.connect(java.net.InetSocketAddress("127.0.0.1", tempHostPort), 250)
              socket.soTimeout = 250
              val readVal = socket.inputStream.read()
              readVal != -1
          }
      } catch (_: java.net.SocketTimeoutException) {
          true
      } catch (_: Exception) {
          false
      }
      val appId = if (isBusy) PortConflictHandler.queryApplicationId("127.0.0.1", tempHostPort) else null

      ProcessBuilder("adb", "-s", serial, "forward", "--remove", "tcp:$tempHostPort").start().waitFor()
      return DevicePortState(isBusy, appId)
  }

  private companion object {
      val targetLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

      fun getLockFor(target: String): Any {
          return targetLocks.computeIfAbsent(target) { Any() }
      }
  }
}
