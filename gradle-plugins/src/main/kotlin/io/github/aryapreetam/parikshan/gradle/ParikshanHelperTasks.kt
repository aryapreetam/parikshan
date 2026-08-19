package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.IgnoreEmptyDirectories
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

abstract class ParikshanPreflightTask : DefaultTask() {
  @get:Input
  abstract val targetName: Property<String>

  @get:Input
  @get:Optional
  abstract val deviceOverride: Property<String>

  @get:Internal
  abstract val projectDir: DirectoryProperty

  @TaskAction
  fun run() {
    val target = targetName.get().lowercase()
    val workingDir = projectDir.get().asFile
    val device = deviceOverride.orNull.orEmpty()
    if (target == "android") {
      val serial = AndroidTargetConfigurer.AndroidRecorder.resolveDeviceSerial(logger, workingDir, device)
      logger.lifecycle("Parikshan Android: Found connected device/emulator '$serial'")
    } else if (target == "ios") {
      val process = ProcessBuilder("xcrun", "simctl", "list", "devices", "available").directory(workingDir).start()
      val output = process.inputStream.bufferedReader().readText()
      process.waitFor()
      logger.lifecycle("Parikshan iOS: Preflight completed for '$device'")
    }
  }
}

abstract class ParikshanStopIosTask : DefaultTask() {
  @get:Input
  @get:Optional
  abstract val simulatorUdid: Property<String>

  @get:Input
  abstract val bundleId: Property<String>

  @TaskAction
  fun run() {
    val bId = bundleId.orNull
    if (bId.isNullOrBlank()) return

    val rawDevice = simulatorUdid.orNull ?: "booted"
    val udid = resolveUdid(rawDevice) ?: "booted"

    runCatching {
      ProcessBuilder("xcrun", "simctl", "terminate", udid, bId).start().waitFor()
      logger.lifecycle("Parikshan iOS: App '$bId' terminated")
    }
  }

  private fun resolveUdid(rawDevice: String): String? {
    if (rawDevice.contains("-") && rawDevice.length >= 30) return rawDevice
    return try {
      val activeSdkVersion = ParikshanStartIosTask.IosSimulatorResolver.getActiveIosSdkVersion()
      val devices = ParikshanStartIosTask.IosSimulatorResolver.listAvailableIosDevices()
      ParikshanStartIosTask.IosSimulatorResolver.selectBestDevice(rawDevice, devices, activeSdkVersion).udid
    } catch (_: Throwable) {
      "booted"
    }
  }
}

abstract class ParikshanStartIosTask : DefaultTask() {
  @get:Input
  abstract val iosDevice: Property<String>

  @get:Input
  abstract val xcodeProject: Property<String>

  @get:Input
  abstract val xcodeScheme: Property<String>

  @get:Input
  abstract val bundleId: Property<String>

  @get:Input
  abstract val sessionToken: Property<String>

  @get:Input
  abstract val port: Property<Int>

  @get:Input
  abstract val xcodeTimeout: Property<Long>

  @get:Internal
  abstract val projectDir: DirectoryProperty

  @get:Internal
  abstract val rootDir: DirectoryProperty

  @get:OutputDirectory
  abstract val derivedDataDir: DirectoryProperty

  @get:Internal
  abstract val buildDir: DirectoryProperty

  internal data class DiscoveredIosDevice(
    val name: String,
    val udid: String,
    val isBooted: Boolean,
    val runtimeName: String,
    val runtimeVersion: String
  )

  internal object IosSimulatorResolver {
    fun getActiveIosSdkVersion(): String {
      return runCatching {
        val process = ProcessBuilder("xcrun", "xcodebuild", "-version", "-sdk", "iphonesimulator", "SDKVersion").start()
        val out = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        out
      }.getOrDefault("")
    }

