package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.concurrent.TimeUnit

internal object DesktopTargetConfigurer {
  fun configure(
    project: Project,
    extension: ParikshanExtension,
    sessionToken: String,
    isBackgroundRequested: Boolean,
    isVideoRequested: Boolean,
    e2eTestClasses: List<String>,
    desktopLaunchManifestFile: File,
    targetName: String
  ) {
    val appJarTaskNameVal = extension.appJarTaskName.get()
    val appArgsVal = extension.appArgs.get()
    val hostVal = extension.host.get()
    val portVal = extension.port.get()
    val startupTimeoutMsVal = extension.startupTimeoutMs.get()
    val startupPollIntervalMsVal = extension.startupPollIntervalMs.get()
    val titleVal = extension.desktopWindowTitle.orNull
    val buildDirVal = project.layout.buildDirectory.get().asFile
    val tokenVal = sessionToken
    val manifestFileVal = desktopLaunchManifestFile

    val capitalizedTarget = targetName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    val startTaskName = "startParikshan${capitalizedTarget}App"
    val stopTaskName = "stopParikshan${capitalizedTarget}App"
    val e2eTaskName = "e2e${capitalizedTarget}Test"

    val targetProjectForJar = project.resolveDesktopAppProject(
      userConfiguredPath = null
    )

    val appJarFileProvider = if (targetProjectForJar != null && targetProjectForJar.tasks.names.contains(appJarTaskNameVal)) {
      targetProjectForJar.tasks.named<org.gradle.jvm.tasks.Jar>(appJarTaskNameVal)
        .flatMap { it.archiveFile }
    } else {
      null
    }

    val optWindowSize = project.providers.gradleProperty("parikshan.windowSize")
      .orElse(project.providers.systemProperty("parikshan.windowSize"))
    val optDesktopSize = project.providers.gradleProperty("parikshan.desktop.windowSize")
      .orElse(project.providers.systemProperty("parikshan.desktop.windowSize"))
      .orElse(optWindowSize)
    val optDesktopPos = project.providers.gradleProperty("parikshan.desktop.windowPosition")
      .orElse(project.providers.systemProperty("parikshan.desktop.windowPosition"))

    val startDesktopTask = project.tasks.register(startTaskName) {
      group = "verification"
      if (appJarFileProvider != null) {
        inputs.file(appJarFileProvider)
      }

      doLast {
        if (appJarFileProvider == null) {
          logger.lifecycle("Parikshan: Task $appJarTaskNameVal not found in project. Skipping application startup; running tests directly.")
          return@doLast
        }
        val jar = appJarFileProvider.get().asFile
        val resolvedPort = PortConflictHandler.resolvePortAndCleanStale(
          originalPort = portVal,
          host = hostVal,
          logger = logger
        )
        val sizeStr = optDesktopSize.orNull
        val posStr = optDesktopPos.orNull

        val size = parseSize(sizeStr)
        val pos = parsePosition(posStr)
        if (size != null) {
          System.setProperty("parikshan.desktop.windowWidth", size.first.toString())
          System.setProperty("parikshan.desktop.windowHeight", size.second.toString())
        }
        if (pos != null) {
          System.setProperty("parikshan.desktop.windowX", pos.first.toString())
          System.setProperty("parikshan.desktop.windowY", pos.second.toString())
        }

        DesktopProcess.start(
          jar = jar,
          token = tokenVal,
          logFile = File(buildDirVal, "parikshan/${targetName.lowercase()}-app.log"),
          manifestFile = manifestFileVal,
          appArgs = appArgsVal,
          host = hostVal,
          port = resolvedPort,
          timeoutMs = startupTimeoutMsVal,
          pollMs = startupPollIntervalMsVal,
          title = titleVal,
          background = isBackgroundRequested
        )
      }
    }

    project.tasks.register(stopTaskName) {
      group = "verification"
      doLast {
        DesktopProcess.stop(
          host = hostVal,
          port = portVal,
          token = tokenVal,
          manifestFile = manifestFileVal
        )
      }
    }

    if (appJarFileProvider != null && targetProjectForJar != null) {
      startDesktopTask.configure {
        val taskPath = if (targetProjectForJar == project) {
          appJarTaskNameVal
        } else {
          "${targetProjectForJar.path}:${appJarTaskNameVal}"
        }
        dependsOn(taskPath)
      }
    }

    project.registerE2eTestWithReport(e2eTaskName, capitalizedTarget) {
      group = "verification"
      dependsOn(startDesktopTask)
      finalizedBy(stopTaskName)

      configureE2eHostTestExecution(
        e2eTestClasses = e2eTestClasses,
        target = capitalizedTarget,
        logger = logger
      )
      systemProperty("parikshan.host", hostVal)
      doFirst {
        val manifest = manifestFileVal
        val port = if (manifest.exists()) {
          val props = Properties()
          runCatching { manifest.inputStream().use { props.load(it) } }
          props.getProperty("port") ?: portVal.toString()
        } else {
          portVal.toString()
        }
        systemProperty("parikshan.port", port)

        val sizeStr = optDesktopSize.orNull
        val posStr = optDesktopPos.orNull

        val size = parseSize(sizeStr)
        val pos = parsePosition(posStr)
        if (size != null) {
          systemProperty("parikshan.desktop.windowWidth", size.first.toString())
          systemProperty("parikshan.desktop.windowHeight", size.second.toString())
        }
        if (pos != null) {
          systemProperty("parikshan.desktop.windowX", pos.first.toString())
          systemProperty("parikshan.desktop.windowY", pos.second.toString())
        }
      }
      systemProperty("parikshan.target", targetName.lowercase())
      systemProperty("parikshan.token", tokenVal)
      systemProperty("parikshan.desktop.launchManifest", manifestFileVal.absolutePath)
      val videosDir = project.layout.buildDirectory.dir("parikshan/videos/$targetName").get().asFile.absolutePath
      systemProperty("parikshan.video.outputDir", videosDir)
      if (isBackgroundRequested) {
        systemProperty("parikshan.background", "true")
      }
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
    }
  }
}

