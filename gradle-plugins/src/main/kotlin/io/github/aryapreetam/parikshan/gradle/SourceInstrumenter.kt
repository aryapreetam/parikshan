package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.TaskProvider
import java.io.File

internal fun Project.registerParikshanIosBootSource(
  iosProjectDir: File,
  logger: Logger,
  sessionToken: String
): TaskProvider<Task> {
  val generatedDirProp = layout.buildDirectory.dir("parikshan/generated-ios-main")
  val projDir = layout.projectDirectory
  val prepareTask =
    tasks.register("prepareParikshanIosBootSource", ParikshanPrepareIosSourceTask::class.java)
  prepareTask.configure {
    group = "verification"
    this.iosProjectDir.set(projDir.asFile)
    this.generatedDir.set(generatedDirProp.get().asFile)
    outputs.upToDateWhen { false }
  }

  val generatedDir = generatedDirProp.get().asFile
  replaceIosMainSourceDirWhenAvailable(generatedDir)
  tasks.matching {
    it.name.contains("compileKotlinIos", ignoreCase = true) ||
      it.name.contains("embedAndSign", ignoreCase = true)
  }.configureEach {
    dependsOn(prepareTask)
  }
  @Suppress("UNCHECKED_CAST")
  return prepareTask as TaskProvider<Task>
}

internal fun Project.registerParikshanWasmBootSource(
  logger: Logger,
  wasmAppProject: Project = this
): TaskProvider<Task> {
  val generatedDirProp = wasmAppProject.layout.buildDirectory.dir("parikshan/generated-wasm-main")
  val projDir = wasmAppProject.layout.projectDirectory
  val prepareTask =
    wasmAppProject.tasks.register("prepareParikshanWasmBootSource", ParikshanPrepareWasmSourceTask::class.java)
  prepareTask.configure {
    group = "verification"
    this.projectDir.set(projDir.asFile)
    this.generatedDir.set(generatedDirProp.get().asFile)
    outputs.upToDateWhen { false }
  }

  val generatedDir = generatedDirProp.get().asFile
  wasmAppProject.replaceWasmMainSourceDirWhenAvailable(generatedDir)
  wasmAppProject.tasks.matching {
    it.name.contains("compileKotlinWasmJs", ignoreCase = true) ||
      it.name.contains("wasmJsBrowser", ignoreCase = true)
  }.configureEach {
    dependsOn(prepareTask)
    inputs.dir(generatedDir)
  }
  @Suppress("UNCHECKED_CAST")
  return prepareTask as TaskProvider<Task>
}

private fun Project.replaceWasmMainSourceDirWhenAvailable(generatedDir: File) {
  val kmp = extensions.findByName("kotlin") ?: return
  @Suppress("UNCHECKED_CAST")
  val sourceSets =
    kmp.javaClass.getMethod("getSourceSets").invoke(kmp) as NamedDomainObjectContainer<Any>
  val projectLogger = logger
  val outerProject = this
  // Prefer replacing the source set whose directory physically exists on disk.
  // This avoids duplicate compilation: e.g., if webMain has the sources,
  // replacing wasmJsMain would create duplicates (webMain + instrumented wasmJsMain).
  val candidates = listOf("webMain", "wasmJsMain", "jsMain")
  try {
    val findMethod = sourceSets.javaClass.getMethod("findByName", String::class.java)
    for (candidate in candidates) {
      val ss = findMethod.invoke(sourceSets, candidate) ?: continue
      val srcDir = File(projectDir, "src/${candidate}/kotlin")
      if (srcDir.exists()) {
        projectLogger.lifecycle("Parikshan Wasm: Replacing source set '$candidate' with generated sources: ${generatedDir.absolutePath}")
        replaceKotlinSourceDirs(ss, generatedDir)
        return
      }
    }
    // Fallback: replace first available source set regardless of physical dir
    for (candidate in candidates) {
      val ss = findMethod.invoke(sourceSets, candidate) ?: continue
      projectLogger.lifecycle("Parikshan Wasm: Replacing source set '$candidate' (fallback) with generated sources: ${generatedDir.absolutePath}")
      replaceKotlinSourceDirs(ss, generatedDir)
      return
    }
  } catch (e: Exception) {
    sourceSets.all(
      object : Action<Any> {
        override fun execute(sourceSet: Any) {
          val name = (sourceSet as? Named)?.name
          if (name == "webMain" || name == "wasmJsMain") {
            projectLogger.lifecycle("Parikshan Wasm: Replacing source set '$name' (fallback) with generated sources: ${generatedDir.absolutePath}")
            replaceKotlinSourceDirs(sourceSet, generatedDir)
          }
        }
      }
    )
  }
}

