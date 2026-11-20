package sample.app

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalEncodingApi::class)
actual fun readFileBytes(pathOrName: String): ByteArray {
  val fileName = pathOrName.substringAfterLast('/').substringAfterLast('\\')
  val b64 = getPickedFileBase64Js(fileName) ?: getPickedFileBase64Js(pathOrName)
  return if (b64 != null) {
    Base64.decode(b64)
  } else {
    byteArrayOf()
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun getPickedFileBase64Js(name: String): String? = js(
  """{
    var map = window.__sample_picked_files || {};
    var arr = map[name];
    if (!arr) return null;
    var binary = '';
    var len = arr.byteLength;
    for (var i = 0; i < len; i++) {
      binary += String.fromCharCode(arr[i]);
    }
    return btoa(binary);
  }"""
)
