package sample.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberFilePickerLauncher(
  multiple: Boolean,
  directory: Boolean,
  onResult: (List<String>?) -> Unit
): () -> Unit {
  val currentOnResult = rememberUpdatedState(onResult)
  return remember(multiple, directory) {
    {
      if (directory) {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
      }
      try {
        val dialog = FileDialog(null as Frame?, if (directory) "Select Directory" else "Select File", FileDialog.LOAD)
        dialog.isMultipleMode = multiple
        dialog.isVisible = true

        val files = dialog.files
        val singleFile = dialog.file
        if (files != null && files.isNotEmpty()) {
          currentOnResult.value(files.map { it.absolutePath })
        } else if (singleFile != null) {
          val dir = dialog.directory ?: ""
          currentOnResult.value(listOf(File(dir, singleFile).absolutePath))
        } else {
          currentOnResult.value(null)
        }
      } finally {
        if (directory) {
          System.setProperty("apple.awt.fileDialogForDirectories", "false")
        }
      }
    }
  }
}
