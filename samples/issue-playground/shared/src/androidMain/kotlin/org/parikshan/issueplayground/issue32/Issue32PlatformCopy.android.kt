package org.parikshan.issueplayground.issue32

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
import io.github.vinceglb.filekit.write
import io.github.vinceglb.filekit.writeString

actual suspend fun copyFileBetweenDirectories(
    sourceDir: PlatformFile,
    destDir: PlatformFile,
    fileName: String
): Issue32CopyOutcome {
    val srcAccess = sourceDir.startAccessingSecurityScopedResource()
    val dstAccess = destDir.startAccessingSecurityScopedResource()
    return try {
        val sourceFile = PlatformFile(sourceDir, fileName)
        val expectedSourceBytes = sourceFile.readBytes()

        // Mirrors the exact FileKit copy operation from Issue #32
        PlatformFile(destDir, fileName).write(PlatformFile(sourceDir, fileName))

        val destFile = PlatformFile(destDir, fileName)
        val destBytes = destFile.readBytes()
        val sourceAfterBytes = sourceFile.readBytes()
        val destChildren = destDir.list().map { it.name }.sorted()

        Issue32CopyOutcome(
            supported = true,
            succeeded = true,
            sourceBytesSize = sourceAfterBytes.size,
            destBytesSize = destBytes.size,
            bytesMatch = destBytes.contentEquals(expectedSourceBytes),
            sourceUnchanged = sourceAfterBytes.contentEquals(expectedSourceBytes),
            destChildNames = destChildren,
            destText = destFile.readString().trim()
        )
    } catch (e: Throwable) {
        Issue32CopyOutcome(
            supported = true,
            succeeded = false,
            errorMessage = "${e::class.simpleName}: ${e.message}"
        )
    } finally {
        if (dstAccess) destDir.stopAccessingSecurityScopedResource()
        if (srcAccess) sourceDir.stopAccessingSecurityScopedResource()
    }
}

actual suspend fun verifyDestinationOverwriteSubdirDelete(
    destDir: PlatformFile,
    fileName: String
): Issue32ExtendedOutcome {
    val dstAccess = destDir.startAccessingSecurityScopedResource()
    return try {
        val targetFile = PlatformFile(destDir, fileName)
        // Overwrite with shorter content to verify "wt" truncation
        val shorterPayload = "short\n"
        targetFile.writeString(shorterPayload)
        val readBack = targetFile.readBytes()
        val truncated = readBack.contentEquals(shorterPayload.encodeToByteArray())

        // Create a subdirectory and write a nested file inside it
        val nestedDir = PlatformFile(destDir, "nested_out")
        nestedDir.createDirectories()
        val nestedFile = PlatformFile(nestedDir, "child.txt")
        nestedFile.writeString("nested ok\n")
        val subdirOk = nestedDir.isDirectory() && nestedFile.readString() == "nested ok\n"

        // Delete the root target file and confirm it no longer exists
        targetFile.delete()
        val deletedOk = !targetFile.exists() && destDir.list().none { it.name == fileName }

        Issue32ExtendedOutcome(
            supported = true,
            succeeded = truncated && subdirOk && deletedOk,
            overwriteTruncated = truncated,
            subdirCreated = subdirOk,
            deletedCleanly = deletedOk
        )
    } catch (e: Throwable) {
        Issue32ExtendedOutcome(
            supported = true,
            succeeded = false,
            errorMessage = "${e::class.simpleName}: ${e.message}"
        )
    } finally {
        if (dstAccess) destDir.stopAccessingSecurityScopedResource()
    }
}