    fun listAvailableIosDevices(projectDir: File? = null): List<DiscoveredIosDevice> {
      val pb = if (projectDir != null) {
        ProcessBuilder("xcrun", "simctl", "list", "devices", "available").directory(projectDir)
      } else {
        ProcessBuilder("xcrun", "simctl", "list", "devices", "available")
      }
      val process = pb.start()
      val output = process.inputStream.bufferedReader().readText()
      process.waitFor()

      val devices = mutableListOf<DiscoveredIosDevice>()
      var currentRuntime = ""
      var currentRuntimeVersion = ""

      output.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("-- ") && trimmed.endsWith(" --")) {
          val header = trimmed.removeSurrounding("-- ", " --").trim()
          if (header.startsWith("iOS", ignoreCase = true)) {
            currentRuntime = header
            currentRuntimeVersion = header.substringAfter("iOS", "").trim()
          } else {
            currentRuntime = ""
            currentRuntimeVersion = ""
          }
        } else if (currentRuntime.isNotEmpty() && trimmed.contains("(")) {
          val name = trimmed.substringBefore("(").trim()
          val udid = trimmed.substringAfter("(").substringBefore(")")
          val state = trimmed.substringAfterLast("(").substringBefore(")")
          if (name.isNotEmpty() && udid.contains("-")) {
            devices += DiscoveredIosDevice(
              name = name,
              udid = udid,
              isBooted = state.contains("Booted", ignoreCase = true),
              runtimeName = currentRuntime,
              runtimeVersion = currentRuntimeVersion
            )
          }
        }
      }
      return devices
    }

    fun selectBestDevice(
      requestedDevice: String,
      availableDevices: List<DiscoveredIosDevice>,
      activeSdkVersion: String
    ): DiscoveredIosDevice {
      if (availableDevices.isEmpty()) {
        throw GradleException("Parikshan iOS: No available iOS Simulators detected.")
      }

      if (requestedDevice.equals("booted", ignoreCase = true)) {
        val booted = availableDevices.firstOrNull { it.isBooted }
        if (booted != null) return booted
      }

      val byUdid = availableDevices.firstOrNull { it.udid.equals(requestedDevice, ignoreCase = true) }
      if (byUdid != null) return byUdid

      val activeDevices = if (activeSdkVersion.isNotBlank()) {
        availableDevices.filter { it.runtimeVersion.startsWith(activeSdkVersion) || activeSdkVersion.startsWith(it.runtimeVersion) }
      } else {
        emptyList()
      }.ifEmpty { availableDevices }

      val exactNameInActive = activeDevices.firstOrNull { it.name.equals(requestedDevice, ignoreCase = true) }
      if (exactNameInActive != null) return exactNameInActive

      val exactNameAnywhere = availableDevices.firstOrNull { it.name.equals(requestedDevice, ignoreCase = true) }
      if (exactNameAnywhere != null) return exactNameAnywhere

      val bootedInActive = activeDevices.firstOrNull { it.isBooted }
        ?: availableDevices.firstOrNull { it.isBooted }
      if (bootedInActive != null) return bootedInActive

      val preferredModels = listOf("iPhone 17", "iPhone 17 Pro", "iPhone 16", "iPhone 16 Pro", "iPhone 15", "iPhone 15 Pro", "iPhone 14")
      for (model in preferredModels) {
        val found = activeDevices.firstOrNull { it.name.equals(model, ignoreCase = true) }
        if (found != null) return found
      }

      return activeDevices.firstOrNull { it.name.startsWith("iPhone", ignoreCase = true) }
        ?: activeDevices.first()
    }
  }

  @TaskAction
  fun run() {
    val projectDirFile = projectDir.get().asFile
    val rootDirFile = rootDir.get().asFile
    val buildDirFile = buildDir.get().asFile
    val derivedDataDirFile = derivedDataDir.get().asFile
    val deviceVal = iosDevice.get()
    val tokenVal = sessionToken.get()
    val activePort = port.get()
    val timeoutVal = xcodeTimeout.get()
    val projVal = xcodeProject.get()
    val schemeVal = xcodeScheme.get()
    val bundleIdVal = bundleId.get()

    val isCi = System.getenv("CI") == "true"
    val activeSdkVersion = IosSimulatorResolver.getActiveIosSdkVersion()
    val availableDevices = IosSimulatorResolver.listAvailableIosDevices(projectDirFile)
    val targetDevice = IosSimulatorResolver.selectBestDevice(deviceVal, availableDevices, activeSdkVersion)

    val selectedName = targetDevice.name
    val selectedUdid = targetDevice.udid
    val isBooted = targetDevice.isBooted

    logger.lifecycle("Parikshan iOS: Using simulator '$selectedName' ($selectedUdid) [${targetDevice.runtimeName}]")

    if (!isBooted) {
      logger.lifecycle("Parikshan iOS: Booting simulator...")
      ProcessBuilder("xcrun", "simctl", "boot", selectedUdid).start().waitFor()
      if (!isCi) { runCatching { ProcessBuilder("open", "-a", "Simulator").start().waitFor() } }
      ProcessBuilder("xcrun", "simctl", "bootstatus", selectedUdid, "-b").start().waitFor()
    }

    val originalIosAppDir = File(projVal).parentFile
    val generatedIosAppDir = File(buildDirFile, "parikshan/ios-host")
    generatedIosAppDir.deleteRecursively()
    generatedIosAppDir.mkdirs()

    originalIosAppDir.walkTopDown()
      .onEnter { dir -> dir.name != "build" && dir.name != ".gradle" && dir.name != "DerivedData" }
      .forEach { file ->
        val relativePath = file.relativeTo(originalIosAppDir)
        val targetFile = File(generatedIosAppDir, relativePath.path)
        if (file.isDirectory) {
          targetFile.mkdirs()
        } else {
          runCatching { file.copyTo(targetFile, overwrite = true) }
        }
      }

    val absoluteGradlew = File(rootDirFile, "gradlew").absolutePath
    val javaHomeVal = System.getProperty("java.home") ?: System.getenv("JAVA_HOME") ?: ""
    val javaHomeExport = if (javaHomeVal.isNotBlank()) "export JAVA_HOME=\"$javaHomeVal\"\nexport PATH=\"$javaHomeVal/bin:\$PATH\"\n" else ""
    val gradlewShim = File(generatedIosAppDir, "gradlew")
    val shimContent = """
        #!/bin/sh
        $javaHomeExport exec "$absoluteGradlew" -p "${rootDirFile.absolutePath}" --no-daemon --no-configuration-cache -Pparikshan.e2e.active=true -Pparikshan.targets=ios -Pparikshan.token=$tokenVal "${'$'}@"
        """.trimIndent()
    gradlewShim.writeText(shimContent)
    gradlewShim.setExecutable(true)

    val parentShim = File(generatedIosAppDir.parentFile, "gradlew")
    parentShim.writeText(shimContent)
    parentShim.setExecutable(true)

    val grandParentShim = File(generatedIosAppDir.parentFile.parentFile, "gradlew")
    grandParentShim.writeText(shimContent)
    grandParentShim.setExecutable(true)

    val appBuildProducts = File(derivedDataDirFile, "Build/Products/Debug-iphonesimulator")
    appBuildProducts.mkdirs()

    val logFile = File(buildDirFile, "parikshan/xcodebuild.log")
    logFile.parentFile.mkdirs()

    val buildProcess = ProcessBuilder(
      "xcodebuild", "build", "-project", File(generatedIosAppDir, File(projVal).name).absolutePath,
      "-scheme", schemeVal, "-configuration", "Debug",
      "-sdk", "iphonesimulator",
      "-destination", "platform=iOS Simulator,id=$selectedUdid",
      "-derivedDataPath", derivedDataDirFile.absolutePath,
      "CONFIGURATION_BUILD_DIR=${appBuildProducts.absolutePath}",
      "SUPPORTED_PLATFORMS=iphonesimulator",
      "DEBUG_INFORMATION_FORMAT=dwarf",
      "ONLY_ACTIVE_ARCH=YES",
      "ENABLE_BITCODE=NO"
    ).apply {
      environment()["PARIKSHAN_TOKEN"] = tokenVal
      if (javaHomeVal.isNotBlank()) {
        environment()["JAVA_HOME"] = javaHomeVal
        environment()["PATH"] = "$javaHomeVal/bin:" + (environment()["PATH"] ?: System.getenv("PATH") ?: "")
      }
      redirectErrorStream(true)
      redirectOutput(logFile)
    }.start()

    if (!buildProcess.waitFor(timeoutVal, java.util.concurrent.TimeUnit.SECONDS)) {
      buildProcess.destroyForcibly()
      throw GradleException("Parikshan iOS: xcodebuild compilation timed out after $timeoutVal seconds.")
    }
    val buildResult = buildProcess.exitValue()
    if (buildResult != 0) {
      val lines = if (logFile.exists()) logFile.readLines() else emptyList()
      val errorLines = lines.filter { it.contains("error:", ignoreCase = true) || it.contains("FAILURE:", ignoreCase = true) }
      val detailOutput = if (errorLines.isNotEmpty()) errorLines.takeLast(20).joinToString("\n") else lines.takeLast(50).joinToString("\n")
      logger.error("Parikshan iOS: xcodebuild log tail:\n$detailOutput")
      throw GradleException("xcodebuild failed with exit code $buildResult:\n$detailOutput")
    }

    val appBundle = appBuildProducts.listFiles()?.firstOrNull { it.name.endsWith(".app") }
      ?: throw GradleException("No .app bundle")

    logger.lifecycle("Parikshan iOS: Launching app on simulator $selectedUdid...")
    ProcessBuilder("xcrun", "simctl", "terminate", selectedUdid, bundleIdVal).start().waitFor()

    val installResult = ProcessBuilder("xcrun", "simctl", "install", selectedUdid, appBundle.absolutePath)
      .redirectErrorStream(true).start()
    installResult.waitFor()
    if (installResult.exitValue() != 0) {
      throw GradleException("simctl install failed with exit code ${installResult.exitValue()}")
    }

    val launchProcess = ProcessBuilder("xcrun", "simctl", "launch", selectedUdid, bundleIdVal).apply {
      environment()["SIMCTL_CHILD_PARIKSHAN_TOKEN"] = tokenVal
      environment()["PARIKSHAN_TOKEN"] = tokenVal
      environment()["SIMCTL_CHILD_PARIKSHAN_PORT"] = activePort.toString()
      environment()["PARIKSHAN_PORT"] = activePort.toString()
      redirectErrorStream(true)
    }.start()
    launchProcess.waitFor()
    if (launchProcess.exitValue() != 0) {
      throw GradleException("simctl launch failed with exit code ${launchProcess.exitValue()}")
    }

    logger.lifecycle("Parikshan iOS: Waiting for server on port $activePort...")
    val deadline = System.currentTimeMillis() + 90_000
    var serverReady = false
    val workingPort = activePort
    while (System.currentTimeMillis() <= deadline) {
      if (postPing(activePort, tokenVal)) {
        serverReady = true
        break
      }
      Thread.sleep(500)
    }

    val portFile = File(buildDirFile, "parikshan/ios-port.txt")
    portFile.parentFile.mkdirs()
    portFile.writeText(workingPort.toString())

    if (!serverReady) {
      val processName = appBundle.nameWithoutExtension
      logger.error("Parikshan iOS: Server failed to start. Dumping logs for process '$processName'...")
      val pidCheck = ProcessBuilder("xcrun", "simctl", "spawn", selectedUdid, "launchctl", "list")
        .start().inputStream.bufferedReader().readText()
      logger.error("Parikshan iOS: App process still running: ${pidCheck.contains(bundleIdVal)}")
      val crashLogs = ProcessBuilder("xcrun", "simctl", "spawn", selectedUdid, "log", "show",
        "--predicate", "process == \"$processName\" OR (eventMessage CONTAINS \"$processName\" AND eventMessage CONTAINS[c] \"crash\")",
        "--style", "syslog", "--last", "5m").start().inputStream.bufferedReader().readText()
      if (crashLogs.trim().lines().size <= 1) {
        val allLogs = ProcessBuilder("xcrun", "simctl", "spawn", selectedUdid, "log", "show",
          "--style", "syslog", "--last", "3m").start().inputStream.bufferedReader().readText()
        allLogs.lines().takeLast(200).forEach { logger.error(it) }
      } else {
        logger.error(crashLogs)
      }
      throw GradleException("Parikshan iOS server failed readiness check")
    }
    logger.lifecycle("Parikshan iOS: Server ready.")
  }
}

