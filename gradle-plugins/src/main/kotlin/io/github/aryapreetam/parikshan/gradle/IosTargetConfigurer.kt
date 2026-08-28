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
      project.logger.info("Parikshan iOS: Extracted bundle ID: $extracted")
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
      simulatorUdid.set(project.provider {
        resolveIosRuntimeProperty("device") ?: resolveIosRuntimeProperty("serial") ?: resolveIosRuntimeProperty("parikshan.ios.device") ?: "booted"
      })
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
      this.rootDir.set(iosRootDirVal)
      this.derivedDataDir.set(buildDirProvider.dir("parikshan/ios-build").get().asFile)
      this.buildDir.set(buildDirProvider.get().asFile)
    }

    project.registerE2eTestWithReport("e2eIosTest", "iOS") {
      group = "verification"
      dependsOn(startIosAppTask)
      finalizedBy(stopIosAppTask)
      configureE2eHostTestExecution(
        e2eTestClasses = e2eTestClasses,
        target = "iOS",
        logger = logger
      )
      systemProperty("parikshan.target", "ios")
      systemProperty("parikshan.host", "127.0.0.1")
      systemProperty("parikshan.port", iosPortVal.toString())
      systemProperty("parikshan.token", sessionTokenVal)
      val videosDir = project.layout.buildDirectory.dir("parikshan/videos/ios").get().asFile.absolutePath
      systemProperty("parikshan.video.outputDir", videosDir)
      if (isVideoRequested) {
        systemProperty("parikshan.video.enabled", "true")
      }
      val fps = project.findProperty("parikshan.video.fps")?.toString()
      if (!fps.isNullOrBlank()) {
        systemProperty("parikshan.video.fps", fps)
      }
      val stepDelay = project.findProperty("parikshan.video.stepDelayMs")?.toString()
      if (!stepDelay.isNullOrBlank()) {
        systemProperty("parikshan.video.stepDelayMs", stepDelay)
      }
      val postRoll = project.findProperty("parikshan.video.postRollMs")?.toString()
      if (!postRoll.isNullOrBlank()) {
        systemProperty("parikshan.video.postRollMs", postRoll)
      }
      val granularity = project.findProperty("parikshan.video.granularity")?.toString()
      if (!granularity.isNullOrBlank()) {
        systemProperty("parikshan.video.granularity", granularity)
      }
      val iosPortFileVal = project.layout.buildDirectory.file("parikshan/ios-port.txt").get().asFile
      doFirst {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        if (!osName.contains("mac")) {
          throw GradleException(
            "Parikshan iOS: Running iOS E2E tests requires macOS host OS with Xcode tools installed. " +
            "Current OS: ${System.getProperty("os.name")}"
          )
        }
        val simulator = resolveIosSimulatorDevice(iosDeviceVal, iosProjectDirVal)
        systemProperty("parikshan.ios.udid", simulator.udid)
        systemProperty("parikshan.ios.bundleId", iosBundleIdProvider.get())

        val portFile = iosPortFileVal
        if (portFile.exists()) {
          val actualPort = portFile.readText().trim()
          if (actualPort.isNotEmpty()) {
            systemProperty("parikshan.port", actualPort)
          }
        }
      }
    }
  }

  private data class IosSimulatorDevice(val name: String, val udid: String, val runtime: String, val isBooted: Boolean)

  private fun resolveIosSimulatorDevice(requested: String, workingDir: File): IosSimulatorDevice {
    val activeSdkVersion = ParikshanStartIosTask.IosSimulatorResolver.getActiveIosSdkVersion()
    val available = ParikshanStartIosTask.IosSimulatorResolver.listAvailableIosDevices(workingDir)
    val best = ParikshanStartIosTask.IosSimulatorResolver.selectBestDevice(requested, available, activeSdkVersion)
    return IosSimulatorDevice(best.name, best.udid, best.runtimeName, best.isBooted)
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
