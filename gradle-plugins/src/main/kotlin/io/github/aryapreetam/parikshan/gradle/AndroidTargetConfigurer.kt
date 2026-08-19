package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import java.io.File

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal object AndroidTargetConfigurer {
  fun configure(
    project: Project,
    extension: ParikshanExtension,
    sessionToken: String,
    isE2EActive: Boolean,
    isBackgroundRequested: Boolean,
    isVideoRequested: Boolean,
    e2eTestClasses: List<String>,
    hostTestTask: org.gradle.api.tasks.TaskProvider<Test>
  ) {
    val androidProjectDirVal = project.projectDir
    
    val mergedManifestDirProvider = AndroidComponentsHelper.getMergedManifestDirectory(project)

    val overrideLauncher = extension.androidLaunchActivityClassName.orNull
      ?: project.providers.gradleProperty("parikshan.androidLaunchActivityClassName")
           .orElse(project.providers.systemProperty("parikshan.androidLaunchActivityClassName"))
           .orNull

    val androidApplicationIdProvider = project.objects.property(String::class.java)

    project.gradle.projectsEvaluated {
      val directId = AndroidRecorder.resolveAndroidApplicationId(project)
      if (directId != null) {
        androidApplicationIdProvider.set(directId)
      } else {
        val manifestDir = mergedManifestDirProvider.orNull?.asFile
        if (manifestDir != null) {
          val manifestFile = File(manifestDir, "AndroidManifest.xml")
          val parsed = parseAndroidManifest(manifestFile, null, overrideLauncher)
          if (parsed.packageName != null) {
            androidApplicationIdProvider.set(parsed.packageName)
          }
        }
      }
    }

    fun resolveAndroidRuntimeProperty(name: String): String? =
      project.providers.gradleProperty(name).orElse(project.providers.systemProperty(name)).orNull

    val androidSerialVal = resolveAndroidRuntimeProperty("device")
      ?: resolveAndroidRuntimeProperty("serial")
      ?: resolveAndroidRuntimeProperty("parikshan.android.serial")
      ?: System.getenv("PARIKSHAN_ANDROID_SERIAL")
    val sessionTokenVal = sessionToken

    val portProvider = project.providers.gradleProperty("parikshan.port").orElse("9879")

    val projDirProvider = project.layout.projectDirectory

    val androidPreflightTask = project.tasks.register("parikshanAndroidPreflight", ParikshanPreflightTask::class.java)
    androidPreflightTask.configure {
      group = "verification"
      targetName.set("android")
      deviceOverride.set(androidSerialVal.orEmpty())
      projectDir.set(projDirProvider.asFile)
    }

    if (isE2EActive) {
      project.tasks.matching {
        it.name in setOf("preBuild", "preDebugBuild", "preDebugAndroidTestBuild")
      }.configureEach {
        dependsOn(androidPreflightTask)
      }
    }

    val stopAndroidAppTask = project.tasks.register("stopParikshanAndroidApp", ParikshanStopAndroidTask::class.java)
    stopAndroidAppTask.configure {
      group = "verification"
      androidSerial.set(androidSerialVal)
      port.set(portProvider)
      applicationId.set(androidApplicationIdProvider)
      projectDir.set(projDirProvider.asFile)
    }

    val startAndroidAppTask = project.tasks.register("startParikshanAndroidApp", ParikshanStartAndroidTask::class.java)
    startAndroidAppTask.configure {
      group = "verification"
      val appProject = project.findAndroidAppProject() ?: project
      val installTask = if (appProject == project) "installDebug" else "${appProject.path}:installDebug"
      val testInstallTask = if (appProject == project) "installDebugAndroidTest" else "${appProject.path}:installDebugAndroidTest"

      dependsOn(androidPreflightTask, installTask, testInstallTask)
      this.androidSerial.set(androidSerialVal)
      this.port.set(portProvider)
      this.sessionToken.set(sessionTokenVal)
      this.applicationId.set(androidApplicationIdProvider)
      this.launcherActivity.set(overrideLauncher.orEmpty())
      this.projectDir.set(projDirProvider.asFile)
    }

    project.registerE2eTestWithReport("e2eAndroidTest", "Android") {
      group = "verification"
      dependsOn(startAndroidAppTask)
      finalizedBy("stopParikshanAndroidApp")
      configureE2eHostTestExecution(
        hostTestTaskProvider = hostTestTask,
        e2eTestClasses = e2eTestClasses,
        target = "Android",
        logger = logger
      )
      systemProperty("parikshan.target", "android")
      systemProperty("parikshan.host", "127.0.0.1")
      systemProperty("parikshan.port", "9879")
      systemProperty("parikshan.token", sessionTokenVal)
      doFirst {
        val serial = AndroidRecorder.resolveDeviceSerial(logger, androidProjectDirVal, androidSerialVal)
        systemProperty("parikshan.android.serial", serial)
      }
      doLast {
        val serial = AndroidRecorder.resolveDeviceSerial(logger, androidProjectDirVal, androidSerialVal)
        val devicePath = "/sdcard/parikshan-screenshot.png"
        val hostPath = "build/parikshan/screenshots/android-failure.png"
        ProcessBuilder("adb", "-s", serial, "pull", devicePath, hostPath).start().waitFor()
      }
    }
  }

  internal fun parseAndroidManifest(manifestFile: File, logger: Logger?, overrideActivity: String?): ParsedManifest {
    if (!manifestFile.exists()) {
      logger?.debug("Parikshan: Manifest file does not exist at ${manifestFile.absolutePath}")
      return ParsedManifest(packageName = null, launcherActivity = overrideActivity)
    }

    try {
      val factory = DocumentBuilderFactory.newInstance()
      val builder = factory.newDocumentBuilder()
      val doc = builder.parse(manifestFile)
      doc.documentElement.normalize()

      val manifestElement = doc.documentElement
      val packageName = manifestElement.getAttribute("package").takeIf { it.isNotBlank() }

      if (overrideActivity != null && overrideActivity.isNotBlank()) {
        return ParsedManifest(packageName = packageName, launcherActivity = overrideActivity)
      }

      val launcherActivities = mutableListOf<String>()
      val activityNodes = doc.getElementsByTagName("activity")
      for (i in 0 until activityNodes.length) {
        val activityEl = activityNodes.item(i) as Element
        val activityName = activityEl.getAttribute("android:name")
        if (activityName.isBlank()) continue

        val intentFilters = activityEl.getElementsByTagName("intent-filter")
        for (j in 0 until intentFilters.length) {
          val filterEl = intentFilters.item(j) as Element
          var isMain = false
          var isLauncher = false

          val actions = filterEl.getElementsByTagName("action")
          for (k in 0 until actions.length) {
            val actionEl = actions.item(k) as Element
            if (actionEl.getAttribute("android:name") == "android.intent.action.MAIN") {
              isMain = true
              break
            }
          }

          val categories = filterEl.getElementsByTagName("category")
          for (k in 0 until categories.length) {
            val categoryEl = categories.item(k) as Element
            if (categoryEl.getAttribute("android:name") == "android.intent.category.LAUNCHER") {
              isLauncher = true
              break
            }
          }

          if (isMain && isLauncher) {
            launcherActivities.add(activityName)
            break
          }
        }
      }

      // Also check activity-alias
      val aliasNodes = doc.getElementsByTagName("activity-alias")
      for (i in 0 until aliasNodes.length) {
        val aliasEl = aliasNodes.item(i) as Element
        val aliasName = aliasEl.getAttribute("android:name")
        if (aliasName.isBlank()) continue

        val intentFilters = aliasEl.getElementsByTagName("intent-filter")
        for (j in 0 until intentFilters.length) {
          val filterEl = intentFilters.item(j) as Element
          var isMain = false
          var isLauncher = false

          val actions = filterEl.getElementsByTagName("action")
          for (k in 0 until actions.length) {
            val actionEl = actions.item(k) as Element
            if (actionEl.getAttribute("android:name") == "android.intent.action.MAIN") {
              isMain = true
              break
            }
          }

          val categories = filterEl.getElementsByTagName("category")
          for (k in 0 until categories.length) {
            val categoryEl = categories.item(k) as Element
            if (categoryEl.getAttribute("android:name") == "android.intent.category.LAUNCHER") {
              isLauncher = true
              break
            }
          }

          if (isMain && isLauncher) {
            launcherActivities.add(aliasName)
            break
          }
        }
      }

      val resolvedLauncher = when {
        launcherActivities.isEmpty() -> null
        launcherActivities.size == 1 -> launcherActivities.first()
        else -> {
          logger?.lifecycle("Parikshan WARNING: Multiple launcher activities found in AndroidManifest.xml: $launcherActivities. Falling back to first one.")
          launcherActivities.first()
        }
      }

      val fullyQualifiedLauncher = if (resolvedLauncher != null && packageName != null) {
        if (resolvedLauncher.startsWith(".")) {
          "$packageName$resolvedLauncher"
        } else if (!resolvedLauncher.contains(".")) {
          "$packageName.$resolvedLauncher"
        } else {
          resolvedLauncher
        }
      } else {
        resolvedLauncher
      }

      return ParsedManifest(packageName = packageName, launcherActivity = fullyQualifiedLauncher)
    } catch (e: Exception) {
      logger?.warn("Parikshan: Failed to parse merged AndroidManifest.xml", e)
      return ParsedManifest(packageName = null, launcherActivity = overrideActivity)
    }
  }

  internal data class ParsedManifest(val packageName: String?, val launcherActivity: String?)

  private data class AndroidDevice(val serial: String, val state: String)

  internal object AndroidRecorder {
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

      val devices = output.lineSequence()
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
            "Specify a target using `--android-device=<serial>`, `--device=<serial>`, or `-Pparikshan.android.serial=<serial>`."
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
}
