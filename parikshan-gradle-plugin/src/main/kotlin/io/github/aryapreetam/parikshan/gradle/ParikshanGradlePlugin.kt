package io.github.aryapreetam.parikshan.gradle

import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import javax.inject.Inject
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
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

    project.afterEvaluate {
      project.configureParikshanDependencies()

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
        outputs.upToDateWhen { false }
        testClassesDirs = hostTestTask.get().testClassesDirs
        classpath = hostTestTask.get().classpath
        systemProperty("parikshan.host", extension.host.get())
        systemProperty("parikshan.port", extension.port.get().toString())
        systemProperty("parikshan.target", "desktop")
        filter { e2eTestClasses.forEach { includeTestsMatching(it) } }
      }

      project.tasks.register<Test>("e2eWasmTest") {
          group = "verification"
          dependsOn(installPlaywrightTask, startWasmTask)
          finalizedBy(stopWasmTask)
          outputs.upToDateWhen { false }
          testClassesDirs = hostTestTask.get().testClassesDirs
          classpath = hostTestTask.get().classpath
          systemProperty("parikshan.target", "wasm")
          systemProperty("parikshan.wasm.url", "http://127.0.0.1:${extension.wasmServerPort.get()}")
          filter { e2eTestClasses.forEach { includeTestsMatching(it) } }
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

      // Resolve at configuration time for Cache safety
      val iosProjectDir = project.projectDir
      val iosLayout = project.layout
      val iosLogger = project.logger

      var iosSimulatorUdid: String? = null

      val startIosAppTask = project.tasks.register("startIosApp") {
        group = "verification"
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

          try {
            val iosMainDir = File(iosProjectDir, "src/iosMain/kotlin")
            iosLogger.lifecycle("Parikshan iOS: Searching for entry point in ${iosMainDir.absolutePath}...")
            
            val mainFile = if (iosMainDir.exists()) {
                iosMainDir.walkTopDown().filter { it.extension == "kt" }.firstOrNull { it.readText().contains("ComposeUIViewController") }
            } else null
            
            val backupFile = mainFile?.let { File(it.absolutePath + ".bak") }

            if (mainFile != null && backupFile != null) {
              iosLogger.lifecycle("Parikshan iOS: Found entry point at ${mainFile.absolutePath}")
              iosLogger.lifecycle("Parikshan iOS: Injecting test server into ${mainFile.name}...")
              mainFile.copyTo(backupFile, overwrite = true)
              var text = mainFile.readText()
              if (text.contains("ComposeUIViewController")) {
                text = "import io.github.aryapreetam.parikshan.ParikshanUIViewController\n" + text
                text = text.replace("= ComposeUIViewController", "= ParikshanUIViewController")
                text = text.replace("return ComposeUIViewController", "return ParikshanUIViewController")
                mainFile.writeText(text)
                iosLogger.lifecycle("Parikshan iOS: Injection successful.")
              }
            }

            // Build app - DEEPLY CLEAN build to ensure injection is picked up
            iosLogger.lifecycle("Parikshan iOS: Cleaning and building via xcodebuild...")
            iosDerivedData.deleteRecursively()
            iosDerivedData.mkdirs()
            val appBuildProducts = File(iosDerivedData, "Build/Products/Debug-iphonesimulator")
            appBuildProducts.mkdirs()
            
            val buildResult = ProcessBuilder(
              "xcodebuild", "build", "-project", File(iosXcodeProject).absolutePath,
              "-scheme", iosXcodeScheme, "-configuration", "Debug",
              "-destination", "platform=iOS Simulator,id=${simulator.udid}",
              "-derivedDataPath", iosDerivedData.absolutePath,
              "CONFIGURATION_BUILD_DIR=${appBuildProducts.absolutePath}"
            ).inheritIO().start().waitFor()
            
            if (buildResult != 0) {
              throw GradleException("xcodebuild failed with exit code $buildResult")
            }
            
            val appBundle = appBuildProducts.listFiles()?.firstOrNull { it.name.endsWith(".app") } ?: throw GradleException("No .app bundle found in ${appBuildProducts.absolutePath}")

            // Install and launch
            iosLogger.lifecycle("Parikshan iOS: Installing ${appBundle.name}...")
            val installResult = ProcessBuilder("xcrun", "simctl", "install", simulator.udid, appBundle.absolutePath).start().waitFor()
            if (installResult != 0) throw GradleException("simctl install failed with exit code $installResult")

            iosLogger.lifecycle("Parikshan iOS: Launching app...")
            val launchResult = ProcessBuilder("xcrun", "simctl", "launch", simulator.udid, iosBundleId).start().waitFor()
            if (launchResult != 0) throw GradleException("simctl launch failed with exit code $launchResult")

          } finally {
            val iosMainDir = File(iosProjectDir, "src/iosMain/kotlin")
            val mainFile = if (iosMainDir.exists()) {
               iosMainDir.walkTopDown().filter { it.extension == "kt" }.firstOrNull { it.readText().contains("ParikshanUIViewController") }
            } else null
            val backupFile = mainFile?.let { File(it.absolutePath + ".bak") }
            if (mainFile != null && backupFile?.exists() == true) {
              iosLogger.lifecycle("Parikshan iOS: Reverting injection in ${mainFile.name}...")
              backupFile.copyTo(mainFile, overwrite = true)
              backupFile.delete()
            }
          }

          // Wait for server
          iosLogger.lifecycle("Parikshan iOS: Waiting for in-app server on port $iosPort...")
          val deadline = System.currentTimeMillis() + 60_000
          var serverReady = false
          while (System.currentTimeMillis() <= deadline) {
            if (runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", iosPort), 500) }; true }.getOrDefault(false)) {
              serverReady = true; break
            }
            Thread.sleep(500)
          }
          if (!serverReady) throw GradleException("Parikshan iOS server did not start on port $iosPort")
          iosLogger.lifecycle("Parikshan iOS: Server ready on port $iosPort")
        }
      }

      val stopIosAppTask = project.tasks.register("stopIosApp") {
        group = "verification"
        doLast {
          iosSimulatorUdid?.let { udid ->
            ProcessBuilder("xcrun", "simctl", "terminate", udid, iosBundleId).start().waitFor()
            iosLogger.lifecycle("Parikshan iOS: App terminated")
          }
        }
      }

      project.tasks.register<Test>("e2eIosTest") {
        group = "verification"
        dependsOn(startIosAppTask)
        finalizedBy(stopIosAppTask)
        outputs.upToDateWhen { false }
        testClassesDirs = hostTestTask.get().testClassesDirs
        classpath = hostTestTask.get().classpath
        systemProperty("parikshan.target", "ios")
        systemProperty("parikshan.host", "127.0.0.1")
        systemProperty("parikshan.port", iosPort.toString())
        filter { e2eTestClasses.forEach { includeTestsMatching(it) } }
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
          ProcessBuilder("adb", "-s", serial, "shell", "am", "instrument", "-w", "-e", "class", "io.github.aryapreetam.parikshan.ParikshanAndroidRunner", "$testPackage/androidx.test.runner.AndroidJUnitRunner").start()
        }
      }

      project.tasks.register<Test>("e2eAndroidTest") {
        group = "verification"
        dependsOn(startAndroidAppTask)
        finalizedBy(stopAndroidAppTask)
        outputs.upToDateWhen { false }
        testClassesDirs = hostTestTask.get().testClassesDirs
        classpath = hostTestTask.get().classpath
        systemProperty("parikshan.target", "android")
        systemProperty("parikshan.host", "127.0.0.1")
        systemProperty("parikshan.port", "9879")
        filter { e2eTestClasses.forEach { includeTestsMatching(it) } }
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
                ex.sendResponseHeaders(200, file.length())
                file.inputStream().use { it.copyTo(ex.responseBody) }
            } else ex.sendResponseHeaders(404, 0)
            ex.close()
        }
        s.start()
        server = s
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

private fun Project.configureParikshanDependencies() {
  addParikshanDependency("commonMainImplementation", ":lib", "io.github.aryapreetam:parikshan:0.0.1")
  addParikshanDependency("commonMainImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client:0.0.1")
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
