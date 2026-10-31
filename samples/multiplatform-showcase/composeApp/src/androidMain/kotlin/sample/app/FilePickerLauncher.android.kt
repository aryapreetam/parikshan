package sample.app

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePickerLauncher(
  multiple: Boolean,
  directory: Boolean,
  onResult: (List<String>?) -> Unit
): () -> Unit {
  val context = LocalContext.current

  fun resolveUri(uri: Uri): String {
    var displayName: String? = null
    if (uri.scheme == "content") {
      try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIndex != -1 && cursor.moveToFirst()) {
            displayName = cursor.getString(nameIndex)
          }
        }
      } catch (_: Throwable) {}
    }
    return displayName ?: uri.lastPathSegment ?: uri.toString()
  }

  val singleLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      onResult(listOf(resolveUri(uri)))
    } else {
      onResult(null)
    }
  }

  val multiLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetMultipleContents()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      onResult(uris.map { resolveUri(it) })
    } else {
      onResult(null)
    }
  }

  val directoryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { uri: Uri? ->
    if (uri != null) {
      onResult(listOf(resolveUri(uri)))
    } else {
      onResult(null)
    }
  }

  return {
    when {
      directory -> directoryLauncher.launch(null)
      multiple -> multiLauncher.launch("*/*")
      else -> singleLauncher.launch("*/*")
    }
  }
}
