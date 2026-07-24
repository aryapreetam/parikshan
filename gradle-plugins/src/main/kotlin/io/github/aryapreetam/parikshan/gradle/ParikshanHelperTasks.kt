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

  @get:InputDirectory
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
    val udid = simulatorUdid.orNull
    if (!udid.isNullOrBlank()) {
      ProcessBuilder("xcrun", "simctl", "terminate", udid, bundleId.get()).start().waitFor()
      logger.lifecycle("Parikshan iOS: App terminated")
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

  @get:InputDirectory
  abstract val projectDir: DirectoryProperty

  @get:InputDirectory
  abstract val rootDir: DirectoryProperty

  @get:OutputDirectory
  abstract val derivedDataDir: DirectoryProperty

  @get:OutputDirectory
  abstract val buildDir: DirectoryProperty

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
    if (isCi) {
      logger.lifecycle("Parikshan iOS: Running pre-run simulator cleanup (CI environment)...")
      runCatching { ProcessBuilder("killall", "Simulator").start().waitFor() }
      runCatching { ProcessBuilder("xcrun", "simctl", "shutdown", "all").start().waitFor() }
    }

    val process = ProcessBuilder("xcrun", "simctl", "list", "devices", "available").directory(projectDirFile).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()

    var runtime = ""
    var selectedUdid = ""
    var selectedName = deviceVal
    var isBooted = false

    output.lineSequence().forEach { line ->
      if (line.startsWith("--")) {
        runtime = line.trim('-', ' ')
      } else if (line.contains("(")) {
        val name = line.substringBefore("(").trim()
        val udid = line.substringAfter("(").substringBefore(")")
        val state = line.substringAfterLast("(").substringBefore(")")
        if ((deviceVal == "booted" && state.contains("Booted", ignoreCase = true)) || deviceVal == name || deviceVal == udid) {
          if (selectedUdid.isEmpty() || state.contains("Booted", ignoreCase = true)) {
            selectedUdid = udid
            selectedName = name
            isBooted = state.contains("Booted", ignoreCase = true)
          }
        }
      }
    }

    if (selectedUdid.isEmpty()) {
      selectedUdid = "iPhone 16"
    }

    logger.lifecycle("Parikshan iOS: Using simulator '$selectedName' ($selectedUdid)")

    if (!isBooted) {
      logger.lifecycle("Parikshan iOS: Booting simulator...")
      ProcessBuilder("xcrun", "simctl", "boot", selectedUdid).start().waitFor()
      if (!isCi) { runCatching { ProcessBuilder("open", "-a", "Simulator").start().waitFor() } }
      ProcessBuilder("xcrun", "simctl", "bootstatus", selectedUdid, "-b").start().waitFor()
    }

    val originalIosAppDir = File(projVal).parentFile
    val generatedIosAppDir = File(buildDirFile, "parikshan/ios-host")
    generatedIosAppDir.deleteRecursively()
    originalIosAppDir.copyRecursively(generatedIosAppDir)

    val absoluteGradlew = File(rootDirFile, "gradlew").absolutePath
    val gradlewShim = File(generatedIosAppDir, "gradlew")
    val shimContent = """
        #!/bin/sh
        exec "$absoluteGradlew" -p "${rootDirFile.absolutePath}" --no-configuration-cache -Pparikshan.e2e.active=true -Pparikshan.token=$tokenVal "${'$'}@"
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
      redirectErrorStream(true)
      redirectOutput(logFile)
    }.start()

    if (!buildProcess.waitFor(timeoutVal, java.util.concurrent.TimeUnit.SECONDS)) {
      buildProcess.destroyForcibly()
      throw GradleException("Parikshan iOS: xcodebuild compilation timed out after $timeoutVal seconds.")
    }
    val buildResult = buildProcess.exitValue()
    if (buildResult != 0) {
      val lines = logFile.readLines()
      lines.takeLast(50).forEach { logger.error(it) }
      throw GradleException("xcodebuild failed with exit code $buildResult")
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
    while (System.currentTimeMillis() <= deadline) {
      if (postPing(activePort, tokenVal)) {
        serverReady = true
        break
      }
      Thread.sleep(500)
    }

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

  private fun postPing(port: Int, token: String): Boolean {
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
}

abstract class ParikshanStopAndroidTask : DefaultTask() {
  @get:Input
  @get:Optional
  abstract val androidSerial: Property<String>

  @get:Input
  abstract val port: Property<String>

  @get:Input
  abstract val applicationId: Property<String>

  @get:InputDirectory
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

  @get:InputDirectory
  abstract val projectDir: DirectoryProperty

  @TaskAction
  fun run() {
    val workingDir = projectDir.get().asFile
    val serial = AndroidTargetConfigurer.AndroidRecorder.resolveDeviceSerial(logger, workingDir, androidSerial.orNull)
    val appId = applicationId.get()
    val resolvedLauncher = launcherActivity.orNull
    val portVal = port.get()

    logger.lifecycle("Parikshan Android: Resolved applicationId: $appId")
    if (resolvedLauncher != null) {
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
    if (resolvedLauncher != null) {
      command.add("-e")
      command.add("launcher_class")
      command.add(resolvedLauncher)
    }
    command.add("$testPackage/androidx.test.runner.AndroidJUnitRunner")

    ProcessBuilder(command).start()
  }
}

abstract class ParikshanPrepareIosSourceTask : DefaultTask() {
  @get:InputDirectory
  abstract val iosProjectDir: DirectoryProperty

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
          val withoutComposeImport = original.replace(Regex("""import\s+androidx\.compose\.ui\.window\.ComposeUIViewController\s*\R"""), "")
          val instrumented = "import io.github.aryapreetam.parikshan.ParikshanUIViewController\n" + withoutComposeImport.replace("ComposeUIViewController", "ParikshanUIViewController")
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
  @get:InputDirectory
  abstract val projectDir: DirectoryProperty

  @get:OutputDirectory
  abstract val generatedDir: DirectoryProperty

  @TaskAction
  fun run() {
    val genDirFile = generatedDir.get().asFile
    val projectDirFile = projectDir.get().asFile
    genDirFile.deleteRecursively()
    genDirFile.mkdirs()
    val sourceDirs = listOf(
      File(projectDirFile, "src/webMain/kotlin"),
      File(projectDirFile, "src/wasmJsMain/kotlin"),
      File(projectDirFile, "src/jsMain/kotlin"),
      File(projectDirFile, "src/commonMain/kotlin")
    )
    var copiedAny = false
    for (srcDir in sourceDirs) {
      if (srcDir.exists()) {
        srcDir.copyRecursively(genDirFile, overwrite = true)
        logger.lifecycle("Parikshan Wasm: Found source directory at ${srcDir.absolutePath}")
        copiedAny = true
        break
      }
    }
    if (!copiedAny) {
      logger.warn("Parikshan Wasm: No source directories found in ${projectDirFile.absolutePath}")
    }
    genDirFile.walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .forEach { file ->
        val original = file.readText()
        if (original.contains("ComposeViewport")) {
          var result = original.replace(Regex("""import\s+androidx\.compose\.ui\.window\.ComposeViewport\s*\R"""), "").replace("ComposeViewport", "ParikshanComposeViewport")
          result = "import io.github.aryapreetam.parikshan.ParikshanComposeViewport\nimport io.github.aryapreetam.parikshan.initializeParikshanWasm\n" + result
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
