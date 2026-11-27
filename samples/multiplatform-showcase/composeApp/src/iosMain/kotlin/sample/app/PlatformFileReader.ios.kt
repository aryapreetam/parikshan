package sample.app

import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun readFileBytes(pathOrName: String): ByteArray {
  val data = NSData.dataWithContentsOfFile(pathOrName) ?: return byteArrayOf()
  val length = data.length.toInt()
  val bytes = ByteArray(length)
  if (length > 0) {
    bytes.usePinned { pinned ->
      memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
  }
  return bytes
}
