package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

internal object KmpWasmSourceResolver {
  fun resolveWasmSources(project: Project, sourceFiles: ConfigurableFileCollection) {
    val kmp = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
    
    val wasmTargets = kmp.targets.filter { target ->
      target.javaClass.name.contains("Wasm", ignoreCase = true) || 
        target.name.contains("wasm", ignoreCase = true) ||
        target.name.contains("web", ignoreCase = true)
    }

    val projectDirCanonical = project.projectDir.canonicalPath
    val buildDirCanonical = project.layout.buildDirectory.get().asFile.canonicalPath
    val candidates = listOf("webMain", "wasmJsMain", "jsMain")

    for (target in wasmTargets) {
      val mainCompilation = target.compilations.findByName("main") ?: continue
      
      // Look for active source sets matching our candidates on disk
      for (candidateName in candidates) {
        val ss = mainCompilation.allKotlinSourceSets.find { it.name == candidateName } ?: continue
        ss.kotlin.srcDirs.forEach { dir ->
          if (dir.exists()) {
            val dirCanonical = dir.canonicalPath
            if (dirCanonical.startsWith(projectDirCanonical) && !dirCanonical.startsWith(buildDirCanonical)) {
              sourceFiles.from(dir)
            }
          }
        }
      }
      
      // Fallback: if no candidates matched on disk, track the compilation's default source set
      if (sourceFiles.isEmpty) {
        val defaultSourceSet = mainCompilation.defaultSourceSet
        defaultSourceSet.kotlin.srcDirs.forEach { dir ->
          if (dir.exists()) {
            val dirCanonical = dir.canonicalPath
            if (dirCanonical.startsWith(projectDirCanonical) && !dirCanonical.startsWith(buildDirCanonical)) {
              sourceFiles.from(dir)
            }
          }
        }
      }

      if (!sourceFiles.isEmpty) {
        break
      }
    }
  }

  fun resolveWasmMainSourceSet(project: Project): KotlinSourceSet? {
    val kmp = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return null
    
    val wasmTargets = kmp.targets.filter { target ->
      target.javaClass.name.contains("Wasm", ignoreCase = true) || 
        target.name.contains("wasm", ignoreCase = true) ||
        target.name.contains("web", ignoreCase = true)
    }

    val candidates = listOf("webMain", "wasmJsMain", "jsMain")

    for (target in wasmTargets) {
      val mainCompilation = target.compilations.findByName("main") ?: continue
      
      // Prioritize replacing the source set whose directory physically exists on disk
      for (candidateName in candidates) {
        val ss = mainCompilation.allKotlinSourceSets.find { it.name == candidateName } ?: continue
        val srcDir = File(project.projectDir, "src/${candidateName}/kotlin")
        if (srcDir.exists()) {
          return ss
        }
      }
      
      // Fallback
      return mainCompilation.defaultSourceSet
    }
    return null
  }
}
