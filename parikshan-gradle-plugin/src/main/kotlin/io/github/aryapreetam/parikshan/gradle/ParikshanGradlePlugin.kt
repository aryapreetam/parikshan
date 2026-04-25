package io.github.aryapreetam.parikshan.gradle

import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import javax.inject.Inject
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

    // --- Desktop Tasks ---

    val startDesktopTask =
      project.tasks.register("startParikshanDesktopApp") {
        group = "verification"
        doLast {
          ParikshanDesktopProcess.start(
            project = project,
            appJarTaskName = extension.appJarTaskName.get(),
            appArgs = extension.appArgs.get(),
            host = extension.host.get(),
            port = extension.port.get(),
            timeoutMs = extension.startupTimeoutMs.get(),
            pollMs = extension.startupPollIntervalMs.get(),
            title = extension.desktopWindowTitle.orNull
          )
        }
      }

    val stopDesktopTask =
      project.tasks.register("stopParikshanDesktopApp") {
        group = "verification"
        doLast {
          ParikshanDesktopProcess.stop()
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
        doLast {
          val outputDir = wasmOutputDir.get().asFile
          ParikshanWasmServer.start(extension.wasmServerPort.get(), outputDir)
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
    val sessionToken = project.providers.gradleProperty("parikshan.token").getOrNull() ?: UUID.randomUUID().toString()
    val isE2ERequested =
      project.gradle.startParameter.taskNames.any { it.contains("e2e", ignoreCase = true) } ||
        project.hasProperty("parikshan.e2e.active")
    var prepareIosBootSourceTask: TaskProvider<Task>? = null

    if (isE2ERequested) {
      project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        prepareIosBootSourceTask =
          project.registerParikshanIosBootSource(
            iosProjectDir = iosProjectDir,
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

      prepareWasmAssetsTask.configure {
        dependsOn(wasmDistributionTaskName)
        project.tasks.findByName("wasmJsProcessResources")?.let { dependsOn(it) }
        doLast {
          val output = wasmOutputDir.get().asFile
          output.deleteRecursively()
          output.mkdirs()
          val distDir = if (wasmDevDir.get().asFile.exists()) wasmDevDir.get().asFile else wasmProdDir.get().asFile
          distDir.copyRecursively(output, overwrite = true)
          val indexHtml = File(output, "index.html")
          if (!indexHtml.exists()) {
            val srcIndex = File(wasmResourcesDir.get().asFile, "index.html")
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
        configureE2eHostTestExecution(
          hostTestTask = hostTestTask.get(),
          e2eTestClasses = e2eTestClasses,
          target = "Desktop",
          logger = project.logger
        )
        systemProperty("parikshan.host", extension.host.get())
        systemProperty("parikshan.port", extension.port.get().toString())
        systemProperty("parikshan.target", "desktop")
        systemProperty("parikshan.token", sessionToken)
      }

      project.tasks.register<Test>("e2eWasmTest") {
        group = "verification"
        dependsOn(installPlaywrightTask, startWasmTask)
        finalizedBy(stopWasmTask)
        configureE2eHostTestExecution(
          hostTestTask = hostTestTask.get(),
          e2eTestClasses = e2eTestClasses,
          target = "Wasm",
          logger = project.logger
        )
        systemProperty("parikshan.target", "wasm")
        systemProperty("parikshan.token", sessionToken)
        systemProperty("parikshan.wasm.url", "http://127.0.0.1:${extension.wasmServerPort.get()}")
      }

      // --- iOS E2E Test Task ---

      fun resolveIosRuntimeProperty(name: String): String? =
        project.providers.gradleProperty(name).orNull ?: System.getProperty(name)

      val iosDevice = resolveIosRuntimeProperty("parikshan.ios.device") ?: "iPhone 16"
      val iosPort = resolveIosRuntimeProperty("parikshan.ios.port")?.toIntOrNull() ?: 9878
      val iosBundleId = resolveIosRuntimeProperty("parikshan.ios.bundleId") ?: "sample.app.ios"
      val iosXcodeProject = resolveIosRuntimeProperty("parikshan.ios.xcodeProject")
        ?: "${project.projectDir}/../iosApp/iosApp.xcodeproj"
      val iosXcodeScheme = resolveIosRuntimeProperty("parikshan.ios.xcodeScheme") ?: "iosApp"
      val iosDerivedData = project.layout.buildDirectory.dir("parikshan/ios-build").get().asFile

      var iosSimulatorUdid: String? = null

      val stopIosAppTask = project.tasks.register("stopIosApp") {
        group = "verification"
        doLast {
          iosSimulatorUdid?.let { udid ->
            ProcessBuilder("xcrun", "simctl", "terminate", udid, iosBundleId).start().waitFor()
            iosLogger.lifecycle("Parikshan iOS: App terminated")
          }
        }
      }

      val startIosAppTask = project.tasks.register("startIosApp") {
        group = "verification"
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
          
          val pbxprojFile = File(generatedIosAppDir, "${File(iosXcodeProject).name}/project.pbxproj")
          if (pbxprojFile.exists()) {
              var pbxText = pbxprojFile.readText()
              val absoluteSampleDir = iosProjectDir.parentFile.absolutePath
              val absoluteGradlew = File(iosRootDir, "gradlew").absolutePath
              val gradleCmd = "$absoluteGradlew -p ${iosRootDir.absolutePath} --no-configuration-cache -Pparikshan.e2e.active=true -Pparikshan.token=$sessionToken ${project.path}:embedAndSignAppleFrameworkForXcode"
              val oldScript = "cd \\\"\$SRCROOT/..\\\"\\n./../gradlew :sample:composeApp:embedAndSignAppleFrameworkForXcode"
              val newScript = "cd \\\"$absoluteSampleDir\\\"\\n$gradleCmd"
              pbxText = pbxText.replace(oldScript, newScript)
              pbxprojFile.writeText(pbxText)
          }

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

          iosLogger.lifecycle("Parikshan iOS: Launching app...")
          ProcessBuilder("xcrun", "simctl", "terminate", simulator.udid, iosBundleId).start().waitFor()
          ProcessBuilder("xcrun", "simctl", "install", simulator.udid, appBundle.absolutePath).start().waitFor()
          ProcessBuilder("xcrun", "simctl", "launch", simulator.udid, iosBundleId).apply {
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
          hostTestTask = hostTestTask.get(),
          e2eTestClasses = e2eTestClasses,
          target = "iOS",
          logger = project.logger
        )
        systemProperty("parikshan.target", "ios")
        systemProperty("parikshan.host", "127.0.0.1")
        systemProperty("parikshan.port", iosPort.toString())
        systemProperty("parikshan.token", sessionToken)
      }

      // --- Android E2E Test Task ---

      val androidProjectDir = project.projectDir
      val androidApplicationId = ParikshanAndroidRecorder.resolveAndroidApplicationId(project)
      val androidLogger = project.logger

      val stopAndroidAppTask = project.tasks.register("stopParikshanAndroidApp") {
          group = "verification"
          doLast {
            val serial = ParikshanAndroidRecorder.resolveDeviceSerial(androidLogger, androidProjectDir, null)
            ProcessBuilder("adb", "-s", serial, "forward", "--remove", "tcp:9879").start().waitFor()
            ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", androidApplicationId).start().waitFor()
          }
        }

      val startAndroidAppTask = project.tasks.register("startParikshanAndroidApp") {
        group = "verification"
        dependsOn("installDebug", "installDebugAndroidTest")
        doLast {
          val serial = ParikshanAndroidRecorder.resolveDeviceSerial(androidLogger, androidProjectDir, null)
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
          hostTestTask = hostTestTask.get(),
          e2eTestClasses = e2eTestClasses,
          target = "Android",
          logger = project.logger
        )
        systemProperty("parikshan.target", "android")
        systemProperty("parikshan.host", "127.0.0.1")
        systemProperty("parikshan.port", "9879")
        systemProperty("parikshan.token", sessionToken)
        
        doLast {
           val serial = ParikshanAndroidRecorder.resolveDeviceSerial(androidLogger, androidProjectDir, null)
           val devicePath = "/sdcard/parikshan-screenshot.png"
           val hostPath = "build/parikshan/screenshots/android-failure.png"
           ProcessBuilder("adb", "-s", serial, "pull", devicePath, hostPath).start().waitFor()
        }
      }

      project.tasks.withType(Test::class.java).configureEach {
        if (name !in setOf("e2eDesktopTest", "e2eWasmTest", "e2eIosTest", "e2eAndroidTest")) {
          filter { e2eTestClasses.forEach { excludeTestsMatching(it) } }
        }
      }
    }
  }
}

private object ParikshanWasmServer {
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
            } else ex.sendResponseHeaders(404, 0)
            ex.close()
        }
        s.start(); server = s
    }
    fun stop() { server?.stop(0); server = null }
}

private data class IosSimulatorDevice(val name: String, val udid: String, val runtime: String, val isBooted: Boolean)

private fun resolveIosSimulatorDevice(requested: String, workingDir: File): IosSimulatorDevice {
  val output = ProcessBuilder("xcrun", "simctl", "list", "devices", "available").directory(workingDir).start().inputStream.bufferedReader().readText()
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
  return devices.firstOrNull { it.isBooted } ?: devices.firstOrNull() ?: throw GradleException("No simulator")
}

private object ParikshanAndroidRecorder {
  fun resolveDeviceSerial(logger: Logger, workingDir: File, explicit: String?): String {
    val output = ProcessBuilder("adb", "devices").directory(workingDir).start().inputStream.bufferedReader().readText()
    return output.lineSequence().drop(1).firstOrNull { it.isNotBlank() }?.split(Regex("\\s+"))?.get(0) ?: throw GradleException("No device")
  }
  fun resolveAndroidApplicationId(project: Project): String? {
    val android = project.extensions.findByName("android") ?: return null
    val defaultConfig = android.javaClass.methods.firstOrNull { it.name == "getDefaultConfig" }?.invoke(android) ?: return null
    return defaultConfig.javaClass.methods.firstOrNull { it.name == "getApplicationId" }?.invoke(defaultConfig) as? String
  }
}

private object ParikshanDesktopProcess {
  private var process: Process? = null
  fun start(project: Project, appJarTaskName: String, appArgs: List<String>, host: String, port: Int, timeoutMs: Long, pollMs: Long, title: String?) {
    stop()
    val jar = project.tasks.findByName(appJarTaskName)?.outputs?.files?.firstOrNull { it.extension == "jar" } ?: throw GradleException("No jar")
    val mainClass = JarFile(jar).manifest.mainAttributes.getValue("Main-Class")
    process = ProcessBuilder(listOf(System.getProperty("java.home") + "/bin/java", "-Dparikshan.host=$host", "-Dparikshan.port=$port", "-Dparikshan.desktop.appMainClass=$mainClass", "-cp", jar.absolutePath, "io.github.aryapreetam.parikshan.server.ParikshanDesktopLauncher")).start()
  }
  fun stop() { process?.destroy(); process = null }
}

private fun Project.configureParikshanDependencies(isE2EActive: Boolean) {
  // Test DSL is always available in commonTest
  addParikshanDependency("commonTestImplementation", ":lib", "io.github.aryapreetam:parikshan:0.0.1")
  addParikshanDependency("commonTestImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client:0.0.1")

  // The client engine is only injected into the production binary during active E2E tasks.
  // This prevents production pollution for all other builds.
  if (isE2EActive) {
      addParikshanDependency("commonMainImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client:0.0.1")
  }
}

private fun Project.addParikshanDependency(config: String, path: String, maven: String) {
  val dep = rootProject.findProject(path)?.let { dependencies.project(mapOf("path" to it.path)) } ?: maven
  val configuration = configurations.findByName(config) ?: return
  dependencies.add(config, dep)
}

private fun Project.resolveHostTestTaskName(override: String?): String = override ?: "jvmTest"
private fun Project.resolveWasmDistributionTaskName(override: String?): String = override ?: "wasmJsBrowserDevelopmentWebpack"

private fun Project.discoverE2eTestClasses(): List<String> {
  val classes = mutableListOf<String>()
  layout.projectDirectory.dir("src").asFile.walkTopDown().filter { it.name.endsWith("Test.kt") || it.name.endsWith("Scenarios.kt") }.forEach { file ->
    val text = file.readText()
    if (text.contains("e2eTest")) {
      val pkg = Regex("""package\s+([a-zA-Z0-9_.]+)""").find(text)?.groupValues?.get(1) ?: ""
      val cls = Regex("""(?:class|object)\s+([a-zA-Z0-9_]+)""").find(text)?.groupValues?.get(1) ?: file.nameWithoutExtension
      classes.add(if (pkg.isNotEmpty()) "$pkg.$cls" else cls)
    }
  }
  return classes
}

private fun Test.configureE2eHostTestExecution(
  hostTestTask: Test,
  e2eTestClasses: List<String>,
  target: String,
  logger: Logger
) {
  if (e2eTestClasses.isEmpty()) {
    throw GradleException("No E2E test classes discovered in src/commonTest")
  }

  outputs.upToDateWhen { false }
  testClassesDirs = hostTestTask.testClassesDirs
  classpath = hostTestTask.classpath
  dependsOn(hostTestTask.testClassesDirs.buildDependencies)

  filter {
    isFailOnNoMatchingTests = true
    e2eTestClasses.forEach { includeTestsMatching(it) }
  }

  doFirst {
    val existingClassDirs = testClassesDirs.files.filter { it.exists() }
    if (existingClassDirs.isEmpty()) {
      throw GradleException(
        "Parikshan $target: host test classes were not compiled. Expected output from '${hostTestTask.name}'."
      )
    }
    logger.lifecycle("Parikshan $target: running E2E test classes ${e2eTestClasses.joinToString()}")
  }
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