internal fun postPing(port: Int, token: String): Boolean {
  val body = """{"type":"ping","id":"health","token":"$token"}"""
  val conn = runCatching { URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection }.getOrNull() ?: return false
  return try {
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 500
    conn.readTimeout = 500
    conn.outputStream.use { it.write(body.toByteArray()) }
    conn.responseCode == 200 && conn.inputStream.bufferedReader().readText().contains("ok")
  } catch (_: Exception) {
    false
  } finally {
    conn.disconnect()
  }
}

abstract class ParikshanStopAndroidTask : DefaultTask() {
  @get:Input
  @get:Optional
  abstract val androidSerial: Property<String>

  @get:Input
  abstract val port: Property<String>

  @get:Input
  abstract val applicationId: Property<String>

  @get:Internal
  abstract val projectDir: DirectoryProperty

  @TaskAction
  fun run() {
    val workingDir = projectDir.get().asFile
    val serial = AndroidTargetConfigurer.AndroidRecorder.resolveDeviceSerial(logger, workingDir, androidSerial.orNull)
    val portVal = port.get()
    ProcessBuilder("adb", "-s", serial, "forward", "--remove", "tcp:$portVal").start().waitFor()
    val appId = applicationId.get()
    ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", appId).start().waitFor()
  }
}

