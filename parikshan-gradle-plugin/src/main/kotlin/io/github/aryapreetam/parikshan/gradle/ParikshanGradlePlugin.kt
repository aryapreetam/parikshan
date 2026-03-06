package io.github.aryapreetam.parikshan.gradle

import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

abstract class ParikshanExtension @Inject constructor(
  objects: ObjectFactory
) {
  @get:Input
  val appJarTaskName: Property<String> = objects.property(String::class.java).convention("packageUberJarForCurrentOS")

  @get:Input
  val desktopTestTaskName: Property<String> = objects.property(String::class.java).convention("desktopTest")

  @get:Input
  val appArgs: ListProperty<String> = objects.listProperty(String::class.java).convention(listOf("--test-mode"))

  @get:Input
  val host: Property<String> = objects.property(String::class.java).convention("127.0.0.1")

  @get:Input
  val port: Property<Int> = objects.property(Int::class.java).convention(9877)

  @get:Input
  val startupTimeoutMs: Property<Long> = objects.property(Long::class.java).convention(90_000L)

  @get:Input
  val startupPollIntervalMs: Property<Long> = objects.property(Long::class.java).convention(250L)

  @get:Input
  val wasmDistributionTaskName: Property<String> = objects.property(String::class.java).convention("wasmJsBrowserDistribution")

  @get:Input
  val wasmServerPort: Property<Int> = objects.property(Int::class.java).convention(8081)
}

class ParikshanGradlePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create<ParikshanExtension>("parikshan")

    // --- Desktop Tasks ---

    val startDesktopTask =
      project.tasks.register("startParikshanDesktopApp") {
        group = "verification"
        description = "Builds and launches desktop app in Parikshan test mode"

        doLast {
          ParikshanDesktopProcess.start(
            project = project,
            appArgs = extension.appArgs.get(),
            host = extension.host.get(),
            port = extension.port.get(),
            startupTimeoutMs = extension.startupTimeoutMs.get(),
            startupPollIntervalMs = extension.startupPollIntervalMs.get()
          )
        }
      }

    val stopDesktopTask =
      project.tasks.register("stopParikshanDesktopApp") {
        group = "verification"
        description = "Stops desktop app launched for Parikshan E2E tests"
        doLast {
          ParikshanDesktopProcess.stop()
        }
      }

    // --- Wasm Tasks ---

    // Pre-resolve all build directory paths during configuration (CC-safe providers).
    val wasmOutputDir = project.layout.buildDirectory.dir("parikshan/wasm-app")
    val wasmDevDir = project.layout.buildDirectory.dir("kotlin-webpack/wasmJs/developmentExecutable")
    val wasmProdDir = project.layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    val wasmResourcesDir = project.layout.buildDirectory.dir("processedResources/wasmJs/main")

    // 1. Prepare Assets Task: Merges webpack/distribution output with resources (index.html) if needed
    val prepareWasmAssetsTask = project.tasks.register("prepareParikshanWasmAssets") {
      group = "verification"
      description = "Prepares Wasm assets (JS, Wasm, HTML) for Parikshan serving"

      val distTaskName = extension.wasmDistributionTaskName.get()
      val isDevelopment = distTaskName.contains("Development", ignoreCase = true)
      dependsOn(distTaskName)
      // Ensure processResources runs so index.html is available
      project.tasks.findByName("wasmJsProcessResources")?.let { dependsOn(it) }

      // Declare inputs/outputs for caching; providers are resolved lazily.
      val primaryDistDir = if (isDevelopment) wasmDevDir else wasmProdDir
      inputs.dir(primaryDistDir).optional()
      inputs.dir(wasmResourcesDir).optional()
      outputs.dir(wasmOutputDir)

      doLast {
        val output = wasmOutputDir.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        // Resolve distribution directory: primary (based on task name) → fallback to whichever exists.
        val primary = primaryDistDir.get().asFile
        val distDir = when {
          primary.exists() -> primary
          wasmDevDir.get().asFile.exists() -> wasmDevDir.get().asFile
          wasmProdDir.get().asFile.exists() -> wasmProdDir.get().asFile
          else -> throw GradleException(
            "Parikshan: Could not find Wasm distribution output.\n" +
              "  Checked: ${primary.absolutePath}\n" +
              "  Dev:     ${wasmDevDir.get().asFile.absolutePath}\n" +
              "  Prod:    ${wasmProdDir.get().asFile.absolutePath}\n" +
              "Ensure '${distTaskName}' has run successfully."
          )
        }
        logger.lifecycle("Parikshan: Copying Wasm distribution from ${distDir.absolutePath}")
        distDir.copyRecursively(output, overwrite = true)

        // Ensure index.html exists (webpack output doesn't always include it)
        val indexHtml = File(output, "index.html")
        if (!indexHtml.exists()) {
          val srcIndex = File(wasmResourcesDir.get().asFile, "index.html")
          if (srcIndex.exists()) {
            srcIndex.copyTo(indexHtml)
            logger.lifecycle("Parikshan: Copied index.html from processedResources")
          }
        }

        if (!indexHtml.exists()) {
          logger.warn("Parikshan: Warning — index.html not found in ${output.absolutePath}. Wasm app will not load.")
        }
      }
    }

    // 2. Start Server Task
    val startWasmTask =
      project.tasks.register("startParikshanWasmApp") {
        group = "verification"
        description = "Builds and serves Wasm app for Parikshan E2E tests"
        dependsOn(prepareWasmAssetsTask)

        // Capture port during configuration (CC-safe)
        val port = extension.wasmServerPort.get()

        doLast {
          val outputDir = wasmOutputDir.get().asFile
          if (!outputDir.exists()) {
            throw GradleException("Parikshan Wasm assets not found at $outputDir. 'prepareParikshanWasmAssets' may have failed.")
          }
          ParikshanWasmServer.start(port, outputDir)
          logger.lifecycle("Parikshan: Wasm server started at http://127.0.0.1:$port")
        }
      }

    val stopWasmTask =
      project.tasks.register("stopParikshanWasmApp") {
        group = "verification"
        description = "Stops Wasm server launched for Parikshan E2E tests"
        doLast {
          ParikshanWasmServer.stop()
        }
      }

    // 3. Install Playwright Browsers Task
    val installPlaywrightTask = project.tasks.register<JavaExec>("installPlaywrightBrowsers") {
        group = "verification"
        description = "Installs Playwright browsers required for Parikshan Wasm tests"
        mainClass.set("com.microsoft.playwright.CLI")
        args = listOf("install", "chromium")

        // Use the test runtime classpath which should contain the Playwright dependency.
        // findByName is safe here: this lambda runs during task realization (configuration phase).
        val jvmTestTask = project.tasks.findByName("jvmTest") as? Test
            ?: project.tasks.findByName("test") as? Test

        if (jvmTestTask != null) {
            classpath = jvmTestTask.classpath
        } else {
            logger.warn("Parikshan: Could not resolve 'jvmTest' or 'test' task to find Playwright classpath.")
        }
    }

    project.afterEvaluate {
      startDesktopTask.configure {
        dependsOn(extension.appJarTaskName.get())
      }

      val desktopTestTask = project.tasks.named<Test>(extension.desktopTestTaskName.get())
      desktopTestTask.configure {
        dependsOn(startDesktopTask)
        finalizedBy(stopDesktopTask)

        systemProperty("parikshan.host", extension.host.get())
        systemProperty("parikshan.port", extension.port.get().toString())
        systemProperty("parikshan.target", "desktop")
      }

      project.tasks.register("e2eDesktopTest") {
        group = "verification"
        description = "Runs desktop tests with visible app automation through Parikshan"
        dependsOn(desktopTestTask)
      }

      // Wasm E2E Test Task
      project.tasks.register<Test>("e2eWasmTest") {
          group = "verification"
          description = "Runs Wasm E2E tests with browser automation through Parikshan"
          dependsOn(installPlaywrightTask)
          dependsOn(startWasmTask)
          finalizedBy(stopWasmTask)

          // Inherit classpath/testClasses from the standard test task or a configured one
          val jvmTestTask = project.tasks.findByName("jvmTest") as? Test 
              ?: project.tasks.findByName("test") as? Test

          if (jvmTestTask != null) {
              testClassesDirs = jvmTestTask.testClassesDirs
              classpath = jvmTestTask.classpath
          }

          val port = extension.wasmServerPort.get()
          systemProperty("parikshan.target", "wasm")
          systemProperty("parikshan.wasm.url", "http://127.0.0.1:$port")
          
          // Pass through video properties, preferring Gradle properties (-P)
          val videoEnabled = project.providers.gradleProperty("parikshan.video.enabled").orNull
              ?: System.getProperty("parikshan.video.enabled") 
              ?: "false"
              
          val videoOutputDir = project.providers.gradleProperty("parikshan.video.outputDir").orNull
              ?: System.getProperty("parikshan.video.outputDir")
              ?: project.layout.buildDirectory.dir("parikshan/videos/wasm").get().asFile.absolutePath
          
          systemProperty("parikshan.video.enabled", videoEnabled)
          systemProperty("parikshan.video.outputDir", videoOutputDir)
          
          val videoFps = project.providers.gradleProperty("parikshan.video.fps").orNull
          if (videoFps != null) systemProperty("parikshan.video.fps", videoFps)
          
          val videoShowCursor = project.providers.gradleProperty("parikshan.video.showCursor").orNull
          if (videoShowCursor != null) systemProperty("parikshan.video.showCursor", videoShowCursor)
          
          val videoStepDelayMs = project.providers.gradleProperty("parikshan.video.stepDelayMs").orNull
          if (videoStepDelayMs != null) systemProperty("parikshan.video.stepDelayMs", videoStepDelayMs)
          
          // Pass through Wasm specific properties
          val headless = project.providers.gradleProperty("parikshan.wasm.headless").orNull
          if (headless != null) systemProperty("parikshan.wasm.headless", headless)
          
          val viewportWidth = project.providers.gradleProperty("parikshan.wasm.viewportWidth").orNull
          if (viewportWidth != null) systemProperty("parikshan.wasm.viewportWidth", viewportWidth)

          val viewportHeight = project.providers.gradleProperty("parikshan.wasm.viewportHeight").orNull
          if (viewportHeight != null) systemProperty("parikshan.wasm.viewportHeight", viewportHeight)

          val bridgeTimeout = project.providers.gradleProperty("parikshan.wasm.bridgeReadyTimeoutMs").orNull
          if (bridgeTimeout != null) systemProperty("parikshan.wasm.bridgeReadyTimeoutMs", bridgeTimeout)
          
          // Default filter: only run E2E test classes (convention: *E2ETest*)
          // This can be overridden via -Pparikshan.testFilter=<pattern>
          val testFilter = project.providers.gradleProperty("parikshan.testFilter").orNull
          filter {
              isFailOnNoMatchingTests = true
              if (!testFilter.isNullOrBlank()) {
                  testFilter.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { includeTestsMatching(it) }
              } else {
                  includeTestsMatching("*E2ETest*")
              }
          }
      }

      // Android E2E Test Task (instrumentation/emulator)
      val stopAndroidVideoTask =
        project.tasks.register("stopParikshanAndroidVideo") {
          group = "verification"
          description = "Stops Android screen recording and pulls video artifact for Parikshan E2E tests"

          doLast {
            val enabled =
              project.providers.gradleProperty("parikshan.video.enabled").orNull
                ?: System.getProperty("parikshan.video.enabled")
                ?: "false"
            if (!enabled.toBooleanStrictOrNull().orFalse()) {
              return@doLast
            }

            val postRollMs =
              project.providers.gradleProperty("parikshan.video.postRollMs").orNull
                ?.toLongOrNull()
                ?.coerceIn(0L, 10_000L)
                ?: 1_500L

            ParikshanAndroidRecorder.stopAndPull(
              project = project,
              postRollMs = postRollMs
            )
          }
        }

      project.tasks.register("e2eAndroidTest") {
        group = "verification"
        description = "Runs Android emulator instrumentation tests for Parikshan E2E"

        val connectedTask = project.tasks.findByName("connectedDebugAndroidTest")
        if (connectedTask != null) {
          connectedTask.doFirst {
            val enabled =
              project.providers.gradleProperty("parikshan.video.enabled").orNull
                ?: System.getProperty("parikshan.video.enabled")
                ?: "false"
            if (!enabled.toBooleanStrictOrNull().orFalse()) {
              return@doFirst
            }

            val outputDir =
              project.providers.gradleProperty("parikshan.video.outputDir").orNull
                ?: System.getProperty("parikshan.video.outputDir")
                ?: project.layout.buildDirectory.dir("parikshan/videos/android").get().asFile.absolutePath
            val remoteOutputPath =
              project.providers.gradleProperty("parikshan.video.remotePath").orNull
                ?: System.getProperty("parikshan.video.remotePath")
                ?: "/sdcard/Download/parikshan-e2e.mp4"
            val maxDurationSec =
              project.providers.gradleProperty("parikshan.video.maxDurationSec").orNull
                ?.toIntOrNull()
                ?.coerceIn(10, 180)
                ?: 180
            val watchPackage =
              project.providers.gradleProperty("parikshan.android.watchPackage").orNull
            val testFilter =
              project.providers.gradleProperty("parikshan.testFilter").orNull
            val startTimeoutMs =
              project.providers.gradleProperty("parikshan.video.startTimeoutMs").orNull
                ?.toLongOrNull()
                ?.coerceIn(5_000L, 600_000L)
                ?: 180_000L
            val startDelayMs =
              project.providers.gradleProperty("parikshan.video.startDelayMs").orNull
                ?.toLongOrNull()
                ?.coerceIn(0L, 10_000L)
                ?: 3_000L

            val explicitSerial =
              project.providers.gradleProperty("parikshan.android.deviceSerial").orNull
                ?: System.getenv("ANDROID_SERIAL")
            val serial = ParikshanAndroidRecorder.resolveDeviceSerial(project, explicitSerial)

            val outputDirectoryFile = File(outputDir).also { it.mkdirs() }
            val timestamp = System.currentTimeMillis()
            val localOutputPath = File(outputDirectoryFile, "android-e2e-$timestamp.mp4").absolutePath

            ParikshanAndroidRecorder.start(
              project = project,
              serial = serial,
              remoteOutputPath = remoteOutputPath,
              localOutputPath = localOutputPath,
              maxDurationSec = maxDurationSec,
              watchPackage = watchPackage,
              testFilter = testFilter,
              startTimeoutMs = startTimeoutMs,
              startDelayMs = startDelayMs
            )
          }
          connectedTask.finalizedBy(stopAndroidVideoTask)
          dependsOn(connectedTask)
        } else {
          doFirst {
            throw GradleException(
              "Parikshan could not find 'connectedDebugAndroidTest'. " +
                "Ensure the Android target is configured and an emulator/device is available."
            )
          }
        }
      }
    }
  }
}

