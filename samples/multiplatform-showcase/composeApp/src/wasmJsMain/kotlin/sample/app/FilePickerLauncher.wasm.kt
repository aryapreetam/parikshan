@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package sample.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.js.JsName

@Composable
actual fun rememberFilePickerLauncher(
  multiple: Boolean,
  directory: Boolean,
  onResult: (List<String>?) -> Unit
): () -> Unit {
  val currentOnResult = rememberUpdatedState(onResult)
  return remember(multiple, directory) {
    {
      FilePickerBridge.onPickerResult = { resultString ->
        if (resultString == null) {
          currentOnResult.value(null)
        } else if (resultString.isEmpty()) {
          currentOnResult.value(emptyList())
        } else {
          currentOnResult.value(resultString.split(","))
        }
      }
      openFilePickerJs(multiple, directory)
    }
  }
}

private fun openFilePickerJs(multiple: Boolean, directory: Boolean): Unit = js(
  """
  {
    var input = document.createElement('input');
    input.type = 'file';
    if (directory) {
      input.webkitdirectory = true;
      input.setAttribute('webkitdirectory', '');
    }
    if (multiple) {
      input.multiple = true;
      input.setAttribute('multiple', '');
    }
    input.style.display = 'none';

    input.onchange = function() {
      if (input.files && input.files.length > 0) {
        var names = [];
        window.__sample_picked_files = window.__sample_picked_files || {};
        var promises = [];
        for (var i = 0; i < input.files.length; i++) {
          var f = input.files[i];
          var dirName = (directory && f.webkitRelativePath && f.webkitRelativePath.indexOf('/') !== -1)
            ? f.webkitRelativePath.split('/')[0]
            : f.name;
          names.push(dirName);
          (function(file) {
            promises.push(file.arrayBuffer().then(function(buf) {
              window.__sample_picked_files[file.name] = new Uint8Array(buf);
            }));
          })(f);
        }
        Promise.all(promises).then(function() {
          globalThis.__sample_file_picker_result(names.join(','));
        });
      } else {
        globalThis.__sample_file_picker_result(null);
      }
      if (input.parentNode) {
        input.parentNode.removeChild(input);
      }
    };

    input.addEventListener('cancel', function() {
      globalThis.__sample_file_picker_result(null);
      if (input.parentNode) {
        input.parentNode.removeChild(input);
      }
    });

    document.body.appendChild(input);
    input.click();
  }
  """
)

private object FilePickerBridge {
  var onPickerResult: ((String?) -> Unit)? = null

  init {
    GlobalThis.filePickerResult = { result ->
      onPickerResult?.invoke(result)
    }
  }
}

@JsName("globalThis")
private external object GlobalThis {
  @JsName("__sample_file_picker_result")
  var filePickerResult: (String?) -> Unit
}
