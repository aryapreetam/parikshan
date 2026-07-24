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

    val projDirProvider = project.layout.projectDirectory
    val buildDirProvider = project.layout.buildDirectory

    val iosPreflightTask = project.tasks.register("parikshanIosPreflight", ParikshanPreflightTask::class.java)
    iosPreflightTask.configure {
      group = "verification"
      targetName.set("ios")
      deviceOverride.set(iosDeviceVal)
      projectDir.set(projDirProvider.asFile)
    }

    val stopIosAppTask = project.tasks.register("stopIosApp", ParikshanStopIosTask::class.java)
    stopIosAppTask.configure {
      group = "verification"
      bundleId.set(iosBundleIdProvider)
    }

    val startIosAppTask = project.tasks.register("startIosApp", ParikshanStartIosTask::class.java)
    startIosAppTask.configure {
      group = "verification"
      dependsOn(iosPreflightTask)
      prepareIosBootSourceTask?.let { dependsOn(it) }
      outputs.upToDateWhen { false }

      this.iosDevice.set(iosDeviceVal)
      this.xcodeProject.set(iosXcodeProjectVal)
      this.xcodeScheme.set(iosXcodeSchemeVal)
      this.bundleId.set(iosBundleIdProvider)
      this.sessionToken.set(sessionTokenVal)
      this.port.set(iosPortVal)
      this.xcodeTimeout.set(xcodebuildTimeoutProvider)
      this.projectDir.set(projDirProvider.asFile)
      this.rootDir.set(projDirProvider.asFile)
      this.derivedDataDir.set(buildDirProvider.dir("parikshan/ios-build").get().asFile)
      this.buildDir.set(buildDirProvider.get().asFile)
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
