package io.github.aryapreetam.parikshan.gradle

import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import javax.inject.Inject
import org.gradle.api.file.FileCollection
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
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
  val appArgs: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

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
  val desktopWindowTitle: Property<String> = objects.property(String::class.java)

  @get:Input
  val wasmServerPort: Property<Int> = objects.property(Int::class.java).convention(8081)

  @get:Input
  val androidLaunchActivityClassName: Property<String> = objects.property(String::class.java)
}

class ParikshanGradlePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create<ParikshanExtension>("parikshan")

    // Register dummy tasks for clean CLI syntax
    project.tasks.register("background") {
      group = "verification"
      description = "Enable background mode for E2E tests"
    }
    project.tasks.register("video") {
      group = "verification"
      description = "Enable video recording for E2E tests"
    }

    // Ensure all ZIP/JAR tasks can handle large entry counts (Zip64)
    // This is required for large Compose Uber JARs.
    project.tasks.withType(Zip::class.java).configureEach {
      isZip64 = true
    }
    val sessionToken = project.providers.gradleProperty("parikshan.token").getOrNull() ?: UUID.randomUUID().toString()

    // Centralized flag detection
    val isBackgroundRequested = 
      project.gradle.startParameter.taskNames.any { it.contains("background", ignoreCase = true) } ||
      (project.hasProperty("background") && project.property("background").toString() == "true")

    val isVideoRequested = 
      project.gradle.startParameter.taskNames.any { it.contains("video", ignoreCase = true) } ||
      (project.hasProperty("video") && project.property("video").toString() == "true")

    if (isBackgroundRequested) {
        project.logger.lifecycle("Parikshan: Background mode DETECTED.")
    }
    if (isVideoRequested) {
        project.logger.lifecycle("Parikshan: Video recording ENABLED.")
    }

    // --- Desktop Tasks ---

    val appJarTaskNameValue = extension.appJarTaskName
    val appArgsValue = extension.appArgs
    val hostValue = extension.host
    val portValue = extension.port
    val timeoutMsValue = extension.startupTimeoutMs
    val pollMsValue = extension.startupPollIntervalMs
    val titleValue = extension.desktopWindowTitle
    val buildDirValue = project.layout.buildDirectory
    val tokenValue = sessionToken
    val desktopLaunchManifestFile = project.layout.buildDirectory.file("parikshan/desktop-launch.properties")

    val wasmPortValue = extension.wasmServerPort
    val wasmPortFile = project.layout.buildDirectory.file("parikshan/wasm-port.txt")

    val startDesktopTask =
      project.tasks.register("startParikshanDesktopApp") {
        group = "verification"
        
        // Resolve JAR location at configuration time as a Provider
        val appJarFileProvider = project.tasks.named<org.gradle.jvm.tasks.Jar>(appJarTaskNameValue.get())
          .flatMap { it.archiveFile }
        val isBackground = isBackgroundRequested
        val desktopLogger = logger

        inputs.file(appJarFileProvider)

        doLast {
          val jar = appJarFileProvider.get().asFile
          val resolvedPort = ParikshanPortConflictHandler.resolvePortAndCleanStale(
            originalPort = portValue.get(),
            host = hostValue.get(),
            logger = desktopLogger
          )
          ParikshanDesktopProcess.start(
            jar = jar,
            token = tokenValue,
            logFile = File(buildDirValue.get().asFile, "parikshan/desktop-app.log"),
            manifestFile = desktopLaunchManifestFile.get().asFile,
            appArgs = appArgsValue.get(),
            host = hostValue.get(), port = resolvedPort,
            timeoutMs = timeoutMsValue.get(),
            pollMs = pollMsValue.get(),
            title = titleValue.orNull,
            background = isBackground
          )
        }
      }

    val stopDesktopTask =
      project.tasks.register("stopParikshanDesktopApp") {
        group = "verification"
        doLast {
          ParikshanDesktopProcess.stop(
            host = hostValue.get(),
            port = portValue.get(),
            token = tokenValue,
            manifestFile = desktopLaunchManifestFile.get().asFile
          )
        }
      }

    // --- Wasm Tasks ---

    val wasmOutputDir = project.layout.buildDirectory.dir("parikshan/wasm-app")
    val wasmDevDir = project.layout.buildDirectory.dir("kotlin-webpack/wasmJs/developmentExecutable")
    val wasmProdDir = project.layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    val wasmResourcesDir = project.layout.buildDirectory.dir("processedResources/wasmJs/main")

    val prepareWasmAssetsTask = project.tasks.register("prepareParikshanWasmAssets") {
      group = "verification"
    }

    fun String.isDevelopmentWasmTask(): Boolean = contains("Development", ignoreCase = true)

    val startWasmTask =
      project.tasks.register("startParikshanWasmApp") {
        group = "verification"
        dependsOn(prepareWasmAssetsTask)
        val wasmLogger = logger
        val portValue = wasmPortValue
        val portFileValue = wasmPortFile
        val outputDirValue = wasmOutputDir
        doLast {
          val outputDir = outputDirValue.get().asFile
          val resolvedPort = ParikshanPortConflictHandler.resolvePortAndCleanStale(
            originalPort = portValue.get(),
            host = "127.0.0.1",
            logger = wasmLogger
          )
          val portFile = portFileValue.get().asFile
          portFile.parentFile.mkdirs()
          portFile.writeText(resolvedPort.toString())

          ParikshanWasmServer.start(resolvedPort, outputDir)
        }
      }

    val stopWasmTask =
      project.tasks.register("stopParikshanWasmApp") {
        group = "verification"
        doLast {
          ParikshanWasmServer.stop()
        }
      }

    val installPlaywrightTask = project.tasks.register<JavaExec>("installPlaywrightBrowsers") {
        group = "verification"
        mainClass.set("com.microsoft.playwright.CLI")
        args = listOf("install", "chromium")
    }

    project.pluginManager.withPlugin("com.android.application") {
      project.configureAndroidInstrumentationDefaults()
    }

    // Resolve at configuration time for Cache safety
    val iosProjectDir = project.projectDir
    val iosLayout = project.layout
    val iosLogger = project.logger
    val iosRootDir = project.rootDir
    
    // ONE Global Session Token
    val isE2ERequested =
      project.gradle.startParameter.taskNames.any { it.contains("e2e", ignoreCase = true) } ||
        project.hasProperty("parikshan.e2e.active")
    var prepareIosBootSourceTask: TaskProvider<Task>? = null
    var prepareWasmBootSourceTask: TaskProvider<Task>? = null

    if (isE2ERequested) {
      project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        prepareIosBootSourceTask =
          project.registerParikshanIosBootSource(
            iosProjectDir = iosProjectDir,
            logger = iosLogger
          )
        prepareWasmBootSourceTask =
          project.registerParikshanWasmBootSource(
            logger = iosLogger
          )
      }
    }

    project.afterEvaluate {
      val isE2EActive = isE2ERequested

      project.configureParikshanDependencies(isE2EActive)

      val e2eTestClasses = project.discoverE2eTestClasses()
      val hostTestTaskName = project.resolveHostTestTaskName(extension.desktopTestTaskName.orNull)
      val hostTestTask = project.tasks.named<Test>(hostTestTaskName)
      val wasmDistributionTaskName = project.resolveWasmDistributionTaskName(extension.wasmDistributionTaskName.orNull)

      startDesktopTask.configure { dependsOn(extension.appJarTaskName.get()) }

      val wasmOutputDirProvider = wasmOutputDir
      val wasmDevDirProvider = wasmDevDir
      val wasmProdDirProvider = wasmProdDir
      val wasmResourcesDirProvider = wasmResourcesDir
      val buildDirProvider = project.layout.buildDirectory
      val gradleLogger = project.logger

      prepareWasmAssetsTask.configure {
        dependsOn(wasmDistributionTaskName)
        dependsOn("wasmJsProcessResources")
        doLast {
          val output = wasmOutputDirProvider.get().asFile
          output.deleteRecursively()
          output.mkdirs()
          val distDir = if (wasmDevDirProvider.get().asFile.exists()) wasmDevDirProvider.get().asFile else wasmProdDirProvider.get().asFile
          if (distDir.exists()) distDir.copyRecursively(output, overwrite = true)
          val buildDir = buildDirProvider.get().asFile
          listOf("processedResources/wasmJs/main", "kotlin-multiplatform-resources/assemble-hierarchically/wasmJsResolveSelfResources", "kotlin-multiplatform-resources/aggregated-resources/wasmJs")
            .map { File(buildDir, it) }.filter { it.exists() }.forEach { resDir ->
              gradleLogger.lifecycle("Parikshan Wasm: Copying resources from ${resDir.absolutePath}")
              resDir.copyRecursively(output, overwrite = true)
            }
          val indexHtml = File(output, "index.html")
          if (!indexHtml.exists()) {
            val srcIndex = File(wasmResourcesDirProvider.get().asFile, "index.html")
            if (srcIndex.exists()) srcIndex.copyTo(indexHtml)
          }
        }
      }

      installPlaywrightTask.configure {
        classpath = hostTestTask.get().classpath
      }

      project.tasks.register<Test>("e2eDesktopTest") {
        group = "verification"
        dependsOn(startDesktopTask)
        finalizedBy(stopDesktopTask)
        
        // Support native --background flag via task option
        options {
            if (this is org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions) {
                // We just need to register it, even if we don't use it directly here
            }
        }

        configureE2eHostTestExecution(
          hostTestClassesDirs = hostTestTask.get().testClassesDirs,
          hostTestClasspath = hostTestTask.get().classpath,
          e2eTestClasses = e2eTestClasses,
          target = "Desktop",
          logger = project.logger
        )
        systemProperty("parikshan.host", extension.host.get())
        doFirst {
          val manifest = desktopLaunchManifestFile.get().asFile
          val port = if (manifest.exists()) {
            val props = java.util.Properties()
            runCatching { manifest.inputStream().use { props.load(it) } }
            props.getProperty("port") ?: extension.port.get().toString()
          } else {
            extension.port.get().toString()
          }
          systemProperty("parikshan.port", port)
        }
        systemProperty("parikshan.target", "desktop")
        systemProperty("parikshan.token", sessionToken)
        systemProperty("parikshan.desktop.launchManifest", desktopLaunchManifestFile.get().asFile.absolutePath)
        if (isBackgroundRequested) {
          systemProperty("parikshan.background", "true")
        }
        if (isVideoRequested) {
          systemProperty("parikshan.video.enabled", "true")
        }
      }
 
      project.tasks.register<Test>("e2eWasmTest") {
        group = "verification"
        dependsOn(installPlaywrightTask, startWasmTask)
        finalizedBy(stopWasmTask)
        configureE2eHostTestExecution(
          hostTestClassesDirs = hostTestTask.get().testClassesDirs,
          hostTestClasspath = hostTestTask.get().classpath,
          e2eTestClasses = e2eTestClasses,
          target = "Wasm",
          logger = project.logger
        )
        systemProperty("parikshan.target", "wasm")
        systemProperty("parikshan.token", sessionToken)
        doFirst {
          val portFile = project.layout.buildDirectory.file("parikshan/wasm-port.txt").get().asFile
          val port = if (portFile.exists()) portFile.readText().trim() else extension.wasmServerPort.get().toString()
          systemProperty("parikshan.wasm.url", "http://127.0.0.1:$port")
        }
        if (isBackgroundRequested) {
          systemProperty("parikshan.wasm.headless", "true")
        }
        if (isVideoRequested) {
          systemProperty("parikshan.video.enabled", "true")
        }
        testLogging {
          showStandardStreams = true
          exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
      }

      // --- iOS E2E Test Task ---

      fun resolveIosRuntimeProperty(name: String): String? =
        project.providers.gradleProperty(name).orElse(project.providers.systemProperty(name)).orNull

      val iosDevice = resolveIosRuntimeProperty("device")
        ?: resolveIosRuntimeProperty("serial")
        ?: resolveIosRuntimeProperty("parikshan.ios.device")
        ?: System.getenv("PARIKSHAN_IOS_DEVICE")
        ?: "iPhone 16"
      val iosPort = resolveIosRuntimeProperty("parikshan.ios.port")?.toIntOrNull() ?: 9878
      val iosXcodeProject = resolveIosRuntimeProperty("parikshan.ios.xcodeProject")
        ?: project.discoverIosXcodeProject()?.absolutePath
        ?: "${project.projectDir}/../iosApp/iosApp.xcodeproj"
      val iosXcodeScheme = resolveIosRuntimeProperty("parikshan.ios.xcodeScheme") ?: "iosApp"
      fun getIosBundleId(): String {
        val prop = resolveIosRuntimeProperty("parikshan.ios.bundleId")
        if (prop != null) return prop
        val provider = project.providers.of(XcodeBundleIdValueSource::class.java) {
          parameters.xcodeProject.set(File(iosXcodeProject))
          parameters.scheme.set(iosXcodeScheme)
        }
        val extracted = provider.orNull
        project.logger.lifecycle("Parikshan iOS: Extracted bundle ID: $extracted")
        return extracted ?: "sample.app.ios"
      }
      val iosDerivedData = project.layout.buildDirectory.dir("parikshan/ios-build").get().asFile
      var iosSimulatorUdid: String? = null

      val iosPreflightTask = project.tasks.register("parikshanIosPreflight") {
        group = "verification"
        doLast {
          val simulator = resolveIosSimulatorDevice(iosDevice, iosProjectDir)
          iosLogger.lifecycle("Parikshan iOS: Found simulator '${simulator.name}' (${simulator.udid})")
        }
      }

      val stopIosAppTask = project.tasks.register("stopIosApp") {
        group = "verification"
        doLast {
          iosSimulatorUdid?.let { udid ->
            val bundleId = getIosBundleId()
            ProcessBuilder("xcrun", "simctl", "terminate", udid, bundleId).start().waitFor()
            iosLogger.lifecycle("Parikshan iOS: App terminated")
          }
        }
      }

      val projectPath = project.path
      val rootDirAbs = iosRootDir.absolutePath
      val sampleDirAbs = iosProjectDir.parentFile.absolutePath

      val startIosAppTask = project.tasks.register("startIosApp") {
        group = "verification"
        dependsOn(iosPreflightTask)
        prepareIosBootSourceTask?.let { dependsOn(it) }
        outputs.upToDateWhen { false }
        
        doLast {
          val simulator = resolveIosSimulatorDevice(iosDevice, iosProjectDir)
          iosSimulatorUdid = simulator.udid
          iosLogger.lifecycle("Parikshan iOS: Using simulator '${simulator.name}' (${simulator.udid})")

          if (!simulator.isBooted) {
            iosLogger.lifecycle("Parikshan iOS: Booting simulator...")
            ProcessBuilder("xcrun", "simctl", "boot", simulator.udid).start().waitFor()
            ProcessBuilder("xcrun", "simctl", "bootstatus", simulator.udid, "-b").start().waitFor()
          }

          // --- ISOLATED HOST ARTIFACT ---
          val originalIosAppDir = File(iosXcodeProject).parentFile
          val generatedIosAppDir = File(iosLayout.buildDirectory.get().asFile, "parikshan/ios-host")
          generatedIosAppDir.deleteRecursively()
          originalIosAppDir.copyRecursively(generatedIosAppDir)
          
          val absoluteGradlew = File(rootDirAbs, "gradlew").absolutePath
          val gradlewShim = File(generatedIosAppDir, "gradlew")
          val shimContent = """
              #!/bin/sh
              exec "$absoluteGradlew" -p "$rootDirAbs" --no-configuration-cache -Pparikshan.e2e.active=true -Pparikshan.token=$sessionToken "${'$'}@"
              """.trimIndent()
          
          gradlewShim.writeText(shimContent)
          gradlewShim.setExecutable(true)
          
          // --- XCODE ./../gradlew COMPATIBILITY ---
          // Some Xcode projects use relative paths to find gradlew. Since we moved the project to a subfolder,
          // we place shims at the expected relative locations as well.
          val parentShim = File(generatedIosAppDir.parentFile, "gradlew")
          parentShim.writeText(shimContent)
          parentShim.setExecutable(true)

          val grandParentShim = File(generatedIosAppDir.parentFile.parentFile, "gradlew")
          grandParentShim.writeText(shimContent)
          grandParentShim.setExecutable(true)

          iosLogger.lifecycle("Parikshan iOS: Building app via xcodebuild...")
          val appBuildProducts = File(iosDerivedData, "Build/Products/Debug-iphonesimulator")
          appBuildProducts.mkdirs()
          
          val logFile = File(iosLayout.buildDirectory.get().asFile, "parikshan/xcodebuild.log")
          logFile.parentFile.mkdirs()

          val buildResult = ProcessBuilder(
            "xcodebuild", "build", "-project", File(generatedIosAppDir, File(iosXcodeProject).name).absolutePath,
            "-scheme", iosXcodeScheme, "-configuration", "Debug",
            "-destination", "platform=iOS Simulator,id=${simulator.udid}",
            "-derivedDataPath", iosDerivedData.absolutePath,
            "CONFIGURATION_BUILD_DIR=${appBuildProducts.absolutePath}"
          ).apply {
              environment()["PARIKSHAN_TOKEN"] = sessionToken
              redirectErrorStream(true)
              redirectOutput(logFile)
          }.start().waitFor()
          
          if (buildResult != 0) {
              iosLogger.error("Parikshan iOS: xcodebuild failed. Dumping last 50 lines of log...")
              val lines = logFile.readLines()
              lines.takeLast(50).forEach { iosLogger.error(it) }
              throw GradleException("xcodebuild failed with exit code $buildResult")
          }
          
          val appBundle = appBuildProducts.listFiles()?.firstOrNull { it.name.endsWith(".app") } ?: throw GradleException("No .app bundle")

          val bundleId = getIosBundleId()
          iosLogger.lifecycle("Parikshan iOS: Launching app...")
          ProcessBuilder("xcrun", "simctl", "terminate", simulator.udid, bundleId).start().waitFor()
          ProcessBuilder("xcrun", "simctl", "install", simulator.udid, appBundle.absolutePath).start().waitFor()
          ProcessBuilder("xcrun", "simctl", "launch", simulator.udid, bundleId).apply {
              environment()["SIMCTL_CHILD_PARIKSHAN_TOKEN"] = sessionToken
              environment()["PARIKSHAN_TOKEN"] = sessionToken
          }.start().waitFor()

          // READINESS: protocol-level Ping using the same token the test JVM will use.
          iosLogger.lifecycle("Parikshan iOS: Waiting for server on port $iosPort...")
          val deadline = System.currentTimeMillis() + 90_000
          var serverReady = false
          while (System.currentTimeMillis() <= deadline) {
            if (postIosPing(iosPort, sessionToken)) {
              serverReady = true
              break
            }
            Thread.sleep(500)
          }
          
          if (!serverReady) {
              iosLogger.error("Parikshan iOS: Server failed to start. Dumping logs...")
              val logOutput = ProcessBuilder("xcrun", "simctl", "spawn", simulator.udid, "log", "show", "--predicate", "process == \"SampleApp\"", "--last", "2m").start().inputStream.bufferedReader().readText()
              iosLogger.error(logOutput)
              throw GradleException("Parikshan iOS server failed readiness check")
          }
          iosLogger.lifecycle("Parikshan iOS: Server ready.")
        }
      }

      project.tasks.register<Test>("e2eIosTest") {
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
        systemProperty("parikshan.port", iosPort.toString())
        systemProperty("parikshan.token", sessionToken)
        doFirst {
          val simulator = resolveIosSimulatorDevice(iosDevice, iosProjectDir)
          systemProperty("parikshan.ios.udid", simulator.udid)
          systemProperty("parikshan.ios.bundleId", getIosBundleId())
        }
      }

      // --- Android E2E Test Task ---

      val androidProjectDir = project.projectDir
      val androidApplicationId =
        ParikshanAndroidRecorder.resolveAndroidApplicationId(project)
          ?: throw GradleException(
            "Parikshan Android: Could not resolve the Android applicationId. " +
              "Set defaultConfig.applicationId in the Android application module."
          )
      val androidLogger = project.logger
      fun resolveAndroidRuntimeProperty(name: String): String? =
        project.providers.gradleProperty(name).orElse(project.providers.systemProperty(name)).orNull
      val androidSerial = resolveAndroidRuntimeProperty("device")
        ?: resolveAndroidRuntimeProperty("serial")
        ?: resolveAndroidRuntimeProperty("parikshan.android.serial")
        ?: System.getenv("PARIKSHAN_ANDROID_SERIAL")

      val androidPreflightTask = project.tasks.register("parikshanAndroidPreflight") {
        group = "verification"
        doLast {
          val serial = ParikshanAndroidRecorder.resolveDeviceSerial(androidLogger, androidProjectDir, androidSerial)
          androidLogger.lifecycle("Parikshan Android: Found connected device/emulator '$serial'")
        }
      }

      if (isE2EActive) {
        project.tasks.matching {
          it.name in setOf("preBuild", "preDebugBuild", "preDebugAndroidTestBuild")
        }.configureEach {
          dependsOn(androidPreflightTask)
        }
      }

      val stopAndroidAppTask = project.tasks.register("stopParikshanAndroidApp") {
          group = "verification"
          doLast {
            val serial = ParikshanAndroidRecorder.resolveDeviceSerial(androidLogger, androidProjectDir, androidSerial)
            ProcessBuilder("adb", "-s", serial, "forward", "--remove", "tcp:9879").start().waitFor()
            ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", androidApplicationId).start().waitFor()
          }
        }

      val startAndroidAppTask = project.tasks.register("startParikshanAndroidApp") {
        group = "verification"
        val appProject = project.findAndroidAppProject() ?: project
        val installTask = if (appProject == project) "installDebug" else "${appProject.path}:installDebug"
        val testInstallTask = if (appProject == project) "installDebugAndroidTest" else "${appProject.path}:installDebugAndroidTest"

        dependsOn(androidPreflightTask, installTask, testInstallTask)
        doLast {
          val serial = ParikshanAndroidRecorder.resolveDeviceSerial(androidLogger, androidProjectDir, androidSerial)
          ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", androidApplicationId).start().waitFor()
          ProcessBuilder("adb", "-s", serial, "forward", "tcp:9879", "tcp:9879").start().waitFor()
          val testPackage = "$androidApplicationId.test"
          androidLogger.lifecycle("Parikshan Android: Starting instrumentation...")
          ProcessBuilder("adb", "-s", serial, "shell", "am", "instrument", "-w", "-e", "class", "io.github.aryapreetam.parikshan.ParikshanAndroidRunner", "-e", "parikshan_token", sessionToken, "$testPackage/androidx.test.runner.AndroidJUnitRunner").start()
        }
      }

      project.tasks.register<Test>("e2eAndroidTest") {
        group = "verification"
        dependsOn(startAndroidAppTask)
        finalizedBy(stopAndroidAppTask)
        configureE2eHostTestExecution(
          hostTestClassesDirs = hostTestTask.get().testClassesDirs,
          hostTestClasspath = hostTestTask.get().classpath,
          e2eTestClasses = e2eTestClasses,
          target = "Android",
          logger = project.logger
        )
        systemProperty("parikshan.target", "android")
        systemProperty("parikshan.host", "127.0.0.1")
        systemProperty("parikshan.port", "9879")
        systemProperty("parikshan.token", sessionToken)
        doFirst {
           val serial = ParikshanAndroidRecorder.resolveDeviceSerial(androidLogger, androidProjectDir, androidSerial)
           systemProperty("parikshan.android.serial", serial)
        }
        
        doLast {
           val serial = ParikshanAndroidRecorder.resolveDeviceSerial(androidLogger, androidProjectDir, androidSerial)
           val devicePath = "/sdcard/parikshan-screenshot.png"
           val hostPath = "build/parikshan/screenshots/android-failure.png"
           ProcessBuilder("adb", "-s", serial, "pull", devicePath, hostPath).start().waitFor()
        }
      }

      val manifestFile = desktopLaunchManifestFile
      val wasmOutputDirVal = wasmOutputDir
      val wasmPortFileVal = wasmPortFile
      val extAppArgs = extension.appArgs
      val discoveredE2eClasses = e2eTestClasses

      val junitConsoleConfig = project.configurations.detachedConfiguration(
        project.dependencies.create("org.junit.platform:junit-platform-console-standalone:1.10.2")
      )

      val targetAndroidAppId = androidApplicationId
      val targetIosPort = iosPort

      val e2eTestReport = project.tasks.register("e2eTestReport") {
        group = "verification"
        description = "Generates a unified HTML report for all orchestrated Parikshan E2E targets."
        
        val resultsDir = project.layout.buildDirectory.dir("test-results/e2eTest")
        val reportsDir = project.layout.buildDirectory.dir("reports/tests/e2eTest")
        
        inputs.dir(resultsDir)
        outputs.dir(reportsDir)
        
        doLast {
          val resDirFile = resultsDir.get().asFile
          val repDirFile = reportsDir.get().asFile
          repDirFile.deleteRecursively()
          repDirFile.mkdirs()
          
          val testCases = mutableListOf<E2ETestCase>()
          val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
          
          if (resDirFile.exists()) {
            val xmlFiles = resDirFile.walkTopDown()
              .filter { it.isFile && it.name.startsWith("TEST-") && it.extension.lowercase() == "xml" }
              .toList()
              
            xmlFiles.forEach { file ->
              val rawTarget = file.parentFile.name.lowercase()
              val target = when (rawTarget) {
                "desktop" -> "desktop"
                "wasm" -> "wasm"
                "ios" -> "ios"
                "android" -> "android"
                else -> rawTarget
              }
              
              try {
                val builder = factory.newDocumentBuilder()
                val doc = builder.parse(file)
                doc.documentElement.normalize()
                
                val caseNodes = doc.getElementsByTagName("testcase")
                for (i in 0 until caseNodes.length) {
                  val caseNode = caseNodes.item(i) as org.w3c.dom.Element
                  val methodName = caseNode.getAttribute("name")
                  val caseClassName = caseNode.getAttribute("classname")
                  val caseDuration = caseNode.getAttribute("time").toDoubleOrNull() ?: 0.0
                  
                  val packageName = caseClassName.substringBeforeLast('.', "")
                  val nameWithTarget = "$methodName[$target]"
                  
                  var status = "passed"
                  var failureMessage: String? = null
                  var failureType: String? = null
                  var failureDetail: String? = null
                  
                  val failureNodes = caseNode.getElementsByTagName("failure")
                  val errorNodes = caseNode.getElementsByTagName("error")
                  val skippedNodes = caseNode.getElementsByTagName("skipped")
                  
                  if (failureNodes.length > 0) {
                    status = "failed"
                    val failureEl = failureNodes.item(0) as org.w3c.dom.Element
                    failureMessage = failureEl.getAttribute("message").takeIf { it.isNotBlank() }
                    failureType = failureEl.getAttribute("type").takeIf { it.isNotBlank() }
                    failureDetail = failureEl.textContent.takeIf { it.isNotBlank() }
                  } else if (errorNodes.length > 0) {
                    status = "failed"
                    val errorEl = errorNodes.item(0) as org.w3c.dom.Element
                    failureMessage = errorEl.getAttribute("message").takeIf { it.isNotBlank() }
                    failureType = errorEl.getAttribute("type").takeIf { it.isNotBlank() }
                    failureDetail = errorEl.textContent.takeIf { it.isNotBlank() }
                  } else if (skippedNodes.length > 0) {
                    status = "ignored"
                  }
                  
                  testCases.add(E2ETestCase(
                    name = nameWithTarget,
                    methodName = methodName,
                    className = caseClassName,
                    packageName = packageName,
                    duration = caseDuration,
                    status = status,
                    failureMessage = failureMessage,
                    failureType = failureType,
                    failureDetail = failureDetail
                  ))
                }
              } catch (e: Exception) {
                logger.error("Parikshan: Failed to parse XML test report: ${file.absolutePath}", e)
              }
            }
          }
          
          val totalTests = testCases.size
          val totalFailures = testCases.count { it.status == "failed" }
          val totalIgnored = testCases.count { it.status == "ignored" }
          val totalDuration = testCases.sumOf { it.duration }
          val successRate = if (totalTests - totalIgnored > 0) {
            ((totalTests - totalFailures - totalIgnored) * 100) / (totalTests - totalIgnored)
          } else {
            100
          }
          val successRateClass = if (totalFailures > 0) "failures" else "success"
          
          val classesList = testCases.groupBy { it.className }.map { (className, cases) ->
            val pkgName = cases.first().packageName
            val total = cases.size
            val failures = cases.count { it.status == "failed" }
            val ignored = cases.count { it.status == "ignored" }
            val duration = cases.sumOf { it.duration }
            E2EClassSummary(className, pkgName, total, failures, 0, ignored, duration, cases)
          }
          
          val packagesList = classesList.groupBy { it.packageName }.map { (pkgName, classes) ->
            val total = classes.sumOf { it.tests }
            val failures = classes.sumOf { it.failures }
            val ignored = classes.sumOf { it.ignored }
            val duration = classes.sumOf { it.time }
            E2EPackageSummary(pkgName, total, failures, 0, ignored, duration, classes)
          }
          
          // Write CSS & JS assets
          val cssDir = File(repDirFile, "css")
          cssDir.mkdirs()
          File(cssDir, "base-style.css").writeText(BASE_STYLE_CSS)
          File(cssDir, "style.css").writeText(STYLE_CSS)
          
          val jsDir = File(repDirFile, "js")
          jsDir.mkdirs()
          File(jsDir, "report.js").writeText(REPORT_JS)
          
          // Write index.html
          val indexHtml = File(repDirFile, "index.html")
          indexHtml.writeText(generateIndexHtml(totalTests, totalFailures, totalIgnored, totalDuration, successRate, successRateClass, packagesList, classesList))
          
          // Write package files
          val packagesDir = File(repDirFile, "packages")
          packagesDir.mkdirs()
          packagesList.forEach { pkg ->
            File(packagesDir, "${pkg.name}.html").writeText(generatePackageHtml(pkg))
          }
          
          // Write class files
          val classesDir = File(repDirFile, "classes")
          classesDir.mkdirs()
          classesList.forEach { clazz ->
            File(classesDir, "${clazz.name}.html").writeText(generateClassHtml(clazz))
          }
          
          logger.lifecycle("Parikshan: Unified E2E HTML Report generated at file://${indexHtml.absolutePath}")
        }
      }

      project.tasks.register<E2ETestTask>("e2eTest") {
        group = "verification"
        description = "Run E2E tests for multiple targets concurrently (e.g. desktop,wasm)"
        
        finalizedBy(e2eTestReport)
        
        dependsOn(hostTestTask.get().testClassesDirs.buildDependencies)
        dependsOn(installPlaywrightTask)
        dependsOn(prepareWasmAssetsTask)
        dependsOn(extension.appJarTaskName.get())
        
        hostTestClassesDirs.setFrom(hostTestTask.get().testClassesDirs)
        hostTestClasspath.setFrom(hostTestTask.get().classpath)
        junitConsoleJars.setFrom(junitConsoleConfig)
        this.e2eTestClasses.set(project.provider { discoveredE2eClasses })
        host.set(extension.host)
        originalDesktopPort.set(extension.port)
        originalWasmPort.set(extension.wasmServerPort)
        
        val appJarFileProvider = project.tasks.named<org.gradle.jvm.tasks.Jar>(extension.appJarTaskName.get())
          .flatMap { it.archiveFile }
        appJarFile.set(appJarFileProvider)
        
        this.desktopLaunchManifestFile.set(manifestFile)
        this.wasmOutputDir.set(wasmOutputDirVal)
        this.wasmPortFile.set(wasmPortFileVal)
        this.appArgs.set(extAppArgs)
        token.set(sessionToken)
        this.title.set(extension.desktopWindowTitle)
        buildDir.set(project.layout.buildDirectory)
        projectRootDir.set(project.rootDir.absolutePath)
        targetAndroidAppId?.let { this@register.androidApplicationId.set(it) }
        this@register.iosPort.set(targetIosPort)
        this@register.iosBundleId.set(getIosBundleId())

        gradleAndroidSerial.set(project.providers.gradleProperty("parikshan.android.serial").orElse(project.providers.systemProperty("parikshan.android.serial")))
        gradleIosDevice.set(project.providers.gradleProperty("parikshan.ios.device").orElse(project.providers.systemProperty("parikshan.ios.device")))
        gradleDevice.set(project.providers.gradleProperty("device").orElse(project.providers.systemProperty("device")))
        gradleSerial.set(project.providers.gradleProperty("serial").orElse(project.providers.systemProperty("serial")))
      }

      project.tasks.configureEach {
        val isE2eTask = name in setOf("e2eDesktopTest", "e2eWasmTest", "e2eIosTest", "e2eAndroidTest", "e2eTest")
        if (isE2eTask) return@configureEach

        // Robustly find any task that has a test filter (Test, KotlinJsTest, KotlinNativeTest, etc.)
        try {
          val filterMethod = javaClass.methods.find { it.name == "getFilter" }
          val filter = filterMethod?.invoke(this) ?: return@configureEach
          
          val excludeMethod = filter.javaClass.methods.find {
            it.name == "excludeTestsMatching" && it.parameterCount == 1
          }
          
          if (excludeMethod != null) {
            e2eTestClasses.forEach { excludeMethod.invoke(filter, it) }
          }
        } catch (_: Exception) {
          // Task doesn't support filtering, skip
        }
      }
    }
  }
}