abstract class ParikshanStartAndroidTask : DefaultTask() {
  @get:Input
  @get:Optional
  abstract val androidSerial: Property<String>

  @get:Input
  abstract val port: Property<String>

  @get:Input
  abstract val sessionToken: Property<String>

  @get:Input
  abstract val applicationId: Property<String>

  @get:Input
  @get:Optional
  abstract val launcherActivity: Property<String>

  @get:Internal
  abstract val projectDir: DirectoryProperty

  @TaskAction
  fun run() {
    val workingDir = projectDir.get().asFile
    val serial = AndroidTargetConfigurer.AndroidRecorder.resolveDeviceSerial(logger, workingDir, androidSerial.orNull)
    val appId = applicationId.get()
    val resolvedLauncher = launcherActivity.orNull
    val portVal = port.get()

    logger.lifecycle("Parikshan Android: Resolved applicationId: $appId")
    if (!resolvedLauncher.isNullOrBlank()) {
      logger.lifecycle("Parikshan Android: Resolved launcherActivity: $resolvedLauncher")
    }

    ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", appId).start().waitFor()
    ProcessBuilder("adb", "-s", serial, "forward", "tcp:$portVal", "tcp:$portVal").start().waitFor()
    val testPackage = "$appId.test"

    val command = mutableListOf(
      "adb", "-s", serial, "shell", "am", "instrument", "-w",
      "-e", "class", "io.github.aryapreetam.parikshan.ParikshanAndroidRunner",
      "-e", "parikshan_token", sessionToken.get(),
      "-e", "parikshan_port", portVal
    )
    if (!resolvedLauncher.isNullOrBlank()) {
      command.add("-e")
      command.add("launcher_class")
      command.add(resolvedLauncher)
    }
    command.add("$testPackage/androidx.test.runner.AndroidJUnitRunner")

    val process = ProcessBuilder(command).redirectErrorStream(true).start()

    logger.lifecycle("Parikshan Android: Waiting for server on port $portVal...")
    val deadline = System.currentTimeMillis() + 60_000
    var serverReady = false
    while (System.currentTimeMillis() <= deadline) {
      if (!process.isAlive) {
        val output = process.inputStream.bufferedReader().readText()
        logger.error("Parikshan Android: am instrument process exited unexpectedly:\n$output")
        throw GradleException("Parikshan Android: am instrument process exited with code ${process.exitValue()}:\n$output")
      }
      if (postPing(portVal.toInt(), sessionToken.get())) {
        serverReady = true
        break
      }
      Thread.sleep(500)
    }

    if (!serverReady) {
      val logcatOutput = runCatching {
        ProcessBuilder("adb", "-s", serial, "logcat", "-d", "-t", "200").start().inputStream.bufferedReader().readText()
      }.getOrDefault("")
      logger.error("Parikshan Android: Server failed readiness check on port $portVal. Recent logcat:\n$logcatOutput")
      throw GradleException("Parikshan Android server failed readiness check on port $portVal")
    }
    logger.lifecycle("Parikshan Android: Server ready on port $portVal.")
  }
}

