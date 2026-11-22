package org.parikshan.issueplayground.issue32

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
import kotlinx.coroutines.launch

@Composable
fun Issue32Screen() {
    val coroutineScope = rememberCoroutineScope()
    var selectedSource by remember { mutableStateOf<PlatformFile?>(null) }
    var selectedDestination by remember { mutableStateOf<PlatformFile?>(null) }

    var sourceStatus by remember { mutableStateOf("Source Ready: false (0 bytes)") }
    var sourceText by remember { mutableStateOf("") }
    var destinationInitialCount by remember { mutableStateOf<Int?>(null) }
    var copyOutcome by remember { mutableStateOf<Issue32CopyOutcome?>(null) }
    var extendedOutcome by remember { mutableStateOf<Issue32ExtendedOutcome?>(null) }

    val sourcePickerLauncher = rememberDirectoryPickerLauncher { directory: PlatformFile? ->
        selectedSource = directory
        copyOutcome = null
        extendedOutcome = null
        if (directory == null) {
            sourceStatus = "Source Ready: false (0 bytes)"
            sourceText = ""
            return@rememberDirectoryPickerLauncher
        }

        coroutineScope.launch {
            val accessed = directory.startAccessingSecurityScopedResource()
            try {
                val children = if (directory.isDirectory()) directory.list() else emptyList()
                val readyFile = children.find { it.name == "ready.txt" }
                if (readyFile != null) {
                    val bytes = readyFile.readBytes()
                    sourceStatus = "Source Ready: true (${bytes.size} bytes)"
                    sourceText = readyFile.readString().trim()
                } else {
                    sourceStatus = "Source Ready: false (missing ready.txt)"
                    sourceText = ""
                }
            } catch (e: Throwable) {
                sourceStatus = "Source Error: ${e::class.simpleName}: ${e.message}"
                sourceText = ""
            } finally {
                if (accessed) directory.stopAccessingSecurityScopedResource()
            }
        }
    }

    val destinationPickerLauncher = rememberDirectoryPickerLauncher { directory: PlatformFile? ->
        selectedDestination = directory
        copyOutcome = null
        extendedOutcome = null
        if (directory == null) {
            destinationInitialCount = null
            return@rememberDirectoryPickerLauncher
        }

        coroutineScope.launch {
            val accessed = directory.startAccessingSecurityScopedResource()
            try {
                val children = if (directory.isDirectory()) directory.list() else emptyList()
                destinationInitialCount = children.size
            } catch (_: Throwable) {
                destinationInitialCount = -1
            } finally {
                if (accessed) directory.stopAccessingSecurityScopedResource()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Issue #32 Reproducer",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row {
            Button(
                onClick = { sourcePickerLauncher.launch() },
                modifier = Modifier.testTag("choose_source")
            ) {
                Text("Choose Source")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { destinationPickerLauncher.launch() },
                modifier = Modifier.testTag("choose_destination")
            ) {
                Text("Choose Destination")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row {
            Button(
                onClick = {
                    val src = selectedSource
                    val dst = selectedDestination
                    if (src != null && dst != null) {
                        coroutineScope.launch {
                            copyOutcome = copyFileBetweenDirectories(src, dst, "ready.txt")
                        }
                    }
                },
                enabled = selectedSource != null && selectedDestination != null,
                modifier = Modifier.testTag("sync_action")
            ) {
                Text("Sync")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    val dst = selectedDestination
                    if (dst != null) {
                        coroutineScope.launch {
                            extendedOutcome = verifyDestinationOverwriteSubdirDelete(dst, "ready.txt")
                        }
                    }
                },
                enabled = selectedDestination != null && copyOutcome?.succeeded == true,
                modifier = Modifier.testTag("saf_extended_action")
            ) {
                Text("Verify Overwrite & Cleanup")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = sourceStatus, modifier = Modifier.testTag("source_status"))
        if (sourceText.isNotEmpty()) {
            Text(text = "Source Text: $sourceText", modifier = Modifier.testTag("source_text"))
        }

        destinationInitialCount?.let { count ->
            Text(
                text = "Destination Initial Count: $count",
                modifier = Modifier.testTag("destination_initial_count")
            )
        }

        copyOutcome?.let { outcome ->
            val statusLabel = when {
                !outcome.supported -> "Unsupported on Web"
                outcome.succeeded -> "Sync succeeded"
                else -> "Sync failed"
            }
            Text(text = "Sync Status: $statusLabel", modifier = Modifier.testTag("sync_status"))
            if (outcome.succeeded) {
                Text(
                    text = "Destination Bytes: ${outcome.destBytesSize}",
                    modifier = Modifier.testTag("destination_bytes")
                )
                Text(
                    text = "Bytes Match: ${outcome.bytesMatch}",
                    modifier = Modifier.testTag("bytes_match")
                )
                Text(
                    text = "Source Unchanged: ${outcome.sourceUnchanged}",
                    modifier = Modifier.testTag("source_unchanged")
                )
                Text(
                    text = "Destination Files: ${outcome.destChildNames.joinToString(",")}",
                    modifier = Modifier.testTag("destination_files")
                )
                Text(
                    text = "Destination Text: ${outcome.destText}",
                    modifier = Modifier.testTag("destination_text")
                )
            }
            outcome.errorMessage?.let { err ->
                Text(text = "Sync Error: $err", modifier = Modifier.testTag("sync_error"))
            }
        }

        extendedOutcome?.let { ext ->
            val label = when {
                !ext.supported -> "Unsupported on Web"
                ext.succeeded -> "Extended succeeded"
                else -> "Extended failed"
            }
            Text(text = "Extended Status: $label", modifier = Modifier.testTag("extended_status"))
            Text(
                text = "Overwrite Truncated: ${ext.overwriteTruncated}",
                modifier = Modifier.testTag("overwrite_truncated")
            )
            Text(
                text = "Subdir Created: ${ext.subdirCreated}",
                modifier = Modifier.testTag("subdir_created")
            )
            Text(
                text = "Deleted Cleanly: ${ext.deletedCleanly}",
                modifier = Modifier.testTag("deleted_cleanly")
            )
            ext.errorMessage?.let { err ->
                Text(text = "Extended Error: $err", modifier = Modifier.testTag("extended_error"))
            }
        }
    }
}