internal object ParikshanWasmServer {
    private var server: com.sun.net.httpserver.HttpServer? = null
    fun start(port: Int, root: File) {
        stop()
        val s = com.sun.net.httpserver.HttpServer.create(InetSocketAddress(port), 0)
        s.createContext("/") { ex ->
            val path = if (ex.requestURI.path == "/") "/index.html" else ex.requestURI.path
            val file = File(root, path.removePrefix("/"))
            if (file.exists() && file.isFile) {
                ex.responseHeaders.add("Cross-Origin-Opener-Policy", "same-origin")
                ex.responseHeaders.add("Cross-Origin-Embedder-Policy", "require-corp")
                val contentType = when (file.extension.lowercase()) {
                    "wasm" -> "application/wasm"
                    "js" -> "application/javascript"
                    "html" -> "text/html"
                    else -> "application/octet-stream"
                }
                ex.responseHeaders.add("Content-Type", contentType)
                ex.sendResponseHeaders(200, file.length())
                file.inputStream().use { it.copyTo(ex.responseBody) }
            } else {
                println("[ParikshanWasmServer] 404: ${ex.requestURI.path} (Looked in: ${file.absolutePath})")
                ex.sendResponseHeaders(404, 0)
            }
            ex.close()
        }

        s.start(); server = s
    }
    fun stop() { server?.stop(0); server = null }
}

