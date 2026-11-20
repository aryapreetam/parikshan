package sample.app

import java.io.File

actual fun readFileBytes(pathOrName: String): ByteArray {
  val file = File(pathOrName)
  if (file.exists()) return file.readBytes()
  val fromUserDir = File(System.getProperty("user.dir"), pathOrName)
  if (fromUserDir.exists()) return fromUserDir.readBytes()
  return byteArrayOf()
}