private fun Project.replaceIosMainSourceDirWhenAvailable(generatedDir: File) {
  val kmp = extensions.findByName("kotlin") ?: return
  @Suppress("UNCHECKED_CAST")
  val sourceSets =
    kmp.javaClass.getMethod("getSourceSets").invoke(kmp) as NamedDomainObjectContainer<Any>
  sourceSets.all(
    object : Action<Any> {
      override fun execute(sourceSet: Any) {
        val name = (sourceSet as? Named)?.name
        if (name == "iosMain") {
          replaceKotlinSourceDirs(sourceSet, generatedDir)
        }
      }
    }
  )
}

private fun Project.replaceKotlinSourceDirs(
  sourceSet: Any,
  generatedDir: File
) {
  val name = (sourceSet as? org.gradle.api.Named)?.name ?: "unknown"
  val kotlinSrc = sourceSet.javaClass.getMethod("getKotlin").invoke(sourceSet) as SourceDirectorySet
  val physicalSrcDirPrefix = File(projectDir, "src").absolutePath

  kotlinSrc.exclude(object : org.gradle.api.specs.Spec<org.gradle.api.file.FileTreeElement> {
    override fun isSatisfiedBy(element: org.gradle.api.file.FileTreeElement): Boolean {
      return element.file.absolutePath.startsWith(physicalSrcDirPrefix)
    }
  })

  kotlinSrc.srcDirs(generatedDir)
  logger.lifecycle("Parikshan: Configured source set '$name' for project '${this.path}'. Added source dir: ${generatedDir.absolutePath}")
}

private fun instrumentComposeUIViewControllerSource(source: String): String {
  if (!source.contains("ComposeUIViewController")) {
    return source
  }
  val withoutComposeImport =
    source.replace(
      Regex("""import\s+androidx\.compose\.ui\.window\.ComposeUIViewController\s*\R"""),
      ""
    )
  val withParikshanImport =
    addKotlinImport(
      source = withoutComposeImport,
      importLine = "import io.github.aryapreetam.parikshan.ParikshanUIViewController"
    )
  return withParikshanImport.replace("ComposeUIViewController", "ParikshanUIViewController")
}

private fun addKotlinImport(
  source: String,
  importLine: String
): String {
  if (source.contains(importLine)) {
    return source
  }
  val packageMatch = Regex("""\A\s*package\s+[a-zA-Z0-9_.]+\s*\R""").find(source)
  return if (packageMatch != null) {
    source.replaceRange(
      packageMatch.range.last + 1,
      packageMatch.range.last + 1,
      "\n$importLine\n"
    )
  } else {
    "$importLine\n$source"
  }
}

private fun discoverIosMainPackage(iosProjectDir: File): String? {
  val iosMainDir = File(iosProjectDir, "src/iosMain/kotlin")
  if (!iosMainDir.exists()) return null
  iosMainDir.walkTopDown()
    .filter { it.isFile && it.extension == "kt" }
    .forEach { file ->
      val text = file.readText()
      if (text.contains("MainViewController")) {
        return Regex("""package\s+([a-zA-Z0-9_.]+)""").find(text)?.groupValues?.get(1)
      }
    }
  return null
}

private fun instrumentComposeWasmSource(source: String): String {
  if (!source.contains("ComposeViewport")) {
    return source
  }
  
  var result = source
  
  // 1. Swap ComposeViewport with ParikshanComposeViewport
  result = result.replace(
    Regex("""import\s+androidx\.compose\.ui\.window\.ComposeViewport\s*\R"""),
    ""
  ).replace("ComposeViewport", "ParikshanComposeViewport")
  
  // 2. Inject Parikshan imports and initializer
  result = addKotlinImport(result, "import io.github.aryapreetam.parikshan.ParikshanComposeViewport")
  result = addKotlinImport(result, "import io.github.aryapreetam.parikshan.initializeParikshanWasm")
  
  // 3. Inject initializeParikshanWasm() at the start of main()
  val mainMatch = Regex("""fun\s+main\s*\([^)]*\)\s*\{""").find(result)
  if (mainMatch != null) {
      result = result.replaceRange(
          mainMatch.range.last + 1,
          mainMatch.range.last + 1,
          "\n  initializeParikshanWasm()\n"
      )
  }
  
  return result
}