private data class IosSimulatorDevice(val name: String, val udid: String, val runtime: String, val isBooted: Boolean)
private data class AndroidDevice(val serial: String, val state: String)

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
  val devices = mutableListOf<IosSimulatorDevice>()
  output.lineSequence().forEach { line ->
    if (line.startsWith("--")) runtime = line.trim('-', ' ')
    else if (line.contains("(")) {
      val name = line.substringBefore("(").trim()
      val udid = line.substringAfter("(").substringBefore(")")
      val state = line.substringAfterLast("(").substringBefore(")")
      if (requested == "booted" && state == "Booted" || requested == name || requested == udid) devices += IosSimulatorDevice(name, udid, runtime, state == "Booted")
    }
  }
  return devices.firstOrNull { it.isBooted } ?: devices.firstOrNull()
    ?: throw GradleException(
      "Parikshan iOS: No iOS Simulator found matching '$requested'. " +
        "Run `xcrun simctl list devices available` to see available simulators, " +
        "or set `-Pparikshan.ios.device=<name-or-udid>`."
    )
}

private object ParikshanIosVideoRecorder {
  var process: Process? = null
}

private object ParikshanAndroidRecorder {
  fun resolveDeviceSerial(logger: Logger, workingDir: File, explicit: String?): String {
    val process = ProcessBuilder("adb", "devices").directory(workingDir).start()
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    logger.debug("Parikshan Android: `adb devices` output:\n$output")
    if (exitCode != 0) {
      throw GradleException(
        "Parikshan Android: Could not list Android devices using `adb devices` " +
          "(exit code $exitCode). ${error.ifBlank { output }.trim()}"
      )
    }

    val devices =
      output.lineSequence()
        .drop(1)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
          val parts = line.split(Regex("\\s+"))
          if (parts.size >= 2) AndroidDevice(serial = parts[0], state = parts[1]) else null
        }
        .toList()

