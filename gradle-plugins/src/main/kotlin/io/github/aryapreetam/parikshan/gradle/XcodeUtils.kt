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
    return rootDir.walkTopDown()
        .filter { it.isDirectory && it.extension == "xcodeproj" && !it.absolutePath.contains(".gradle") && !it.absolutePath.contains("build") }
        .firstOrNull()
}

abstract class XcodeBundleIdValueSource : ValueSource<String, XcodeBundleIdValueSource.Parameters> {
  interface Parameters : ValueSourceParameters {
    val xcodeProject: Property<File>
    val scheme: Property<String>
  }

  @get:Inject
  abstract val execOperations: ExecOperations

  override fun obtain(): String? {
    val projectFile = parameters.xcodeProject.orNull ?: return null
    val schemeName = parameters.scheme.orNull ?: return null
    return try {
      val outputStream = ByteArrayOutputStream()
      execOperations.exec {
        commandLine("xcodebuild", "-project", projectFile.absolutePath, "-scheme", schemeName, "-showBuildSettings")
        standardOutput = outputStream
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