abstract class ParikshanPrepareIosSourceTask : DefaultTask() {
  @get:Internal
  abstract val iosProjectDir: DirectoryProperty

  @get:InputDirectory
  @get:Optional
  abstract val iosMainDir: DirectoryProperty

  @get:OutputDirectory
  abstract val generatedDir: DirectoryProperty

  @TaskAction
  fun run() {
    val genDirFile = generatedDir.get().asFile
    val projectDirFile = iosProjectDir.get().asFile
    genDirFile.deleteRecursively()
    genDirFile.mkdirs()
    val iosMainDir = File(projectDirFile, "src/iosMain/kotlin")
    if (iosMainDir.exists()) {
      iosMainDir.copyRecursively(genDirFile, overwrite = true)
    }
    genDirFile.walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .forEach { file ->
        val original = file.readText()
        if (original.contains("ComposeUIViewController")) {
          val withoutComposeImport = original.replace(Regex("""import\s+androidx\.compose\.ui\.window\.ComposeUIViewController\s*\R?"""), "")
          val bodyReplaced = withoutComposeImport.replace("ComposeUIViewController", "ParikshanUIViewController")
          val importLine = "import io.github.aryapreetam.parikshan.ParikshanUIViewController\n"
          val instrumented = if (bodyReplaced.contains(Regex("""package\s+[\w.]+\s*\R"""))) {
            bodyReplaced.replace(Regex("""(package\s+[\w.]+\s*\R)"""), "$1$importLine")
          } else {
            importLine + bodyReplaced
          }
          file.writeText(instrumented)
          logger.lifecycle("Parikshan iOS: Instrumented generated source ${file.name}")
        }
      }

    var packageName: String? = null
    if (iosMainDir.exists()) {
      iosMainDir.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .forEach { file ->
          val text = file.readText()
          if (text.contains("MainViewController")) {
            packageName = Regex("""package\s+([a-zA-Z0-9_.]+)""").find(text)?.groupValues?.get(1)
          }
        }
    }
    val packageLine = packageName?.let { "package $it\n\n" }.orEmpty()
    File(genDirFile, "ParikshanBoot.kt").writeText(
      packageLine +
        """
        import io.github.aryapreetam.parikshan.server.IosServer

        @Suppress("unused")
        fun ParikshanStartServer() {
          IosServer.startIfNeeded()
        }
        """.trimIndent() +
        "\n"
    )
    logger.lifecycle("Parikshan iOS: Generated source set at ${genDirFile.absolutePath}")
  }
}