    val requested = explicit?.trim().orEmpty()
    if (requested.isNotEmpty()) {
      val device = devices.firstOrNull { it.serial == requested }
        ?: throw GradleException(
          "Parikshan Android: Device/emulator '$requested' was not found. " +
            "Run `adb devices` to check connected devices, or update `-Pparikshan.android.serial=<serial>`."
        )
      if (device.state != "device") {
        throw GradleException(
          "Parikshan Android: Device/emulator '$requested' is '${device.state}', not ready. " +
            "Run `adb devices` and resolve the device state before running E2E tests."
        )
      }
      return device.serial
    }

    val readyDevices = devices.filter { it.state == "device" }
    if (readyDevices.size == 1) {
      return readyDevices.single().serial
    }
    if (readyDevices.size > 1) {
      val serials = readyDevices.joinToString { it.serial }
      throw GradleException(
        "Parikshan Android: Multiple Android devices/emulators are connected: $serials. " +
          "Set `-Pparikshan.android.serial=<serial>` to choose one."
      )
    }

    val deviceStateSummary = devices.joinToString { "${it.serial} (${it.state})" }.ifBlank { "none" }
    throw GradleException(
      "Parikshan Android: No ready Android device/emulator connected. " +
        "Run `adb devices` to check devices, start an emulator, or connect a device with USB debugging enabled. " +
        "Detected devices: $deviceStateSummary."
    )
  }
  fun resolveAndroidApplicationId(project: Project): String? {
    val appProject = project.findAndroidAppProject() ?: return null
    val android = appProject.extensions.findByName("android") ?: return null
    return try {
      val defaultConfig = android.javaClass.methods.firstOrNull { it.name == "getDefaultConfig" }?.invoke(android)
      defaultConfig?.javaClass?.methods?.firstOrNull { it.name == "getApplicationId" }?.invoke(defaultConfig) as? String
    } catch (e: Exception) {
      null
    }
  }
}

internal object ParikshanDesktopProcess {
  private var process: Process? = null
  fun start(jar: File, token: String, logFile: File, manifestFile: File, appArgs: List<String>, host: String, port: Int, timeoutMs: Long, pollMs: Long, title: String?, background: Boolean) {
    stop(host = host, port = port, token = token, manifestFile = manifestFile)
    val javaExecutable = System.getProperty("java.home") + "/bin/java"
    val mainClass = JarFile(jar).manifest.mainAttributes.getValue("Main-Class")
    logFile.parentFile.mkdirs()
    val command =
      buildList {
        add(javaExecutable)
        add("-Dparikshan.host=$host")
        add("-Dparikshan.port=$port")
        add("-Dparikshan.token=$token")
        add("-Dparikshan.desktop.appMainClass=$mainClass")
        if (background) {
            add("-Dparikshan.background=true")
            if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
                add("-Dapple.awt.UIElement=true")
            }
        }
        title?.let { add("-Dparikshan.desktop.windowTitle=$it") }
        add("-cp")
        add(jar.absolutePath)
        add("io.github.aryapreetam.parikshan.server.ParikshanDesktopLauncher")
        addAll(appArgs)
      }
    val pb = ProcessBuilder(command)
    pb.environment()["NSAppSleepDisabled"] = "YES"
    val startedProcess = pb
      .redirectErrorStream(true)
      .redirectOutput(logFile)
      .start()
    process = startedProcess
    writeLaunchManifest(
      manifestFile = manifestFile,
      pid = startedProcess.pid(),
      javaExecutable = javaExecutable,
      jar = jar,
      appMainClass = mainClass,
      token = token,
      logFile = logFile,
      appArgs = appArgs,
      host = host,
      port = port,
      title = title,
      background = background
    )
  }
  fun stop(host: String = "127.0.0.1", port: Int = 9877, token: String = "", manifestFile: File? = null) {
    val resolvedPort = if (manifestFile != null && manifestFile.exists()) {
        val props = java.util.Properties()
        runCatching { manifestFile.inputStream().use { props.load(it) } }
        props.getProperty("port")?.toIntOrNull() ?: port
    } else {
        port
    }
    runCatching {
      val json = """{"type":"stopRecording","id":"desktop-stop","sessionName":"any","token":"$token"}"""
      val url = URL("http://$host:$resolvedPort/")
      val conn = url.openConnection() as HttpURLConnection
      conn.requestMethod = "POST"
      conn.setRequestProperty("Content-Type", "application/json")
      conn.doOutput = true
      conn.connectTimeout = 1000
      conn.readTimeout = 1000
      conn.outputStream.use { it.write(json.toByteArray()) }
      conn.responseCode
    }
    Thread.sleep(3000)
    process?.let { active ->
      if (active.isAlive) {
        active.destroy()
      }
    }
    manifestFile?.let { destroyManifestProcess(it) }
    process = null
  }

  private fun writeLaunchManifest(
    manifestFile: File,
    pid: Long,
    javaExecutable: String,
    jar: File,
    appMainClass: String,
    token: String,
    logFile: File,
    appArgs: List<String>,
    host: String,
    port: Int,
    title: String?,
    background: Boolean
  ) {
    manifestFile.parentFile.mkdirs()
    val properties =
      Properties().apply {
        setProperty("pid", pid.toString())
        setProperty("javaExecutable", javaExecutable)
        setProperty("jar", jar.absolutePath)
        setProperty("appMainClass", appMainClass)
        setProperty("token", token)
        setProperty("host", host)
        setProperty("port", port.toString())
        setProperty("logFile", logFile.absolutePath)
        setProperty("background", background.toString())
        title?.let { setProperty("windowTitle", it) }
        setProperty("appArgCount", appArgs.size.toString())
        appArgs.forEachIndexed { index, value ->
          setProperty("appArg.$index", value)
        }
      }
    manifestFile.outputStream().use { output ->
      properties.store(output, "Parikshan desktop launch state")
    }
  }

  private fun destroyManifestProcess(manifestFile: File) {
    if (!manifestFile.exists()) {
      return
    }
    val pid =
      Properties()
        .also { properties -> manifestFile.inputStream().use(properties::load) }
        .getProperty("pid")
        ?.toLongOrNull()
        ?: return
    val handle = ProcessHandle.of(pid).orElse(null) ?: return
    if (!handle.isAlive) {
      return
    }
    handle.destroy()
    runCatching {
      handle.onExit().get(10, TimeUnit.SECONDS)
    }.onFailure {
      if (handle.isAlive) {
        handle.destroyForcibly()
      }
    }
  }
}

private fun Project.configureParikshanDependencies(isE2EActive: Boolean) {
  // A world-class plugin resolves its own runtime version dynamically.
  // We read the version of the plugin class that Gradle actually loaded into the buildscript classpath.
  val pluginVersion = ParikshanGradlePlugin::class.java.`package`.implementationVersion ?: "0.0.1"

  // Test DSL is always available in commonTest
  addParikshanDependency("commonTestImplementation", ":parikshan", "io.github.aryapreetam:parikshan:$pluginVersion")
  addParikshanDependency("commonTestImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client:$pluginVersion")

  // The client engine is only injected into the production binary during active E2E tasks.
  // This prevents production pollution for all other builds.
  if (isE2EActive) {
      addParikshanDependency("commonMainImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client:$pluginVersion")
      
      // Inject server into all JVM targets
      val kmp = extensions.findByName("kotlin")
      if (kmp != null) {
          try {
              @Suppress("UNCHECKED_CAST")
              val targets = kmp.javaClass.getMethod("getTargets").invoke(kmp) as org.gradle.api.NamedDomainObjectCollection<Any>
              targets.forEach { target ->
                  val targetName = (target as org.gradle.api.Named).name
                  val className = target.javaClass.name
                  if (className.contains("KotlinJvmTarget", ignoreCase = true) || 
                      targetName.contains("jvm", ignoreCase = true) || 
                      targetName.contains("desktop", ignoreCase = true)) {
                      addParikshanDependency("${targetName}MainImplementation", ":parikshan-server", "io.github.aryapreetam:parikshan-server:$pluginVersion")
                  }
              }
          } catch (_: Exception) { }
      }

      // Fallback for non-KMP or failed resolution
      if (configurations.findByName("jvmMainImplementation") != null) {
          addParikshanDependency("jvmMainImplementation", ":parikshan-server", "io.github.aryapreetam:parikshan-server:$pluginVersion")
      }

      val appProject = findAndroidAppProject()
      if (appProject != null) {
          appProject.configurations.configureEach {
              if (name == "androidTestImplementation") {
                  appProject.addParikshanDependency("androidTestImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client:$pluginVersion")
                  appProject.addParikshanDependency("androidTestImplementation", "androidx.test:runner:1.6.2", "androidx.test:runner:1.6.2")
              }
          }
      }
  }
}

private fun Project.addParikshanDependency(config: String, path: String, maven: String) {
  val dep = rootProject.findProject(path)?.let { dependencies.project(mapOf("path" to it.path)) } ?: maven
  val configuration = configurations.findByName(config) ?: return
  dependencies.add(config, dep)
}

private fun Project.resolveHostTestTaskName(override: String?): String {
  if (override != null) return override
  val kmp = extensions.findByName("kotlin") ?: return "jvmTest"
  try {
      @Suppress("UNCHECKED_CAST")
      val targets = kmp.javaClass.getMethod("getTargets").invoke(kmp) as org.gradle.api.NamedDomainObjectCollection<Any>
      
      logger.debug("Parikshan: Found ${targets.size} Kotlin Multiplatform targets")
      targets.forEach { logger.debug("Parikshan: Target name=${(it as org.gradle.api.Named).name}, class=${it.javaClass.name}") }

      val jvmTargets = targets.filter { target ->
          val targetName = (target as org.gradle.api.Named).name
          val className = target.javaClass.name
          val match = className.contains("KotlinJvmTarget", ignoreCase = true) || 
                    targetName.contains("jvm", ignoreCase = true) || 
                    targetName.contains("desktop", ignoreCase = true)
          if (match) logger.debug("Parikshan: Matched target=$targetName as JVM test host target")
          match
      }
      
      val target = jvmTargets.find { (it as org.gradle.api.Named).name in listOf("desktop", "jvm") }
          ?: jvmTargets.firstOrNull()
          
      if (target != null) {
          val name = (target as org.gradle.api.Named).name
          val taskName = "${name}Test"
          logger.debug("Parikshan: Resolved hostTestTaskName=$taskName")
          return taskName
      }
  } catch (e: Exception) { 
      logger.debug("Parikshan: Exception while resolving host test task name", e)
  }
  logger.debug("Parikshan: Falling back to jvmTest")
  return "jvmTest"
}