internal object DesktopProcess {
  private var process: Process? = null

  fun start(
    jar: File,
    token: String,
    logFile: File,
    manifestFile: File,
    appArgs: List<String>,
    host: String,
    port: Int,
    timeoutMs: Long,
    pollMs: Long,
    title: String?,
    background: Boolean
  ) {
    stop(host = host, port = port, token = token, manifestFile = manifestFile)
    val javaExecutable = System.getProperty("java.home") + "/bin/java"
    val mainClass = java.util.jar.JarFile(jar).manifest.mainAttributes.getValue("Main-Class")
    logFile.parentFile.mkdirs()
    val command = buildList {
      add(javaExecutable)
      add("-Dparikshan.host=$host")
      add("-Dparikshan.port=$port")
      add("-Dparikshan.token=$token")
      add("-Dparikshan.desktop.appMainClass=$mainClass")
      add("-Dapple.awt.takeFocusOnShow=false")
      if (background) {
        add("-Dparikshan.background=true")
      }
      val wX = System.getProperty("parikshan.desktop.windowX")
      val wY = System.getProperty("parikshan.desktop.windowY")
      val wW = System.getProperty("parikshan.desktop.windowWidth")
      val wH = System.getProperty("parikshan.desktop.windowHeight")
      if (!wX.isNullOrEmpty()) add("-Dparikshan.desktop.windowX=$wX")
      if (!wY.isNullOrEmpty()) add("-Dparikshan.desktop.windowY=$wY")
      if (!wW.isNullOrEmpty()) add("-Dparikshan.desktop.windowWidth=$wW")
      if (!wH.isNullOrEmpty()) add("-Dparikshan.desktop.windowHeight=$wH")
      title?.let { add("-Dparikshan.desktop.windowTitle=$it") }
      add("-cp")
      add(jar.absolutePath)
      add("io.github.aryapreetam.parikshan.server.DesktopAppLauncher")
      addAll(appArgs)
    }
    val pb = ProcessBuilder(command)
    pb.environment()["NSAppSleepDisabled"] = "YES"
    val startedProcess = pb
      .redirectErrorStream(true)
      .redirectOutput(logFile)
      .start()
    process = startedProcess
    writeLaunchManifest(
      manifestFile = manifestFile,
      pid = startedProcess.pid(),
      javaExecutable = javaExecutable,
      jar = jar,
      appMainClass = mainClass,
      token = token,
      logFile = logFile,
      appArgs = appArgs,
      host = host,
      port = port,
      title = title,
      background = background
    )
  }

