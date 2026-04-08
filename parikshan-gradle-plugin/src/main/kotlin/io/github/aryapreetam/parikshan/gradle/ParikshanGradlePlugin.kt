package io.github.aryapreetam.parikshan.gradle

import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

abstract class ParikshanExtension @Inject constructor(
  objects: ObjectFactory
) {
  @get:Input
  val appJarTaskName: Property<String> = objects.property(String::class.java).convention("packageUberJarForCurrentOS")

  @get:Input
  val desktopTestTaskName: Property<String> = objects.property(String::class.java)

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
  val wasmDistributionTaskName: Property<String> = objects.property(String::class.java)

  @get:Input
  val wasmServerPort: Property<Int> = objects.property(Int::class.java).convention(8081)

  @get:Input
  val androidLaunchActivityClassName: Property<String> = objects.property(String::class.java)
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
    }

    project.afterEvaluate {
      project.configureParikshanDependencies()
      project.configureAndroidInstrumentationDefaults()
      project.configureGeneratedParikshanRunners(extension)

      val hostTestTaskName = project.resolveHostTestTaskName(extension.desktopTestTaskName.orNull)
      val hostTestTask = project.tasks.named<Test>(hostTestTaskName)
      logger.lifecycle("Parikshan: Using host JVM test task '$hostTestTaskName'")

      val wasmDistributionTaskName =
        project.resolveWasmDistributionTaskName(extension.wasmDistributionTaskName.orNull)
      logger.lifecycle("Parikshan: Using Wasm distribution task '$wasmDistributionTaskName'")

      startDesktopTask.configure {
        dependsOn(extension.appJarTaskName.get())
      }

      prepareWasmAssetsTask.configure {
        val isDevelopment = wasmDistributionTaskName.isDevelopmentWasmTask()
        val primaryDistDir = if (isDevelopment) wasmDevDir else wasmProdDir

        dependsOn(wasmDistributionTaskName)
        project.tasks.findByName("wasmJsProcessResources")?.let { dependsOn(it) }

        inputs.dir(primaryDistDir).optional()
        inputs.dir(wasmResourcesDir).optional()
        outputs.dir(wasmOutputDir)

        doLast {
          val output = wasmOutputDir.get().asFile
          output.deleteRecursively()
          output.mkdirs()

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
                "Ensure '$wasmDistributionTaskName' has run successfully."
            )
          }
          logger.lifecycle("Parikshan: Copying Wasm distribution from ${distDir.absolutePath}")
          distDir.copyRecursively(output, overwrite = true)

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

      installPlaywrightTask.configure {
        classpath = hostTestTask.get().classpath
      }

      project.tasks.register<Test>("e2eDesktopTest") {
        group = "verification"
        description = "Runs desktop tests with visible app automation through Parikshan"
        dependsOn(startDesktopTask)
        finalizedBy(stopDesktopTask)
        outputs.upToDateWhen { false }

        testClassesDirs = hostTestTask.get().testClassesDirs
        classpath = hostTestTask.get().classpath

        systemProperty("parikshan.host", extension.host.get())
        systemProperty("parikshan.port", extension.port.get().toString())
        systemProperty("parikshan.target", "desktop")

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

      // Wasm E2E Test Task
      project.tasks.register<Test>("e2eWasmTest") {
          group = "verification"
          description = "Runs Wasm E2E tests with browser automation through Parikshan"
          dependsOn(installPlaywrightTask)
          dependsOn(startWasmTask)
          finalizedBy(stopWasmTask)
          outputs.upToDateWhen { false } // Always re-run against the live Wasm app

          testClassesDirs = hostTestTask.get().testClassesDirs
          classpath = hostTestTask.get().classpath

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

      // --- iOS E2E Test Task ---
      // Follows the same task-dependency pattern as Desktop E2E:
      // startIosApp → jvmTest (with parikshan.target=ios) → stopIosApp

      fun resolveIosRuntimeProperty(name: String): String? =
        project.providers.gradleProperty(name).orNull ?: System.getProperty(name)

      val iosDevice = resolveIosRuntimeProperty("parikshan.ios.device") ?: "iPhone 16"
      val iosPort = resolveIosRuntimeProperty("parikshan.ios.port")?.toIntOrNull() ?: 9878
      val iosBundleId = resolveIosRuntimeProperty("parikshan.ios.bundleId") ?: "sample.app.ios"
      val iosVideoEnabled = resolveIosRuntimeProperty("parikshan.video.enabled")?.toBooleanStrictOrNull() ?: false
      val iosVideoOutputDir = resolveIosRuntimeProperty("parikshan.video.outputDir")
        ?: project.layout.buildDirectory.dir("parikshan/videos/ios").get().asFile.absolutePath
      val iosXcodeProject = resolveIosRuntimeProperty("parikshan.ios.xcodeProject")
        ?: "${project.projectDir}/../iosApp/iosApp.xcodeproj"
      val iosXcodeScheme = resolveIosRuntimeProperty("parikshan.ios.xcodeScheme") ?: "iosApp"
      val iosDerivedData = project.layout.buildDirectory.dir("parikshan/ios-xcode-build").get().asFile
      val iosBuildProducts = File(iosDerivedData, "Build/Products/Debug-iphonesimulator")

      // Shared mutable state for video process cleanup
      var iosVideoProcess: Process? = null
      var iosVideoFile: String? = null

      val startIosAppTask = project.tasks.register("startIosApp") {
        group = "verification"
        description = "Builds, installs, and launches the iOS app on the simulator for E2E testing"
        outputs.upToDateWhen { false }
        doLast {
          val xcodeProjectFile = File(iosXcodeProject)
          if (!xcodeProjectFile.exists()) {
            throw GradleException("Xcode project not found at ${xcodeProjectFile.absolutePath}")
          }

          // 1. Boot simulator
          logger.lifecycle("Parikshan iOS: Booting simulator '$iosDevice'...")
          val bootResult = ProcessBuilder("xcrun", "simctl", "boot", iosDevice)
            .redirectErrorStream(true).start()
          bootResult.inputStream.bufferedReader().readText()
          bootResult.waitFor()

          // 2. Build via xcodebuild
          logger.lifecycle("Parikshan iOS: Building via xcodebuild (scheme: $iosXcodeScheme)...")
          iosBuildProducts.mkdirs()
          val buildProc = ProcessBuilder(
            "xcodebuild", "build",
            "-project", xcodeProjectFile.absolutePath,
            "-scheme", iosXcodeScheme,
            "-configuration", "Debug",
            "-destination", "platform=iOS Simulator,name=$iosDevice",
            "-derivedDataPath", iosDerivedData.absolutePath,
            "CONFIGURATION_BUILD_DIR=${iosBuildProducts.absolutePath}"
          ).redirectErrorStream(true).start()
          val buildOutput = buildProc.inputStream.bufferedReader().readText()
          val buildExit = buildProc.waitFor()
          if (buildExit != 0) {
            logger.error(buildOutput)
            throw GradleException("xcodebuild failed with exit code $buildExit")
          }
          logger.lifecycle("Parikshan iOS: xcodebuild succeeded")

          // 3. Find .app bundle
          val appBundle = iosBuildProducts.listFiles()
            ?.firstOrNull { f -> f.name.endsWith(".app") && f.isDirectory }
            ?: throw GradleException("No .app bundle found in ${iosBuildProducts.absolutePath}")
          logger.lifecycle("Parikshan iOS: Built ${appBundle.name}")

          // 4. Install on simulator
          ProcessBuilder("xcrun", "simctl", "uninstall", "booted", iosBundleId)
            .redirectErrorStream(true).start().waitFor()
          val installProc = ProcessBuilder("xcrun", "simctl", "install", "booted", appBundle.absolutePath)
            .redirectErrorStream(true).start()
          val installOutput = installProc.inputStream.bufferedReader().readText()
          if (installProc.waitFor() != 0) {
            throw GradleException("Failed to install ${appBundle.name}: $installOutput")
          }
          logger.lifecycle("Parikshan iOS: Installed on simulator")

          // 5. Start video recording (if enabled)
          if (iosVideoEnabled) {
            val videoDir = File(iosVideoOutputDir).also { it.mkdirs() }
            iosVideoFile = File(videoDir, "ios-e2e-${System.currentTimeMillis()}.mp4").absolutePath
            iosVideoProcess = ProcessBuilder(
              "xcrun", "simctl", "io", "booted", "recordVideo", "--codec=h264", iosVideoFile!!
            ).redirectErrorStream(true).start()
            Thread.sleep(1000)
            logger.lifecycle("Parikshan iOS: Video recording started → $iosVideoFile")
          }

          // 6. Launch app on simulator
          logger.lifecycle("Parikshan iOS: Launching app on simulator...")
          val launchProc = ProcessBuilder(
            "xcrun", "simctl", "launch", "booted", iosBundleId
          ).redirectErrorStream(true).start()
          val launchOutput = launchProc.inputStream.bufferedReader().readText()
          if (launchProc.waitFor() != 0) {
            throw GradleException("Failed to launch app: $launchOutput")
          }
          logger.lifecycle("Parikshan iOS: App launched ($launchOutput)")

          // 7. Wait for the in-app HTTP server to become available
          logger.lifecycle("Parikshan iOS: Waiting for in-app server on port $iosPort...")
          val host = extension.host.get()
          val deadline = System.currentTimeMillis() + extension.startupTimeoutMs.get()
          var serverReady = false
          while (System.currentTimeMillis() <= deadline) {
            val ready = runCatching {
              Socket().use { socket ->
                socket.connect(InetSocketAddress(host, iosPort), 750)
              }
              true
            }.getOrDefault(false)
            if (ready) {
              serverReady = true
              break
            }
            Thread.sleep(extension.startupPollIntervalMs.get())
          }
          if (!serverReady) {
            throw GradleException("Parikshan iOS server did not start on port $iosPort")
          }
          logger.lifecycle("Parikshan iOS: Server ready on port $iosPort")
        }
        notCompatibleWithConfigurationCache(
          "Parikshan iOS E2E uses runtime simctl/xcodebuild process management."
        )
      }

      val stopIosAppTask = project.tasks.register("stopIosApp") {
        group = "verification"
        description = "Terminates the iOS app and stops video recording"
        doLast {
          // Stop video recording — must send SIGINT (not SIGTERM) so simctl
          // writes the moov atom and produces a valid video file.
          iosVideoProcess?.let { proc ->
            val pid = proc.pid()
            logger.lifecycle("Parikshan iOS: Stopping video recording (pid=$pid) with SIGINT...")
            ProcessBuilder("kill", "-2", pid.toString())
              .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS)
            // Give simctl time to finalize the file
            proc.waitFor(15, TimeUnit.SECONDS)
            if (proc.isAlive) {
              logger.warn("Parikshan iOS: Video process did not exit after SIGINT, forcing...")
              proc.destroyForcibly()
            }
            iosVideoFile?.let { path ->
              val vf = File(path)
              if (vf.exists() && vf.length() > 0) {
                logger.lifecycle("Parikshan iOS: Video saved → $path (${vf.length()} bytes)")
              } else {
                logger.warn("Parikshan iOS: Video file missing or empty at $path")
              }
            }
          }
          // Terminate the app
          ProcessBuilder("xcrun", "simctl", "terminate", "booted", iosBundleId)
            .redirectErrorStream(true).start().waitFor()
          logger.lifecycle("Parikshan iOS: App terminated")
        }
      }

      // Configure the iOS E2E test task (mirrors e2eWasmTest pattern)
      project.tasks.register<Test>("e2eIosTest") {
        group = "verification"
        description = "Runs iOS simulator E2E tests: builds via xcodebuild, launches on simulator, runs JVM tests"
        dependsOn(startIosAppTask)
        finalizedBy(stopIosAppTask)
        outputs.upToDateWhen { false } // Always re-run against the live iOS app

        testClassesDirs = hostTestTask.get().testClassesDirs
        classpath = hostTestTask.get().classpath

        systemProperty("parikshan.target", "ios")
        systemProperty("parikshan.host", extension.host.get())
        systemProperty("parikshan.port", iosPort.toString())

        // Pass through video properties
        val videoEnabled = resolveIosRuntimeProperty("parikshan.video.enabled") ?: "false"
        systemProperty("parikshan.video.enabled", videoEnabled)
        systemProperty("parikshan.video.outputDir", iosVideoOutputDir)

        val videoStepDelayMs = resolveIosRuntimeProperty("parikshan.video.stepDelayMs")
        if (videoStepDelayMs != null) systemProperty("parikshan.video.stepDelayMs", videoStepDelayMs)

        // Test filter
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
      fun resolveAndroidRuntimeProperty(name: String): String? =
        project.providers.gradleProperty(name).orNull ?: System.getProperty(name)

      val androidProjectDir = project.projectDir
      val androidBuildDir = project.layout.buildDirectory.get().asFile
      val androidVideoEnabledValue = resolveAndroidRuntimeProperty("parikshan.video.enabled") ?: "false"
      val androidVideoOutputDirValue =
        resolveAndroidRuntimeProperty("parikshan.video.outputDir")
          ?: File(androidBuildDir, "parikshan/videos/android").absolutePath
      val androidVideoRemotePathValue =
        resolveAndroidRuntimeProperty("parikshan.video.remotePath")
          ?: "/sdcard/Download/parikshan-e2e.mp4"
      val androidVideoMaxDurationSecValue =
        resolveAndroidRuntimeProperty("parikshan.video.maxDurationSec")
          ?.toIntOrNull()
          ?.coerceIn(10, 180)
          ?: 180
      val androidVideoPostRollMsValue =
        resolveAndroidRuntimeProperty("parikshan.video.postRollMs")
          ?.toLongOrNull()
          ?.coerceIn(0L, 10_000L)
          ?: 1_500L
      val androidVideoStartTimeoutMsValue =
        resolveAndroidRuntimeProperty("parikshan.video.startTimeoutMs")
          ?.toLongOrNull()
          ?.coerceIn(5_000L, 600_000L)
          ?: 180_000L
      val androidVideoStartDelayMsValue =
        resolveAndroidRuntimeProperty("parikshan.video.startDelayMs")
          ?.toLongOrNull()
          ?.coerceIn(0L, 10_000L)
          ?: 0L
      val androidWatchPackageValue = resolveAndroidRuntimeProperty("parikshan.android.watchPackage")
      val androidTestFilterValue = resolveAndroidRuntimeProperty("parikshan.testFilter")
      val androidDeviceSerialValue =
        resolveAndroidRuntimeProperty("parikshan.android.deviceSerial") ?: System.getenv("ANDROID_SERIAL")
      val androidApplicationId = ParikshanAndroidRecorder.resolveAndroidApplicationId(project)

      val stopAndroidVideoTask =
        project.tasks.register("stopParikshanAndroidVideo") {
          group = "verification"
          description = "Stops Android screen recording and pulls video artifact for Parikshan E2E tests"

          doLast {
            if (!androidVideoEnabledValue.toBooleanStrictOrNull().orFalse()) {
              return@doLast
            }

            ParikshanAndroidRecorder.stopAndPull(
              logger = logger,
              workingDir = androidProjectDir,
              postRollMs = androidVideoPostRollMsValue
            )
          }
          notCompatibleWithConfigurationCache(
            "Parikshan Android video stop/pull uses runtime adb process management."
          )
        }

      project.tasks.register("e2eAndroidTest") {
        group = "verification"
        description = "Runs Android emulator instrumentation tests for Parikshan E2E"

        val connectedTask = project.tasks.findByName("connectedDebugAndroidTest")
        if (connectedTask != null) {
          connectedTask.doFirst {
            if (!androidVideoEnabledValue.toBooleanStrictOrNull().orFalse()) {
              return@doFirst
            }

            val serial =
              ParikshanAndroidRecorder.resolveDeviceSerial(
                logger = logger,
                workingDir = androidProjectDir,
                explicitSerial = androidDeviceSerialValue
              )

            val outputDirectoryFile = File(androidVideoOutputDirValue).also { it.mkdirs() }
            val timestamp = System.currentTimeMillis()
            val localOutputPath = File(outputDirectoryFile, "android-e2e-$timestamp.mp4").absolutePath

            ParikshanAndroidRecorder.start(
              logger = logger,
              workingDir = androidProjectDir,
              buildDir = androidBuildDir,
              serial = serial,
              remoteOutputPath = androidVideoRemotePathValue,
              localOutputPath = localOutputPath,
              maxDurationSec = androidVideoMaxDurationSecValue,
              watchPackage = androidWatchPackageValue,
              testFilter = androidTestFilterValue,
              applicationId = androidApplicationId,
              startTimeoutMs = androidVideoStartTimeoutMsValue,
              startDelayMs = androidVideoStartDelayMsValue
            )
          }
          connectedTask.notCompatibleWithConfigurationCache(
            "Parikshan Android video orchestration attaches runtime adb process management to connected tests."
          )
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
        notCompatibleWithConfigurationCache(
          "Parikshan Android e2e task orchestrates external adb screen recording processes."
        )
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
    logger: Logger,
    workingDir: File,
    explicitSerial: String?
  ): String {
    if (!explicitSerial.isNullOrBlank()) {
      return explicitSerial
    }

    val output =
      runAdbCommand(
        workingDir = workingDir,
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
      logger.lifecycle(
        "Parikshan: Multiple Android devices detected (${devices.joinToString()}). Using '${devices.first()}'. " +
          "Set -Pparikshan.android.deviceSerial to override."
      )
    }
    return devices.first()
  }

  fun start(
    logger: Logger,
    workingDir: File,
    buildDir: File,
    serial: String,
    remoteOutputPath: String,
    localOutputPath: String,
    maxDurationSec: Int,
    watchPackage: String?,
    testFilter: String?,
    applicationId: String?,
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
      workingDir = workingDir,
      serial = serial,
      args = listOf("shell", "sh", "-c", "pkill -2 screenrecord || killall -2 screenrecord || true"),
      ignoreExitCode = true
    )
    Thread.sleep(300)

    runAdbCommand(
      workingDir = workingDir,
      serial = serial,
      args = listOf("shell", "rm", "-f", remoteOutputPath),
      ignoreExitCode = true
    )

    this.serial = serial
    this.remoteOutputPath = remoteOutputPath
    this.localOutputPath = localOutputPath
    this.recordingProcess = null

    val resolvedWatchPackage = resolveWatchPackage(workingDir, serial, watchPackage, testFilter, applicationId)
    if (!resolvedWatchPackage.isNullOrBlank()) {
      runAdbCommand(
        workingDir = workingDir,
        serial = serial,
        args = listOf("shell", "am", "force-stop", resolvedWatchPackage),
        ignoreExitCode = true
      )
      logger.lifecycle(
        "Parikshan: Using watch package '$resolvedWatchPackage' for Android video run setup."
      )
    }

    if (startDelayMs > 0L) {
      logger.lifecycle(
        "Parikshan: startDelayMs=$startDelayMs is ignored for package-trigger mode; " +
          "recording starts as soon as '$resolvedWatchPackage' is detected."
      )
    }

    val watcher =
      Thread(
        {
          try {
            val canWatch = !resolvedWatchPackage.isNullOrBlank()
            val shouldStart =
              if (!canWatch) {
                true
              } else {
                val deadline = System.currentTimeMillis() + startTimeoutMs
                var detected = false
                while (!stopRequested && System.currentTimeMillis() <= deadline) {
                  if (isPackageProcessRunning(workingDir, serial, resolvedWatchPackage)) {
                    detected = true
                    break
                  }
                  Thread.sleep(200)
                }
                if (!detected && !stopRequested) {
                  logger.warn(
                    "Parikshan: Timed out waiting for package '$resolvedWatchPackage'; " +
                      "starting recording immediately."
                  )
                }
                detected || !stopRequested
              }

            if (!shouldStart || stopRequested) {
              return@Thread
            }

            startScreenrecordProcess(
              logger = logger,
              workingDir = workingDir,
              buildDir = buildDir,
              serial = serial,
              remoteOutputPath = remoteOutputPath,
              maxDurationSec = maxDurationSec
            )
          } catch (ie: InterruptedException) {
            // Expected during stop/cancel.
          } catch (t: Throwable) {
            startFailure = t.message ?: t::class.java.simpleName
            logger.warn("Parikshan: Android video watcher failed: ${t.message}")
          }
        },
        "parikshan-android-video-start-watcher"
      )
    watcher.isDaemon = true
    watcher.start()
    startWatcherThread = watcher
  }

  fun stopAndPull(
    logger: Logger,
    workingDir: File,
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
      if (activeRecordingProcess == null) {
        val activeStartFailure = startFailure
        if (!activeStartFailure.isNullOrBlank()) {
          throw GradleException("Parikshan Android video capture failed to start: $activeStartFailure")
        }
        logger.warn("Parikshan: Android video recording did not start; skipping artifact pull.")
        return
      }

      if (postRollMs > 0L) {
        logger.lifecycle("Parikshan: Android video post-roll ${postRollMs}ms")
        Thread.sleep(postRollMs)
      }

      runAdbCommand(
        workingDir = workingDir,
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
        workingDir = workingDir,
        serial = activeSerial,
        args = listOf("pull", activeRemoteOutputPath, activeLocalOutputPath),
        ignoreExitCode = true
      )
      runAdbCommand(
        workingDir = workingDir,
        serial = activeSerial,
        args = listOf("shell", "rm", "-f", activeRemoteOutputPath),
        ignoreExitCode = true
      )

      if (outputFile.exists() && outputFile.length() > 0L) {
        logger.lifecycle("Parikshan: Android video saved to ${outputFile.absolutePath}")
      } else {
        logger.warn(
          "Parikshan: Android video was not produced at ${outputFile.absolutePath}. " +
            "Check build/parikshan/android-screenrecord.log"
        )
      }
    } finally {
      clearState()
    }
  }

  private fun runAdbCommand(
    workingDir: File,
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
        .directory(workingDir)
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
    logger: Logger,
    workingDir: File,
    buildDir: File,
    serial: String,
    remoteOutputPath: String,
    maxDurationSec: Int
  ) {
    val logFile = File(buildDir, "parikshan/android-screenrecord.log")
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
        .directory(workingDir)
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
    logger.lifecycle("Parikshan: Started Android video capture for '$serial' (maxDurationSec=$maxDurationSec)")
  }

  private fun resolveWatchPackage(
    workingDir: File,
    serial: String,
    explicitWatchPackage: String?,
    testFilter: String?,
    applicationId: String?
  ): String? {
    if (!explicitWatchPackage.isNullOrBlank()) {
      return explicitWatchPackage
    }
    applicationId?.takeIf { it.isNotBlank() }?.let { return it }
    extractPackageFromTestFilter(testFilter)?.let { return it }

    val output =
      runAdbCommand(
        workingDir = workingDir,
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

  private fun isPackageProcessRunning(
    workingDir: File,
    serial: String,
    packageName: String
  ): Boolean {
    val output =
      runAdbCommand(
        workingDir = workingDir,
        serial = serial,
        args = listOf("shell", "pidof", packageName),
        ignoreExitCode = true
      )
    return output.isNotBlank()
  }

  fun resolveAndroidApplicationId(project: Project): String? {
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

private const val PARIKSHAN_LOCAL_RUNTIME_PROJECT = ":lib"
private const val PARIKSHAN_LOCAL_ANDROID_CLIENT_PROJECT = ":parikshan-client"
private const val ANDROID_COMPOSE_UI_TEST_DEPENDENCY = "androidx.compose.ui:ui-test-junit4-android:1.9.0"
private const val ANDROID_COMPOSE_UI_TEST_MANIFEST_DEPENDENCY = "androidx.compose.ui:ui-test-manifest:1.9.0"

private fun Project.configureParikshanDependencies() {
  addLocalProjectDependencyIfPresent(
    configurationName = "commonMainImplementation",
    projectPath = PARIKSHAN_LOCAL_RUNTIME_PROJECT,
    description = "Parikshan runtime"
  )
  addLocalProjectDependencyIfPresent(
    configurationName = "androidTestImplementation",
    projectPath = PARIKSHAN_LOCAL_ANDROID_CLIENT_PROJECT,
    description = "Parikshan Android client"
  )
  addDependencyIfAbsent(
    configurationName = "androidTestImplementation",
    notation = ANDROID_COMPOSE_UI_TEST_DEPENDENCY,
    description = "Compose Android UI test runner"
  )
  addDependencyIfAbsent(
    configurationName = "debugImplementation",
    notation = ANDROID_COMPOSE_UI_TEST_MANIFEST_DEPENDENCY,
    description = "Compose Android UI test manifest"
  )
}

private fun Project.addLocalProjectDependencyIfPresent(
  configurationName: String,
  projectPath: String,
  description: String
) {
  val dependencyProject = rootProject.findProject(projectPath)
  if (dependencyProject == null) {
    logger.info(
      "Parikshan: Skipping $description auto-wiring because project '$projectPath' is not part of this build."
    )
    return
  }

  addDependencyIfAbsent(
    configurationName = configurationName,
    notation = dependencies.project(mapOf("path" to dependencyProject.path)),
    description = description
  )
}

private fun Project.addDependencyIfAbsent(
  configurationName: String,
  notation: Any,
  description: String
) {
  val configuration = configurations.findByName(configurationName) ?: return
  val candidate = dependencies.create(notation)
  val alreadyPresent = configuration.dependencies.any { it.matchesParikshanDependency(candidate) }
  if (alreadyPresent) {
    return
  }

  dependencies.add(configurationName, candidate)
  logger.info("Parikshan: Added $description to ${path}:$configurationName")
}

private fun Dependency.matchesParikshanDependency(candidate: Dependency): Boolean {
  return when {
    this is ProjectDependency && candidate is ProjectDependency ->
      name == candidate.name

    else -> group == candidate.group && name == candidate.name
  }
}

private fun Project.resolveHostTestTaskName(overrideName: String?): String {
  val requestedName = overrideName?.trim().orEmpty()
  if (requestedName.isNotEmpty()) {
    val requestedTask = tasks.findByName(requestedName)
    require(requestedTask is Test) {
      "Parikshan: Configured desktopTestTaskName '$requestedName' was not found as a Test task."
    }
    return requestedName
  }

  val preferredNames = listOf("jvmTest", "desktopTest", "test")
  preferredNames.firstOrNull { tasks.findByName(it) is Test }?.let { return it }

  val availableTasks = tasks.withType(Test::class.java).map { it.name }.sorted()
  throw GradleException(
    "Parikshan could not find a host JVM Test task. " +
      "Checked ${preferredNames.joinToString()}. " +
      "Available Test tasks: ${availableTasks.ifEmpty { listOf("<none>") }.joinToString()}."
  )
}

private fun Project.resolveWasmDistributionTaskName(overrideName: String?): String {
  val requestedName = overrideName?.trim().orEmpty()
  if (requestedName.isNotEmpty()) {
    requireNotNull(tasks.findByName(requestedName)) {
      "Parikshan: Configured wasmDistributionTaskName '$requestedName' was not found."
    }
    return requestedName
  }

  val preferredNames =
    listOf(
      "wasmJsBrowserDevelopmentWebpack",
      "wasmJsBrowserDevelopmentExecutableDistribution",
      "wasmJsBrowserDistribution",
      "wasmJsBrowserProductionWebpack",
    )
  preferredNames.firstOrNull { tasks.findByName(it) != null }?.let { return it }

  val availableTasks =
    tasks
      .matching { task -> task.name.contains("wasm", ignoreCase = true) }
      .map { it.name }
      .sorted()

  throw GradleException(
    "Parikshan could not find a supported Wasm distribution task. " +
      "Checked ${preferredNames.joinToString()}. " +
      "Available Wasm-like tasks: ${availableTasks.ifEmpty { listOf("<none>") }.joinToString()}."
  )
}

private fun String.isDevelopmentWasmTask(): Boolean = contains("Development", ignoreCase = true)
