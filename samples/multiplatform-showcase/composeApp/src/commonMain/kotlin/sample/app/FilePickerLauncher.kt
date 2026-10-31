package sample.app

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFilePickerLauncher(
  multiple: Boolean = false,
  directory: Boolean = false,
  onResult: (List<String>?) -> Unit
): () -> Unit