private fun Project.resolveWasmDistributionTaskName(override: String?): String {
  if (override != null) return override
  val kmp = extensions.findByName("kotlin") ?: return "wasmJsBrowserDevelopmentWebpack"
  try {
      @Suppress("UNCHECKED_CAST")
      val targets = kmp.javaClass.getMethod("getTargets").invoke(kmp) as org.gradle.api.NamedDomainObjectCollection<Any>
      val wasmTargets = targets.filter { it.javaClass.name.contains("KotlinWasm", ignoreCase = true) }
      if (wasmTargets.size == 1) {
          val name = (wasmTargets[0] as org.gradle.api.Named).name
          return "${name}BrowserDevelopmentWebpack"
      }
  } catch (_: Exception) { }
  return "wasmJsBrowserDevelopmentWebpack"
}

private fun Project.findAndroidAppProject(): Project? {
    if (pluginManager.hasPlugin("com.android.application")) return this
    return rootProject.subprojects.find { it.pluginManager.hasPlugin("com.android.application") }
}

private fun Project.discoverE2eTestClasses(): List<String> {
  val commonTestKotlin = layout.projectDirectory.dir("src/commonTest/kotlin").asFile
  if (!commonTestKotlin.exists()) {
    return emptyList()
  }

  return commonTestKotlin
    .walkTopDown()
    .filter { it.isFile && it.extension == "kt" }
    .flatMap { file -> discoverE2eTestClassesInFile(file).asSequence() }
    .distinct()
    .sorted()
    .toList()
}

private fun discoverE2eTestClassesInFile(file: File): List<String> {
  val raw = file.readText()
  val source = raw.maskKotlinCommentsAndLiterals()
  if (!E2E_TEST_INVOCATION_REGEX.containsMatchIn(source)) {
    return emptyList()
  }

  val pkg = PACKAGE_REGEX.find(source)?.groupValues?.get(1).orEmpty()
  return CLASS_OR_OBJECT_REGEX
    .findAll(source)
    .mapNotNull { match ->
      val className = match.groupValues[1]
      val bodyStart = source.indexOf('{', startIndex = match.range.last + 1)
      if (bodyStart < 0) {
        return@mapNotNull null
      }
      val bodyEnd = source.findMatchingBrace(bodyStart)
      if (bodyEnd < 0) {
        return@mapNotNull null
      }
      val body = source.substring(bodyStart + 1, bodyEnd)
      if (!E2E_TEST_INVOCATION_REGEX.containsMatchIn(body)) {
        return@mapNotNull null
      }
      if (pkg.isNotEmpty()) "$pkg.$className" else className
    }
    .toList()
}

private val PACKAGE_REGEX = Regex("""\bpackage\s+([a-zA-Z_][a-zA-Z0-9_.]*)""")
private val CLASS_OR_OBJECT_REGEX = Regex("""\b(?:class|object)\s+([a-zA-Z_][a-zA-Z0-9_]*)""")
private val E2E_TEST_INVOCATION_REGEX = Regex("""(?<!\w)e2eTest\s*(?:\(|\{)""")

private fun String.findMatchingBrace(openIndex: Int): Int {
  var depth = 0
  for (index in openIndex until length) {
    when (this[index]) {
      '{' -> depth += 1
      '}' -> {
        depth -= 1
        if (depth == 0) return index
      }
    }
  }
  return -1
}

private fun String.maskKotlinCommentsAndLiterals(): String {
  val output = StringBuilder(length)
  var index = 0

  fun appendMasked(char: Char) {
    output.append(if (char == '\n' || char == '\r') char else ' ')
  }

  fun startsWithAt(value: String, startIndex: Int): Boolean =
    startIndex + value.length <= length && regionMatches(startIndex, value, 0, value.length)

  while (index < length) {
    when {
      startsWithAt("//", index) -> {
        appendMasked(this[index])
        appendMasked(this[index + 1])
        index += 2
        while (index < length && this[index] != '\n') {
          appendMasked(this[index])
          index += 1
        }
      }
      startsWithAt("/*", index) -> {
        appendMasked(this[index])
        appendMasked(this[index + 1])
        index += 2
        while (index < length) {
          if (startsWithAt("*/", index)) {
            appendMasked(this[index])
            appendMasked(this[index + 1])
            index += 2
            break
          }
          appendMasked(this[index])
          index += 1
        }
      }
      startsWithAt("\"\"\"", index) -> {
        repeat(3) {
          appendMasked(this[index])
          index += 1
        }
        while (index < length) {
          if (startsWithAt("\"\"\"", index)) {
            repeat(3) {
              appendMasked(this[index])
              index += 1
            }
            break
          }
          appendMasked(this[index])
          index += 1
        }
      }
      this[index] == '"' -> {
        appendMasked(this[index])
        index += 1
        var escaped = false
        while (index < length) {
          val char = this[index]
          appendMasked(char)
          index += 1
          if (escaped) {
            escaped = false
          } else if (char == '\\') {
            escaped = true
          } else if (char == '"') {
            break
          }
        }
      }
      this[index] == '\'' -> {
        appendMasked(this[index])
        index += 1
        var escaped = false
        while (index < length) {
          val char = this[index]
          appendMasked(char)
          index += 1
          if (escaped) {
            escaped = false
          } else if (char == '\\') {
            escaped = true
          } else if (char == '\'') {
            break
          }
        }
      }
      else -> {
        output.append(this[index])
        index += 1
      }
    }
  }

  return output.toString()
}

private fun Test.configureE2eHostTestExecution(
  hostTestClassesDirs: FileCollection,
  hostTestClasspath: FileCollection,
  e2eTestClasses: List<String>,
  target: String,
  logger: Logger
) {
  if (e2eTestClasses.isEmpty()) {
    throw GradleException("No E2E test classes discovered in src/commonTest")
  }

  outputs.upToDateWhen { false }
  testClassesDirs = hostTestClassesDirs
  classpath = hostTestClasspath
  dependsOn(hostTestClassesDirs.buildDependencies)

  filter {
    isFailOnNoMatchingTests = true
    if (includePatterns.isEmpty()) {
        e2eTestClasses.forEach { includeTestsMatching(it) }
    }
  }

  doFirst {
    val existingClassDirs = testClassesDirs.files.filter { it.exists() }
    if (existingClassDirs.isEmpty()) {
      throw GradleException(
        "Parikshan $target: host test classes were not compiled."
      )
    }
    logger.lifecycle("Parikshan $target: running E2E test classes ${e2eTestClasses.joinToString()}")
  }

  val videoOutputDirProvider = project.providers.gradleProperty("parikshan.video.outputDir")
      .orElse(project.providers.systemProperty("parikshan.video.outputDir"))
      .map { java.io.File(it) }
      .orElse(project.layout.buildDirectory.dir("parikshan/videos/${target.lowercase()}").map { it.asFile })

  outputs.dir(videoOutputDirProvider)
  
  // Forward all relevant parikshan.* properties to the test JVM using provider-aware API.
  // This ensures that command-line overrides (-P flags) are correctly picked up
  // even when the Gradle configuration cache is reused.
  val propsToForward = listOf(
    "parikshan.target",
    "parikshan.token",
    "parikshan.video.enabled",
    "parikshan.video.fps",
    "parikshan.video.showCursor",
    "parikshan.video.stepDelayMs",
    "parikshan.video.postRollMs",
    "parikshan.video.strategy",
    "parikshan.video.width",
    "parikshan.video.height",
    "parikshan.wasm.url",
    "parikshan.wasm.headless",
    "parikshan.wasm.viewportWidth",
    "parikshan.wasm.viewportHeight",
    "parikshan.wasm.bridgeReadyTimeoutMs",
    "parikshan.ios.device",
    "parikshan.ios.port",
    "parikshan.ios.bundleId",
    "parikshan.ios.xcodeProject",
    "parikshan.ios.xcodeScheme",
    "parikshan.ios.udid",
    "parikshan.android.serial"
  )

  for (propName in propsToForward) {
    val provider = project.providers.gradleProperty(propName)
      .orElse(project.providers.systemProperty(propName))
      .orElse("")

    // Resolve the provider at configuration time and only set non-empty values.
    // Using a resolved string here ensures the test JVM receives the actual
    // property value instead of a Provider's debug string representation.
    val resolved = provider.orNull ?: ""
    if (resolved.isNotEmpty()) {
      systemProperty(propName, resolved)
    }
  }

  jvmArgumentProviders.add(org.gradle.process.CommandLineArgumentProvider {
    listOf("-Dparikshan.video.outputDir=${videoOutputDirProvider.get().absolutePath}")
  })
}

private fun Project.registerParikshanIosBootSource(
  iosProjectDir: File,
  logger: Logger
): TaskProvider<Task> {
  val generatedDir = layout.buildDirectory.dir("parikshan/generated-ios-main").get().asFile
  val prepareTask =
    tasks.register("prepareParikshanIosBootSource") {
      group = "verification"
      outputs.dir(generatedDir)
      outputs.upToDateWhen { false }
      doLast {
        generatedDir.deleteRecursively()
        generatedDir.mkdirs()
        val iosMainDir = File(iosProjectDir, "src/iosMain/kotlin")
        if (iosMainDir.exists()) {
          iosMainDir.copyRecursively(generatedDir, overwrite = true)
        }
        generatedDir.walkTopDown()
          .filter { it.isFile && it.extension == "kt" }
          .forEach { file ->
            val original = file.readText()
            val instrumented = instrumentComposeUIViewControllerSource(original)
            if (instrumented != original) {
              file.writeText(instrumented)
              logger.lifecycle("Parikshan iOS: Instrumented generated source ${file.name}")
            }
          }

        val packageName = discoverIosMainPackage(iosProjectDir)
        val packageLine = packageName?.let { "package $it\n\n" }.orEmpty()
        File(generatedDir, "ParikshanBoot.kt").writeText(
          packageLine +
            """
            import io.github.aryapreetam.parikshan.server.ParikshanIosServer

            @Suppress("unused")
            fun ParikshanStartServer() {
              ParikshanIosServer.startIfNeeded()
            }
            """.trimIndent() +
            "\n"
        )
        logger.lifecycle("Parikshan iOS: Generated source set at ${generatedDir.absolutePath}")
      }
    }

  replaceIosMainSourceDirWhenAvailable(generatedDir)
  tasks.matching {
    it.name.contains("compileKotlinIos", ignoreCase = true) ||
      it.name.contains("embedAndSign", ignoreCase = true)
  }.configureEach {
    dependsOn(prepareTask)
  }
  return prepareTask
}

