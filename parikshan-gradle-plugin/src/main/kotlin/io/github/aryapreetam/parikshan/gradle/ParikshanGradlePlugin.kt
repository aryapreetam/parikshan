package io.github.aryapreetam.parikshan.gradle

import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
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
  val desktopTestTaskName: Property<String> = objects.property(String::class.java).convention("desktopTest")

  @get:Input
  val appArgs: ListProperty<String> = objects.listProperty(String::class.java).convention(listOf("--test-mode"))

  @get:Input
  val host: Property<String> = objects.property(String::class.java).convention("127.0.0.1")

  @get:Input
  val port: Property<Int> = objects.property(Int::class.java).convention(9877)

  @get:Input
  val startupTimeoutMs: Property<Long> = objects.property(Long::class.java).convention(90_000L)

  @get:Input
  val startupPollIntervalMs: Property<Long> = objects.property(Long::class.java).convention(250L)
}

class ParikshanGradlePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create<ParikshanExtension>("parikshan")

    val startTask =
      project.tasks.register("startParikshanDesktopApp") {
        group = "verification"
        description = "Builds and launches desktop app in Parikshan test mode"

        doLast {
          ParikshanDesktopProcess.start(
            project = project,
            appArgs = extension.appArgs.get(),
            host = extension.host.get(),
            port = extension.port.get(),
            startupTimeoutMs = extension.startupTimeoutMs.get(),
            startupPollIntervalMs = extension.startupPollIntervalMs.get()
          )
        }
      }

    val stopTask =
      project.tasks.register("stopParikshanDesktopApp") {
        group = "verification"
        description = "Stops desktop app launched for Parikshan E2E tests"
        doLast {
          ParikshanDesktopProcess.stop()
        }
      }

    project.afterEvaluate {
      startTask.configure {
        dependsOn(extension.appJarTaskName.get())
      }

      val desktopTestTask = project.tasks.named<Test>(extension.desktopTestTaskName.get())
      desktopTestTask.configure {
        dependsOn(startTask)
        finalizedBy(stopTask)

        systemProperty("parikshan.host", extension.host.get())
        systemProperty("parikshan.port", extension.port.get().toString())
      }

      project.tasks.register("e2eDesktopTest") {
        group = "verification"
        description = "Runs desktop tests with visible app automation through Parikshan"
        dependsOn(desktopTestTask)
      }
    }
  }
}

private object ParikshanDesktopProcess {
  @Volatile
  private var process: Process? = null

  fun start(
    project: Project,
    appArgs: List<String>,
    host: String,
    port: Int,
    startupTimeoutMs: Long,
    startupPollIntervalMs: Long
  ) {
    stop()

    val jar = resolveDesktopJar(project)
    val javaExecutable = resolveJavaExecutable()
    val logFile = project.layout.buildDirectory.file("parikshan/app-process.log").get().asFile
    logFile.parentFile.mkdirs()

    val command = mutableListOf(javaExecutable.absolutePath, "-jar", jar.absolutePath).apply { addAll(appArgs) }
    project.logger.lifecycle("Parikshan launching desktop app: ${command.joinToString(" ")}")

    val started =
      ProcessBuilder(command)
        .directory(project.projectDir)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        .start()

    process = started
    waitForPort(
      host = host,
      port = port,
      timeout = Duration.ofMillis(startupTimeoutMs),
      poll = Duration.ofMillis(startupPollIntervalMs)
    )
  }

  fun stop() {
    val active = process ?: return
    runCatching {
      active.destroy()
      active.waitFor(3, TimeUnit.SECONDS)
      if (active.isAlive) {
        active.destroyForcibly()
        active.waitFor(2, TimeUnit.SECONDS)
      }
    }
    process = null
  }

  private fun resolveDesktopJar(project: Project): File {
    val jarsDir = project.layout.buildDirectory.dir("compose/jars").get().asFile
    val jar =
      jarsDir
        .listFiles()
        ?.filter { it.isFile && it.extension == "jar" }
        ?.maxByOrNull { it.lastModified() }

    return requireNotNull(jar) {
      "Parikshan could not find a desktop executable jar in ${jarsDir.absolutePath}."
    }
  }

  private fun resolveJavaExecutable(): File {
    val javaHome = System.getProperty("java.home") ?: throw GradleException("java.home is not available")
    return File(javaHome, "bin/java")
  }

  private fun waitForPort(
    host: String,
    port: Int,
    timeout: Duration,
    poll: Duration
  ) {
    val deadline = System.currentTimeMillis() + timeout.toMillis()
    while (System.currentTimeMillis() <= deadline) {
      val ready =
        runCatching {
          Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 750)
          }
          true
        }.getOrDefault(false)

      if (ready) {
        return
      }
      Thread.sleep(poll.toMillis())
    }

    throw GradleException("Parikshan timed out waiting for app server at $host:$port")
  }
}
