package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

internal fun Test.configureE2eHostTestExecution(
  hostTestClassesDirs: FileCollection,
  hostTestClasspath: FileCollection,
  e2eTestClasses: List<String>,
  target: String,
  logger: Logger
) {
  if (e2eTestClasses.isEmpty()) {
    throw GradleException("No E2E test classes discovered in src/commonTest")
  }

  outputs.upToDateWhen { false }
  testClassesDirs = hostTestClassesDirs
  classpath = hostTestClasspath
  dependsOn(hostTestClassesDirs.buildDependencies)

  filter {
    isFailOnNoMatchingTests = true
    if (includePatterns.isEmpty()) {
        e2eTestClasses.forEach { includeTestsMatching(it) }
    }
  }

  doFirst {
    val existingClassDirs = testClassesDirs.files.filter { it.exists() }
    if (existingClassDirs.isEmpty()) {
      throw GradleException(
        "Parikshan $target: host test classes were not compiled."
      )
    }
    logger.lifecycle("Parikshan $target: running E2E test classes ${e2eTestClasses.joinToString()}")
  }

  val videoOutputDirProvider = project.providers.gradleProperty("parikshan.video.outputDir")
      .orElse(project.providers.systemProperty("parikshan.video.outputDir"))
      .map { File(it) }
      .orElse(project.layout.buildDirectory.dir("parikshan/videos/${target.lowercase()}").map { it.asFile })

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
    "parikshan.video.strategy",
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
    "parikshan.android.serial"
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
  val kmp = extensions.findByName("kotlin") ?: return "jvmTest"
  try {
      @Suppress("UNCHECKED_CAST")
      val targets = kmp.javaClass.getMethod("getTargets").invoke(kmp) as NamedDomainObjectCollection<Any>
      
      logger.debug("Parikshan: Found ${targets.size} Kotlin Multiplatform targets")
      targets.forEach { logger.debug("Parikshan: Target name=${(it as Named).name}, class=${it.javaClass.name}") }

      val jvmTargets = targets.filter { target ->
          val targetName = (target as Named).name
          val className = target.javaClass.name
          val match = className.contains("KotlinJvmTarget", ignoreCase = true) || 
                    targetName.contains("jvm", ignoreCase = true) || 
                    targetName.contains("desktop", ignoreCase = true)
          if (match) logger.debug("Parikshan: Matched target=$targetName as JVM test host target")
          match
      }
      
      val target = jvmTargets.find { (it as Named).name in listOf("desktop", "jvm") }
          ?: jvmTargets.firstOrNull()
          
      if (target != null) {
          val name = (target as Named).name
          val taskName = "${name}Test"
          logger.debug("Parikshan: Resolved hostTestTaskName=$taskName")
          return taskName
      }
  } catch (e: Exception) { 
      logger.debug("Parikshan: Exception while resolving host test task name", e)
  }
  logger.debug("Parikshan: Falling back to jvmTest")
  return "jvmTest"
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

internal fun Project.discoverE2eTestClasses(): List<String> {
  val commonTestKotlin = layout.projectDirectory.dir("src/commonTest/kotlin").asFile
  if (!commonTestKotlin.exists()) {
    return emptyList()
  }

  return commonTestKotlin
    .walkTopDown()
    .filter { it.isFile && it.extension == "kt" }
    .flatMap { file -> discoverE2eTestClassesInFile(file).asSequence() }
    .distinct()
    .sorted()
    .toList()
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

  // Test DSL is always available in commonTest
  addParikshanDependency("commonTestImplementation", ":parikshan", "io.github.aryapreetam:parikshan:$pluginVersion")
  addParikshanDependency("commonTestImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client:$pluginVersion")

  // The client engine is only injected into the production binary during active E2E tasks.
  if (isE2EActive) {
      addParikshanDependency("commonMainImplementation", ":parikshan-client", "io.github.aryapreetam:parikshan-client:$pluginVersion")
      
      // Inject server into all JVM targets
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
                      addParikshanDependency("${targetName}MainImplementation", ":parikshan-server", "io.github.aryapreetam:parikshan-server:$pluginVersion")
                  }
              }
          } catch (_: Exception) { }
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

internal fun Project.addParikshanDependency(config: String, path: String, maven: String) {
  val dep = rootProject.findProject(path)?.let { dependencies.project(mapOf("path" to it.path)) } ?: maven
  val configuration = configurations.findByName(config) ?: return
  dependencies.add(config, dep)
}