private fun Project.registerParikshanWasmBootSource(
  logger: Logger
): TaskProvider<Task> {
  val generatedDir = layout.buildDirectory.dir("parikshan/generated-wasm-main").get().asFile
  val projectDirFile = project.projectDir
  val prepareTask =
    tasks.register("prepareParikshanWasmBootSource") {
      group = "verification"
      outputs.dir(generatedDir)
      outputs.upToDateWhen { false }
      doLast {
        generatedDir.deleteRecursively()
        generatedDir.mkdirs()
        val wasmMainDir = File(projectDirFile, "src/wasmJsMain/kotlin")
        if (wasmMainDir.exists()) {
          wasmMainDir.copyRecursively(generatedDir, overwrite = true)
        }
        generatedDir.walkTopDown()
          .filter { it.isFile && it.extension == "kt" }
          .forEach { file ->
            val original = file.readText()
            val instrumented = instrumentComposeWasmSource(original)
            if (instrumented != original) {
              file.writeText(instrumented)
              logger.lifecycle("Parikshan Wasm: Instrumented generated source ${file.name}")
            }
          }
      }
    }

  replaceWasmMainSourceDirWhenAvailable(generatedDir)
  tasks.matching {
    it.name.contains("compileKotlinWasmJs", ignoreCase = true) ||
      it.name.contains("wasmJsBrowser", ignoreCase = true)
  }.configureEach {
    dependsOn(prepareTask)
  }
  return prepareTask
}

private fun Project.replaceWasmMainSourceDirWhenAvailable(generatedDir: File) {
  val kmp = extensions.findByName("kotlin") ?: return
  @Suppress("UNCHECKED_CAST")
  val sourceSets =
    kmp.javaClass.getMethod("getSourceSets").invoke(kmp) as org.gradle.api.NamedDomainObjectContainer<Any>
  sourceSets.all(
    object : Action<Any> {
      override fun execute(sourceSet: Any) {
        val name = (sourceSet as? org.gradle.api.Named)?.name
        if (name == "wasmJsMain") {
          replaceKotlinSourceDirs(sourceSet, generatedDir)
        }
      }
    }
  )
}

private fun Project.replaceIosMainSourceDirWhenAvailable(generatedDir: File) {
  val kmp = extensions.findByName("kotlin") ?: return
  @Suppress("UNCHECKED_CAST")
  val sourceSets =
    kmp.javaClass.getMethod("getSourceSets").invoke(kmp) as org.gradle.api.NamedDomainObjectContainer<Any>
  sourceSets.all(
    object : Action<Any> {
      override fun execute(sourceSet: Any) {
        val name = (sourceSet as? org.gradle.api.Named)?.name
        if (name == "iosMain") {
          replaceKotlinSourceDirs(sourceSet, generatedDir)
        }
      }
    }
  )
}

private fun replaceKotlinSourceDirs(
  sourceSet: Any,
  generatedDir: File
) {
  val kotlinSrc = sourceSet.javaClass.getMethod("getKotlin").invoke(sourceSet) as org.gradle.api.file.SourceDirectorySet
  kotlinSrc.setSrcDirs(listOf(generatedDir))
}

private fun instrumentComposeUIViewControllerSource(source: String): String {
  if (!source.contains("ComposeUIViewController")) {
    return source
  }
  val withoutComposeImport =
    source.replace(
      Regex("""import\s+androidx\.compose\.ui\.window\.ComposeUIViewController\s*\R"""),
      ""
    )
  val withParikshanImport =
    addKotlinImport(
      source = withoutComposeImport,
      importLine = "import io.github.aryapreetam.parikshan.ParikshanUIViewController"
    )
  return withParikshanImport.replace("ComposeUIViewController", "ParikshanUIViewController")
}

private fun addKotlinImport(
  source: String,
  importLine: String
): String {
  if (source.contains(importLine)) {
    return source
  }
  val packageMatch = Regex("""\A\s*package\s+[a-zA-Z0-9_.]+\s*\R""").find(source)
  return if (packageMatch != null) {
    source.replaceRange(
      packageMatch.range.last + 1,
      packageMatch.range.last + 1,
      "\n$importLine\n"
    )
  } else {
    "$importLine\n$source"
  }
}

private fun discoverIosMainPackage(iosProjectDir: File): String? {
  val iosMainDir = File(iosProjectDir, "src/iosMain/kotlin")
  if (!iosMainDir.exists()) return null
  iosMainDir.walkTopDown()
    .filter { it.isFile && it.extension == "kt" }
    .forEach { file ->
      val text = file.readText()
      if (text.contains("MainViewController")) {
        return Regex("""package\s+([a-zA-Z0-9_.]+)""").find(text)?.groupValues?.get(1)
      }
    }
  return null
}

private fun postIosPing(
  port: Int,
  token: String
): Boolean {
  val body = """{"type":"ping","id":"health","token":"${escapeJson(token)}"}"""
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

private fun instrumentComposeWasmSource(source: String): String {
  if (!source.contains("ComposeViewport")) {
    return source
  }
  
  var result = source
  
  // 1. Swap ComposeViewport with ParikshanComposeViewport
  result = result.replace(
    Regex("""import\s+androidx\.compose\.ui\.window\.ComposeViewport\s*\R"""),
    ""
  ).replace("ComposeViewport", "ParikshanComposeViewport")
  
  // 2. Inject Parikshan imports and initializer
  result = addKotlinImport(result, "import io.github.aryapreetam.parikshan.ParikshanComposeViewport")
  result = addKotlinImport(result, "import io.github.aryapreetam.parikshan.initializeParikshanWasm")
  
  // 3. Inject initializeParikshanWasm() at the start of main()
  val mainMatch = Regex("""fun\s+main\s*\([^)]*\)\s*\{""").find(result)
  if (mainMatch != null) {
      result = result.replaceRange(
          mainMatch.range.last + 1,
          mainMatch.range.last + 1,
          "\n  initializeParikshanWasm()\n"
      )
  }
  
  return result
}

private fun Project.discoverIosXcodeProject(): File? {
    return rootDir.walkTopDown()
        .filter { it.isDirectory && it.extension == "xcodeproj" && !it.absolutePath.contains(".gradle") && !it.absolutePath.contains("build") }
        .firstOrNull()
}

abstract class XcodeBundleIdValueSource : org.gradle.api.provider.ValueSource<String, XcodeBundleIdValueSource.Parameters> {
  interface Parameters : org.gradle.api.provider.ValueSourceParameters {
    val xcodeProject: org.gradle.api.provider.Property<File>
    val scheme: org.gradle.api.provider.Property<String>
  }

  @get:Inject
  abstract val execOperations: org.gradle.process.ExecOperations

  override fun obtain(): String? {
    val projectFile = parameters.xcodeProject.orNull ?: return null
    val schemeName = parameters.scheme.orNull ?: return null
    return try {
      val outputStream = java.io.ByteArrayOutputStream()
      execOperations.exec {
        commandLine("xcodebuild", "-project", projectFile.absolutePath, "-scheme", schemeName, "-showBuildSettings")
        standardOutput = outputStream
        isIgnoreExitValue = true
      }
      val output = outputStream.toString()
      val match = Regex("""\bPRODUCT_BUNDLE_IDENTIFIER\s*=\s*(.+)""").find(output)
      match?.groupValues?.get(1)?.trim()
    } catch (e: Exception) {
      null
    }
  }
}

private fun escapeJson(value: String): String =
  buildString {
    value.forEach { char ->
      when (char) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> append(char)
      }
    }
  }

internal object ParikshanPortConflictHandler {
    fun resolvePortAndCleanStale(originalPort: Int, host: String, logger: org.gradle.api.logging.Logger): Int {
        if (isPortAvailable(host, originalPort)) {
            return originalPort
        }
        
        logger.lifecycle("[Parikshan] Port $originalPort is in use on $host. Probing for stale Parikshan instance...")
        if (isStaleParikshanServer(host, originalPort)) {
            logger.lifecycle("[Parikshan] Detected stale Parikshan instance on port $originalPort. Attempting to auto-terminate...")
            val pid = findPidUsingPort(originalPort)
            if (pid != null) {
                logger.lifecycle("[Parikshan] Found stale process PID: $pid. Terminating...")
                terminateProcess(pid)
                var released = false
                for (i in 1..20) {
                    Thread.sleep(100)
                    if (isPortAvailable(host, originalPort)) {
                        released = true
                        break
                    }
                }
                if (released) {
                    logger.lifecycle("[Parikshan] Stale process terminated and port $originalPort released successfully.")
                    return originalPort
                } else {
                    logger.warn("[Parikshan] Failed to release port $originalPort after terminating PID $pid.")
                }
            } else {
                logger.warn("[Parikshan] Could not resolve PID for stale Parikshan instance on port $originalPort.")
            }
        } else {
            logger.lifecycle("[Parikshan] Port $originalPort is occupied by a non-Parikshan process or is unresponsive.")
        }
        
        var fallbackPort = originalPort + 1
        while (fallbackPort <= 65535) {
            if (isPortAvailable(host, fallbackPort)) {
                logger.lifecycle("[Parikshan] Port $originalPort is busy. Fallback chosen: $fallbackPort")
                return fallbackPort
            }
            fallbackPort++
        }
        
        error("No available ports found in range $originalPort to 65535 on $host")
    }

