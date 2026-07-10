package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

abstract class E2ETestTask : DefaultTask() {

  @get:Input
  @set:Option(option = "targets", description = "Comma-separated list of E2E targets to execute (e.g. desktop,wasm,android,ios)")
  var targets: String = "desktop,wasm,android,ios"

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
  @get:Optional
  abstract val androidApplicationId: Property<String>

  @get:Input
  @get:Optional
  abstract val iosPort: Property<Int>

  @get:Input
  @get:Optional
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
      try { ParikshanWasmServer.stop() } catch (_: Exception) {}
    }
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    val executor = Executors.newFixedThreadPool(activeTargets.size)
    val futures = mutableListOf<Future<TargetResult>>()

    activeTargets.forEach { target ->
      val future = executor.submit<TargetResult> {
        try {
          executeTarget(target, filteredClasses, activeProcesses, finalAndroidSerial, finalIosDevice)
        } catch (e: Exception) {
          TargetResult(target, false, e.message ?: "Execution failed")
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
      logger.lifecycle("[${res.target.uppercase()}] $status - ${res.message}")
    }
    logger.lifecycle("========================================\n")

    val anyFailure = results.any { !it.success }
    if (anyFailure) {
      throw GradleException("Parikshan E2E test execution failed for one or more targets.")
    }
  }

  private data class TargetResult(val target: String, val success: Boolean, val message: String)

  private fun executeTarget(
    target: String,
    classes: List<String>,
    activeProcesses: MutableList<Process>,
    finalAndroidSerial: String?,
    finalIosDevice: String
  ): TargetResult {
    val logger = logger
    logger.lifecycle("Parikshan [$target]: Starting target E2E execution...")

    when (target) {
      "desktop" -> {
        val resolvedPort = ParikshanPortConflictHandler.resolvePortAndCleanStale(
          originalPort = originalDesktopPort.get(),
          host = host.get(),
          logger = logger
        )
        ParikshanDesktopProcess.start(
          jar = appJarFile.get().asFile,
          token = token.get(),
          logFile = File(buildDir.get().asFile, "parikshan/desktop-app-logs.log"),
          manifestFile = desktopLaunchManifestFile.get().asFile,
          appArgs = appArgs.get(),
          host = host.get(),
          port = resolvedPort,
          timeoutMs = 15000L,
          pollMs = 250L,
          title = title.orNull,
          background = true
        )

        classes.forEach { testClass ->
          logger.lifecycle("Parikshan [desktop]: Running $testClass...")
          val exitCode = spawnTestJvm(
            target = "desktop",
            testClass = testClass,
            systemProperties = mapOf(
              "parikshan.target" to "desktop",
              "parikshan.host" to host.get(),
              "parikshan.port" to resolvedPort.toString(),
              "parikshan.token" to token.get(),
              "parikshan.desktop.launchManifest" to desktopLaunchManifestFile.get().asFile.absolutePath
            ),
            activeProcesses = activeProcesses
          )
          if (exitCode != 0) {
            printTestFailures("desktop", testClass)
            ParikshanDesktopProcess.stop(
              host = host.get(),
              port = resolvedPort,
              token = token.get(),
              manifestFile = desktopLaunchManifestFile.get().asFile
            )
            return createTargetResult("desktop", false, classes, "Test class $testClass failed (exit code $exitCode). Check logs at build/parikshan/logs/desktop-${testClass.substringAfterLast('.')}.log")
          }
        }

        ParikshanDesktopProcess.stop(
          host = host.get(),
          port = resolvedPort,
          token = token.get(),
          manifestFile = desktopLaunchManifestFile.get().asFile
        )
        return createTargetResult("desktop", true, classes)
      }

      "wasm" -> {
        val resolvedPort = ParikshanPortConflictHandler.resolvePortAndCleanStale(
          originalPort = originalWasmPort.get(),
          host = "127.0.0.1",
          logger = logger
        )
        val portFile = wasmPortFile.get().asFile
        portFile.parentFile.mkdirs()
        portFile.writeText(resolvedPort.toString())

        ParikshanWasmServer.start(resolvedPort, wasmOutputDir.get().asFile)

        classes.forEach { testClass ->
          logger.lifecycle("Parikshan [wasm]: Running $testClass...")
          val exitCode = spawnTestJvm(
            target = "wasm",
            testClass = testClass,
            systemProperties = mapOf(
              "parikshan.target" to "wasm",
              "parikshan.token" to token.get(),
              "parikshan.wasm.url" to "http://127.0.0.1:$resolvedPort"
            ),
            activeProcesses = activeProcesses
          )
          if (exitCode != 0) {
            printTestFailures("wasm", testClass)
            return createTargetResult("wasm", false, classes, "Test class $testClass failed (exit code $exitCode). Check logs at build/parikshan/logs/wasm-${testClass.substringAfterLast('.')}.log")
          }
        }

        ParikshanWasmServer.stop()
        return createTargetResult("wasm", true, classes)
      }

      "android" -> {
        val isExplicit = targets.split(",").map { it.trim().lowercase() }.contains("android")
        val isAndroidE2EExplicit = isExplicit && targets != "desktop,wasm,android,ios"
        val hasAndroidDeviceSpecified = androidDevice.isNotBlank() || device.isNotBlank() || gradleAndroidSerial.orNull?.isNotBlank() == true || gradleDevice.orNull?.isNotBlank() == true || gradleSerial.orNull?.isNotBlank() == true
        val shouldExecuteAndroid = isAndroidE2EExplicit || hasAndroidDeviceSpecified || isAndroidDeviceOnline(finalAndroidSerial)

        if (!shouldExecuteAndroid) {
          logger.lifecycle("Parikshan: Skipping target 'android' because no active emulator or device was detected.")
          return TargetResult("android", true, "Skipped (no device detected)")
        }

        logger.lifecycle("Parikshan [android]: Device detected. Starting E2E execution...")
        val gradlew = getGradlewExecutable(File(projectRootDir.get()))
        
        // 1. Start App
        val startArgs = mutableListOf(gradlew, ":sample:composeApp:startParikshanAndroidApp", "-Pparikshan.token=${token.get()}", "-Pparikshan.e2e.active=true")
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

        // 2. Run Tests
        var testFailureMessage: String? = null
        classes.forEach { testClass ->
          logger.lifecycle("Parikshan [android]: Running $testClass...")
          val testSystemProps = mutableMapOf(
            "parikshan.target" to "android",
            "parikshan.host" to "127.0.0.1",
            "parikshan.port" to "9879",
            "parikshan.token" to token.get()
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
            printTestFailures("android", testClass)
            testFailureMessage = "Test class $testClass failed (exit code $exitCode). Check logs at build/parikshan/logs/android-${testClass.substringAfterLast('.')}.log"
          }
        }

        // 3. Stop App
        val stopArgs = mutableListOf(gradlew, ":sample:composeApp:stopParikshanAndroidApp")
        if (!finalAndroidSerial.isNullOrBlank()) {
          stopArgs.add("-Pparikshan.android.serial=$finalAndroidSerial")
        }
        val stopProcess = ProcessBuilder(stopArgs).apply {
          cleanXcodeEnv(this)
        }.start()
        stopProcess.waitFor()

        if (testFailureMessage != null) {
          return createTargetResult("android", false, classes, testFailureMessage)
        }
        return createTargetResult("android", true, classes)
      }

      "ios" -> {
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

        logger.lifecycle("Parikshan [ios]: Simulator target: '$finalIosDevice' ($udid). Starting E2E execution...")
        val gradlew = getGradlewExecutable(File(projectRootDir.get()))
        
        // 1. Start App
        val startProcess = ProcessBuilder(
          gradlew, 
          ":sample:composeApp:startIosApp", 
          "-Pparikshan.token=${token.get()}", 
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

        // 2. Run Tests
        var testFailureMessage: String? = null
        classes.forEach { testClass ->
          logger.lifecycle("Parikshan [ios]: Running $testClass...")
          val exitCode = spawnTestJvm(
            target = "ios",
            testClass = testClass,
            systemProperties = mapOf(
              "parikshan.target" to "ios",
              "parikshan.host" to "127.0.0.1",
              "parikshan.port" to iosPort.get().toString(),
              "parikshan.token" to token.get(),
              "parikshan.ios.bundleId" to bundleId,
              "parikshan.ios.udid" to udid,
              "parikshan.ios.device" to finalIosDevice
            ),
            activeProcesses = activeProcesses
          )
          if (exitCode != 0) {
            printTestFailures("ios", testClass)
            testFailureMessage = "Test class $testClass failed (exit code $exitCode). Check logs at build/parikshan/logs/ios-${testClass.substringAfterLast('.')}.log"
          }
        }

        // 3. Stop App
        val stopProcess = ProcessBuilder(
          gradlew, 
          ":sample:composeApp:stopIosApp",
          "-Pparikshan.ios.device=$finalIosDevice"
        ).apply {
          cleanXcodeEnv(this)
        }.start()
        stopProcess.waitFor()

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
      var udid: String? = null
      output.lineSequence().forEach { line ->
        if (line.contains("(")) {
          val name = line.substringBefore("(").trim()
          val currentUdid = line.substringAfter("(").substringBefore(")")
          if (currentUdid.isNotEmpty() && (requested == name || requested == currentUdid)) {
            udid = currentUdid
          }
        }
      }
      udid
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

    val propsToForward = listOf(
      "parikshan.video.enabled",
      "parikshan.video.fps",
      "parikshan.video.showCursor",
      "parikshan.video.stepDelayMs",
      "parikshan.video.postRollMs",
      "parikshan.video.strategy",
      "parikshan.video.width",
      "parikshan.video.height",
      "parikshan.wasm.headless",
      "parikshan.wasm.viewportWidth",
      "parikshan.wasm.viewportHeight",
      "parikshan.wasm.bridgeReadyTimeoutMs"
    )
    propsToForward.forEach { prop ->
      val v = System.getProperty(prop) ?: project.findProperty(prop)?.toString()
      if (!v.isNullOrEmpty()) {
        pbArgs.add("-D$prop=$v")
      }
    }

    val reportsDir = File(buildDir.get().asFile, "test-results/e2eTest/$target").absolutePath
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

    val logFile = File(buildDir.get().asFile, "parikshan/logs/${target}-${testClass.substringAfterLast('.')}.log")
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
    val logFile = File(buildDir.get().asFile, "parikshan/logs/${target}-${testClass.substringAfterLast('.')}.log")
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
    val logFile = File(buildDir.get().asFile, "parikshan/logs/${target}-${testClass.substringAfterLast('.')}.log")
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
}
