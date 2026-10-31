package sample.app

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFilePickerLauncher(
  multiple: Boolean,
  directory: Boolean,
  onResult: (List<String>?) -> Unit
): () -> Unit {
  return {
    onResult(null)
  }
}
