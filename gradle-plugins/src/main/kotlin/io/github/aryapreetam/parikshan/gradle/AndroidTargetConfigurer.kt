package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import java.io.File

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
    val androidApplicationIdVal = AndroidRecorder.resolveAndroidApplicationId(project)
      ?: throw GradleException(
        "Parikshan Android: Could not resolve the Android applicationId. " +
          "Set defaultConfig.applicationId in the Android application module."
      )

    fun resolveAndroidRuntimeProperty(name: String): String? =
      project.providers.gradleProperty(name).orElse(project.providers.systemProperty(name)).orNull

    val androidSerialVal = resolveAndroidRuntimeProperty("device")
      ?: resolveAndroidRuntimeProperty("serial")
      ?: resolveAndroidRuntimeProperty("parikshan.android.serial")
      ?: System.getenv("PARIKSHAN_ANDROID_SERIAL")
    val sessionTokenVal = sessionToken

    val androidPreflightTask = project.tasks.register("parikshanAndroidPreflight") {
      group = "verification"
      doLast {
        val serial = AndroidRecorder.resolveDeviceSerial(logger, androidProjectDirVal, androidSerialVal)
        logger.lifecycle("Parikshan Android: Found connected device/emulator '$serial'")
      }
    }

    if (isE2EActive) {
      project.tasks.matching {
        it.name in setOf("preBuild", "preDebugBuild", "preDebugAndroidTestBuild")
      }.configureEach {
        dependsOn(androidPreflightTask)
      }
    }

    project.tasks.register("stopParikshanAndroidApp") {
      group = "verification"
      doLast {
        val serial = AndroidRecorder.resolveDeviceSerial(logger, androidProjectDirVal, androidSerialVal)
        ProcessBuilder("adb", "-s", serial, "forward", "--remove", "tcp:9879").start().waitFor()
        ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", androidApplicationIdVal).start().waitFor()
      }
    }

    val startAndroidAppTask = project.tasks.register("startParikshanAndroidApp") {
      group = "verification"
      val appProject = project.findAndroidAppProject() ?: project
      val installTask = if (appProject == project) "installDebug" else "${appProject.path}:installDebug"
      val testInstallTask = if (appProject == project) "installDebugAndroidTest" else "${appProject.path}:installDebugAndroidTest"

      dependsOn(androidPreflightTask, installTask, testInstallTask)
      doLast {
        val serial = AndroidRecorder.resolveDeviceSerial(logger, androidProjectDirVal, androidSerialVal)
        ProcessBuilder("adb", "-s", serial, "shell", "am", "force-stop", androidApplicationIdVal).start().waitFor()
        ProcessBuilder("adb", "-s", serial, "forward", "tcp:9879", "tcp:9879").start().waitFor()
        val testPackage = "$androidApplicationIdVal.test"
        logger.lifecycle("Parikshan Android: Starting instrumentation...")
        ProcessBuilder("adb", "-s", serial, "shell", "am", "instrument", "-w", "-e", "class", "io.github.aryapreetam.parikshan.ParikshanAndroidRunner", "-e", "parikshan_token", sessionTokenVal, "$testPackage/androidx.test.runner.AndroidJUnitRunner").start()
      }
    }

    project.registerE2eTestWithReport("e2eAndroidTest", "Android") {
      group = "verification"
      dependsOn(startAndroidAppTask)
      finalizedBy("stopParikshanAndroidApp")
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
}
