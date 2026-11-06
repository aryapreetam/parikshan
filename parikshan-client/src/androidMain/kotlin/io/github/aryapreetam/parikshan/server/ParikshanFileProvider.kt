package io.github.aryapreetam.parikshan.server

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

/**
 * Embedded ContentProvider that serves test fixture files and directories to the
 * application under test via `content://` URIs without requiring any changes to user code.
 */
class ParikshanFileProvider : ContentProvider() {

  override fun onCreate(): Boolean = true

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?
  ): Cursor {
    val defaultCols = arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_SIZE,
      DocumentsContract.Document.COLUMN_LAST_MODIFIED,
      DocumentsContract.Document.COLUMN_FLAGS
    )
    val cols = projection ?: defaultCols
    val cursor = MatrixCursor(cols)

    val targetFile = getFileForUri(uri)
    val isChildrenQuery = uri.path?.endsWith("/children") == true

    if (isChildrenQuery && targetFile != null && targetFile.isDirectory) {
      val children = targetFile.listFiles()?.sortedBy { it.name } ?: emptyList()
      for (child in children) {
        addFileRow(cursor, cols, child)
      }
    } else if (targetFile != null) {
      addFileRow(cursor, cols, targetFile)
    } else {
      // Fallback row if file cannot be found on device storage
      val row = cursor.newRow()
      val name = uri.lastPathSegment ?: "file"
      for (col in cols) {
        when (col) {
          DocumentsContract.Document.COLUMN_DISPLAY_NAME, OpenableColumns.DISPLAY_NAME, "_display_name" -> {
            row.add(name)
          }
          DocumentsContract.Document.COLUMN_DOCUMENT_ID, "document_id" -> {
            row.add(name)
          }
          DocumentsContract.Document.COLUMN_MIME_TYPE, "mime_type" -> {
            row.add(getType(uri))
          }
          DocumentsContract.Document.COLUMN_SIZE, OpenableColumns.SIZE, "_size" -> {
            row.add(0L)
          }
          else -> row.add(null)
        }
      }
    }
    return cursor
  }

  private fun addFileRow(
    cursor: MatrixCursor,
    cols: Array<out String>,
    file: File
  ) {
    val row = cursor.newRow()
    val isDir = file.isDirectory
    val mimeType = if (isDir) DocumentsContract.Document.MIME_TYPE_DIR else getTypeForFile(file)
    val docId = getDocumentIdForFile(file)

    for (col in cols) {
      when (col) {
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, OpenableColumns.DISPLAY_NAME, "_display_name" -> {
          row.add(file.name)
        }
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, "document_id" -> {
          row.add(docId)
        }
        DocumentsContract.Document.COLUMN_MIME_TYPE, "mime_type" -> {
          row.add(mimeType)
        }
        DocumentsContract.Document.COLUMN_SIZE, OpenableColumns.SIZE, "_size" -> {
          row.add(if (isDir) 0L else file.length())
        }
        DocumentsContract.Document.COLUMN_LAST_MODIFIED, "last_modified" -> {
          row.add(file.lastModified())
        }
        DocumentsContract.Document.COLUMN_FLAGS, "flags" -> {
          val flags = if (isDir) {
            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
          } else {
            0
          }
          row.add(flags)
        }
        else -> row.add(null)
      }
    }
  }

  private fun getDocumentIdForFile(file: File): String {
    val absPath = file.absolutePath
    val prefixes = listOf(
      "/data/local/tmp/parikshan_fixtures/",
      "/sdcard/Download/parikshan_fixtures/",
      "/sdcard/Download/"
    )
    for (prefix in prefixes) {
      if (absPath.startsWith(prefix)) {
        return absPath.removePrefix(prefix)
      }
    }
    return file.name
  }

  override fun getType(uri: Uri): String {
    val file = getFileForUri(uri)
    if (file != null) {
      return if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else getTypeForFile(file)
    }
    val name = uri.lastPathSegment ?: ""
    return when {
      name.endsWith(".png", ignoreCase = true) -> "image/png"
      name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
      name.endsWith(".txt", ignoreCase = true) -> "text/plain"
      name.endsWith(".kt", ignoreCase = true) || name.endsWith(".kts", ignoreCase = true) -> "text/plain"
      name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
      name.endsWith(".json", ignoreCase = true) -> "application/json"
      uri.path?.contains("/tree/") == true && uri.path?.contains("/document/") != true -> DocumentsContract.Document.MIME_TYPE_DIR
      else -> "application/octet-stream"
    }
  }

  private fun getTypeForFile(file: File): String {
    val name = file.name
    return when {
      name.endsWith(".png", ignoreCase = true) -> "image/png"
      name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
      name.endsWith(".txt", ignoreCase = true) -> "text/plain"
      name.endsWith(".kt", ignoreCase = true) || name.endsWith(".kts", ignoreCase = true) -> "text/plain"
      name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
      name.endsWith(".json", ignoreCase = true) -> "application/json"
      name.endsWith(".xml", ignoreCase = true) -> "text/xml"
      name.endsWith(".csv", ignoreCase = true) -> "text/csv"
      else -> "application/octet-stream"
    }
  }

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
    val file = getFileForUri(uri) ?: throw java.io.FileNotFoundException("Cannot resolve $uri to local file")
    val pfdMode = when (mode) {
      "r" -> ParcelFileDescriptor.MODE_READ_ONLY
      "w" -> ParcelFileDescriptor.MODE_WRITE_ONLY
      "rw" -> ParcelFileDescriptor.MODE_READ_WRITE
      else -> ParcelFileDescriptor.MODE_READ_ONLY
    }
    return ParcelFileDescriptor.open(file, pfdMode)
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

  private fun getFileForUri(uri: Uri): File? {
    val rawPath = uri.path ?: return null
    var path = rawPath
    if (path.startsWith("/")) path = path.substring(1)

    if (path.endsWith("/children")) {
      path = path.removeSuffix("/children")
    }

    val cleanPath = when {
      path.contains("document/") -> {
        val afterDoc = path.substringAfter("document/")
        Uri.decode(afterDoc)
      }
      path.startsWith("tree/") -> {
        val afterTree = path.removePrefix("tree/")
        Uri.decode(afterTree)
      }
      path.startsWith("files/") -> {
        val afterFiles = path.removePrefix("files/")
        Uri.decode(afterFiles)
      }
      else -> Uri.decode(path)
    }

    // Check /data/local/tmp/parikshan_fixtures directory first (world-readable on Android 11+)
    val tmpFixture = File("/data/local/tmp/parikshan_fixtures", cleanPath)
    if (tmpFixture.exists() && tmpFixture.canRead()) return tmpFixture

    // Check fixtures directory in Download
    val fixture = File("/sdcard/Download/parikshan_fixtures", cleanPath)
    if (fixture.exists() && fixture.canRead()) return fixture

    // Check download root
    val downloadFile = File("/sdcard/Download", cleanPath)
    if (downloadFile.exists() && downloadFile.canRead()) return downloadFile

    // Check direct path
    val direct = File(cleanPath)
    if (direct.exists() && direct.canRead()) return direct

    val directWithSlash = File(rawPath)
    if (directWithSlash.exists() && directWithSlash.canRead()) return directWithSlash

    // Check app cache
    val ctx = context
    if (ctx != null) {
      val cacheFile = File(ctx.cacheDir, cleanPath)
      if (cacheFile.exists()) return cacheFile
    }

    // Fallback if exists even if canRead is not true yet
    if (tmpFixture.exists()) return tmpFixture
    if (fixture.exists()) return fixture

    return null
  }

  companion object {
    fun buildUri(authority: String, pathOrName: String, isDirectory: Boolean = false): Uri {
      val cleanName = File(pathOrName).name
      return if (isDirectory) {
        Uri.parse("content://$authority/tree/$cleanName")
      } else {
        Uri.parse("content://$authority/files/$cleanName")
      }
    }
  }
}
