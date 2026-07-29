package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

internal fun Test.configureE2eHostTestExecution(
  hostTestClassesDirs: FileCollection,
  hostTestClasspath: FileCollection,
  e2eTestClasses: List<String>,
  target: String,
  logger: Logger
) {
  outputs.upToDateWhen { false }
  val videoOutputDirProvider = project.providers.gradleProperty("parikshan.video.outputDir")
      .orElse(project.providers.systemProperty("parikshan.video.outputDir"))
      .map { File(it) }
      .orElse(project.layout.buildDirectory.dir("parikshan/videos/${target.lowercase()}").map { it.asFile })

  val hasKotlinExtension = project.extensions.findByName("kotlin") != null
  val sourceDirName = if (hasKotlinExtension) "src/commonTest" else "src/test"
  val videoStrategyProvider = project.providers.gradleProperty("parikshan.video.granularity")
      .orElse(project.providers.systemProperty("parikshan.video.granularity"))
      .orElse("class")

  testClassesDirs = hostTestClassesDirs
  val launcherConfig = project.configurations.detachedConfiguration(
      project.dependencies.create("org.junit.platform:junit-platform-launcher:1.10.2"),
      project.dependencies.create("org.junit.vintage:junit-vintage-engine:5.10.2")
  )
  classpath = hostTestClasspath.plus(launcherConfig)
  reports.junitXml.outputLocation.set(
    project.layout.buildDirectory.dir("test-results/e2eTest/${target.lowercase()}")
  )
  dependsOn(hostTestClassesDirs.buildDependencies)

  filter {
    isFailOnNoMatchingTests = true
    if (includePatterns.isEmpty()) {
        e2eTestClasses.forEach { includeTestsMatching(it) }
    }
  }

  doFirst {
    if (e2eTestClasses.isEmpty()) {
      throw GradleException("No E2E test classes discovered in $sourceDirName containing 'e2eTest { ... }' invocation.")
    }

    val existingClassDirs = testClassesDirs.files.filter { it.exists() }
    if (existingClassDirs.isEmpty()) {
      throw GradleException(
        "Parikshan $target: host test classes were not compiled."
      )
    }
    logger.lifecycle("Parikshan $target: running E2E test classes ${e2eTestClasses.joinToString()}")

    val strategy = videoStrategyProvider.get().lowercase()
    if (strategy == "class" || strategy == "run" || strategy == "session") {
      val patterns = filter.includePatterns
      val isMethodFilter = patterns.any { pattern ->
        val lastDot = pattern.lastIndexOf('.')
        if (lastDot >= 0) {
          val method = pattern.substring(lastDot + 1)
          method.isNotEmpty() && method.firstOrNull()?.isLowerCase() == true
        } else false
      }
      if (isMethodFilter) {
        logger.warn("Parikshan: Method-level test filter is active, but video granularity is set to '${strategy.uppercase()}'. A class/run scoped video will be recorded containing only this method.")
      }
    }

    val outDir = videoOutputDirProvider.get()
    val indexFile = File(outDir, "video-index.txt")
    if (indexFile.exists()) {
      indexFile.delete()
    }

    val resultsDir = reports.junitXml.outputLocation.get().asFile
    if (resultsDir.exists()) {
      resultsDir.listFiles()?.forEach { it.deleteRecursively() }
    }
  }
  outputs.dir(videoOutputDirProvider)
  
  // Forward all parikshan.* properties to the test JVM.
  val propsToForward = listOf(
    "parikshan.target",
    "parikshan.token",
    "parikshan.video.enabled",
    "parikshan.video.fps",
    "parikshan.video.showCursor",
    "parikshan.video.stepDelayMs",
    "parikshan.video.postRollMs",
    "parikshan.video.granularity",
    "parikshan.video.width",
    "parikshan.video.height",
    "parikshan.wasm.url",
    "parikshan.wasm.headless",
    "parikshan.wasm.viewportWidth",
    "parikshan.wasm.viewportHeight",
    "parikshan.wasm.bridgeReadyTimeoutMs",
    "parikshan.ios.device",
    "parikshan.ios.port",
    "parikshan.ios.bundleId",
    "parikshan.ios.xcodeProject",
    "parikshan.ios.xcodeScheme",
    "parikshan.ios.udid",
    "parikshan.android.serial",
    "parikshan.keepAlive"
  )

  for (propName in propsToForward) {
    val provider = project.providers.gradleProperty(propName)
      .orElse(project.providers.systemProperty(propName))
      .orElse("")

    val resolved = provider.orNull ?: ""
    if (resolved.isNotEmpty()) {
      systemProperty(propName, resolved)
    }
  }

  jvmArgumentProviders.add(CommandLineArgumentProvider {
    listOf("-Dparikshan.video.outputDir=${videoOutputDirProvider.get().absolutePath}")
  })
}