    internal fun isPortAvailable(host: String, port: Int): Boolean {
        return try {
            java.net.ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(java.net.InetSocketAddress(host, port))
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    internal fun isStaleParikshanServer(host: String, port: Int): Boolean {
        var conn: java.net.HttpURLConnection? = null
        return try {
            val url = java.net.URL("http://$host:$port/")
            conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Connection", "close")
            conn.doOutput = true
            conn.connectTimeout = 500
            conn.readTimeout = 500
            val probePayload = """{"type":"ping","id":"probe","token":"probe"}"""
            conn.outputStream.use { it.write(probePayload.toByteArray()) }
            
            val responseCode = conn.responseCode
            if (responseCode == 401) {
                val body = conn.errorStream?.use { it.bufferedReader().readText() }.orEmpty()
                body.contains("Unauthorized") || body.contains("Token mismatch")
            } else if (responseCode == 200) {
                val body = conn.inputStream?.use { it.bufferedReader().readText() }.orEmpty()
                body.contains("\"type\":\"Ok\"") || body.contains("pong")
            } else {
                false
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    internal fun findPidUsingPort(port: Int, excludeCurrentPid: Boolean = true): Long? {
        val os = System.getProperty("os.name").lowercase()
        val currentPid = java.lang.ProcessHandle.current().pid()
        return try {
            if (os.contains("win")) {
                val process = ProcessBuilder("cmd", "/c", "netstat -ano").start()
                val output = process.inputStream.bufferedReader().readText()
                output.lineSequence()
                    .filter { it.contains(":$port") && it.contains("LISTENING", ignoreCase = true) }
                    .map { it.trim().split(Regex("\\s+")).lastOrNull()?.toLongOrNull() }
                    .filterNotNull()
                    .filter { !excludeCurrentPid || it != currentPid }
                    .firstOrNull()
            } else {
                val process = ProcessBuilder("lsof", "-t", "-i", ":$port").start()
                val output = process.inputStream.bufferedReader().readText().trim()
                output.lineSequence()
                    .map { it.trim().toLongOrNull() }
                    .filterNotNull()
                    .filter { !excludeCurrentPid || it != currentPid }
                    .firstOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun terminateProcess(pid: Long) {
        if (pid == java.lang.ProcessHandle.current().pid()) {
            return
        }
        try {
            val handle = java.lang.ProcessHandle.of(pid).orElse(null) ?: return
            if (!handle.isAlive) return
            handle.destroy()
            runCatching {
                handle.onExit().get(2, java.util.concurrent.TimeUnit.SECONDS)
            }.onFailure {
                if (handle.isAlive) {
                    handle.destroyForcibly()
                }
            }
        } catch (_: Exception) {
            // Ignore security or permission exceptions to let the port fallback happen gracefully
        }
    }
}

private data class E2ETestCase(
  val name: String,
  val methodName: String,
  val className: String,
  val packageName: String,
  val duration: Double,
  val status: String, // "passed", "failed", "ignored"
  val failureMessage: String?,
  val failureType: String?,
  val failureDetail: String?
)

private data class E2EClassSummary(
  val name: String,
  val packageName: String,
  val tests: Int,
  val failures: Int,
  val errors: Int,
  val ignored: Int,
  val time: Double,
  val testCases: List<E2ETestCase>
)

private data class E2EPackageSummary(
  val name: String,
  val tests: Int,
  val failures: Int,
  val errors: Int,
  val ignored: Int,
  val time: Double,
  val classes: List<E2EClassSummary>
)

private fun generateIndexHtml(
  totalTests: Int,
  totalFailures: Int,
  totalIgnored: Int,
  totalDuration: Double,
  successRate: Int,
  successRateClass: String,
  packages: List<E2EPackageSummary>,
  classes: List<E2EClassSummary>
): String {
  val dateString = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss")
    .format(java.time.ZonedDateTime.now())
  
  val packagesRows = packages.joinToString("\n") { pkg ->
    val statusClass = if (pkg.failures > 0) "failures" else "success"
    """
<tr>
<td class="$statusClass">
<a href="packages/${pkg.name}.html">${pkg.name}</a>
</td>
<td>${pkg.tests}</td>
<td>${pkg.failures}</td>
<td>${pkg.ignored}</td>
<td>${String.format(java.util.Locale.US, "%.3f", pkg.time)}s</td>
<td class="$statusClass">${if (pkg.tests - pkg.ignored > 0) ((pkg.tests - pkg.failures - pkg.ignored) * 100) / (pkg.tests - pkg.ignored) else 100}%</td>
</tr>
    """.trimIndent()
  }

  val classesRows = classes.joinToString("\n") { clazz ->
    val statusClass = if (clazz.failures > 0) "failures" else "success"
    """
<tr>
<td class="$statusClass">
<a href="classes/${clazz.name}.html">${clazz.name}</a>
</td>
<td>${clazz.tests}</td>
<td>${clazz.failures}</td>
<td>${clazz.ignored}</td>
<td>${String.format(java.util.Locale.US, "%.3f", clazz.time)}s</td>
<td class="$statusClass">${if (clazz.tests - clazz.ignored > 0) ((clazz.tests - clazz.failures - clazz.ignored) * 100) / (clazz.tests - clazz.ignored) else 100}%</td>
</tr>
    """.trimIndent()
  }

  return """
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<meta http-equiv="x-ua-compatible" content="IE=edge"/>
<title>Test results - Test Summary</title>
<link href="css/base-style.css" rel="stylesheet" type="text/css"/>
<link href="css/style.css" rel="stylesheet" type="text/css"/>
<script src="js/report.js" type="text/javascript"></script>
</head>
<body>
<div id="content">
<h1>Test Summary</h1>
<div id="summary">
<table>
<tr>
<td>
<div class="summaryGroup">
<table>
<tr>
<td>
<div class="infoBox" id="tests">
<div class="counter">$totalTests</div>
<p>tests</p>
</div>
</td>
<td>
<div class="infoBox" id="failures">
<div class="counter">$totalFailures</div>
<p>failures</p>
</div>
</td>
<td>
<div class="infoBox" id="ignored">
<div class="counter">$totalIgnored</div>
<p>ignored</p>
</div>
</td>
<td>
<div class="infoBox" id="duration">
<div class="counter">${String.format(java.util.Locale.US, "%.3f", totalDuration)}s</div>
<p>duration</p>
</div>
</td>
</tr>
</table>
</div>
</td>
<td>
<div class="infoBox $successRateClass" id="successRate">
<div class="percent">$successRate%</div>
<p>successful</p>
</div>
</td>
</tr>
</table>
</div>
<div class="tab-container">
<ul class="tabLinks">
<li>
<a href="#tab0">Packages</a>
</li>
<li>
<a href="#tab1">Classes</a>
</li>
</ul>
<div class="tab" id="tab0">
<h2>Packages</h2>
<table>
<thead>
<tr>
<th>Package</th>
<th>Tests</th>
<th>Failures</th>
<th>Ignored</th>
<th>Duration</th>
<th>Success rate</th>
</tr>
</thead>
<tbody>
$packagesRows
</tbody>
</table>
</div>
<div class="tab" id="tab1">
<h2>Classes</h2>
<table>
<thead>
<tr>
<th>Class</th>
<th>Tests</th>
<th>Failures</th>
<th>Ignored</th>
<th>Duration</th>
<th>Success rate</th>
</tr>
</thead>
<tbody>
$classesRows
</tbody>
</table>
</div>
</div>
<div id="footer">
<p>
<div>
<label class="hidden" id="label-for-line-wrapping-toggle" for="line-wrapping-toggle">Wrap lines
<input id="line-wrapping-toggle" type="checkbox" autocomplete="off"/>
</label>
</div>Generated by 
<a href="https://www.gradle.org">Gradle 9.1.0</a> at $dateString</p>
</div>
</div>
</body>
</html>
  """.trimIndent()
}

private fun generatePackageHtml(pkg: E2EPackageSummary): String {
  val dateString = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss")
    .format(java.time.ZonedDateTime.now())
  
  val successRate = if (pkg.tests - pkg.ignored > 0) {
    ((pkg.tests - pkg.failures - pkg.ignored) * 100) / (pkg.tests - pkg.ignored)
  } else {
    100
  }
  val successRateClass = if (pkg.failures > 0) "failures" else "success"

  val classesRows = pkg.classes.joinToString("\n") { clazz ->
    val statusClass = if (clazz.failures > 0) "failures" else "success"
    """
<tr>
<td class="$statusClass">
<a href="../classes/${clazz.name}.html">${clazz.name.substringAfterLast('.')}</a>
</td>
<td>${clazz.tests}</td>
<td>${clazz.failures}</td>
<td>${clazz.ignored}</td>
<td>${String.format(java.util.Locale.US, "%.3f", clazz.time)}s</td>
<td class="$statusClass">${if (clazz.tests - clazz.ignored > 0) ((clazz.tests - clazz.failures - clazz.ignored) * 100) / (clazz.tests - clazz.ignored) else 100}%</td>
</tr>
    """.trimIndent()
  }

  return """
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<meta http-equiv="x-ua-compatible" content="IE=edge"/>
<title>Test results - Package ${pkg.name}</title>
<link href="../css/base-style.css" rel="stylesheet" type="text/css"/>
<link href="../css/style.css" rel="stylesheet" type="text/css"/>
<script src="../js/report.js" type="text/javascript"></script>
</head>
<body>
<div id="content">
<h1>Package ${pkg.name}</h1>
<div class="breadcrumbs">
<a href="../index.html">all</a> &gt; ${pkg.name}</div>
<div id="summary">
<table>
<tr>
<td>
<div class="summaryGroup">
<table>
<tr>
<td>
<div class="infoBox" id="tests">
<div class="counter">${pkg.tests}</div>
<p>tests</p>
</div>
</td>
<td>
<div class="infoBox" id="failures">
<div class="counter">${pkg.failures}</div>
<p>failures</p>
</div>
</td>
<td>
<div class="infoBox" id="ignored">
<div class="counter">${pkg.ignored}</div>
<p>ignored</p>
</div>
</td>
<td>
<div class="infoBox" id="duration">
<div class="counter">${String.format(java.util.Locale.US, "%.3f", pkg.time)}s</div>
<p>duration</p>
</div>
</td>
</tr>
</table>
</div>
</td>
<td>
<div class="infoBox $successRateClass" id="successRate">
<div class="percent">$successRate%</div>
<p>successful</p>
</div>
</td>
</tr>
</table>
</div>
<div class="tab-container">
<ul class="tabLinks">
<li>
<a href="#tab0">Classes</a>
</li>
</ul>
<div class="tab" id="tab0">
<h2>Classes</h2>
<table>
<thead>
<tr>
<th>Class</th>
<th>Tests</th>
<th>Failures</th>
<th>Ignored</th>
<th>Duration</th>
<th>Success rate</th>
</tr>
</thead>
<tbody>
$classesRows
</tbody>
</table>
</div>
</div>
<div id="footer">
<p>
<div>
<label class="hidden" id="label-for-line-wrapping-toggle" for="line-wrapping-toggle">Wrap lines
<input id="line-wrapping-toggle" type="checkbox" autocomplete="off"/>
</label>
</div>Generated by 
<a href="https://www.gradle.org">Gradle 9.1.0</a> at $dateString</p>
</div>
</div>
</body>
</html>
  """.trimIndent()
}

private fun generateClassHtml(clazz: E2EClassSummary): String {
  val dateString = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss")
    .format(java.time.ZonedDateTime.now())
  
  val successRate = if (clazz.tests - clazz.ignored > 0) {
    ((clazz.tests - clazz.failures - clazz.ignored) * 100) / (clazz.tests - clazz.ignored)
  } else {
    100
  }
  val successRateClass = if (clazz.failures > 0) "failures" else "success"
  val simpleClassName = clazz.name.substringAfterLast('.')

  val failedCases = clazz.testCases.filter { it.status == "failed" }
  val hasFailed = failedCases.isNotEmpty()

  val tabLinks = buildString {
    if (hasFailed) {
      append("""
<li>
<a href="#tab0">Failed tests</a>
</li>
<li>
<a href="#tab1">Tests</a>
</li>
      """.trimIndent())
    } else {
      append("""
<li>
<a href="#tab0">Tests</a>
</li>
      """.trimIndent())
    }
  }

  val failedTestsTab = if (hasFailed) {
    val failedBlocks = failedCases.joinToString("\n") { case ->
      val detailEscaped = case.failureDetail?.replace("<", "&lt;")?.replace(">", "&gt;") ?: ""
      """
<div class="test">
<a name="${case.name}"></a>
<h3 class="failures">${case.name}</h3>
<span class="code">
<pre>${case.failureMessage ?: "Test Failed"}
$detailEscaped
</pre>
</span>
</div>
      """.trimIndent()
    }
    """
<div class="tab" id="tab0">
<h2>Failed tests</h2>
$failedBlocks
</div>
    """.trimIndent()
  } else ""

  val testsTabId = if (hasFailed) "tab1" else "tab0"
  
  val testCasesRows = clazz.testCases.joinToString("\n") { case ->
    val resultClass = when (case.status) {
      "failed" -> "failures"
      "ignored" -> "skipped"
      else -> "success"
    }
    val resultText = when (case.status) {
      "failed" -> "failed"
      "ignored" -> "ignored"
      else -> "passed"
    }
    """
<tr>
<td class="$resultClass">
<a href="#${case.name}">${case.name}</a>
</td>
<td>${case.methodName}</td>
<td>${String.format(java.util.Locale.US, "%.3f", case.duration)}s</td>
<td class="$resultClass">$resultText</td>
</tr>
    """.trimIndent()
  }

  val testsTab = """
<div class="tab" id="$testsTabId">
<h2>Tests</h2>
<table>
<thead>
<tr>
<th>Test</th>
<th>Method name</th>
<th>Duration</th>
<th>Result</th>
</tr>
</thead>
<tbody>
$testCasesRows
</tbody>
</table>
</div>
  """.trimIndent()

  return """
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<meta http-equiv="x-ua-compatible" content="IE=edge"/>
<title>Test results - Class ${clazz.name}</title>
<link href="../css/base-style.css" rel="stylesheet" type="text/css"/>
<link href="../css/style.css" rel="stylesheet" type="text/css"/>
<script src="../js/report.js" type="text/javascript"></script>
</head>
<body>
<div id="content">
<h1>Class ${clazz.name}</h1>
<div class="breadcrumbs">
<a href="../index.html">all</a> &gt; <a href="../packages/${clazz.packageName}.html">${clazz.packageName}</a> &gt; $simpleClassName</div>
<div id="summary">
<table>
<tr>
<td>
<div class="summaryGroup">
<table>
<tr>
<td>
<div class="infoBox" id="tests">
<div class="counter">${clazz.tests}</div>
<p>tests</p>
</div>
</td>
<td>
<div class="infoBox" id="failures">
<div class="counter">${clazz.failures}</div>
<p>failures</p>
</div>
</td>
<td>
<div class="infoBox" id="ignored">
<div class="counter">${clazz.ignored}</div>
<p>ignored</p>
</div>
</td>
<td>
<div class="infoBox" id="duration">
<div class="counter">${String.format(java.util.Locale.US, "%.3f", clazz.time)}s</div>
<p>duration</p>
</div>
</td>
</tr>
</table>
</div>
</td>
<td>
<div class="infoBox $successRateClass" id="successRate">
<div class="percent">$successRate%</div>
<p>successful</p>
</div>
</td>
</tr>
</table>
</div>
<div class="tab-container">
<ul class="tabLinks">
$tabLinks
</ul>
$failedTestsTab
$testsTab
</div>
<div id="footer">
<p>
<div>
<label class="hidden" id="label-for-line-wrapping-toggle" for="line-wrapping-toggle">Wrap lines
<input id="line-wrapping-toggle" type="checkbox" autocomplete="off"/>
</label>
</div>Generated by 
<a href="https://www.gradle.org">Gradle 9.1.0</a> at $dateString</p>
</div>
</div>
</body>
</html>
  """.trimIndent()
}

private const val BASE_STYLE_CSS = """
body {
    margin: 0;
    padding: 0;
    font-family: sans-serif;
    font-size: 12pt;
}
body, a, a:visited {
    color: #303030;
}
#content {
    padding: 30px 50px;
}
#content h1 {
    font-size: 160%;
    margin-bottom: 10px;
}
#footer {
    margin-top: 100px;
    font-size: 80%;
    white-space: nowrap;
}
#footer, #footer a {
    color: #a0a0a0;
}
#line-wrapping-toggle {
    vertical-align: middle;
}
#label-for-line-wrapping-toggle {
    vertical-align: middle;
}
ul {
    margin-left: 0;
}
h1, h2, h3 {
    white-space: nowrap;
}
h2 {
    font-size: 120%;
}
.tab-container .tab-container {
    margin-left: 8px;
}
ul.tabLinks {
    padding: 0;
    margin-bottom: 0;
    overflow: auto;
    min-width: 800px;
    width: auto;
    border-bottom: solid 1px #aaa;
}
ul.tabLinks li {
    float: left;
    height: 100%;
    list-style: none;
    padding: 5px 10px;
    border-radius: 7px 7px 0 0;
    border: solid 1px transparent;
    border-bottom: none;
    margin-right: 6px;
    background-color: #f0f0f0;
}
ul.tabLinks li.deselected > a {
    color: #6d6d6d;
}
ul.tabLinks li:hover {
    background-color: #fafafa;
}
ul.tabLinks li.selected {
    background-color: #c5f0f5;
    border-color: #aaa;
}
ul.tabLinks a {
    font-size: 120%;
    display: block;
    outline: none;
    text-decoration: none;
    margin: 0;
    padding: 0;
}
ul.tabLinks li h2 {
    margin: 0;
    padding: 0;
}
div.tab {
}
div.selected {
    display: block;
}
div.deselected {
    display: none;
}
div.tab table {
    min-width: 350px;
    width: auto;
    border-collapse: collapse;
}
div.tab th, div.tab table {
    border-bottom: solid 1px #d0d0d0;
}
div.tab th {
    text-align: left;
    white-space: nowrap;
    padding-left: 6em;
}
div.tab th:first-child {
    padding-left: 0;
}
div.tab td {
    white-space: nowrap;
    padding-left: 6em;
    padding-top: 5px;
    padding-bottom: 5px;
}
div.tab td:first-child {
    padding-left: 0;
}
div.tab td.numeric, div.tab th.numeric {
    text-align: right;
}
span.code {
    display: inline-block;
    margin-top: 0;
    margin-bottom: 1em;
}
span.code pre {
    font-size: 11pt;
    padding: 10px;
    margin: 0;
    background-color: #f7f7f7;
    border: solid 1px #d0d0d0;
    min-width: 700px;
    width: auto;
}
span.wrapped pre {
    word-wrap: break-word;
    white-space: pre-wrap;
    word-break: break-all;
}
label.hidden {
    display: none;
}
"""

private const val STYLE_CSS = """
#summary {
    margin-top: 30px;
    margin-bottom: 40px;
}
#summary table {
    border-collapse: collapse;
}
#summary td {
    vertical-align: top;
}
.breadcrumbs, .breadcrumbs a {
    color: #606060;
}
.infoBox {
    width: 110px;
    padding-top: 15px;
    padding-bottom: 15px;
    text-align: center;
}
.infoBox p {
    margin: 0;
}
.counter, .percent {
    font-size: 120%;
    font-weight: bold;
    margin-bottom: 8px;
}
#duration {
    width: 125px;
}
#successRate, .summaryGroup {
    border: solid 2px #d0d0d0;
    -moz-border-radius: 10px;
    border-radius: 10px;
}
#successRate {
    width: 140px;
    margin-left: 35px;
}
#successRate .percent {
    font-size: 180%;
}
.success, .success a {
    color: #008000;
}
div.success, #successRate.success {
    background-color: #bbd9bb;
    border-color: #008000;
}
.failures, .failures a {
    color: #b60808;
}
.skipped, .skipped a {
    color: #c09853;
}
div.failures, #successRate.failures {
    background-color: #ecdada;
    border-color: #b60808;
}
ul.linkList {
    padding-left: 0;
}
ul.linkList li {
    list-style: none;
    margin-bottom: 5px;
}
.code {
    position: relative;
}
.clipboard-copy-btn {
    position: absolute;
    top: 8px;
    right: 8px;
    padding: 4px 8px;
    font-size: 0.9em;
    cursor: pointer;
}
"""

private const val REPORT_JS = """
(function (window, document) {
    "use strict";

    function changeElementClass(element, classValue) {
        if (element.getAttribute("className")) {
            element.setAttribute("className", classValue);
        } else {
            element.setAttribute("class", classValue);
        }
    }

    function getClassAttribute(element) {
        if (element.getAttribute("className")) {
            return element.getAttribute("className");
        } else {
            return element.getAttribute("class");
        }
    }

    function addClass(element, classValue) {
        changeElementClass(element, getClassAttribute(element) + " " + classValue);
    }

    function removeClass(element, classValue) {
        changeElementClass(element, getClassAttribute(element).replace(classValue, ""));
    }

    function getCheckBox() {
        return document.getElementById("line-wrapping-toggle");
    }

    function getLabelForCheckBox() {
        return document.getElementById("label-for-line-wrapping-toggle");
    }

    function findCodeBlocks() {
        const codeBlocks = [];
        const tabContainers = getTabContainers();
        for (let i = 0; i < tabContainers.length; i++) {
            const spans = tabContainers[i].getElementsByTagName("span");
            for (let i = 0; i < spans.length; ++i) {
                if (spans[i].className.indexOf("code") >= 0) {
                    codeBlocks.push(spans[i]);
                }
            }
        }
        return codeBlocks;
    }

    function forAllCodeBlocks(operation) {
        const codeBlocks = findCodeBlocks();

        for (let i = 0; i < codeBlocks.length; ++i) {
            operation(codeBlocks[i], "wrapped");
        }
    }

    function toggleLineWrapping() {
        const checkBox = getCheckBox();

        if (checkBox.checked) {
            forAllCodeBlocks(addClass);
        } else {
            forAllCodeBlocks(removeClass);
        }
    }

    function initClipboardCopyButton() {
        document.querySelectorAll(".clipboard-copy-btn").forEach((button) => {
            const copyElementId = button.getAttribute("data-copy-element-id");
            const elementWithCodeToSelect = document.getElementById(copyElementId);

            button.addEventListener("click", () => {
                const text = elementWithCodeToSelect.innerText.trim();
                navigator.clipboard
                    .writeText(text)
                    .then(() => {
                        button.textContent = "Copied!";
                        setTimeout(() => {
                            button.textContent = "Copy";
                        }, 1500);
                    })
                    .catch((err) => {
                        alert("Failed to copy to the clipboard: '" + err.message + "'. Check JavaScript console for more details.")
                        console.warn("Failed to copy to the clipboard", err);
                    });
            });
        });
    }

    function initControls() {
        if (findCodeBlocks().length > 0) {
            const checkBox = getCheckBox();
            const label = getLabelForCheckBox();

            checkBox.onclick = toggleLineWrapping;
            checkBox.checked = false;

            removeClass(label, "hidden");
         }

         initClipboardCopyButton()
    }

    class TabManager {
        baseId;
        tabs;
        titles;
        headers;

        constructor(baseId, tabs, titles, headers) {
            this.baseId = baseId;
            this.tabs = tabs;
            this.titles = titles;
            this.headers = headers;
        }

        select(i) {
            this.deselectAll();

            changeElementClass(this.tabs[i], "tab selected");
            changeElementClass(this.headers[i], "selected");

            while (this.headers[i].firstChild) {
                this.headers[i].removeChild(this.headers[i].firstChild);
            }

            const a = document.createElement("a");

            a.appendChild(document.createTextNode(this.titles[i]));
            this.headers[i].appendChild(a);
        }

        deselectAll() {
            for (let i = 0; i < this.tabs.length; i++) {
                changeElementClass(this.tabs[i], "tab deselected");
                changeElementClass(this.headers[i], "deselected");

                while (this.headers[i].firstChild) {
                    this.headers[i].removeChild(this.headers[i].firstChild);
                }

                const a = document.createElement("a");

                const id = this.baseId + "-tab" + i;
                a.setAttribute("id", id);
                a.setAttribute("href", "#tab" + i);
                a.onclick = () => {
                    this.select(i);
                    return false;
                };
                a.appendChild(document.createTextNode(this.titles[i]));

                this.headers[i].appendChild(a);
            }
        }
    }

    function getTabContainers() {
        const tabContainers = Array.from(document.getElementsByClassName("tab-container"));

        // Used by existing TabbedPageRenderer users, which have not adjusted to use TabsRenderer yet.
        const legacyContainer = document.getElementById("tabs");
        if (legacyContainer) {
            tabContainers.push(legacyContainer);
        }

        return tabContainers;
    }

    function initTabs() {
        let tabGroups = 0;

        function createTab(num, container) {
            const tabElems = findTabs(container);
            const tabManager = new TabManager("tabs" + num, tabElems, findTitles(tabElems), findHeaders(container));
            tabManager.select(0);
        }

        const tabContainers = getTabContainers();

        for (let i = 0; i < tabContainers.length; i++) {
            createTab(tabGroups, tabContainers[i]);
            tabGroups++;
        }

        return true;
    }

    // Helper functions...
    function findTabs(container) {
        return findChildElements(container, "DIV", "tab");
    }

    function findHeaders(container) {
        const owner = findChildElements(container, "UL", "tabLinks");
        return findChildElements(owner[0], "LI", null);
    }

    function findTitles(tabs) {
        const titles = [];

        for (let i = 0; i < tabs.length; i++) {
            const tab = tabs[i];
            const header = findChildElements(tab, "H2", null)[0];

            header.parentNode.removeChild(header);

            if (header.innerText) {
                titles.push(header.innerText);
            } else {
                titles.push(header.textContent);
            }
        }

        return titles;
    }

    function findChildElements(container, name, targetClass) {
        const elements = [];
        const children = container.childNodes;

        for (let i = 0; i < children.length; i++) {
            const child = children.item(i);

            if (child.nodeType === 1 && child.nodeName === name) {
                if (targetClass && child.className.indexOf(targetClass) < 0) {
                    continue;
                }

                elements.push(child);
            }
        }

        return elements;
    }

    // Entry point.
    window.onload = function() {
        initTabs();
        initControls();
    };
} (window, window.document));
"""
