package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import java.io.File
import java.util.Properties
import java.util.UUID

class ParikshanPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create<ParikshanExtension>("parikshan")

    // Register dummy tasks for clean CLI syntax
    project.tasks.register("background") {
      group = "verification"
      description = "Enable background mode for E2E tests"
    }
    project.tasks.register("video") {
      group = "verification"
      description = "Enable video recording for E2E tests"
    }

    // Ensure all ZIP/JAR tasks can handle large entry counts (Zip64)
    project.tasks.withType(Zip::class.java).configureEach {
      isZip64 = true
    }
    val sessionToken = project.providers.gradleProperty("parikshan.token").getOrNull() ?: UUID.randomUUID().toString()

    // Centralized flag detection
    val isBackgroundRequested = 
      project.gradle.startParameter.taskNames.any { it.contains("background", ignoreCase = true) } ||
      (project.hasProperty("background") && project.property("background").toString() == "true")

    val isVideoRequested = 
      project.gradle.startParameter.taskNames.any { it.contains("video", ignoreCase = true) } ||
      (project.hasProperty("video") && project.property("video").toString() == "true")

    if (isBackgroundRequested) {
        project.logger.lifecycle("Parikshan: Background mode DETECTED.")
    }
    if (isVideoRequested) {
        project.logger.lifecycle("Parikshan: Video recording ENABLED.")
    }

    val desktopLaunchManifestFile = project.layout.buildDirectory.file("parikshan/desktop-launch.properties")
    val wasmOutputDir = project.layout.buildDirectory.dir("parikshan/wasm-app")
    val wasmDevDir = project.layout.buildDirectory.dir("kotlin-webpack/wasmJs/developmentExecutable")
    val wasmProdDir = project.layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    val wasmResourcesDir = project.layout.buildDirectory.dir("processedResources/wasmJs/main")
    val wasmPortFile = project.layout.buildDirectory.file("parikshan/wasm-port.txt")

    val prepareWasmAssetsTask = project.tasks.register("prepareParikshanWasmAssets") {
      group = "verification"
    }

    val installPlaywrightTask = project.tasks.register<JavaExec>("installPlaywrightBrowsers") {
        group = "verification"
        mainClass.set("com.microsoft.playwright.CLI")
        args = listOf("install", "chromium")
    }

    project.pluginManager.withPlugin("com.android.application") {
      project.configureAndroidInstrumentationDefaults()
    }

    val iosProjectDir = project.projectDir
    val iosLogger = project.logger
    
    val isE2ERequested =
      project.gradle.startParameter.taskNames.any { it.contains("e2e", ignoreCase = true) } ||
        project.hasProperty("parikshan.e2e.active")
    var prepareIosBootSourceTask: TaskProvider<Task>? = null
    var prepareWasmBootSourceTask: TaskProvider<Task>? = null

    if (isE2ERequested) {
      project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        prepareIosBootSourceTask =
          project.registerParikshanIosBootSource(
            iosProjectDir = iosProjectDir,
            logger = iosLogger,
            sessionToken = sessionToken
          )
        prepareWasmBootSourceTask =
          project.registerParikshanWasmBootSource(
            logger = iosLogger
          )
      }
    }

    project.afterEvaluate {
      val isE2EActive = isE2ERequested

      project.configureParikshanDependencies(isE2EActive)

      val e2eTestClasses = project.discoverE2eTestClasses()
      val hostTestTaskName = project.resolveHostTestTaskName(extension.desktopTestTaskName.orNull)
      val hostTestTask = project.tasks.named<Test>(hostTestTaskName)
      val wasmDistributionTaskName = project.resolveWasmDistributionTaskName(extension.wasmDistributionTaskName.orNull)

      val wasmOutputDirProvider = wasmOutputDir
      val wasmDevDirProvider = wasmDevDir
      val wasmProdDirProvider = wasmProdDir
      val wasmResourcesDirProvider = wasmResourcesDir
      val buildDirProvider = project.layout.buildDirectory
      val gradleLogger = project.logger

      prepareWasmAssetsTask.configure {
        dependsOn(wasmDistributionTaskName)
        dependsOn("wasmJsProcessResources")
        doLast {
          val output = wasmOutputDirProvider.get().asFile
          output.deleteRecursively()
          output.mkdirs()
          val distDir = if (wasmDevDirProvider.get().asFile.exists()) wasmDevDirProvider.get().asFile else wasmProdDirProvider.get().asFile
          if (distDir.exists()) distDir.copyRecursively(output, overwrite = true)
          val buildDir = buildDirProvider.get().asFile
          listOf("processedResources/wasmJs/main", "kotlin-multiplatform-resources/assemble-hierarchically/wasmJsResolveSelfResources", "kotlin-multiplatform-resources/aggregated-resources/wasmJs")
            .map { File(buildDir, it) }.filter { it.exists() }.forEach { resDir ->
              gradleLogger.lifecycle("Parikshan Wasm: Copying resources from ${resDir.absolutePath}")
              resDir.copyRecursively(output, overwrite = true)
            }
          val indexHtml = File(output, "index.html")
          if (!indexHtml.exists()) {
            val srcIndex = File(wasmResourcesDirProvider.get().asFile, "index.html")
            if (srcIndex.exists()) srcIndex.copyTo(indexHtml)
          }
        }
      }

      installPlaywrightTask.configure {
        classpath = hostTestTask.get().classpath
      }

      // Delegate configurations to single-responsibility classes
      DesktopTargetConfigurer.configure(
        project = project,
        extension = extension,
        sessionToken = sessionToken,
        isBackgroundRequested = isBackgroundRequested,
        isVideoRequested = isVideoRequested,
        e2eTestClasses = e2eTestClasses,
        hostTestTask = hostTestTask,
        desktopLaunchManifestFile = desktopLaunchManifestFile.get().asFile
      )

      WasmTargetConfigurer.configure(
        project = project,
        extension = extension,
        sessionToken = sessionToken,
        isBackgroundRequested = isBackgroundRequested,
        isVideoRequested = isVideoRequested,
        e2eTestClasses = e2eTestClasses,
        hostTestTask = hostTestTask,
        wasmOutputDir = wasmOutputDir.get().asFile,
        wasmPortFile = wasmPortFile.get().asFile,
        prepareWasmAssetsTask = prepareWasmAssetsTask,
        installPlaywrightTask = installPlaywrightTask
      )

      IosTargetConfigurer.configure(
        project = project,
        extension = extension,
        sessionToken = sessionToken,
        isBackgroundRequested = isBackgroundRequested,
        isVideoRequested = isVideoRequested,
        e2eTestClasses = e2eTestClasses,
        hostTestTask = hostTestTask,
        prepareIosBootSourceTask = prepareIosBootSourceTask
      )

      AndroidTargetConfigurer.configure(
        project = project,
        extension = extension,
        sessionToken = sessionToken,
        isE2EActive = isE2EActive,
        isBackgroundRequested = isBackgroundRequested,
        isVideoRequested = isVideoRequested,
        e2eTestClasses = e2eTestClasses,
        hostTestTask = hostTestTask
      )

      val targetAndroidAppId = AndroidTargetConfigurer.AndroidRecorder.resolveAndroidApplicationId(project)
      val iosPort = project.providers.gradleProperty("parikshan.ios.port").orElse(project.providers.systemProperty("parikshan.ios.port")).orNull?.toIntOrNull() ?: 9878

      fun getIosBundleId(): String {
        val prop = project.providers.gradleProperty("parikshan.ios.bundleId").orElse(project.providers.systemProperty("parikshan.ios.bundleId")).orNull
        if (prop != null) return prop
        val iosXcodeProject = project.providers.gradleProperty("parikshan.ios.xcodeProject").orElse(project.providers.systemProperty("parikshan.ios.xcodeProject")).orNull
          ?: project.discoverIosXcodeProject()?.absolutePath
          ?: "${project.projectDir}/../iosApp/iosApp.xcodeproj"
        val iosXcodeScheme = project.providers.gradleProperty("parikshan.ios.xcodeScheme").orElse(project.providers.systemProperty("parikshan.ios.xcodeScheme")).orNull ?: "iosApp"
        val provider = project.providers.of(XcodeBundleIdValueSource::class.java) {
          parameters.xcodeProject.set(File(iosXcodeProject))
          parameters.scheme.set(iosXcodeScheme)
        }
        val extracted = provider.orNull
        project.logger.lifecycle("Parikshan iOS: Extracted bundle ID: $extracted")
        return extracted ?: "sample.app.ios"
      }

      val junitConsoleConfig = project.configurations.detachedConfiguration(
        project.dependencies.create("org.junit.platform:junit-platform-console-standalone:1.10.2")
      )

      val e2eTestReport = project.tasks.register("e2eTestReport") {
        group = "verification"
        description = "Generates a unified HTML report for all orchestrated Parikshan E2E targets."
        
        val resultsDir = project.layout.buildDirectory.dir("test-results/e2eTest")
        val reportsDir = project.layout.buildDirectory.dir("reports/tests/e2eTest")
        
        inputs.dir(resultsDir)
        outputs.dir(reportsDir)
        
        doLast {
          generateUnifiedReport(
            resDirFile = resultsDir.get().asFile,
            repDirFile = reportsDir.get().asFile,
            logger = logger
          )
        }
      }

      project.tasks.register<E2ETestTask>("e2eTest") {
        group = "verification"
        description = "Run E2E tests for multiple targets concurrently (e.g. desktop,wasm)"
        
        finalizedBy(e2eTestReport)
        
        dependsOn(hostTestTask.get().testClassesDirs.buildDependencies)
        dependsOn(installPlaywrightTask)
        dependsOn(prepareWasmAssetsTask)
        dependsOn(extension.appJarTaskName.get())
        
        hostTestClassesDirs.setFrom(hostTestTask.get().testClassesDirs)
        hostTestClasspath.setFrom(hostTestTask.get().classpath)
        junitConsoleJars.setFrom(junitConsoleConfig)
        this.e2eTestClasses.set(project.provider { e2eTestClasses })
        host.set(extension.host)
        originalDesktopPort.set(extension.port)
        originalWasmPort.set(extension.wasmServerPort)
        
        val appJarFileProvider = project.tasks.named<org.gradle.jvm.tasks.Jar>(extension.appJarTaskName.get())
          .flatMap { it.archiveFile }
        appJarFile.set(appJarFileProvider)
        
        this.desktopLaunchManifestFile.set(desktopLaunchManifestFile)
        this.wasmOutputDir.set(wasmOutputDir)
        this.wasmPortFile.set(wasmPortFile)
        this.appArgs.set(extension.appArgs)
        token.set(sessionToken)
        this.title.set(extension.desktopWindowTitle)
        buildDir.set(project.layout.buildDirectory)
        projectRootDir.set(project.rootDir.absolutePath)
        targetAndroidAppId?.let { this@register.androidApplicationId.set(it) }
        this@register.iosPort.set(iosPort)
        this@register.iosBundleId.set(getIosBundleId())

        gradleAndroidSerial.set(project.providers.gradleProperty("parikshan.android.serial").orElse(project.providers.systemProperty("parikshan.android.serial")))
        gradleIosDevice.set(project.providers.gradleProperty("parikshan.ios.device").orElse(project.providers.systemProperty("parikshan.ios.device")))
        gradleDevice.set(project.providers.gradleProperty("device").orElse(project.providers.systemProperty("device")))
        gradleSerial.set(project.providers.gradleProperty("serial").orElse(project.providers.systemProperty("serial")))
      }

      project.tasks.configureEach {
        val isE2eTask = name in setOf("e2eDesktopTest", "e2eWasmTest", "e2eIosTest", "e2eAndroidTest", "e2eTest")
        if (isE2eTask) return@configureEach

        // Find any task that has a test filter (Test, KotlinJsTest, KotlinNativeTest, etc.)
        try {
          val filterMethod = javaClass.methods.find { it.name == "getFilter" }
          val filter = filterMethod?.invoke(this) ?: return@configureEach
          
          val excludeMethod = filter.javaClass.methods.find {
            it.name == "excludeTestsMatching" && it.parameterCount == 1
          }
          
          if (excludeMethod != null) {
            e2eTestClasses.forEach { excludeMethod.invoke(filter, it) }
          }
        } catch (_: Exception) {
          // Task doesn't support filtering, skip
        }
      }
    }
  }
}
