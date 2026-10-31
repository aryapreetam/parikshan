package io.github.aryapreetam.parikshan

import java.io.File
import java.io.FileNotFoundException

internal object PickerPathResolver {

  fun resolveAndValidatePaths(paths: List<String>): List<String> {
    val projectDirProp = System.getProperty("parikshan.projectDir")
    return paths.map { pathStr ->
      val expandedPath = if (pathStr == "~" || pathStr.startsWith("~/")) {
        val userHome = System.getProperty("user.home") ?: ""
        if (pathStr == "~") userHome else userHome + pathStr.removePrefix("~")
      } else {
        pathStr
      }
      val directFile = File(expandedPath)
      val candidateFiles = listOfNotNull(
        directFile.takeIf { directFile.isAbsolute || directFile.exists() },
        projectDirProp?.let { File(it, expandedPath) },
        projectDirProp?.let { File(it, "src/commonTest/resources/$expandedPath") },
        projectDirProp?.let { File(it, "src/test/resources/$expandedPath") },
        projectDirProp?.let { File(it, "src/androidTest/resources/$expandedPath") },
        projectDirProp?.let { File(it, "src/androidTest/assets/$expandedPath") },
        projectDirProp?.let { File(it, "src/androidUnitTest/resources/$expandedPath") }
      )
      val file = candidateFiles.firstOrNull { it.exists() } ?: directFile.absoluteFile
      if (!file.exists()) {
        throw FileNotFoundException(
          "File or directory does not exist: $pathStr\n" +
            "  Resolved absolute path: ${file.absolutePath}"
        )
      }
      file.canonicalPath
    }
  }
}