  fun stop(host: String = "127.0.0.1", port: Int = 9877, token: String = "", manifestFile: File? = null) {
    val resolvedPort = if (manifestFile != null && manifestFile.exists()) {
      val props = Properties()
      runCatching { manifestFile.inputStream().use { props.load(it) } }
      props.getProperty("port")?.toIntOrNull() ?: port
    } else {
      port
    }
    runCatching {
      val json = """{"type":"stopRecording","id":"desktop-stop","sessionName":"any","token":"$token"}"""
      val url = URL("http://$host:$resolvedPort/")
      val conn = url.openConnection() as HttpURLConnection
      conn.requestMethod = "POST"
      conn.setRequestProperty("Content-Type", "application/json")
      conn.doOutput = true
      conn.connectTimeout = 1000
      conn.readTimeout = 1000
      conn.outputStream.use { it.write(json.toByteArray()) }
      conn.responseCode
    }
    process?.let { active ->
      if (active.isAlive) {
        active.destroy()
        try { active.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: Exception) {}
      }
    }
    manifestFile?.let { destroyManifestProcess(it) }
    process = null
  }

  fun isProcessAlive(manifestFile: File?): Boolean {
    if (process != null) {
      return process?.isAlive == true
    }
    if (manifestFile == null || !manifestFile.exists()) {
      return false
    }
    val pid = runCatching {
      Properties().apply { manifestFile.inputStream().use(::load) }.getProperty("pid")?.toLongOrNull()
    }.getOrNull() ?: return false
    return ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
  }

  private fun writeLaunchManifest(
    manifestFile: File,
    pid: Long,
    javaExecutable: String,
    jar: File,
    appMainClass: String,
    token: String,
    logFile: File,
    appArgs: List<String>,
    host: String,
    port: Int,
    title: String?,
    background: Boolean
  ) {
    manifestFile.parentFile.mkdirs()
    val properties = Properties().apply {
      setProperty("pid", pid.toString())
      setProperty("javaExecutable", javaExecutable)
      setProperty("jar", jar.absolutePath)
      setProperty("appMainClass", appMainClass)
      setProperty("token", token)
      setProperty("host", host)
      setProperty("port", port.toString())
      setProperty("logFile", logFile.absolutePath)
      setProperty("background", background.toString())
      title?.let { setProperty("windowTitle", it) }
      setProperty("appArgCount", appArgs.size.toString())
      appArgs.forEachIndexed { index, value ->
        setProperty("appArg.$index", value)
      }
    }
    manifestFile.outputStream().use { output ->
      properties.store(output, "Parikshan desktop launch state")
    }
  }

  private fun destroyManifestProcess(manifestFile: File) {
    if (!manifestFile.exists()) {
      return
    }
    val pid = Properties()
      .also { properties -> manifestFile.inputStream().use(properties::load) }
      .getProperty("pid")
      ?.toLongOrNull()
      ?: return
    val handle = ProcessHandle.of(pid).orElse(null) ?: return
    if (!handle.isAlive) {
      return
    }
    handle.destroy()
    runCatching {
      handle.onExit().get(10, TimeUnit.SECONDS)
    }.onFailure {
      if (handle.isAlive) {
        handle.destroyForcibly()
      }
    }
  }
}
