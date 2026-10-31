package sample.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.UniformTypeIdentifiers.UTTypeContent
import platform.UniformTypeIdentifiers.UTTypeFolder

@Composable
actual fun rememberFilePickerLauncher(
  multiple: Boolean,
  directory: Boolean,
  onResult: (List<String>?) -> Unit
): () -> Unit {
  val localViewController = LocalUIViewController.current
  val activeDelegate = remember { mutableStateOf<UIDocumentPickerDelegateProtocol?>(null) }
  val currentOnResult = rememberUpdatedState(onResult)
  return remember(multiple, directory, localViewController) {
    {
      val contentTypes = if (directory) {
        listOf(UTTypeFolder)
      } else {
        listOf(UTTypeContent)
      }

      val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
          controller: UIDocumentPickerViewController,
          didPickDocumentsAtURLs: List<*>
        ) {
          try {
            val paths = didPickDocumentsAtURLs.mapNotNull { item ->
              val url = item as? NSURL ?: return@mapNotNull null
              val isAccessing = url.startAccessingSecurityScopedResource()
              try {
                url.path ?: url.lastPathComponent ?: url.absoluteString
              } finally {
                if (isAccessing) {
                  url.stopAccessingSecurityScopedResource()
                }
              }
            }
            if (paths.isNotEmpty()) {
              currentOnResult.value(paths)
            } else {
              currentOnResult.value(null)
            }
          } finally {
            activeDelegate.value = null
          }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
          try {
            currentOnResult.value(null)
          } finally {
            activeDelegate.value = null
          }
        }
      }

      activeDelegate.value = delegate

      val picker = UIDocumentPickerViewController(forOpeningContentTypes = contentTypes).apply {
        this.delegate = delegate
        this.allowsMultipleSelection = multiple
      }

      var top: UIViewController? = localViewController
      while (top?.presentedViewController != null) {
        top = top.presentedViewController
      }
      top?.presentViewController(picker, animated = true, completion = null)
    }
  }
}
