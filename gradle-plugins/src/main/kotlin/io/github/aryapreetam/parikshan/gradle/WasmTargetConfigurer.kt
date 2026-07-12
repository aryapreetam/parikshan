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
    hostTestTask: TaskProvider<Test>,
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
        wasmPortFileVal.parentFile.mkdirs()
        wasmPortFileVal.writeText(resolvedPort.toString())

        WasmServer.start(resolvedPort, wasmOutputDirVal)
      }
    }

    project.tasks.register("stopParikshanWasmApp") {
      group = "verification"
      doLast {
        WasmServer.stop()
      }
    }

    project.tasks.register<Test>("e2eWasmTest") {
      group = "verification"
      dependsOn(installPlaywrightTask, startWasmTask)
      finalizedBy("stopParikshanWasmApp")
      configureE2eHostTestExecution(
        hostTestClassesDirs = hostTestTask.get().testClassesDirs,
        hostTestClasspath = hostTestTask.get().classpath,
        e2eTestClasses = e2eTestClasses,
        target = "Wasm",
        logger = project.logger
      )
      systemProperty("parikshan.target", "wasm")
      systemProperty("parikshan.token", tokenVal)
      doFirst {
        val portFile = wasmPortFileVal
        val port = if (portFile.exists()) portFile.readText().trim() else wasmServerPortVal.toString()
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
  }
}

internal object WasmServer {
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
        println("[WasmServer] 404: ${ex.requestURI.path} (Looked in: ${file.absolutePath})")
        ex.sendResponseHeaders(404, 0)
      }
      ex.close()
    }
    s.start()
    server = s
  }

  fun stop() {
    server?.stop(0)
    server = null
  }
}
