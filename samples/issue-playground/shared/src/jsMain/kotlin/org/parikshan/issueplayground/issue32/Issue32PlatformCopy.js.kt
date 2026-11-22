package org.parikshan.issueplayground.issue32

import io.github.vinceglb.filekit.PlatformFile

actual suspend fun copyFileBetweenDirectories(
    sourceDir: PlatformFile,
    destDir: PlatformFile,
    fileName: String
): Issue32CopyOutcome = Issue32CopyOutcome(
    supported = false,
    succeeded = false,
    errorMessage = "Directory write is not supported on Web webkitdirectory"
)

actual suspend fun verifyDestinationOverwriteSubdirDelete(
    destDir: PlatformFile,
    fileName: String
): Issue32ExtendedOutcome = Issue32ExtendedOutcome(
    supported = false,
    succeeded = false,
    errorMessage = "Directory write is not supported on Web webkitdirectory"
)