internal fun Project.resolveHostTestTaskName(override: String?): String {
  if (override != null) return override
  val kmp = extensions.findByName("kotlin")
  val isKmp = kmp != null && runCatching { kmp.javaClass.getMethod("getTargets") }.isSuccess

  if (isKmp && kmp != null) {
    try {
      @Suppress("UNCHECKED_CAST")
      val targets = kmp.javaClass.getMethod("getTargets").invoke(kmp) as NamedDomainObjectCollection<Any>
      
      val jvmTargets = targets.filter { target ->
          val targetName = (target as Named).name
          val className = target.javaClass.name
          className.contains("KotlinJvmTarget", ignoreCase = true) || 
                    targetName.contains("jvm", ignoreCase = true) || 
                    targetName.contains("desktop", ignoreCase = true)
      }
      
      val target = jvmTargets.find { (it as Named).name in listOf("desktop", "jvm") }
          ?: jvmTargets.firstOrNull()
          
      if (target != null) {
          val name = (target as Named).name
          return "${name}Test"
      }
    } catch (_: Exception) {}
  }

  return when {
      tasks.names.contains("testDebugUnitTest") -> "testDebugUnitTest"
      tasks.names.contains("test") -> "test"
      tasks.names.contains("jvmTest") -> "jvmTest"
      else -> "test"
  }
}

internal fun Project.resolveWasmDistributionTaskName(override: String?): String {
  if (override != null) return override
  val kmp = extensions.findByName("kotlin") ?: return "wasmJsBrowserDevelopmentWebpack"
  try {
      @Suppress("UNCHECKED_CAST")
      val targets = kmp.javaClass.getMethod("getTargets").invoke(kmp) as NamedDomainObjectCollection<Any>
      val wasmTargets = targets.filter { it.javaClass.name.contains("KotlinWasm", ignoreCase = true) }
      if (wasmTargets.size == 1) {
          val name = (wasmTargets[0] as Named).name
          return "${name}BrowserDevelopmentWebpack"
      }
  } catch (_: Exception) { }
  return "wasmJsBrowserDevelopmentWebpack"
}

internal fun Project.findAndroidAppProject(): Project? {
    if (pluginManager.hasPlugin("com.android.application")) return this
    return rootProject.subprojects.find { it.pluginManager.hasPlugin("com.android.application") }
}

internal fun Project.resolveDesktopAppProject(
    userConfiguredPath: String?
): Project? {
    val appJarTaskName = "packageUberJarForCurrentOS"
    
    // Step 1: Explicit user configuration (highest priority)
    if (!userConfiguredPath.isNullOrEmpty()) {
        val configuredProject = rootProject.findProject(userConfiguredPath)
        if (configuredProject != null && configuredProject.tasks.names.contains(appJarTaskName)) {
            return configuredProject
        }
        return configuredProject  // Return even if task not found, let caller handle error
    }
    
    // Step 2: Check current project
    if (this.tasks.names.contains(appJarTaskName)) {
        return this
    }
    
    // Step 3: Check parent sibling (for nested projects like :app:shared → :app:desktopApp)
    val parentPath = this.path.substringBeforeLast(":")
    if (parentPath.isNotEmpty()) {
        val possibleSiblings = listOf(
            "${parentPath}:desktopApp",
            "${parentPath}:desktop"
        )
        for (siblingPath in possibleSiblings) {
            val sibling = rootProject.findProject(siblingPath)
            if (sibling != null && sibling.tasks.names.contains(appJarTaskName)) {
                return sibling
            }
        }
    }
    
    // Step 4: Check root level
    val rootLevelApps = listOf(":desktopApp", ":desktop")
    for (appPath in rootLevelApps) {
        val app = rootProject.findProject(appPath)
        if (app != null && app.tasks.names.contains(appJarTaskName)) {
            return app
        }
    }
    
    // No desktop app found - return null for caller to handle error
    return null
}

