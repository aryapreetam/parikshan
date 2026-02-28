@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose)
  alias(libs.plugins.android.application)
}

kotlin {
  jvmToolchain(17)

  androidTarget {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
  }
  jvm()
  wasmJs {
    browser()
    binaries.executable()
  }
  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  ).forEach {
    it.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.ui)
      implementation(compose.foundation)
      implementation(compose.material3)
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
      implementation(compose.uiTest)
    }

    androidMain.dependencies {
      implementation(libs.androidx.activityCompose)
    }

    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(project(":lib"))
    }

  }
}

android {
  namespace = "sample.app"
  compileSdk = 35

  defaultConfig {
    minSdk = 21
    targetSdk = 35

    applicationId = "sample.app"
    versionCode = 1
    versionName = "1.0.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

  }
}

dependencies {
  androidTestImplementation("androidx.compose.ui:ui-test-junit4-android:1.9.0")
  debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.0")
}

compose.desktop {
  application {
    mainClass = "MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "sample"
      packageVersion = "1.0.0"
    }
  }
}

val startSampleE2eDesktopApp = tasks.register("startSampleE2eDesktopApp") {
  group = "verification"
  description = "Builds and launches the sample desktop app in --test-mode"
  notCompatibleWithConfigurationCache("Starts and tracks a long-running external desktop process.")
  dependsOn("packageUberJarForCurrentOS")
  val projectDirFile = project.projectDir
  val pidFile = layout.buildDirectory.file("parikshan/sample-app.pid").get().asFile

  doLast {
    val jarDir = layout.buildDirectory.dir("compose/jars").get().asFile
    val appJar =
      jarDir
        .listFiles()
        ?.filter { it.isFile && it.extension == "jar" }
        ?.maxByOrNull { it.lastModified() }
        ?: error("No sample desktop jar found in ${jarDir.absolutePath}")

    val javaExecutable = file("${System.getProperty("java.home")}/bin/java")
    val logFile = layout.buildDirectory.file("parikshan/sample-app.log").get().asFile
    logFile.parentFile.mkdirs()
    if (pidFile.exists()) {
      val existingPid = pidFile.readText().trim().toLongOrNull()
      if (existingPid != null) {
        ProcessHandle.of(existingPid).ifPresent { existing ->
          if (existing.isAlive) {
            existing.destroy()
            Thread.sleep(600)
            if (existing.isAlive) {
              existing.destroyForcibly()
            }
          }
        }
      }
      pidFile.delete()
    }

    val sampleE2eDesktopProcess =
      ProcessBuilder(
        javaExecutable.absolutePath,
        "-jar",
        appJar.absolutePath,
        "--test-mode"
      )
        .directory(projectDirFile)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        .start()

    pidFile.parentFile.mkdirs()
    pidFile.writeText(sampleE2eDesktopProcess.pid().toString())

    val deadline = System.currentTimeMillis() + 60_000L
    while (System.currentTimeMillis() <= deadline) {
      val isReady =
        runCatching {
          val response = CompletableFuture<String>()
          val webSocket =
            HttpClient.newHttpClient()
              .newWebSocketBuilder()
              .connectTimeout(Duration.ofMillis(800))
              .buildAsync(
                URI("ws://127.0.0.1:9877/parikshan"),
                object : WebSocket.Listener {
                  private val payload = StringBuilder()

                  override fun onOpen(webSocket: WebSocket) {
                    webSocket.request(1)
                    webSocket.sendText("""{"type":"ping","id":"startup-ping"}""", true)
                  }

                  override fun onText(
                    webSocket: WebSocket,
                    data: CharSequence,
                    last: Boolean
                  ): CompletionStage<*> {
                    payload.append(data)
                    if (last) {
                      response.complete(payload.toString())
                    }
                    webSocket.request(1)
                    return CompletableFuture.completedFuture(null)
                  }

                  override fun onError(
                    webSocket: WebSocket,
                    error: Throwable
                  ) {
                    response.completeExceptionally(error)
                  }
                }
              )
              .join()

          val payload = response.get(1_200, TimeUnit.MILLISECONDS)
          runCatching { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok").join() }
          payload.contains(""""type":"ok"""")
        }.getOrDefault(false)

      if (isReady) {
        return@doLast
      }
      if (!sampleE2eDesktopProcess.isAlive) {
        pidFile.delete()
        error("Sample app exited before Parikshan server became ready. Check build/parikshan/sample-app.log")
      }
      Thread.sleep(250)
    }

    error("Timed out waiting for Parikshan websocket ping on ws://127.0.0.1:9877/parikshan")
  }
}

val stopSampleE2eDesktopApp = tasks.register("stopSampleE2eDesktopApp") {
  group = "verification"
  description = "Stops sample desktop app launched for E2E tests"
  notCompatibleWithConfigurationCache("Stops the external desktop process started by E2E orchestration.")
  val pidFile = layout.buildDirectory.file("parikshan/sample-app.pid").get().asFile

  doLast {
    if (!pidFile.exists()) {
      return@doLast
    }

    val pid = pidFile.readText().trim().toLongOrNull()
    if (pid != null) {
      ProcessHandle.of(pid).ifPresent { process ->
        process.destroy()
        Thread.sleep(600)
        if (process.isAlive) {
          process.destroyForcibly()
        }
      }
    }
    pidFile.delete()
  }
}

tasks.named<Test>("jvmTest") {
  mustRunAfter(startSampleE2eDesktopApp)
  val videoEnabled = providers.gradleProperty("parikshan.video").orElse("false").get()
  val videoFps = providers.gradleProperty("parikshan.video.fps").orElse("1").get()
  val videoShowCursor = providers.gradleProperty("parikshan.video.showCursor").orElse("true").get()
  val videoStepDelayMs = providers.gradleProperty("parikshan.video.stepDelayMs").orElse("350").get()
  val defaultVideoOutputDir = layout.buildDirectory.dir("parikshan/videos").get().asFile.absolutePath
  val videoOutputDir = providers.gradleProperty("parikshan.video.outputDir").orElse(defaultVideoOutputDir).get()
  val testFilter = providers.gradleProperty("parikshan.testFilter").orNull

  systemProperty("parikshan.video.enabled", videoEnabled)
  systemProperty("parikshan.video.fps", videoFps)
  systemProperty("parikshan.video.showCursor", videoShowCursor)
  systemProperty("parikshan.video.stepDelayMs", videoStepDelayMs)
  systemProperty("parikshan.video.outputDir", videoOutputDir)

  if (!testFilter.isNullOrBlank()) {
    filter {
      isFailOnNoMatchingTests = true
      testFilter
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { includeTestsMatching(it) }
    }
  }
}

tasks.register("e2eDesktopTest") {
  group = "verification"
  description = "Runs sample JVM tests against the visible sample app via Parikshan"
  notCompatibleWithConfigurationCache("Coordinates external app lifecycle around integration tests.")
  dependsOn(startSampleE2eDesktopApp)
  dependsOn("jvmTest")
  finalizedBy(stopSampleE2eDesktopApp)
}

tasks.withType<Test>().configureEach {
  // works for both testDebugUnitTest & testReleaseUnitTest
  if (name.endsWith("UnitTest")) {
    exclude("**/*UITest*")
  }
}
