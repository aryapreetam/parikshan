package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import java.io.File
import java.net.InetSocketAddress

internal object WasmTargetConfigurer {
  fun configure(
    project: Project,
    extension: ParikshanExtension,
    sessionToken: String,
    isBackgroundRequested: Boolean,
    isVideoRequested: Boolean,
    e2eTestClasses: List<String>,
    wasmOutputDir: File,
    wasmPortFile: File,
    prepareWasmAssetsTask: TaskProvider<Task>,
    installPlaywrightTask: TaskProvider<JavaExec>
  ) {
    val wasmServerPortVal = extension.wasmServerPort.get()
    val wasmOutputDirVal = wasmOutputDir
    val wasmPortFileVal = wasmPortFile
    val tokenVal = sessionToken

    val startWasmTask = project.tasks.register("startParikshanWasmApp") {
      group = "verification"
      dependsOn(prepareWasmAssetsTask)
      doLast {
        val resolvedPort = PortConflictHandler.resolvePortAndCleanStale(
          originalPort = wasmServerPortVal,
          host = "127.0.0.1",
          logger = logger
        )
        val boundPort = WasmServer.start(resolvedPort, wasmOutputDirVal)
        wasmPortFileVal.parentFile.mkdirs()
        wasmPortFileVal.writeText(boundPort.toString())
      }
    }

    project.tasks.register("stopParikshanWasmApp") {
      group = "verification"
      doLast {
        WasmServer.stop()
      }
    }

    val optWindowSize = project.providers.gradleProperty("parikshan.windowSize")
      .orElse(project.providers.systemProperty("parikshan.windowSize"))
    val optWasmSize = project.providers.gradleProperty("parikshan.wasm.windowSize")
      .orElse(project.providers.systemProperty("parikshan.wasm.windowSize"))
      .orElse(optWindowSize)
    val optWasmPos = project.providers.gradleProperty("parikshan.wasm.windowPosition")
      .orElse(project.providers.systemProperty("parikshan.wasm.windowPosition"))
    val optAppMode = project.providers.gradleProperty("parikshan.appMode")
      .orElse(project.providers.systemProperty("parikshan.appMode"))

    project.registerE2eTestWithReport("e2eWasmTest", "Wasm") {
      group = "verification"
      dependsOn(installPlaywrightTask, startWasmTask)
      finalizedBy("stopParikshanWasmApp")
      configureE2eHostTestExecution(
        e2eTestClasses = e2eTestClasses,
        target = "Wasm",
        logger = logger
      )
      systemProperty("parikshan.target", "wasm")
      systemProperty("parikshan.token", tokenVal)
      environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
      doFirst {
        val portFile = wasmPortFileVal
        val port = if (portFile.exists()) portFile.readText().trim() else wasmServerPortVal.toString()
        systemProperty("parikshan.wasm.url", "http://127.0.0.1:$port")

        val sizeStr = optWasmSize.orNull
        val posStr = optWasmPos.orNull

        val size = parseSize(sizeStr)
        val pos = parsePosition(posStr)
        val appModeVal = optAppMode.orNull?.toBoolean() ?: false
        systemProperty("parikshan.wasm.appMode", appModeVal.toString())

        if (size != null) {
          systemProperty("parikshan.wasm.viewportWidth", size.first.toString())
          systemProperty("parikshan.wasm.viewportHeight", size.second.toString())
        }
        if (pos != null) {
          systemProperty("parikshan.wasm.windowX", pos.first.toString())
          systemProperty("parikshan.wasm.windowY", pos.second.toString())
        }
      }
      if (isBackgroundRequested) {
        systemProperty("parikshan.wasm.headless", "true")
      }
      val videosDir = project.layout.buildDirectory.dir("parikshan/videos/wasm").get().asFile.absolutePath
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
      testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
      }
    }
  }
}

internal object WasmServer {
  private var server: com.sun.net.httpserver.HttpServer? = null

  fun start(requestedPort: Int, root: File): Int {
    stop()
    var boundPort = requestedPort
    var s: com.sun.net.httpserver.HttpServer? = null
    for (offset in 0..20) {
      val candidate = requestedPort + offset
      try {
        s = com.sun.net.httpserver.HttpServer.create(InetSocketAddress(candidate), 0)
        boundPort = candidate
        break
      } catch (_: Exception) {}
    }
    val http = s ?: throw IllegalStateException("Could not bind WasmServer to any port in range $requestedPort..${requestedPort + 20}")
    http.createContext("/") { ex ->
      if (ex.requestURI.path == "/health") {
        val bytes = "{\"status\":\"health\"}".toByteArray()
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.write(bytes)
        ex.close()
        return@createContext
      }
      val path = if (ex.requestURI.path == "/") "/index.html" else ex.requestURI.path
      val file = File(root, path.removePrefix("/"))
      if (file.exists() && file.isFile) {
        ex.responseHeaders.add("Cross-Origin-Opener-Policy", "same-origin")
        ex.responseHeaders.add("Cross-Origin-Embedder-Policy", "require-corp")
        val contentType = when (file.extension.lowercase()) {
          "wasm" -> "application/wasm"
          "js" -> "application/javascript"
          "html" -> "text/html"
          "css" -> "text/css"
          "png" -> "image/png"
          "jpg", "jpeg" -> "image/jpeg"
          "gif" -> "image/gif"
          "svg" -> "image/svg+xml"
          "json" -> "application/json"
          else -> "application/octet-stream"
        }
        ex.responseHeaders.add("Content-Type", contentType)
        ex.sendResponseHeaders(200, file.length())
        file.inputStream().use { it.copyTo(ex.responseBody) }
      } else {
        println("[WasmServer] 404: ${ex.requestURI.path} (Looked in: ${file.absolutePath})")
        ex.sendResponseHeaders(404, 0)
      }
      ex.close()
    }
    http.start()
    server = http
    return boundPort
  }

  fun stop() {
    server?.stop(0)
    server = null
  }
}
