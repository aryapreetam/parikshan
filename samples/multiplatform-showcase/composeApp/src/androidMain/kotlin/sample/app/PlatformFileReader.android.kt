package sample.app

import java.io.File

actual fun readFileBytes(pathOrName: String): ByteArray {
  val file = File(pathOrName)
  return if (file.exists()) file.readBytes() else byteArrayOf()
}
