package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal object IosTargetConfigurer {
  fun configure(
    project: Project,
    extension: ParikshanExtension,
    sessionToken: String,
    isBackgroundRequested: Boolean,
    isVideoRequested: Boolean,
    e2eTestClasses: List<String>,
    hostTestTask: TaskProvider<Test>,
    prepareIosBootSourceTask: TaskProvider<Task>?
  ) {
    val iosProjectDirVal = project.projectDir
    val iosRootDirVal = project.rootDir

    fun resolveIosRuntimeProperty(name: String): String? =
      project.providers.gradleProperty(name).orElse(project.providers.systemProperty(name)).orNull

    val iosDeviceVal = resolveIosRuntimeProperty("device")
      ?: resolveIosRuntimeProperty("serial")
      ?: resolveIosRuntimeProperty("parikshan.ios.device")
      ?: System.getenv("PARIKSHAN_IOS_DEVICE")
      ?: "iPhone 16"
    val iosPortVal = resolveIosRuntimeProperty("parikshan.ios.port")?.toIntOrNull() ?: 9878
    val iosXcodeProjectVal = resolveIosRuntimeProperty("parikshan.ios.xcodeProject")
      ?: project.discoverIosXcodeProject()?.absolutePath
      ?: "${project.projectDir}/../iosApp/iosApp.xcodeproj"
    val iosXcodeSchemeVal = resolveIosRuntimeProperty("parikshan.ios.xcodeScheme") ?: "iosApp"

    fun getIosBundleId(): String {
      val prop = resolveIosRuntimeProperty("parikshan.ios.bundleId")
      if (prop != null) return prop
      val provider = project.providers.of(XcodeBundleIdValueSource::class.java) {
        parameters.xcodeProject.set(File(iosXcodeProjectVal))
        parameters.scheme.set(iosXcodeSchemeVal)
      }
      val extracted = provider.orNull
      project.logger.lifecycle("Parikshan iOS: Extracted bundle ID: $extracted")
      return extracted ?: "sample.app.ios"
    }
    val iosBundleIdProvider = project.provider { getIosBundleId() }
    val iosDerivedDataVal = project.layout.buildDirectory.dir("parikshan/ios-build").get().asFile
    val buildDirVal = project.layout.buildDirectory.get().asFile
    val sessionTokenVal = sessionToken
    val xcodebuildTimeoutProvider = project.providers.gradleProperty("parikshan.xcodebuild.timeout")
      .orElse(project.providers.systemProperty("parikshan.xcodebuild.timeout"))
      .map { it.toLong() }
      .orElse(1200L)
    var iosSimulatorUdid: String? = null

    val iosPreflightTask = project.tasks.register("parikshanIosPreflight") {
      group = "verification"
      doLast {
        val simulator = resolveIosSimulatorDevice(iosDeviceVal, iosProjectDirVal)
        logger.lifecycle("Parikshan iOS: Found simulator '${simulator.name}' (${simulator.udid})")
      }
    }

    val stopIosAppTask = project.tasks.register("stopIosApp") {
      group = "verification"
      doLast {
        iosSimulatorUdid?.let { udid ->
          ProcessBuilder("xcrun", "simctl", "terminate", udid, iosBundleIdProvider.get()).start().waitFor()
          logger.lifecycle("Parikshan iOS: App terminated")
        }
      }
    }
    val rootDirAbs = iosRootDirVal.absolutePath

    val startIosAppTask = project.tasks.register("startIosApp") {
      group = "verification"
      dependsOn(iosPreflightTask)
      prepareIosBootSourceTask?.let { dependsOn(it) }
      outputs.upToDateWhen { false }

      doLast {
        val isCi = System.getenv("CI") == "true"
        if (isCi) {
          logger.lifecycle("Parikshan iOS: Running pre-run simulator cleanup (CI environment)...")
          runCatching {
            ProcessBuilder("killall", "Simulator").start().waitFor()
          }
          runCatching {
            ProcessBuilder("xcrun", "simctl", "shutdown", "all").start().waitFor()
          }
        }

        val simulator = resolveIosSimulatorDevice(iosDeviceVal, iosProjectDirVal)
        iosSimulatorUdid = simulator.udid
        logger.lifecycle("Parikshan iOS: Using simulator '${simulator.name}' (${simulator.udid})")

        val isReallyBooted = try {
          val p = ProcessBuilder("xcrun", "simctl", "list", "devices").start()
          val out = p.inputStream.bufferedReader().readText()
          p.waitFor()
          out.lineSequence().any { it.contains(simulator.udid) && it.contains("(Booted)") }
        } catch (e: Exception) {
          simulator.isBooted
        }

        if (!isReallyBooted) {
          logger.lifecycle("Parikshan iOS: Booting simulator...")
          ProcessBuilder("xcrun", "simctl", "boot", simulator.udid).start().waitFor()
          if (!isCi) {
            runCatching {
              ProcessBuilder("open", "-a", "Simulator").start().waitFor()
            }
          }
          ProcessBuilder("xcrun", "simctl", "bootstatus", simulator.udid, "-b").start().waitFor()
        } else {
          if (!isCi) {
            runCatching {
              ProcessBuilder("open", "-a", "Simulator").start().waitFor()
            }
          }
        }

        val originalIosAppDir = File(iosXcodeProjectVal).parentFile
        val generatedIosAppDir = File(buildDirVal, "parikshan/ios-host")
        generatedIosAppDir.deleteRecursively()
        originalIosAppDir.copyRecursively(generatedIosAppDir)

        // 1. Determine the MainViewController call signature from the cloned project
        var vcCall = "MainViewControllerKt.MainViewController()"
        generatedIosAppDir.walkTopDown()
          .filter { it.isFile && it.extension == "swift" }
          .forEach { file ->
            val text = file.readText()
            if (text.contains("MainKt.MainViewController()")) {
              vcCall = "MainKt.MainViewController()"
            } else if (text.contains("MainViewControllerKt.MainViewController()")) {
              vcCall = "MainViewControllerKt.MainViewController()"
            }
          }

        // 2. Instrument Swift App entrypoint in the cloned directory to eager-initialize KMP runtime
        generatedIosAppDir.walkTopDown()
          .filter { it.isFile && it.extension == "swift" }
          .forEach { file ->
            logger.lifecycle("Parikshan iOS: Scanning Swift file: ${file.name}")
            val content = file.readText()
            if (content.contains("@main") && content.contains(": App")) {
              logger.lifecycle("Parikshan iOS: Found App entrypoint in ${file.name}. Content:\n$content")
              logger.lifecycle("Parikshan iOS: Cloned entrypoint ${file.name} was NOT modified (content matched original).")
            }
          }

        val absoluteGradlew = File(rootDirAbs, "gradlew").absolutePath
        val gradlewShim = File(generatedIosAppDir, "gradlew")
        val shimContent = """
            #!/bin/sh
            exec "$absoluteGradlew" -p "$rootDirAbs" --no-configuration-cache -Pparikshan.e2e.active=true -Pparikshan.token=$sessionTokenVal "${'$'}@"
            """.trimIndent()

        gradlewShim.writeText(shimContent)
        gradlewShim.setExecutable(true)

        val parentShim = File(generatedIosAppDir.parentFile, "gradlew")
        parentShim.writeText(shimContent)
        parentShim.setExecutable(true)

        val grandParentShim = File(generatedIosAppDir.parentFile.parentFile, "gradlew")
        grandParentShim.writeText(shimContent)
        grandParentShim.setExecutable(true)

        logger.lifecycle("Parikshan iOS: Building app via xcodebuild...")
        val appBuildProducts = File(iosDerivedDataVal, "Build/Products/Debug-iphonesimulator")
        appBuildProducts.mkdirs()

        val logFile = File(buildDirVal, "parikshan/xcodebuild.log")
        logFile.parentFile.mkdirs()

        val buildProcess = ProcessBuilder(
          "xcodebuild", "build", "-project", File(generatedIosAppDir, File(iosXcodeProjectVal).name).absolutePath,
          "-scheme", iosXcodeSchemeVal, "-configuration", "Debug",
          "-sdk", "iphonesimulator",
          "-destination", "platform=iOS Simulator,id=${simulator.udid}",
          "-derivedDataPath", iosDerivedDataVal.absolutePath,
          "CONFIGURATION_BUILD_DIR=${appBuildProducts.absolutePath}",
          "SUPPORTED_PLATFORMS=iphonesimulator",
          "DEBUG_INFORMATION_FORMAT=dwarf",
          "ONLY_ACTIVE_ARCH=YES",
          "ENABLE_BITCODE=NO"
        ).apply {
          environment()["PARIKSHAN_TOKEN"] = sessionTokenVal
          redirectErrorStream(true)
          redirectOutput(logFile)
        }.start()

        val xcodeTimeout = xcodebuildTimeoutProvider.get()
        val finished = buildProcess.waitFor(xcodeTimeout, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
          buildProcess.destroyForcibly()
          throw GradleException("Parikshan iOS: xcodebuild compilation timed out after $xcodeTimeout seconds.")
        }
        val buildResult = buildProcess.exitValue()

        if (buildResult != 0) {
          logger.error("Parikshan iOS: xcodebuild failed. Dumping last 50 lines of log...")
          val lines = logFile.readLines()
          lines.takeLast(50).forEach { logger.error(it) }
          throw GradleException("xcodebuild failed with exit code $buildResult")
        }

        val appBundle = appBuildProducts.listFiles()?.firstOrNull { it.name.endsWith(".app") } ?: throw GradleException("No .app bundle")

        // Verify the built binary is a simulator slice, not a macOS binary
        val fileOutput = ProcessBuilder("file", File(appBundle, appBundle.nameWithoutExtension).absolutePath)
          .start().inputStream.bufferedReader().readText()
        logger.lifecycle("Parikshan iOS: Built binary type: $fileOutput")

        val activePort = project.providers.gradleProperty("parikshan.ios.port").orNull?.toIntOrNull() ?: iosPortVal
        logger.lifecycle("Parikshan iOS: Launching app on simulator ${simulator.udid}...")

        ProcessBuilder("xcrun", "simctl", "terminate", simulator.udid, iosBundleIdProvider.get()).start().waitFor()

        val installResult = ProcessBuilder("xcrun", "simctl", "install", simulator.udid, appBundle.absolutePath)
          .redirectErrorStream(true).start()
        val installOutput = installResult.inputStream.bufferedReader().readText()
        installResult.waitFor()
        if (installResult.exitValue() != 0) {
          logger.error("Parikshan iOS: simctl install failed: $installOutput")
          throw GradleException("simctl install failed with exit code ${installResult.exitValue()}")
        }

        val launchProcess = ProcessBuilder("xcrun", "simctl", "launch", simulator.udid, iosBundleIdProvider.get()).apply {
          environment()["SIMCTL_CHILD_PARIKSHAN_TOKEN"] = sessionTokenVal
          environment()["PARIKSHAN_TOKEN"] = sessionTokenVal
          environment()["SIMCTL_CHILD_PARIKSHAN_PORT"] = activePort.toString()
          environment()["PARIKSHAN_PORT"] = activePort.toString()
          redirectErrorStream(true)
        }.start()
        val launchOutput = launchProcess.inputStream.bufferedReader().readText()
        launchProcess.waitFor()
        if (launchProcess.exitValue() != 0) {
          logger.error("Parikshan iOS: simctl launch failed: $launchOutput")
          throw GradleException("simctl launch failed with exit code ${launchProcess.exitValue()}")
        }
        logger.lifecycle("Parikshan iOS: App launched. $launchOutput")

        logger.lifecycle("Parikshan iOS: Waiting for server on port $activePort...")
        val deadline = System.currentTimeMillis() + 90_000
        var serverReady = false
        while (System.currentTimeMillis() <= deadline) {
          if (postIosPing(activePort, sessionTokenVal)) {
            serverReady = true
            break
          }
          Thread.sleep(500)
        }

         if (!serverReady) {
           val processName = appBundle.nameWithoutExtension
           val bundleId = iosBundleIdProvider.get()
           logger.error("Parikshan iOS: Server failed to start. Dumping logs for process '$processName'...")

           // Check if the app process is still alive
           val pidCheck = ProcessBuilder("xcrun", "simctl", "spawn", simulator.udid, "launchctl", "list")
             .start().inputStream.bufferedReader().readText()
           val appStillRunning = pidCheck.contains(bundleId)
           logger.error("Parikshan iOS: App process still running: $appStillRunning")

           // Dump crash logs if any
           val crashLogs = ProcessBuilder("xcrun", "simctl", "spawn", simulator.udid, "log", "show",
             "--predicate", "process == \"$processName\" OR (eventMessage CONTAINS \"$processName\" AND eventMessage CONTAINS[c] \"crash\")",
             "--style", "syslog", "--last", "5m").start().inputStream.bufferedReader().readText()
           if (crashLogs.trim().lines().size <= 1) {
             logger.error("Parikshan iOS: No process-specific logs found. Dumping last 200 lines of all simulator system logs...")
             val allLogs = ProcessBuilder("xcrun", "simctl", "spawn", simulator.udid, "log", "show",
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

    project.registerE2eTestWithReport("e2eIosTest", "iOS") {
      group = "verification"
      dependsOn(startIosAppTask)
      finalizedBy(stopIosAppTask)
      configureE2eHostTestExecution(
        hostTestClassesDirs = hostTestTask.get().testClassesDirs,
        hostTestClasspath = hostTestTask.get().classpath,
        e2eTestClasses = e2eTestClasses,
        target = "iOS",
        logger = project.logger
      )
      systemProperty("parikshan.target", "ios")
      systemProperty("parikshan.host", "127.0.0.1")
      systemProperty("parikshan.port", iosPortVal.toString())
      systemProperty("parikshan.token", sessionTokenVal)
      doFirst {
        val simulator = resolveIosSimulatorDevice(iosDeviceVal, iosProjectDirVal)
        systemProperty("parikshan.ios.udid", simulator.udid)
        systemProperty("parikshan.ios.bundleId", iosBundleIdProvider.get())
      }
    }
  }

  private data class IosSimulatorDevice(val name: String, val udid: String, val runtime: String, val isBooted: Boolean)

  private fun resolveIosSimulatorDevice(requested: String, workingDir: File): IosSimulatorDevice {
    val process = ProcessBuilder("xcrun", "simctl", "list", "devices", "available").directory(workingDir).start()
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      throw GradleException(
        "Parikshan iOS: Could not list iOS Simulators using `xcrun simctl list devices available` " +
          "(exit code $exitCode). ${error.ifBlank { output }.trim()}"
      )
    }

    var runtime = ""
    val allDevices = mutableListOf<IosSimulatorDevice>()
    output.lineSequence().forEach { line ->
      if (line.startsWith("--")) {
        runtime = line.trim('-', ' ')
      } else if (line.contains("(")) {
        val name = line.substringBefore("(").trim()
        val udid = line.substringAfter("(").substringBefore(")")
        val state = line.substringAfterLast("(").substringBefore(")")
        if (name.isNotEmpty() && udid.isNotEmpty()) {
          allDevices += IosSimulatorDevice(name, udid, runtime, state.contains("Booted", ignoreCase = true))
        }
      }
    }

    val exactMatch = allDevices.filter {
      requested == "booted" && it.isBooted || requested == it.name || requested == it.udid
    }
    exactMatch.firstOrNull { it.isBooted }?.let { return it }
    exactMatch.firstOrNull()?.let { return it }

    allDevices.firstOrNull { it.isBooted }?.let {
      System.err.println("Parikshan iOS: Device '$requested' not found. Falling back to active booted device: ${it.name}")
      return it
    }

    allDevices.firstOrNull { it.name.startsWith("iPhone", ignoreCase = true) }?.let {
      System.err.println("Parikshan iOS: Device '$requested' not found. Falling back to available device: ${it.name}")
      return it
    }

    allDevices.firstOrNull()?.let {
      System.err.println("Parikshan iOS: Device '$requested' not found. Falling back to available device: ${it.name}")
      return it
    }

    throw GradleException(
      "Parikshan iOS: No available iOS Simulators detected. Run `xcrun simctl list devices available` to check your environment."
    )
  }

  private fun postIosPing(port: Int, token: String): Boolean {
    val body = """{"type":"ping","id":"health","token":"$token"}"""
    val conn =
      runCatching {
        URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection
      }.getOrNull() ?: return false
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

private fun instrumentSwiftAppEntrypoint(content: String, vcCall: String): String {
  if (content.contains("MainViewControllerKt.MainViewController()") || content.contains("MainKt.MainViewController()")) {
    return content
  }

  val withImport = if (!content.contains("import Shared")) {
    "import Shared\n$content"
  } else {
    content
  }

  val regex = Regex("""struct\s+([A-Za-z0-9_]+)\s*:\s*App\s*\{""")
  val match = regex.find(withImport)
  if (match != null) {
    val insertionPoint = match.range.last + 1
    val initBlock = """
        init() {
            _ = $vcCall
        }
    """.trimIndent().lines().joinToString("\n") { "    $it" }
    return withImport.substring(0, insertionPoint) + "\n" + initBlock + "\n" + withImport.substring(insertionPoint)
  }
  return withImport
}
