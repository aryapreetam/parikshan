package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

internal fun Project.discoverIosXcodeProject(): File? {
  // Candidate relative folder names to probe in order of priority
  val candidateFolderNames = listOf("iosApp", "ios", "iOS", "ios-app", "ios_app", "apple")
  val baseDirs = listOfNotNull(projectDir, projectDir.parentFile, projectDir.parentFile?.parentFile, rootDir).distinct()

  // 1. Direct probe for standard .xcodeproj locations inside candidate folders
  for (base in baseDirs) {
    for (folderName in candidateFolderNames) {
      val folder = File(base, folderName)
      if (folder.exists() && folder.isDirectory) {
        val xcodeProj = folder.listFiles()?.firstOrNull { it.isDirectory && it.extension == "xcodeproj" }
        if (xcodeProj != null) return xcodeProj
      }
    }
  }

  // 2. Direct probe for any .xcodeproj directly at root of baseDirs
  for (base in baseDirs) {
    val xcodeProj = base.listFiles()?.firstOrNull { it.isDirectory && it.extension == "xcodeproj" }
    if (xcodeProj != null) return xcodeProj
  }

  // 3. Fallback: Controlled single-level child directory check (excluding build, Pods, .gradle, node_modules)
  val ignoredNames = setOf("build", ".gradle", ".idea", "node_modules", "Pods", "DerivedData", "bin")
  for (base in baseDirs) {
    val children = base.listFiles()?.filter { it.isDirectory && it.name !in ignoredNames && !it.name.startsWith(".") } ?: emptyList()
    for (child in children) {
      val xcodeProj = child.listFiles()?.firstOrNull { it.isDirectory && it.extension == "xcodeproj" }
      if (xcodeProj != null) return xcodeProj
    }
  }

  return null
}

abstract class XcodeBundleIdValueSource : ValueSource<String, XcodeBundleIdValueSource.Parameters> {
  interface Parameters : ValueSourceParameters {
    val xcodeProject: Property<File>
    val scheme: Property<String>
  }

  @get:Inject
  abstract val execOperations: ExecOperations

  override fun obtain(): String? {
    val isMac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
    if (!isMac) return null

    val projectFile = parameters.xcodeProject.orNull ?: return null
    if (!projectFile.exists()) return null

    val schemeName = parameters.scheme.orNull ?: return null
    return try {
      val outputStream = ByteArrayOutputStream()
      val errorStream = ByteArrayOutputStream()
      execOperations.exec {
        commandLine("xcodebuild", "-project", projectFile.absolutePath, "-scheme", schemeName, "-destination", "generic/platform=iOS", "-showBuildSettings")
        standardOutput = outputStream
        errorOutput = errorStream
        isIgnoreExitValue = true
      }
      val output = outputStream.toString()
      val match = Regex("""\bPRODUCT_BUNDLE_IDENTIFIER\s*=\s*(.+)""").find(output)
      match?.groupValues?.get(1)?.trim()
    } catch (e: Exception) {
      null
    }
  }
}