internal fun Project.resolveWasmAppProject(
    userConfiguredPath: String?
): Project? {
    val markerTaskName = "wasmJsBrowserDevelopmentWebpack"

    // Step 1: Explicit user configuration (highest priority)
    if (!userConfiguredPath.isNullOrEmpty()) {
        val configuredProject = rootProject.findProject(userConfiguredPath)
        if (configuredProject != null && configuredProject.tasks.names.contains(markerTaskName)) {
            return configuredProject
        }
        return configuredProject
    }

    // Step 2: Check current project (e.g., single-module where wasmJs is here)
    if (this.tasks.names.contains(markerTaskName)) {
        return this
    }

    // Step 3: Check parent sibling (e.g., :app:shared → :app:webApp)
    val parentPath = this.path.substringBeforeLast(":")
    if (parentPath.isNotEmpty()) {
        val possibleSiblings = listOf(
            "${parentPath}:webApp",
            "${parentPath}:web"
        )
        for (siblingPath in possibleSiblings) {
            val sibling = rootProject.findProject(siblingPath)
            if (sibling != null && sibling.tasks.names.contains(markerTaskName)) {
                return sibling
            }
        }
    }

    // Step 4: Check root level (e.g., :webApp at root)
    val rootLevelApps = listOf(":webApp", ":web")
    for (appPath in rootLevelApps) {
        val app = rootProject.findProject(appPath)
        if (app != null && app.tasks.names.contains(markerTaskName)) {
            return app
        }
    }

    return null
}

internal fun Project.discoverE2eTestClasses(): List<String> {
  val dirsToCheck = mutableListOf<File>()
  val hasKmp = pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")
  if (hasKmp) {
      dirsToCheck.add(layout.projectDirectory.dir("src/commonTest/kotlin").asFile)
  } else {
      dirsToCheck.add(layout.projectDirectory.dir("src/test/java").asFile)
      dirsToCheck.add(layout.projectDirectory.dir("src/test/kotlin").asFile)
  }

  return dirsToCheck
    .filter { it.exists() }
    .flatMap { dir ->
        dir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .flatMap { file -> discoverE2eTestClassesInFile(file).asSequence() }
            .toList()
    }
    .distinct()
    .sorted()
}

private fun discoverE2eTestClassesInFile(file: File): List<String> {
  val raw = file.readText()
  val source = raw.maskKotlinCommentsAndLiterals()
  if (!E2E_TEST_INVOCATION_REGEX.containsMatchIn(source)) {
    return emptyList()
  }

  val pkg = PACKAGE_REGEX.find(source)?.groupValues?.get(1).orEmpty()
  return CLASS_OR_OBJECT_REGEX
    .findAll(source)
    .mapNotNull { match ->
      val className = match.groupValues[1]
      val bodyStart = source.indexOf('{', startIndex = match.range.last + 1)
      if (bodyStart < 0) {
        return@mapNotNull null
      }
      val bodyEnd = source.findMatchingBrace(bodyStart)
      if (bodyEnd < 0) {
        return@mapNotNull null
      }
      val body = source.substring(bodyStart + 1, bodyEnd)
      if (!E2E_TEST_INVOCATION_REGEX.containsMatchIn(body)) {
        return@mapNotNull null
      }
      if (pkg.isNotEmpty()) "$pkg.$className" else className
    }
    .toList()
}