private object ParikshanWasmServer {
    private var server: com.sun.net.httpserver.HttpServer? = null

    fun start(port: Int, root: File) {
        stop()

        val server = com.sun.net.httpserver.HttpServer.create(InetSocketAddress(port), 0)
        server.createContext("/") { exchange ->
            try {
                val uri = exchange.requestURI
                var path = uri.path
                if (path == "/") path = "/index.html"

                // Security: prevent directory traversal
                val safeRoot = root.canonicalFile
                val file = File(root, path.removePrefix("/")).canonicalFile

                if (!file.path.startsWith(safeRoot.path)) {
                    val msg = "403 Forbidden"
                    exchange.sendResponseHeaders(403, msg.length.toLong())
                    exchange.responseBody.use { it.write(msg.toByteArray()) }
                    return@createContext
                }

                // Cross-Origin Isolation headers — required for SharedArrayBuffer.
                // Compose WASM (Skia/Skiko) uses SharedArrayBuffer for threading;
                // without these headers the WASM module silently fails to initialize.
                exchange.responseHeaders.set("Cross-Origin-Opener-Policy", "same-origin")
                exchange.responseHeaders.set("Cross-Origin-Embedder-Policy", "require-corp")

                if (file.exists() && file.isFile) {
                    val mimeType = when (file.extension) {
                        "html" -> "text/html"
                        "js" -> "application/javascript"
                        "mjs" -> "application/javascript"
                        "wasm" -> "application/wasm"
                        "css" -> "text/css"
                        "json" -> "application/json"
                        "png" -> "image/png"
                        "svg" -> "image/svg+xml"
                        else -> "application/octet-stream"
                    }

                    exchange.responseHeaders.set("Content-Type", mimeType)
                    exchange.sendResponseHeaders(200, file.length())
                    file.inputStream().use { input ->
                        exchange.responseBody.use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    val msg = "404 Not Found: $path"
                    exchange.sendResponseHeaders(404, msg.length.toLong())
                    exchange.responseBody.use { it.write(msg.toByteArray()) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try { exchange.sendResponseHeaders(500, 0) } catch (_: Exception) {}
                runCatching { exchange.responseBody.close() }
            }
        }
        server.executor = null // Default executor
        server.start()
        this.server = server
    }

    fun stop() {
        server?.stop(0)
        server = null
    }
}

private object ParikshanAndroidRecorder {
  @Volatile
  private var serial: String? = null

  @Volatile
  private var remoteOutputPath: String? = null

  @Volatile
  private var localOutputPath: String? = null

  @Volatile
  private var recordingProcess: Process? = null

  @Volatile
  private var startWatcherThread: Thread? = null

  @Volatile
  private var stopRequested: Boolean = false

  @Volatile
  private var startFailure: String? = null

  fun resolveDeviceSerial(
    project: Project,
    explicitSerial: String?
  ): String {
    if (!explicitSerial.isNullOrBlank()) {
      return explicitSerial
    }

    val output =
      runAdbCommand(
        project = project,
        serial = null,
        args = listOf("devices"),
        ignoreExitCode = false
      )
    val devices =
      output
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("List of devices attached") }
        .mapNotNull { line ->
          val parts = line.split(Regex("\\s+"))
          if (parts.size >= 2 && parts[1] == "device") parts[0] else null
        }
        .toList()

    if (devices.isEmpty()) {
      throw GradleException(
        "Parikshan could not find a connected Android device. " +
          "Connect a device/emulator or pass -Pparikshan.android.deviceSerial=<serial>."
      )
    }
    if (devices.size > 1) {
      project.logger.lifecycle(
        "Parikshan: Multiple Android devices detected (${devices.joinToString()}). Using '${devices.first()}'. " +
          "Set -Pparikshan.android.deviceSerial to override."
      )
    }
    return devices.first()
  }

  fun start(
    project: Project,
    serial: String,
    remoteOutputPath: String,
    localOutputPath: String,
    maxDurationSec: Int,
    watchPackage: String?,
    testFilter: String?,
    startTimeoutMs: Long,
    startDelayMs: Long
  ) {
    stopRequested = false
    startFailure = null
    runCatching {
      startWatcherThread?.interrupt()
      startWatcherThread?.join(1_000)
    }
    runCatching { recordingProcess?.destroyForcibly() }
    runAdbCommand(
      project = project,
      serial = serial,
      args = listOf("shell", "sh", "-c", "pkill -2 screenrecord || killall -2 screenrecord || true"),
      ignoreExitCode = true
    )
    Thread.sleep(300)

    runAdbCommand(
      project = project,
      serial = serial,
      args = listOf("shell", "rm", "-f", remoteOutputPath),
      ignoreExitCode = true
    )

    this.serial = serial
    this.remoteOutputPath = remoteOutputPath
    this.localOutputPath = localOutputPath
    this.recordingProcess = null

    val resolvedWatchPackage = resolveWatchPackage(project, serial, watchPackage, testFilter)
    if (!resolvedWatchPackage.isNullOrBlank()) {
      runAdbCommand(
        project = project,
        serial = serial,
        args = listOf("shell", "am", "force-stop", resolvedWatchPackage),
        ignoreExitCode = true
      )
      project.logger.lifecycle(
        "Parikshan: Will start Android video capture when package '$resolvedWatchPackage' starts."
      )
    } else {
      project.logger.lifecycle(
        "Parikshan: Could not resolve a watch package; recording will start immediately."
      )
    }

    val watcher =
      Thread(
        {
          try {
            if (!resolvedWatchPackage.isNullOrBlank()) {
              val deadline = System.currentTimeMillis() + startTimeoutMs
              var detected = false
              while (!stopRequested && System.currentTimeMillis() <= deadline) {
                if (isPackageProcessRunning(project, serial, resolvedWatchPackage)) {
                  detected = true
                  break
                }
                Thread.sleep(250)
              }

              if (!detected && !stopRequested) {
                project.logger.warn(
                  "Parikshan: Timed out waiting for package '$resolvedWatchPackage'; " +
                    "starting recording immediately."
                )
              } else if (detected && startDelayMs > 0L) {
                Thread.sleep(startDelayMs)
              }
            }

            if (stopRequested) {
              return@Thread
            }

            startScreenrecordProcess(
              project = project,
              serial = serial,
              remoteOutputPath = remoteOutputPath,
              maxDurationSec = maxDurationSec
            )
          } catch (ie: InterruptedException) {
            // Expected when build stops/cancels.
          } catch (t: Throwable) {
            startFailure = t.message ?: t::class.java.simpleName
            project.logger.warn("Parikshan: Android video start watcher failed: ${t.message}")
          }
        },
        "parikshan-android-video-start-watcher"
      )
    watcher.isDaemon = true
    watcher.start()
    this.startWatcherThread = watcher
  }

  fun stopAndPull(
    project: Project,
    postRollMs: Long
  ) {
    val activeSerial = serial
    val activeRemoteOutputPath = remoteOutputPath
    val activeLocalOutputPath = localOutputPath

    try {
      if (activeSerial.isNullOrBlank() || activeRemoteOutputPath.isNullOrBlank() || activeLocalOutputPath.isNullOrBlank()) {
        return
      }

      stopRequested = true
      runCatching {
        startWatcherThread?.interrupt()
        startWatcherThread?.join(2_000)
      }

      val activeRecordingProcess = recordingProcess
      val activeStartFailure = startFailure
      if (activeRecordingProcess == null) {
        if (!activeStartFailure.isNullOrBlank()) {
          throw GradleException("Parikshan Android video capture failed to start: $activeStartFailure")
        }
        project.logger.warn("Parikshan: Android video recording did not start; skipping artifact pull.")
        return
      }

      if (postRollMs > 0L) {
        project.logger.lifecycle("Parikshan: Android video post-roll ${postRollMs}ms")
        Thread.sleep(postRollMs)
      }

      runAdbCommand(
        project = project,
        serial = activeSerial,
        args = listOf("shell", "sh", "-c", "pkill -2 screenrecord || killall -2 screenrecord || true"),
        ignoreExitCode = true
      )
      if (!activeRecordingProcess.waitFor(20, TimeUnit.SECONDS)) {
        activeRecordingProcess.destroy()
        if (!activeRecordingProcess.waitFor(5, TimeUnit.SECONDS)) {
          activeRecordingProcess.destroyForcibly()
          activeRecordingProcess.waitFor(5, TimeUnit.SECONDS)
        }
      }
      Thread.sleep(500)

      val outputFile = File(activeLocalOutputPath)
      outputFile.parentFile?.mkdirs()

      runAdbCommand(
        project = project,
        serial = activeSerial,
        args = listOf("pull", activeRemoteOutputPath, activeLocalOutputPath),
        ignoreExitCode = true
      )
      runAdbCommand(
        project = project,
        serial = activeSerial,
        args = listOf("shell", "rm", "-f", activeRemoteOutputPath),
        ignoreExitCode = true
      )

      if (outputFile.exists() && outputFile.length() > 0L) {
        project.logger.lifecycle("Parikshan: Android video saved to ${outputFile.absolutePath}")
      } else {
        project.logger.warn(
          "Parikshan: Android video was not produced at ${outputFile.absolutePath}. " +
            "Check build/parikshan/android-screenrecord.log"
        )
      }
    } finally {
      clearState()
    }
  }

  private fun runAdbCommand(
    project: Project,
    serial: String?,
    args: List<String>,
    ignoreExitCode: Boolean
  ): String {
    val adbExecutable = resolveAdbCommand()
    val command = mutableListOf(adbExecutable)
    if (!serial.isNullOrBlank()) {
      command += listOf("-s", serial)
    }
    command += args

    val process =
      ProcessBuilder(command)
        .directory(project.projectDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val code = process.waitFor()

    if (!ignoreExitCode && code != 0) {
      throw GradleException(
        "Parikshan adb command failed (${command.joinToString(" ")}), exit=$code, output=${output.ifBlank { "<empty>" }}"
      )
    }
    return output
  }

  private fun resolveAdbCommand(): String {
    val sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
    if (!sdkRoot.isNullOrBlank()) {
      val candidate = File(sdkRoot, "platform-tools/adb")
      if (candidate.exists()) {
        return candidate.absolutePath
      }
      val windowsCandidate = File(sdkRoot, "platform-tools/adb.exe")
      if (windowsCandidate.exists()) {
        return windowsCandidate.absolutePath
      }
    }
    return "adb"
  }

  private fun clearState() {
    serial = null
    remoteOutputPath = null
    localOutputPath = null
    recordingProcess = null
    startWatcherThread = null
    stopRequested = false
    startFailure = null
  }

  private fun startScreenrecordProcess(
    project: Project,
    serial: String,
    remoteOutputPath: String,
    maxDurationSec: Int
  ) {
    val logFile = project.layout.buildDirectory.file("parikshan/android-screenrecord.log").get().asFile
    logFile.parentFile.mkdirs()
    val adbExecutable = resolveAdbCommand()
    val command =
      mutableListOf(
        adbExecutable,
        "-s",
        serial,
        "shell",
        "screenrecord",
        "--time-limit",
        maxDurationSec.toString(),
        remoteOutputPath
      )
    val process =
      ProcessBuilder(command)
        .directory(project.projectDir)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        .start()

    Thread.sleep(250)
    if (!process.isAlive) {
      val exitCode = runCatching { process.exitValue() }.getOrNull()
      throw GradleException(
        "Parikshan Android video capture failed to start (exit=${exitCode ?: "unknown"}). " +
          "Check ${logFile.absolutePath}"
      )
    }
    recordingProcess = process
    project.logger.lifecycle("Parikshan: Started Android video capture for '$serial' (maxDurationSec=$maxDurationSec)")
  }

  private fun resolveWatchPackage(
    project: Project,
    serial: String,
    explicitWatchPackage: String?,
    testFilter: String?
  ): String? {
    if (!explicitWatchPackage.isNullOrBlank()) {
      return explicitWatchPackage
    }
    resolveAndroidApplicationId(project)?.let { return it }
    extractPackageFromTestFilter(testFilter)?.let { return it }

    val output =
      runAdbCommand(
        project = project,
        serial = serial,
        args = listOf("shell", "pm", "list", "instrumentation"),
        ignoreExitCode = true
      )
    if (output.isBlank()) {
      return null
    }

    val regex =
      Regex("^instrumentation:([^/]+)/([^\\s]+) \\(target=([^)]+)\\)$")
    val entries =
      output
        .lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
          val match = regex.find(line) ?: return@mapNotNull null
          InstrumentationEntry(
            runner = match.groupValues[2],
            target = match.groupValues[3]
          )
        }
        .toList()
    if (entries.isEmpty()) {
      return null
    }

    val preferred = entries.filter { it.runner.endsWith("AndroidJUnitRunner") }
    val candidates = if (preferred.isNotEmpty()) preferred else entries
    val targets = candidates.map { it.target }.distinct()
    return if (targets.size == 1) targets.first() else null
  }

  private fun resolveAndroidApplicationId(project: Project): String? {
    val androidExtension = project.extensions.findByName("android") ?: return null
    val defaultConfig =
      runCatching {
        androidExtension.javaClass.methods.firstOrNull { it.name == "getDefaultConfig" }?.invoke(androidExtension)
      }.getOrNull() ?: return null

    return runCatching {
      defaultConfig.javaClass.methods.firstOrNull { it.name == "getApplicationId" }?.invoke(defaultConfig) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }
  }

  private fun extractPackageFromTestFilter(testFilter: String?): String? {
    if (testFilter.isNullOrBlank()) {
      return null
    }
    val firstPattern =
      testFilter
        .split(",")
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.substringBefore('#')
        ?: return null
    val lastDot = firstPattern.lastIndexOf('.')
    if (lastDot <= 0) {
      return null
    }
    return firstPattern.substring(0, lastDot).takeIf { it.isNotBlank() }
  }

  private fun isPackageProcessRunning(
    project: Project,
    serial: String,
    packageName: String
  ): Boolean {
    val output =
      runAdbCommand(
        project = project,
        serial = serial,
        args = listOf("shell", "pidof", packageName),
        ignoreExitCode = true
      )
    return output.isNotBlank()
  }

  private data class InstrumentationEntry(
    val runner: String,
    val target: String
  )
}

private object ParikshanDesktopProcess {
  @Volatile
  private var process: Process? = null

  fun start(
    project: Project,
    appArgs: List<String>,
    host: String,
    port: Int,
    startupTimeoutMs: Long,
    startupPollIntervalMs: Long
  ) {
    stop()

    val jar = resolveDesktopJar(project)
    val javaExecutable = resolveJavaExecutable()
    val logFile = project.layout.buildDirectory.file("parikshan/app-process.log").get().asFile
    logFile.parentFile.mkdirs()

    val command = mutableListOf(javaExecutable.absolutePath, "-jar", jar.absolutePath).apply { addAll(appArgs) }
    project.logger.lifecycle("Parikshan launching desktop app: ${command.joinToString(" ")}")

    val started =
      ProcessBuilder(command)
        .directory(project.projectDir)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        .start()

    process = started
    waitForPort(
      host = host,
      port = port,
      timeout = Duration.ofMillis(startupTimeoutMs),
      poll = Duration.ofMillis(startupPollIntervalMs)
    )
  }

  fun stop() {
    val active = process ?: return
    runCatching {
      active.destroy()
      active.waitFor(3, TimeUnit.SECONDS)
      if (active.isAlive) {
        active.destroyForcibly()
        active.waitFor(2, TimeUnit.SECONDS)
      }
    }
    process = null
  }

  private fun resolveDesktopJar(project: Project): File {
    val jarsDir = project.layout.buildDirectory.dir("compose/jars").get().asFile
    val jar =
      jarsDir
        .listFiles()
        ?.filter { it.isFile && it.extension == "jar" }
        ?.maxByOrNull { it.lastModified() }

    return requireNotNull(jar) {
      "Parikshan could not find a desktop executable jar in ${jarsDir.absolutePath}."
    }
  }

  private fun resolveJavaExecutable(): File {
    val javaHome = System.getProperty("java.home") ?: throw GradleException("java.home is not available")
    return File(javaHome, "bin/java")
  }

  private fun waitForPort(
    host: String,
    port: Int,
    timeout: Duration,
    poll: Duration
  ) {
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    while (System.currentTimeMillis() <= deadline) {
      val ready =
        runCatching {
          Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 750)
          }
          true
        }.getOrDefault(false)

      if (ready) {
        return
      }
      Thread.sleep(poll.toMillis())
    }

    throw GradleException("Parikshan timed out waiting for app server at $host:$port")
  }
}

private fun Boolean?.orFalse(): Boolean = this == true
