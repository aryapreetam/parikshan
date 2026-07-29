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

    // Workaround for Gradle 9.1 + Kotlin Multiplatform task validation check on synthesized clean tasks
    project.tasks.matching { it.name.startsWith("cleanWasmJsBrowserTest") }.configureEach {
      enabled = false
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
    val taskNames = project.gradle.startParameter.taskNames
    val isWasmRequested = taskNames.isEmpty() || taskNames.any {
      it.contains("wasm", ignoreCase = true) || it.endsWith("e2eTest") || it.endsWith("e2e") || !it.contains("e2e", ignoreCase = true)
    }
    val isIosRequested = taskNames.isEmpty() || taskNames.any {
      it.contains("ios", ignoreCase = true) || it.endsWith("e2eTest") || it.endsWith("e2e") || !it.contains("e2e", ignoreCase = true)
    }
    var prepareIosBootSourceTask: TaskProvider<Task>? = null

    if (isE2ERequested && isIosRequested) {
      project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        prepareIosBootSourceTask =
          project.registerParikshanIosBootSource(
            iosProjectDir = iosProjectDir,
            logger = iosLogger,
            sessionToken = sessionToken
          )
        // Wasm boot source registration is deferred to afterEvaluate/projectsEvaluated
        // so the resolved wasmAppProject is known before instrumenting its sources.
      }
    }

    project.afterEvaluate {
      val isE2EActive = isE2ERequested
      val hasKmp = project.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")
      val hasAndroid = project.pluginManager.hasPlugin("com.android.application") ||
                       project.pluginManager.hasPlugin("com.android.library") ||
                       project.pluginManager.hasPlugin("com.android.kotlin.multiplatform.library")

      project.configureParikshanDependencies(isE2EActive)

      val e2eTestClasses = project.discoverE2eTestClasses()
      val hostTestTaskName = project.resolveHostTestTaskName(extension.desktopTestTaskName.orNull)
      val hostTestTask = project.tasks.named<Test>(hostTestTaskName)

      val wasmOutputDirProvider = wasmOutputDir
      val gradleLogger = project.logger

      if (hasKmp) {
        // Always register wasm tasks so e2eWasmTest appears in the task graph regardless
        // of whether a sibling wasm app project exists.
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

        // Defer wasm app project resolution to after ALL projects are evaluated.
        // Sibling projects (e.g., :app:webApp) are not yet configured during afterEvaluate
        // of :app:shared, so task name checks would incorrectly return null.
        project.gradle.projectsEvaluated {
          val wasmAppProject = project.resolveWasmAppProject(
            userConfiguredPath = extension.wasmAppProjectPath.orNull
          )

          if (wasmAppProject != null) {
            gradleLogger.lifecycle(
              "Parikshan: Resolved Wasm app project: '${wasmAppProject.path}'" +
              if (wasmAppProject == project) " (current project)" else " (cross-project)"
            )

            // Register wasm boot source instrumentation on the resolved project
            if (isE2ERequested && isWasmRequested) {
              project.registerParikshanWasmBootSource(
                logger = gradleLogger,
                wasmAppProject = wasmAppProject
              )
              val pluginVersion = ParikshanPlugin::class.java.`package`.implementationVersion ?: "0.0.1"
              wasmAppProject.addParikshanDependency("commonMainImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client:$pluginVersion")
            }

            val wasmDistributionTaskName = wasmAppProject.resolveWasmDistributionTaskName(
              extension.wasmDistributionTaskName.orNull
            )
            val distTaskPath = if (wasmAppProject == project) wasmDistributionTaskName
                               else "${wasmAppProject.path}:${wasmDistributionTaskName}"
            val processResPath = if (wasmAppProject == project) "wasmJsProcessResources"
                                 else "${wasmAppProject.path}:wasmJsProcessResources"

            val wasmAppBuildDir = wasmAppProject.layout.buildDirectory
            val wasmDevDirVal = wasmAppBuildDir.dir("kotlin-webpack/wasmJs/developmentExecutable")
            val wasmProdDirVal = wasmAppBuildDir.dir("dist/wasmJs/productionExecutable")
            val wasmResourcesDirVal = wasmAppBuildDir.dir("processedResources/wasmJs/main")

            prepareWasmAssetsTask.configure {
              dependsOn(distTaskPath)
              dependsOn(processResPath)
              doLast {
                val output = wasmOutputDirProvider.get().asFile
                output.deleteRecursively()
                output.mkdirs()
                val distDir = if (wasmDevDirVal.get().asFile.exists()) wasmDevDirVal.get().asFile
                              else wasmProdDirVal.get().asFile
                if (distDir.exists()) distDir.copyRecursively(output, overwrite = true)
                val wasmAppBuildDirFile = wasmAppBuildDir.get().asFile
                listOf(
                  "processedResources/wasmJs/main",
                  "kotlin-multiplatform-resources/assemble-hierarchically/wasmJsResolveSelfResources",
                  "kotlin-multiplatform-resources/aggregated-resources/wasmJs"
                ).map { File(wasmAppBuildDirFile, it) }.filter { it.exists() }.forEach { resDir ->
                  gradleLogger.lifecycle("Parikshan Wasm: Copying resources from ${resDir.absolutePath}")
                  resDir.copyRecursively(output, overwrite = true)
                }
                val indexHtml = File(output, "index.html")
                if (!indexHtml.exists()) {
                  val srcIndex = File(wasmResourcesDirVal.get().asFile, "index.html")
                  if (srcIndex.exists()) srcIndex.copyTo(indexHtml)
                }
              }
            }
          } else {
            gradleLogger.lifecycle(
              "Parikshan: No Wasm app project found (no wasmJsBrowserDevelopmentWebpack task detected). " +
              "e2eWasmTest will fail at execution time. " +
              "To configure manually: parikshan { wasmAppProjectPath = \":your:webApp\" }"
            )
          }
        }

        installPlaywrightTask.configure {
          classpath = hostTestTask.get().classpath
        }

        project.findJvmTargets().forEach { targetName ->
          val taskName = "${targetName}Test"
          if (project.tasks.names.contains(taskName)) {
            val hostTestTaskForTarget = project.tasks.named<Test>(taskName)
            DesktopTargetConfigurer.configure(
              project = project,
              extension = extension,
              sessionToken = sessionToken,
              isBackgroundRequested = isBackgroundRequested,
              isVideoRequested = isVideoRequested,
              e2eTestClasses = e2eTestClasses,
              hostTestTask = hostTestTaskForTarget,
              desktopLaunchManifestFile = project.layout.buildDirectory.file("parikshan/${targetName.lowercase()}-launch.properties").get().asFile,
              targetName = targetName
            )
          }
        }

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
      }

      if (hasAndroid) {
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
      }

      val targetAndroidAppIdProvider = project.provider {
        val directId = AndroidTargetConfigurer.AndroidRecorder.resolveAndroidApplicationId(project)
        if (directId != null) return@provider directId

        val mergedManifestDir = AndroidComponentsHelper.getMergedManifestDirectory(project)
        val manifestDir = mergedManifestDir.orNull?.asFile
        if (manifestDir != null) {
          val manifestFile = File(manifestDir, "AndroidManifest.xml")
          val parsed = AndroidTargetConfigurer.parseAndroidManifest(manifestFile, project.logger, null)
          parsed.packageName
        } else {
          null
        }
      }
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
        
        val e2eTask = this
        dependsOn(project.provider {
          val activeTargets = e2eTask.targets.split(",").map { it.trim().lowercase() }
          buildList {
            add(hostTestTask.get().testClassesDirs.buildDependencies)
            if (hasKmp) {
              if (activeTargets.contains("wasm") || activeTargets.contains("web")) {
                add(installPlaywrightTask)
                add(prepareWasmAssetsTask)
              }
              val jvmTargets = project.findJvmTargets().map { it.lowercase() }
              if (activeTargets.contains("desktop") || activeTargets.contains("jvm") || jvmTargets.any { activeTargets.contains(it) }) {
                val jarTaskName = extension.appJarTaskName.get()
                val desktopAppProject = project.resolveDesktopAppProject(
                    userConfiguredPath = extension.desktopAppProjectPath.orNull
                )
                if (desktopAppProject != null && desktopAppProject.tasks.names.contains(jarTaskName)) {
                  val taskPath = if (desktopAppProject == project) {
                    jarTaskName
                  } else {
                    "${desktopAppProject.path}:${jarTaskName}"
                  }
                  add(taskPath)
                }
              }
            }
          }
        })
        
        hostTestClassesDirs.setFrom(hostTestTask.get().testClassesDirs)
        val filesList = mutableListOf<org.gradle.api.file.FileCollection>()
        filesList.add(hostTestTask.get().classpath)
        if (project.tasks.names.contains("e2eDesktopTest")) {
          filesList.add(project.tasks.named<Test>("e2eDesktopTest").get().classpath)
        }
        if (project.tasks.names.contains("e2eWasmTest")) {
          filesList.add(project.tasks.named<Test>("e2eWasmTest").get().classpath)
        }
        if (project.tasks.names.contains("e2eAndroidTest")) {
          filesList.add(project.tasks.named<Test>("e2eAndroidTest").get().classpath)
        }
        if (project.tasks.names.contains("e2eIosTest")) {
          filesList.add(project.tasks.named<Test>("e2eIosTest").get().classpath)
        }
        hostTestClasspath.setFrom(project.files(filesList))
        junitConsoleJars.setFrom(junitConsoleConfig)
        this.e2eTestClasses.set(project.provider { e2eTestClasses })
        this.projectPath.set(project.path)
        this.host.set(extension.host)
        this.originalDesktopPort.set(extension.port)
        this.originalWasmPort.set(extension.wasmServerPort)
        this.androidPort.set(extension.androidPort)
        this.iosPort.set(extension.iosPort)
        
        val defaultTargets = buildList {
          if (hasKmp) {
            addAll(project.findJvmTargets())
            add("wasm")
            add("ios")
          }
          if (hasAndroid) {
            add("android")
          }
        }.joinToString(",")
        
        this.targets = defaultTargets
        this.jvmTargets.set(project.provider { project.findJvmTargets() })
        
        if (hasKmp) {
          val jarTaskName = extension.appJarTaskName.get()
          val desktopAppProject = project.resolveDesktopAppProject(
              userConfiguredPath = extension.desktopAppProjectPath.orNull
          )

          if (desktopAppProject != null && desktopAppProject.tasks.names.contains(jarTaskName)) {
            val appJarFileProvider = desktopAppProject.tasks.named<org.gradle.jvm.tasks.Jar>(jarTaskName)
              .flatMap { it.archiveFile }
            appJarFile.set(appJarFileProvider)
          }
        }
        
        this.desktopLaunchManifestFile.set(desktopLaunchManifestFile)
        this.wasmOutputDir.set(wasmOutputDir)
        this.wasmPortFile.set(wasmPortFile)
        this.appArgs.set(extension.appArgs)
        token.set(sessionToken)
        this.title.set(extension.desktopWindowTitle)
        buildDir.set(project.layout.buildDirectory)
        projectRootDir.set(project.rootDir.absolutePath)
        val isCc = try {
          val sp = project.gradle.startParameter
          val ccProp = sp.javaClass.methods.firstOrNull { it.name == "isConfigurationCache" || it.name == "isConfigurationCacheRequested" }
          ccProp?.invoke(sp) as? Boolean ?: false
        } catch (e: Exception) {
          false
        }
        this.configurationCacheEnabled.set(isCc)
        this@register.androidApplicationId.set(targetAndroidAppIdProvider)
        this@register.iosPort.set(iosPort)
        this@register.iosBundleId.set(project.provider { if (hasKmp) getIosBundleId() else "" })

        gradleAndroidSerial.set(project.providers.gradleProperty("parikshan.android.serial").orElse(project.providers.systemProperty("parikshan.android.serial")))
        gradleIosDevice.set(project.providers.gradleProperty("parikshan.ios.device").orElse(project.providers.systemProperty("parikshan.ios.device")))
        gradleDevice.set(project.providers.gradleProperty("device").orElse(project.providers.systemProperty("device")))
        gradleSerial.set(project.providers.gradleProperty("serial").orElse(project.providers.systemProperty("serial")))

        val prodSources = project.resolveProductionSources()
        this.productionSources.setFrom(prodSources)
        val testSourcesList = project.resolveTestSources()
        this.testSources.setFrom(testSourcesList)
        val prodClassesList = project.resolveProductionClassesDirs()
        this.productionClassesDirs.setFrom(prodClassesList)

        val appProject = project.findAndroidAppProject()
        if (appProject != null) {
          this.androidApkDir.set(appProject.layout.buildDirectory.dir("outputs/apk/debug"))
          this.iosAppDir.set(appProject.layout.buildDirectory.dir("cocoapods/synthetic/IOS/build/Release-iphonesimulator"))
        } else {
          this.androidApkDir.set(project.layout.buildDirectory.dir("outputs/apk/debug"))
          this.iosAppDir.set(project.layout.buildDirectory.dir("cocoapods/synthetic/IOS/build/Release-iphonesimulator"))
        }
      }

      project.tasks.configureEach {
        val jvmTargets = project.findJvmTargets()
        val e2eTaskNames = setOf("e2eWasmTest", "e2eIosTest", "e2eAndroidTest", "e2eTest") + jvmTargets.map { "e2e${it.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}Test" }
        val isE2eTask = name in e2eTaskNames
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