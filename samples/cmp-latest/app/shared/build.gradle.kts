import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URL
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Internal
import org.gradle.api.file.RegularFileProperty

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  id("io.github.aryapreetam.parikshan") version "0.0.13"
}

kotlin {
  listOf(
    iosArm64(),
    iosSimulatorArm64()
  ).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "Shared"
      isStatic = true
    }
  }

  jvm()

  js {
    browser()
  }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser()
  }

  androidLibrary {
    namespace = "org.example.project.app.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
    androidResources {
      enable = true
    }
    withHostTest {
      isIncludeAndroidResources = true
    }
  }

  sourceSets {
    androidMain.dependencies {
      implementation(libs.compose.uiToolingPreview)
    }
    commonMain.dependencies {
      api(projects.core)
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.uiToolingPreview)
      @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
      implementation(compose.materialIconsExtended)
      implementation(libs.androidx.lifecycle.viewmodelCompose)
      implementation(libs.androidx.lifecycle.runtimeCompose)
      implementation(libs.ktor.clientCore)
      implementation(libs.kotlinx.coroutinesCore)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
    jvmMain.dependencies {
      implementation(libs.kotlinx.coroutinesSwing)
      implementation(libs.ktor.clientCio)
    }
    jsMain.dependencies {
      implementation(libs.wrappers.browser)
    }
  }
}

dependencies {
  androidRuntimeClasspath(libs.compose.uiTooling)
}

abstract class StartStorefrontServerTask : DefaultTask() {
  @get:InputFile
  abstract val serverJar: RegularFileProperty

  @get:Internal
  abstract val serverPid: RegularFileProperty

  @get:Internal
  abstract val serverLog: RegularFileProperty

  init {
    outputs.upToDateWhen { false }
  }

  @TaskAction
  fun execute() {
    val jar = serverJar.get().asFile
    val logFile = serverLog.get().asFile
    val pidFile = serverPid.get().asFile
    logFile.parentFile.mkdirs()
    pidFile.parentFile.mkdirs()

    val isAlreadyRunning = runCatching {
      val conn = URL("http://127.0.0.1:8080/api/products").openConnection() as HttpURLConnection
      conn.connectTimeout = 500
      conn.readTimeout = 500
      conn.responseCode == 200
    }.getOrDefault(false)

    if (isAlreadyRunning) {
      logger.lifecycle("Parikshan: Storefront Ktor Server is already active on port 8080.")
      return
    }

    logger.lifecycle("Parikshan: Starting Storefront Ktor Server on port 8080...")
    val javaExec = System.getProperty("java.home") + "/bin/java"
    val pb = ProcessBuilder(javaExec, "-jar", jar.absolutePath)
      .redirectErrorStream(true)
      .redirectOutput(logFile)
    val process = pb.start()
    pidFile.writeText(process.pid().toString())

    val timeoutMs = 15000L
    val startMark = System.currentTimeMillis()
    var healthy = false
    while (System.currentTimeMillis() - startMark < timeoutMs) {
      if (!process.isAlive) {
        error("Storefront Ktor Server process terminated unexpectedly with exit code ${process.exitValue()}. Check logs at ${logFile.absolutePath}")
      }
      healthy = runCatching {
        val conn = URL("http://127.0.0.1:8080/api/products").openConnection() as HttpURLConnection
        conn.connectTimeout = 500
        conn.readTimeout = 500
        conn.responseCode == 200
      }.getOrDefault(false)
      if (healthy) break
      Thread.sleep(200)
    }

    if (!healthy) {
      process.destroyForcibly()
      error("Timed out waiting for Storefront Ktor Server to start on port 8080. Check logs at ${logFile.absolutePath}")
    }
    logger.lifecycle("Parikshan: Storefront Ktor Server started successfully (PID: ${process.pid()}).")
  }
}

abstract class StopStorefrontServerTask : DefaultTask() {
  @get:Internal
  abstract val serverPid: RegularFileProperty

  init {
    outputs.upToDateWhen { false }
  }

  @TaskAction
  fun execute() {
    val pidFile = serverPid.orNull?.asFile ?: return
    if (pidFile.exists()) {
      val pid = pidFile.readText().trim().toLongOrNull()
      if (pid != null) {
        val handle = ProcessHandle.of(pid).orElse(null)
        if (handle != null && handle.isAlive) {
          logger.lifecycle("Parikshan: Stopping Storefront Ktor Server (PID: $pid)...")
          handle.destroy()
          runCatching {
            handle.onExit().get(5, TimeUnit.SECONDS)
          }.onFailure {
            if (handle.isAlive) handle.destroyForcibly()
          }
        }
      }
      pidFile.delete()
    }
  }
}

val startStorefrontServer = tasks.register<StartStorefrontServerTask>("startStorefrontServer") {
  group = "verification"
  description = "Starts the Storefront Ktor backend server on port 8080 if not already running."
  dependsOn(":server:shadowJar")
  serverJar.set(File(rootDir, "server/build/libs/server-all.jar"))
  serverPid.set(layout.buildDirectory.file("parikshan/storefront-server.pid"))
  serverLog.set(layout.buildDirectory.file("parikshan/logs/storefront-server.log"))
}

val stopStorefrontServer = tasks.register<StopStorefrontServerTask>("stopStorefrontServer") {
  group = "verification"
  description = "Stops the Storefront Ktor backend server if started by startStorefrontServer."
  serverPid.set(layout.buildDirectory.file("parikshan/storefront-server.pid"))
}

tasks.matching { it.name == "startParikshanJvmApp" || it.name == "e2eTest" }.configureEach {
  dependsOn(startStorefrontServer)
}

tasks.matching { it.name == "stopParikshanJvmApp" || it.name == "e2eTest" }.configureEach {
  finalizedBy(stopStorefrontServer)
}