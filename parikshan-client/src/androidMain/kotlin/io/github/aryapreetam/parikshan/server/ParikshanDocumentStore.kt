package io.github.aryapreetam.parikshan.server

import java.io.File

/**
 * Pure filesystem operations backing [ParikshanFileProvider].
 *
 * Isolates path resolution, traversal validation, SAF unique naming, and sandbox
 * mirroring so they can be exercised directly by host unit tests without Android
 * framework stubs.
 */
internal object ParikshanDocumentStore {

  const val SANDBOX_DIR_NAME = "parikshan_fixtures"

  fun isValidDisplayName(displayName: String?): Boolean {
    if (displayName.isNullOrBlank()) return false
    if (displayName == "." || displayName == "..") return false
    if (displayName.contains('/') || displayName.contains('\\') || displayName.contains('\u0000')) {
      return false
    }
    return true
  }

  /**
   * Resolves [childName] under [parentDir] while preventing path traversal outside [parentDir].
   */
  fun resolveSafeChild(parentDir: File, childName: String): File? {
    if (!isValidDisplayName(childName)) return null
    val canonicalParent = parentDir.canonicalFile
    val candidate = File(canonicalParent, childName).canonicalFile
    if (candidate.parentFile != canonicalParent) return null
    return candidate
  }

  /**
   * Resolves a SAF-style unique child file inside [parentDir].
   * Returns [displayName] directly when unused, otherwise appends `" (1)"`, `" (2)"`, etc.
   * before the extension (for regular files) or at the end (for directories).
   */
  fun resolveUniqueChild(parentDir: File, displayName: String, isDirectory: Boolean): File? {
    val initial = resolveSafeChild(parentDir, displayName) ?: return null
    if (!initial.exists()) return initial

    val dotIndex = if (isDirectory) -1 else displayName.lastIndexOf('.')
    val baseName = if (dotIndex > 0) displayName.substring(0, dotIndex) else displayName
    val extension = if (dotIndex > 0) displayName.substring(dotIndex) else ""

    for (counter in 1..10_000) {
      val candidateName = "$baseName ($counter)$extension"
      val candidate = resolveSafeChild(parentDir, candidateName) ?: return null
      if (!candidate.exists()) return candidate
    }
    return null
  }

  /**
   * Creates a new file or directory inside [parentDir] following SAF `createDocument` semantics.
   */
  fun createDocument(parentDir: File, displayName: String?, isDirectory: Boolean): File? {
    if (!parentDir.exists() || !parentDir.isDirectory) return null
    if (!isValidDisplayName(displayName)) return null

    val target = resolveUniqueChild(parentDir, displayName!!, isDirectory) ?: return null
    val created = if (isDirectory) {
      target.mkdirs()
    } else {
      target.createNewFile()
    }
    return if (created || target.exists()) target else null
  }

  /**
   * Renames [sourceFile] within its parent directory after validating [newDisplayName].
   */
  fun renameDocument(sourceFile: File, newDisplayName: String?): File? {
    if (!sourceFile.exists()) return null
    val parentDir = sourceFile.parentFile ?: return null
    val target = resolveSafeChild(parentDir, newDisplayName ?: return null) ?: return null
    if (target.exists() && target.canonicalFile != sourceFile.canonicalFile) return null
    return if (sourceFile.renameTo(target)) target else null
  }

  /**
   * Deletes [targetFile] (recursively if directory) and returns true when removed.
   */
  fun deleteDocument(targetFile: File): Boolean {
    if (!targetFile.exists()) return false
    return targetFile.deleteRecursively() && !targetFile.exists()
  }

  /**
   * Returns true if [candidateChild] resides at or beneath [parentDir].
   */
  fun isChildDocument(parentDir: File, candidateChild: File): Boolean {
    val canonicalParent = parentDir.canonicalFile
    val canonicalChild = candidateChild.canonicalFile
    if (canonicalChild == canonicalParent) return true
    val parentPrefix = canonicalParent.path.trimEnd(File.separatorChar) + File.separatorChar
    return canonicalChild.path.startsWith(parentPrefix)
  }

  /**
   * Mirrors a staged fixture from [source] (if present) into an app-owned writable sandbox
   * directory under [sandboxRoot]/[fixtureName], wiping any stale contents first so empty
   * destination fixtures start cleanly across repeated runs.
   */
  fun mirrorToSandbox(
    sandboxRoot: File,
    fixtureName: String,
    source: File?,
    isDirectory: Boolean
  ): File? {
    if (!isValidDisplayName(fixtureName)) return null
    sandboxRoot.mkdirs()
    val destination = resolveSafeChild(sandboxRoot, fixtureName) ?: return null

    if (destination.exists()) {
      destination.deleteRecursively()
    }

    if (isDirectory) {
      if (!destination.mkdirs() && !destination.isDirectory) return null
      if (source != null && source.exists() && source.isDirectory) {
        copyDirectoryContents(source, destination)
      }
      return destination
    }

    if (source != null && source.exists() && source.isFile) {
      destination.parentFile?.mkdirs()
      source.inputStream().use { input ->
        destination.outputStream().use { output ->
          input.copyTo(output)
        }
      }
      return destination
    }

    return null
  }

  /**
   * Strips the matching root prefix to produce a normalized forward-slash relative document ID.
   */
  fun toDocumentId(file: File, rootDirs: List<File>): String {
    val canonicalPath = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
    for (root in rootDirs) {
      val rootCanonical = runCatching { root.canonicalPath }.getOrElse { root.absolutePath }
      val prefix = rootCanonical.trimEnd(File.separatorChar) + File.separatorChar
      if (canonicalPath.startsWith(prefix)) {
        return canonicalPath.removePrefix(prefix).replace(File.separatorChar, '/')
      }
    }
    return file.name
  }

  /**
   * Resolves a relative document path safely within [rootDir], rejecting path traversal.
   */
  fun resolveRelativeInside(rootDir: File, relativePath: String): File? {
    val normalized = relativePath.trimStart('/')
    if (normalized.isEmpty()) return null
    val segments = normalized.split('/')
    if (segments.any { it.isEmpty() || it == "." || it == ".." || it.contains('\\') || it.contains('\u0000') }) {
      return null
    }
    val canonicalRoot = rootDir.canonicalFile
    val candidate = File(canonicalRoot, normalized).canonicalFile
    if (!isChildDocument(canonicalRoot, candidate)) return null
    return candidate
  }

  private fun copyDirectoryContents(sourceDir: File, destDir: File) {
    val children = sourceDir.listFiles() ?: return
    for (child in children) {
      val target = File(destDir, child.name)
      if (child.isDirectory) {
        target.mkdirs()
        copyDirectoryContents(child, target)
      } else if (child.isFile) {
        child.inputStream().use { input ->
          target.outputStream().use { output ->
            input.copyTo(output)
          }
        }
      }
    }
  }
}