private val PACKAGE_REGEX = Regex("""\bpackage\s+([a-zA-Z_][a-zA-Z0-9_.]*)""")
private val CLASS_OR_OBJECT_REGEX = Regex("""\b(?:class|object)\s+([a-zA-Z_][a-zA-Z0-9_]*)""")
private val E2E_TEST_INVOCATION_REGEX = Regex("""(?<!\w)e2eTest\s*(?:\(|\{)""")

private fun String.findMatchingBrace(openIndex: Int): Int {
  var depth = 0
  for (index in openIndex until length) {
    when (this[index]) {
      '{' -> depth += 1
      '}' -> {
        depth -= 1
        if (depth == 0) return index
      }
    }
  }
  return -1
}

private fun String.maskKotlinCommentsAndLiterals(): String {
  val output = StringBuilder(length)
  var index = 0

  fun appendMasked(char: Char) {
    output.append(if (char == '\n' || char == '\r') char else ' ')
  }

  fun startsWithAt(value: String, startIndex: Int): Boolean =
    startIndex + value.length <= length && regionMatches(startIndex, value, 0, value.length)

  while (index < length) {
    when {
      startsWithAt("//", index) -> {
        appendMasked(this[index])
        appendMasked(this[index + 1])
        index += 2
        while (index < length && this[index] != '\n') {
          appendMasked(this[index])
          index += 1
        }
      }
      startsWithAt("/*", index) -> {
        appendMasked(this[index])
        appendMasked(this[index + 1])
        index += 2
        while (index < length) {
          if (startsWithAt("*/", index)) {
            appendMasked(this[index])
            appendMasked(this[index + 1])
            index += 2
            break
          }
          appendMasked(this[index])
          index += 1
        }
      }
      startsWithAt("\"\"\"", index) -> {
        repeat(3) {
          appendMasked(this[index])
          index += 1
        }
        while (index < length) {
          if (startsWithAt("\"\"\"", index)) {
            repeat(3) {
              appendMasked(this[index])
              index += 1
            }
            break
          }
          appendMasked(this[index])
          index += 1
        }
      }
      this[index] == '"' -> {
        appendMasked(this[index])
        index += 1
        var javaEscaped = false
        while (index < length) {
          val char = this[index]
          appendMasked(char)
          index += 1
          if (javaEscaped) {
            javaEscaped = false
          } else if (char == '\\') {
            javaEscaped = true
          } else if (char == '"') {
            break
          }
        }
      }
      this[index] == '\'' -> {
        appendMasked(this[index])
        index += 1
        var javaEscaped = false
        while (index < length) {
          val char = this[index]
          appendMasked(char)
          index += 1
          if (javaEscaped) {
            javaEscaped = false
          } else if (char == '\\') {
            javaEscaped = true
          } else if (char == '\'') {
            break
          }
        }
      }
      else -> {
        output.append(this[index])
        index += 1
      }
    }
  }

  return output.toString()
}

