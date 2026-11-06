package org.parikshan.issueplayground.issue29

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
import kotlinx.coroutines.launch

data class DirectoryResult(
    val uriOrPath: String,
    val isDirectory: Boolean,
    val childCount: Int,
    val fileNames: List<String>,
    val emptyDirChildCount: Int,
    val nestedFileNames: List<String>,
    val reportText: String,
    val nestedNotesText: String,
    val errorMessage: String? = null
)

@Composable
fun Issue29Screen() {
    var directoryResult by remember { mutableStateOf<DirectoryResult?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val launcher = rememberDirectoryPickerLauncher { directory: PlatformFile? ->
        if (directory == null) {
            directoryResult = null
            return@rememberDirectoryPickerLauncher
        }

        coroutineScope.launch {
            try {
                val isAccessing = directory.startAccessingSecurityScopedResource()
                try {
                    val isDir = directory.isDirectory()
                    val topChildren = if (isDir) directory.list() else emptyList()
                    val topNames = topChildren.map { it.name }.sorted()

                    val emptyDir = topChildren.find { it.name == "empty_dir" }
                    val emptyChildren = if (emptyDir != null && emptyDir.isDirectory()) emptyDir.list() else emptyList()

                    val nestedDir = topChildren.find { it.name == "nested" }
                    val nestedChildren = if (nestedDir != null && nestedDir.isDirectory()) nestedDir.list() else emptyList()
                    val nestedNames = nestedChildren.map { it.name }.sorted()

                    val reportFile = topChildren.find { it.name == "report.txt" }
                    val reportContent = reportFile?.readString()?.trim() ?: ""

                    val notesFile = nestedChildren.find { it.name == "notes.txt" }
                    val notesContent = notesFile?.readString()?.trim() ?: ""

                    directoryResult = DirectoryResult(
                        uriOrPath = directory.toString(),
                        isDirectory = isDir,
                        childCount = topChildren.size,
                        fileNames = topNames,
                        emptyDirChildCount = emptyChildren.size,
                        nestedFileNames = nestedNames,
                        reportText = reportContent,
                        nestedNotesText = notesContent
                    )
                } finally {
                    if (isAccessing) {
                        directory.stopAccessingSecurityScopedResource()
                    }
                }
            } catch (e: Throwable) {
                directoryResult = DirectoryResult(
                    uriOrPath = directory.toString(),
                    isDirectory = false,
                    childCount = 0,
                    fileNames = emptyList(),
                    emptyDirChildCount = -1,
                    nestedFileNames = emptyList(),
                    reportText = "",
                    nestedNotesText = "",
                    errorMessage = "${e::class.simpleName}: ${e.message}"
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Issue #29 Reproducer",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { launcher.launch() },
            modifier = Modifier.testTag("choose_directory_button")
        ) {
            Text("Choose Directory")
        }

        Spacer(modifier = Modifier.height(16.dp))

        val result = directoryResult
        if (result == null) {
            Text(text = "Status: Idle", modifier = Modifier.testTag("directory_status"))
        } else {
            Text(
                text = "Is Directory: ${result.isDirectory}",
                modifier = Modifier.testTag("is_directory_text")
            )
            Text(
                text = "Child Count: ${result.childCount}",
                modifier = Modifier.testTag("child_count_text")
            )
            Text(
                text = "Files: ${result.fileNames.joinToString(",")}",
                modifier = Modifier.testTag("file_names_text")
            )
            Text(
                text = "Empty Dir Children: ${result.emptyDirChildCount}",
                modifier = Modifier.testTag("empty_dir_children_text")
            )
            Text(
                text = "Nested Files: ${result.nestedFileNames.joinToString(",")}",
                modifier = Modifier.testTag("nested_files_text")
            )
            Text(
                text = "Report Content: ${result.reportText}",
                modifier = Modifier.testTag("report_content_text")
            )
            Text(
                text = "Nested Notes Content: ${result.nestedNotesText}",
                modifier = Modifier.testTag("nested_notes_content_text")
            )
            if (result.errorMessage != null) {
                Text(
                    text = "Error: ${result.errorMessage}",
                    modifier = Modifier.testTag("error_text")
                )
            }
        }
    }
}