abstract class ParikshanPrepareWasmSourceTask : DefaultTask() {
  @get:Internal
  abstract val projectDir: DirectoryProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:IgnoreEmptyDirectories
  @get:Optional
  abstract val sourceFiles: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val generatedDir: DirectoryProperty

  @TaskAction
  fun run() {
    val genDirFile = generatedDir.get().asFile
    genDirFile.deleteRecursively()
    genDirFile.mkdirs()
    
    var copiedAny = false
    sourceFiles.files.filter { it.exists() }.forEach { srcDir ->
      srcDir.copyRecursively(genDirFile, overwrite = true)
      logger.lifecycle("Parikshan Wasm: Found and copied source directory at ${srcDir.absolutePath}")
      copiedAny = true
    }
    if (!copiedAny) {
      logger.warn("Parikshan Wasm: No source directories found to copy.")
    }
    genDirFile.walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .forEach { file ->
        val original = file.readText()
        if (original.contains("ComposeViewport")) {
          val withoutComposeImport = original.replace(Regex("""import\s+androidx\.compose\.ui\.window\.ComposeViewport\s*\R?"""), "")
          val bodyReplaced = withoutComposeImport.replace("ComposeViewport", "ParikshanComposeViewport")
          val imports = "import io.github.aryapreetam.parikshan.ParikshanComposeViewport\nimport io.github.aryapreetam.parikshan.initializeParikshanWasm\n"
          var result = if (bodyReplaced.contains(Regex("""package\s+[\w.]+\s*\R"""))) {
            bodyReplaced.replace(Regex("""(package\s+[\w.]+\s*\R)"""), "$1$imports")
          } else {
            imports + bodyReplaced
          }

          val mainMatch = Regex("""fun\s+main\s*\([^)]*\)\s*\{""").find(result)
          if (mainMatch != null) {
            result = result.replaceRange(mainMatch.range.last + 1, mainMatch.range.last + 1, "\n  initializeParikshanWasm()\n")
          }
          file.writeText(result)
          logger.lifecycle("Parikshan Wasm: Instrumented generated source ${file.name}")
        }
      }
  }
}