internal fun Project.configureParikshanDependencies(isE2EActive: Boolean) {
  // Resolve version dynamically from loaded plugin class metadata.
  val pluginVersion = ParikshanPlugin::class.java.`package`.implementationVersion ?: "0.0.1"

  val hasKmp = pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")
  if (hasKmp) {
      addParikshanDependency("commonTestImplementation", ":parikshan", "io.github.aryapreetam:parikshan:$pluginVersion")
      addParikshanDependency("commonTestImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client:$pluginVersion")
  } else if (pluginManager.hasPlugin("com.android.application")) {
      addParikshanDependency("testImplementation", ":parikshan", "io.github.aryapreetam:parikshan-jvm:$pluginVersion")
      addParikshanDependency("testImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client-jvm:$pluginVersion")
      addParikshanDependency("testImplementation", "org.junit.platform:junit-platform-launcher:1.10.2", "org.junit.platform:junit-platform-launcher:1.10.2")
      addParikshanDependency("testImplementation", "org.junit.vintage:junit-vintage-engine:5.10.2", "org.junit.vintage:junit-vintage-engine:5.10.2")
  }

  if (!hasKmp) {
      configurations.configureEach {
          if (name.contains("UnitTest", ignoreCase = true) || name.startsWith("test")) {
              exclude(mapOf("group" to "io.github.aryapreetam", "module" to "parikshan-client"))
              exclude(mapOf("group" to "io.github.aryapreetam", "module" to "parikshan"))
          }
      }
  } else {
      configurations.configureEach {
          if (name.startsWith("jvmTest")) {
              exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-test-junit"))
          }
      }
  }

  // The client engine is only injected into the production binary during active E2E tasks.
  if (isE2EActive) {
      if (hasKmp) {
          addParikshanDependency("commonMainImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client:$pluginVersion")
      } else if (pluginManager.hasPlugin("com.android.application") || pluginManager.hasPlugin("com.android.library")) {
          try {
              val manifestDir = File(project.layout.buildDirectory.asFile.get(), "parikshan/manifest")
              manifestDir.mkdirs()
              val manifestFile = File(manifestDir, "AndroidManifest.xml")
              manifestFile.writeText(
                  """
                  <?xml version="1.0" encoding="utf-8"?>
                  <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                      <uses-permission android:name="android.permission.INTERNET" />
                  </manifest>
                  """.trimIndent()
              )
              val android = extensions.findByName("android")
              if (android != null) {
                  val getSourceSets = android.javaClass.getMethod("getSourceSets")
                  val sourceSets = getSourceSets.invoke(android) as? NamedDomainObjectCollection<*>
                  if (sourceSets != null) {
                      val debugSourceSet = sourceSets.findByName("debug")
                      if (debugSourceSet != null) {
                          val getManifest = debugSourceSet.javaClass.getMethod("getManifest")
                          val manifest = getManifest.invoke(debugSourceSet)
                          val srcFileMethod = manifest.javaClass.getMethod("srcFile", Any::class.java)
                          srcFileMethod.invoke(manifest, manifestFile)
                      }
                  }
              }
          } catch (e: Exception) {
              logger.warn("Parikshan: Failed to dynamically inject E2E manifest with INTERNET permission", e)
          }
      }
      
      // Inject server into all JVM targets
      findJvmTargets().forEach { targetName ->
          addParikshanDependency("${targetName}MainImplementation", ":parikshan-server", "io.github.aryapreetam:parikshan-server:$pluginVersion")
      }

      // Fallback for non-KMP or failed resolution
      if (configurations.findByName("jvmMainImplementation") != null) {
          addParikshanDependency("jvmMainImplementation", ":parikshan-server", "io.github.aryapreetam:parikshan-server:$pluginVersion")
      }

      val appProject = findAndroidAppProject()
      if (appProject != null) {
          appProject.configurations.configureEach {
              if (name == "androidTestImplementation") {
                  appProject.addParikshanDependency("androidTestImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client:$pluginVersion")
                  appProject.addParikshanDependency("androidTestImplementation", "androidx.test:runner:1.6.2", "androidx.test:runner:1.6.2")
              }
          }
      }
  }
}

internal fun Project.findJvmTargets(): List<String> {
  val jvmTargetNames = mutableListOf<String>()
  val kmp = extensions.findByName("kotlin")
  if (kmp != null) {
    try {
      @Suppress("UNCHECKED_CAST")
      val targets = kmp.javaClass.getMethod("getTargets").invoke(kmp) as NamedDomainObjectCollection<Any>
      targets.forEach { target ->
        val targetName = (target as Named).name
        val className = target.javaClass.name
        if (className.contains("KotlinJvmTarget", ignoreCase = true) || 
            targetName.contains("jvm", ignoreCase = true) || 
            targetName.contains("desktop", ignoreCase = true)) {
          jvmTargetNames.add(targetName)
        }
      }
    } catch (_: Exception) {}
  }
  return jvmTargetNames
}

