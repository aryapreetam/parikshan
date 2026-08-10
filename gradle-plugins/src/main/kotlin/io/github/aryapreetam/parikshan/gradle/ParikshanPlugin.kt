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
    
    val taskNames = project.gradle.startParameter.taskNames
    val isE2ERequested = taskNames.any { it.contains("e2e", ignoreCase = true) } ||
      project.hasProperty("parikshan.e2e.active")

    val cliTargetsProperty = project.findProperty("targets")?.toString()
      ?: project.findProperty("parikshan.targets")?.toString()
      ?: project.providers.systemProperty("parikshan.targets").orNull
      ?: project.gradle.startParameter.taskNames.find { it.contains("--targets=") }?.substringAfter("=")

    val targetList = cliTargetsProperty?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }

    val hasGenericE2ETask = taskNames.any { it.endsWith("e2eTest") || it.contains("e2eTest") }

    val isWasmRequested = if (targetList != null) {
      targetList.contains("wasm") || targetList.contains("web")
    } else {
      taskNames.isEmpty() || hasGenericE2ETask || taskNames.any { it.contains("wasm", ignoreCase = true) || it.endsWith("e2eWasmTest") }
    }
    val isIosRequested = if (targetList != null) {
      targetList.contains("ios") || targetList.contains("iosapp")
    } else {
      taskNames.isEmpty() || hasGenericE2ETask || taskNames.any {
        it.contains("ios", ignoreCase = true) ||
          it.contains("embedAndSign", ignoreCase = true) ||
          it.contains("appleFramework", ignoreCase = true) ||
          it.endsWith("e2eIosTest")
      }
    }
    val isAndroidRequested = if (targetList != null) {
      targetList.contains("android")
    } else {
      taskNames.isEmpty() || hasGenericE2ETask || taskNames.any { it.contains("android", ignoreCase = true) || it.endsWith("e2eAndroidTest") }
    }
    val isDesktopRequested = if (targetList != null) {
      targetList.contains("desktop") || targetList.contains("jvm")
    } else {
      taskNames.isEmpty() || hasGenericE2ETask || taskNames.any { it.contains("desktop", ignoreCase = true) || it.contains("jvm", ignoreCase = true) || it.endsWith("e2eDesktopTest") }
    }

    var prepareIosBootSourceTask: TaskProvider<Task>? = null

    project.afterEvaluate {
      val isE2EActive = isE2ERequested
      val hasKmp = project.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")
      val hasAndroid = project.pluginManager.hasPlugin("com.android.application") ||
                       project.pluginManager.hasPlugin("com.android.library") ||
                       project.pluginManager.hasPlugin("com.android.kotlin.multiplatform.library")

      if (hasKmp && project.findIosTargets().isNotEmpty() && isIosRequested && isE2EActive) {
        prepareIosBootSourceTask = project.registerParikshanIosBootSource(
          iosProjectDir = iosProjectDir,
          logger = iosLogger,
          sessionToken = sessionToken
        )
      }

      project.configureParikshanDependencies(isE2EActive)

      val e2eTestClasses = project.discoverE2eTestClasses()
      val hostTestTask = project.findOrRegisterHostTestTask(extension.desktopTestTaskName.orNull)

      val wasmOutputDirProvider = wasmOutputDir
      val gradleLogger = project.logger

      if (hasKmp) {
        if (project.findWasmTargets().isNotEmpty() || extension.wasmAppProjectPath.isPresent) {
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
        }

        project.gradle.projectsEvaluated {
          val wasmAppProject = project.resolveWasmAppProject(
            userConfiguredPath = extension.wasmAppProjectPath.orNull
          )

          if (wasmAppProject != null) {
            if (isE2ERequested && isWasmRequested) {
              gradleLogger.lifecycle(
                "Parikshan: Resolved Wasm app project: '${wasmAppProject.path}'" +
                if (wasmAppProject == project) " (current project)" else " (cross-project)"
              )
              project.registerParikshanWasmBootSource(
                logger = gradleLogger,
                wasmAppProject = wasmAppProject
              )
              val pluginVersion = ParikshanPlugin::class.java.`package`.implementationVersion ?: "0.0.1"
              wasmAppProject.addParikshanDependency("commonMainImplementation", ":parikshan-client", "io.github.aryapreetam.parikshan:parikshan-client:$pluginVersion")
              gradleLogger.lifecycle("Parikshan: Wasm instrumentation REGISTERED for project: '${wasmAppProject.path}'")
            } else {
              gradleLogger.lifecycle("Parikshan: Wasm app project found at '${wasmAppProject.path}' but e2eTest is not requested; skipping wasm instrumentation")
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
          } else if (project.findWasmTargets().isNotEmpty() || extension.wasmAppProjectPath.isPresent) {
            gradleLogger.info(
              "Parikshan: No Wasm app project found (no wasmJsBrowserDevelopmentWebpack task detected). " +
              "e2eWasmTest will fail at execution time. " +
              "To configure manually: parikshan { wasmAppProjectPath = \":your:webApp\" }"
            )
          }
        }

        val jvmTestRuntimeConfig = project.configurations.findByName("jvmTestRuntimeClasspath")
          ?: project.configurations.findByName("desktopTestRuntimeClasspath")

        installPlaywrightTask.configure {
          if (jvmTestRuntimeConfig != null) {
            classpath = jvmTestRuntimeConfig
          } else {
            val clientProjForPlaywright = project.rootProject.findProject(":parikshan-client")
            if (clientProjForPlaywright != null) {
              val clientConfig = clientProjForPlaywright.configurations.findByName("jvmRuntimeElements")
              if (clientConfig != null) {
                classpath = project.files(clientConfig)
              }
            }
          }
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

        val iosTargets = project.findIosTargets()
        if (iosTargets.isNotEmpty()) {
          val (isIosSupported, iosReason) = checkIosHostSupport(iosTargets)
          if (isIosSupported) {
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
          } else {
            gradleLogger.lifecycle("Parikshan iOS: Skipping iOS target task registration — $iosReason")
          }
        }
      }

      if (hasAndroid) {
        try {
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
        } catch (e: Throwable) {
          project.logger.warn("Parikshan Android: Skipped Android target task configuration: ${e.message}")
        }
      }

      fun getAndroidAppId(): String? {
        val directId = AndroidTargetConfigurer.AndroidRecorder.resolveAndroidApplicationId(project)
        if (directId != null) return directId

        val mergedManifestDir = AndroidComponentsHelper.getMergedManifestDirectory(project)
        val manifestDir = mergedManifestDir.orNull?.asFile
        if (manifestDir != null) {
          val manifestFile = File(manifestDir, "AndroidManifest.xml")
          val parsed = AndroidTargetConfigurer.parseAndroidManifest(manifestFile, project.logger, null)
          return parsed.packageName
        }
        return null
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
        project.logger.info("Parikshan iOS: Extracted bundle ID: $extracted")
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
        
        doFirst {
          resultsDir.get().asFile.mkdirs()
          reportsDir.get().asFile.mkdirs()
        }
        inputs.dir(resultsDir).optional()
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
        
        dependsOn(hostTestTask.map { it.testClassesDirs.buildDependencies })
        if (hasKmp) {
          val jarTaskName = extension.appJarTaskName.get()
          val desktopAppProject = project.resolveDesktopAppProject(
              userConfiguredPath = extension.desktopAppProjectPath.orNull
          )
          val wasmTask = prepareWasmAssetsTask
          val playwrightTask = installPlaywrightTask
          val jarTask = if (desktopAppProject != null && desktopAppProject.tasks.names.contains(jarTaskName)) desktopAppProject.tasks.named(jarTaskName) else null

          dependsOn(project.providers.provider {
            val activeList = targets.lowercase().split(",").map { it.trim() }
            val tasksToDependOn = mutableListOf<Any>()
            val needsWasm = activeList.isEmpty() || activeList.contains("wasm") || activeList.contains("web") || activeList.contains("all")
            if (needsWasm) {
              wasmTask?.let { tasksToDependOn.add(it) }
              playwrightTask?.let { tasksToDependOn.add(it) }
            }
            val needsDesktop = activeList.isEmpty() || activeList.contains("desktop") || activeList.contains("jvm") || activeList.contains("all")
            if (needsDesktop) {
              jarTask?.let { tasksToDependOn.add(it) }
            }
            tasksToDependOn
          })
        }
        
        val compileTestTask = project.tasks.findByName("compileTestKotlinJvm")
          ?: project.tasks.findByName("jvmTestClasses")
        val e2eTestDirs = if (compileTestTask != null) {
          project.files(compileTestTask.outputs.files.filter { it.isDirectory })
        } else {
          project.files(hostTestTask.flatMap { project.provider { it.testClassesDirs.files } })
        }

        hostTestClassesDirs.setFrom(e2eTestDirs)
        val jvmRuntimeConfig = project.configurations.findByName("jvmTestRuntimeClasspath")
          ?: project.configurations.findByName("desktopTestRuntimeClasspath")

        val hostClasspathList = mutableListOf<Any>()
        val clientProject = project.rootProject.findProject(":parikshan-client")
        if (clientProject != null) {
          val clientDep = project.dependencies.project(mapOf("path" to clientProject.path))
          val hostJvmConfig = project.configurations.detachedConfiguration(clientDep)
          hostClasspathList.add(hostJvmConfig.incoming.files)
        }
        if (jvmRuntimeConfig != null) {
          hostClasspathList.add(project.files(jvmRuntimeConfig))
          hostClasspathList.add(e2eTestDirs)
        } else {
          hostClasspathList.add(hostTestTask.flatMap { project.provider { it.classpath.files } })
        }

        hostTestClasspath.setFrom(hostClasspathList)
        junitConsoleJars.setFrom(junitConsoleConfig.incoming.files)
        this.e2eTestClasses.set(e2eTestClasses)
        this.projectPath.set(project.path)
        this.host.set(extension.host)
        this.originalDesktopPort.set(extension.port)
        this.originalWasmPort.set(extension.wasmServerPort)
        this.androidPort.set(extension.androidPort)
        this.iosPort.set(extension.iosPort)
        
        val configuredTargets = buildList {
          if (hasKmp) {
            addAll(project.findJvmTargets())
            if (project.findWasmTargets().isNotEmpty() || project.resolveWasmAppProject(extension.wasmAppProjectPath.orNull) != null) {
              add("wasm")
            }
          }
          if (hasAndroid) {
            add("android")
          }
          if (hasKmp) {
            val iosTargets = project.findIosTargets()
            if (iosTargets.isNotEmpty() && checkIosHostSupport(iosTargets).first) {
              add("ios")
            }
          }
        }.distinct()
        
        val defaultTargets = configuredTargets.joinToString(",")
        this.targets = defaultTargets
        this.jvmTargets.set(project.findJvmTargets())

        val currentProjectPath = project.path
        doFirst {
          val validVocabulary = setOf("desktop", "jvm", "wasm", "web", "android", "ios")
          val requested = targets.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
          val invalidVocab = requested.filter { it !in validVocabulary }
          if (invalidVocab.isNotEmpty()) {
            throw GradleException(
              "Parikshan: Unknown target(s) specified in --targets: ${invalidVocab.joinToString()}. " +
              "Valid targets: ${validVocabulary.joinToString()}"
            )
          }

          val availableNormalized = configuredTargets.map { it.lowercase() }
          val unconfigured = requested.filter { req ->
            when (req) {
              "desktop", "jvm" -> availableNormalized.none { it in listOf("desktop", "jvm") }
              "wasm", "web" -> availableNormalized.none { it in listOf("wasm", "web") }
              else -> req !in availableNormalized
            }
          }
          if (unconfigured.isNotEmpty()) {
            throw GradleException(
              "Parikshan: Target(s) [${unconfigured.joinToString()}] requested via --targets, " +
              "but project '$currentProjectPath' does not configure them. " +
              "Configured target(s): [${configuredTargets.joinToString()}]"
            )
          }
        }
        
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
        // Defer Android application ID extraction to lazy provider to avoid manifest parsing unless Android is requested
        val androidAppIdProvider = project.provider {
          if (hasAndroid && isAndroidRequested) {
            getAndroidAppId()
          } else {
            null
          }
        }
        this@register.androidApplicationId.set(androidAppIdProvider)
        this@register.iosPort.set(iosPort)
        // Defer iOS bundle ID extraction to lazy provider so xcodebuild doesn't run unless iOS is requested
        val iosBundleIdProvider = project.provider {
          if (hasKmp && project.findIosTargets().isNotEmpty() && isIosRequested) {
            getIosBundleId()
          } else {
            ""
          }
        }
        this@register.iosBundleId.set(iosBundleIdProvider)

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
        val appBuildDir = appProject?.layout?.buildDirectory?.orNull?.asFile ?: project.layout.buildDirectory.get().asFile
        this.androidApkDir.set(File(appBuildDir, "outputs/apk/debug"))
        this.iosAppDir.set(File(project.layout.buildDirectory.get().asFile, "parikshan/ios-build/Build/Products/Debug-iphonesimulator"))
      }

      project.tasks.configureEach {
        val jvmTargets = project.findJvmTargets()
        val e2eTaskNames = setOf("e2eWasmTest", "e2eIosTest", "e2eAndroidTest", "e2eTest", "parikshanHostTest") + jvmTargets.map { "e2e${it.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}Test" }
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