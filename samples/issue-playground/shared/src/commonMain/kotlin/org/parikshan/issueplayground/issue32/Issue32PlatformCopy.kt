package org.parikshan.issueplayground.issue32

import io.github.vinceglb.filekit.PlatformFile

data class Issue32CopyOutcome(
    val supported: Boolean,
    val succeeded: Boolean,
    val sourceBytesSize: Int = 0,
    val destBytesSize: Int = 0,
    val bytesMatch: Boolean = false,
    val sourceUnchanged: Boolean = false,
    val destChildNames: List<String> = emptyList(),
    val destText: String = "",
    val errorMessage: String? = null
)

data class Issue32ExtendedOutcome(
    val supported: Boolean,
    val succeeded: Boolean,
    val overwriteTruncated: Boolean = false,
    val subdirCreated: Boolean = false,
    val deletedCleanly: Boolean = false,
    val errorMessage: String? = null
)

expect suspend fun copyFileBetweenDirectories(
    sourceDir: PlatformFile,
    destDir: PlatformFile,
    fileName: String
): Issue32CopyOutcome

expect suspend fun verifyDestinationOverwriteSubdirDelete(
    destDir: PlatformFile,
    fileName: String
): Issue32ExtendedOutcome