internal fun Project.addParikshanDependency(config: String, path: String, maven: String) {
  val dep = rootProject.findProject(path)?.let { dependencies.project(mapOf("path" to it.path)) } ?: maven
  val configuration = configurations.findByName(config)
  if (configuration == null) {
    logger.lifecycle("Parikshan: Configuration '$config' NOT found in project '${this.path}'")
    return
  }
  logger.lifecycle("Parikshan: Adding dependency to project '${this.path}': configuration='$config', dependency='$dep'")
  dependencies.add(config, dep)
}

internal fun Project.registerE2eTestWithReport(
  name: String,
  target: String,
  configure: Test.() -> Unit
): TaskProvider<Test> {
  val reportTaskName = "e2e${target}TestReport"
  val reportTask = tasks.register(reportTaskName) {
    group = "verification"
    description = "Generates a Parikshan E2E HTML report for the $target target."
    val resultsDir = layout.buildDirectory.dir("test-results/e2eTest/${target.lowercase()}")
    val reportsDir = layout.buildDirectory.dir("reports/tests/e2eTest/${target.lowercase()}")
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

  val testTask = tasks.register(name, Test::class.java) {
    useJUnitPlatform()
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
    finalizedBy(reportTask)
    configure()
  }
  return testTask
}

internal fun Project.resolveProductionSources(): List<File> {
  val dirs = mutableListOf<File>()
  
  // 1. Kotlin Multiplatform Extension resolution (Layout-agnostic)
  val kmp = extensions.findByName("kotlin")
  if (kmp != null) {
    try {
      @Suppress("UNCHECKED_CAST")
      val targets = kmp.javaClass.getMethod("getTargets").invoke(kmp) as org.gradle.api.NamedDomainObjectCollection<Any>
      targets.forEach { target ->
        runCatching {
          @Suppress("UNCHECKED_CAST")
          val compilations = target.javaClass.getMethod("getCompilations").invoke(target) as org.gradle.api.NamedDomainObjectCollection<Any>
          val mainCompilation = compilations.findByName("main")
          if (mainCompilation != null) {
            @Suppress("UNCHECKED_CAST")
            val allSourceSets = mainCompilation.javaClass.getMethod("getAllKotlinSourceSets").invoke(mainCompilation) as Set<Any>
            allSourceSets.forEach { sourceSet ->
              runCatching {
                val kotlinSrcSet = sourceSet.javaClass.getMethod("getKotlin").invoke(sourceSet) as? org.gradle.api.file.SourceDirectorySet
                kotlinSrcSet?.srcDirs?.let { dirs.addAll(it) }
              }
              runCatching {
                val resourcesSrcSet = sourceSet.javaClass.getMethod("getResources").invoke(sourceSet) as? org.gradle.api.file.SourceDirectorySet
                resourcesSrcSet?.srcDirs?.let { dirs.addAll(it) }
              }
            }
          }
        }
      }
    } catch (_: Exception) {
    }
  }

  // 2. Standard Android plugin resolution
  val android = extensions.findByName("android")
  if (android != null) {
    try {
      @Suppress("UNCHECKED_CAST")
      val sourceSets = android.javaClass.getMethod("getSourceSets").invoke(android) as org.gradle.api.NamedDomainObjectCollection<Any>
      val mainSourceSet = sourceSets.findByName("main")
      if (mainSourceSet != null) {
        runCatching {
          val javaSrcSet = mainSourceSet.javaClass.getMethod("getJava").invoke(mainSourceSet) as? org.gradle.api.file.SourceDirectorySet
          javaSrcSet?.srcDirs?.let { dirs.addAll(it) }
        }
        runCatching {
          val resSrcSet = mainSourceSet.javaClass.getMethod("getRes").invoke(mainSourceSet) as? org.gradle.api.file.SourceDirectorySet
          resSrcSet?.srcDirs?.let { dirs.addAll(it) }
        }
      }
    } catch (_: Exception) {
    }
  }

  // 3. Standard Java plugin resolution
  val java = extensions.findByName("java")
  if (java != null) {
    try {
      @Suppress("UNCHECKED_CAST")
      val sourceSets = java.javaClass.getMethod("getSourceSets").invoke(java) as org.gradle.api.NamedDomainObjectCollection<Any>
      val mainSourceSet = sourceSets.findByName("main")
      if (mainSourceSet != null) {
        runCatching {
          val allJava = mainSourceSet.javaClass.getMethod("getAllJava").invoke(mainSourceSet) as? org.gradle.api.file.SourceDirectorySet
          allJava?.srcDirs?.let { dirs.addAll(it) }
        }
        runCatching {
          val resources = mainSourceSet.javaClass.getMethod("getResources").invoke(mainSourceSet) as? org.gradle.api.file.SourceDirectorySet
          resources?.srcDirs?.let { dirs.addAll(it) }
        }
      }
    } catch (_: Exception) {
    }
  }

  return dirs.filter { it.exists() }.distinct()
}

internal fun Project.resolveProductionClassesDirs(): List<File> {
  val dirs = mutableListOf<File>()
  val kmp = extensions.findByName("kotlin")
  if (kmp != null) {
    try {
      @Suppress("UNCHECKED_CAST")
      val targets = kmp.javaClass.getMethod("getTargets").invoke(kmp) as org.gradle.api.NamedDomainObjectCollection<Any>
      targets.forEach { target ->
        runCatching {
          @Suppress("UNCHECKED_CAST")
          val compilations = target.javaClass.getMethod("getCompilations").invoke(target) as org.gradle.api.NamedDomainObjectCollection<Any>
          val mainCompilation = compilations.findByName("main")
          if (mainCompilation != null) {
            val output = mainCompilation.javaClass.getMethod("getOutput").invoke(mainCompilation)
            val classesDirs = output.javaClass.getMethod("getClassesDirs").invoke(output) as org.gradle.api.file.FileCollection
            dirs.addAll(classesDirs.files)
          }
        }
      }
    } catch (_: Exception) {}
  }
  
  val android = extensions.findByName("android")
  if (android != null) {
    try {
      val sourceSets = android.javaClass.getMethod("getSourceSets").invoke(android) as org.gradle.api.NamedDomainObjectCollection<Any>
      val mainSourceSet = sourceSets.findByName("main")
      if (mainSourceSet != null) {
        val output = mainSourceSet.javaClass.getMethod("getOutput").invoke(mainSourceSet)
        val classesDirs = output.javaClass.getMethod("getClassesDirs").invoke(output) as org.gradle.api.file.FileCollection
        dirs.addAll(classesDirs.files)
      }
    } catch (_: Exception) {}
  }

  val java = extensions.findByName("java")
  if (java != null) {
    try {
      val sourceSets = java.javaClass.getMethod("getSourceSets").invoke(java) as org.gradle.api.NamedDomainObjectCollection<Any>
      val mainSourceSet = sourceSets.findByName("main")
      if (mainSourceSet != null) {
        val output = mainSourceSet.javaClass.getMethod("getOutput").invoke(mainSourceSet)
        val classesDirs = output.javaClass.getMethod("getClassesDirs").invoke(output) as org.gradle.api.file.FileCollection
        dirs.addAll(classesDirs.files)
      }
    } catch (_: Exception) {}
  }

  return dirs.filter { it.exists() }.distinct()
}


internal fun Project.resolveTestSources(): List<File> {
  val dirs = mutableListOf<File>()
  
  // 1. Kotlin Multiplatform Extension resolution (Layout-agnostic)
  val kmp = extensions.findByName("kotlin")
  if (kmp != null) {
    try {
      @Suppress("UNCHECKED_CAST")
      val targets = kmp.javaClass.getMethod("getTargets").invoke(kmp) as org.gradle.api.NamedDomainObjectCollection<Any>
      targets.forEach { target ->
        runCatching {
          @Suppress("UNCHECKED_CAST")
          val compilations = target.javaClass.getMethod("getCompilations").invoke(target) as org.gradle.api.NamedDomainObjectCollection<Any>
          val testCompilation = compilations.findByName("test") ?: compilations.findByName("androidTest")
          if (testCompilation != null) {
            @Suppress("UNCHECKED_CAST")
            val allSourceSets = testCompilation.javaClass.getMethod("getAllKotlinSourceSets").invoke(testCompilation) as Set<Any>
            allSourceSets.forEach { sourceSet ->
              runCatching {
                val kotlinSrcSet = sourceSet.javaClass.getMethod("getKotlin").invoke(sourceSet) as? org.gradle.api.file.SourceDirectorySet
                kotlinSrcSet?.srcDirs?.let { dirs.addAll(it) }
              }
              runCatching {
                val resourcesSrcSet = sourceSet.javaClass.getMethod("getResources").invoke(sourceSet) as? org.gradle.api.file.SourceDirectorySet
                resourcesSrcSet?.srcDirs?.let { dirs.addAll(it) }
              }
            }
          }
        }
      }
    } catch (_: Exception) {
    }
  }

  // 2. Standard Android plugin resolution
  val android = extensions.findByName("android")
  if (android != null) {
    try {
      @Suppress("UNCHECKED_CAST")
      val sourceSets = android.javaClass.getMethod("getSourceSets").invoke(android) as org.gradle.api.NamedDomainObjectCollection<Any>
      val testSourceSet = sourceSets.findByName("androidTest") ?: sourceSets.findByName("test")
      if (testSourceSet != null) {
        runCatching {
          val javaSrcSet = testSourceSet.javaClass.getMethod("getJava").invoke(testSourceSet) as? org.gradle.api.file.SourceDirectorySet
          javaSrcSet?.srcDirs?.let { dirs.addAll(it) }
        }
        runCatching {
          val resSrcSet = testSourceSet.javaClass.getMethod("getRes").invoke(testSourceSet) as? org.gradle.api.file.SourceDirectorySet
          resSrcSet?.srcDirs?.let { dirs.addAll(it) }
        }
      }
    } catch (_: Exception) {
    }
  }

  // 3. Standard Java plugin resolution
  val java = extensions.findByName("java")
  if (java != null) {
    try {
      @Suppress("UNCHECKED_CAST")
      val sourceSets = java.javaClass.getMethod("getSourceSets").invoke(java) as org.gradle.api.NamedDomainObjectCollection<Any>
      val testSourceSet = sourceSets.findByName("test")
      if (testSourceSet != null) {
        runCatching {
          val allJava = testSourceSet.javaClass.getMethod("getAllJava").invoke(testSourceSet) as? org.gradle.api.file.SourceDirectorySet
          allJava?.srcDirs?.let { dirs.addAll(it) }
        }
        runCatching {
          val resources = testSourceSet.javaClass.getMethod("getResources").invoke(testSourceSet) as? org.gradle.api.file.SourceDirectorySet
          resources?.srcDirs?.let { dirs.addAll(it) }
        }
      }
    } catch (_: Exception) {
    }
  }

  return dirs.filter { it.exists() }.distinct()
}


internal fun parseSize(input: String?): Pair<Int, Int>? {
  if (input.isNullOrBlank()) return null
  val regex = Regex("^(\\d+)x(\\d+)$")
  val match = regex.matchEntire(input.trim()) ?: return null
  val width = match.groupValues[1].toIntOrNull() ?: return null
  val height = match.groupValues[2].toIntOrNull() ?: return null
  return Pair(width, height)
}

internal fun parsePosition(input: String?): Pair<Int, Int>? {
  if (input.isNullOrBlank()) return null
  val regex = Regex("^(-?\\d+)(?:,|x)(-?\\d+)$")
  val match = regex.matchEntire(input.trim()) ?: return null
  val x = match.groupValues[1].toIntOrNull() ?: return null
  val y = match.groupValues[2].toIntOrNull() ?: return null
  return Pair(x, y)
}


