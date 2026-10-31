package io.github.aryapreetam.parikshan.server

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
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
    val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    val cursor = MatrixCursor(cols)
    val row = cursor.newRow()
    val file = getFileForUri(uri)

    for (col in cols) {
      when (col) {
        OpenableColumns.DISPLAY_NAME, "_display_name" -> {
          val name = file?.name ?: uri.lastPathSegment ?: "file"
          row.add(name)
        }
        OpenableColumns.SIZE, "_size" -> {
          row.add(file?.length() ?: 0L)
        }
        else -> row.add(null)
      }
    }
    return cursor
  }

  override fun getType(uri: Uri): String {
    val name = uri.lastPathSegment ?: ""
    return when {
      name.endsWith(".png", ignoreCase = true) -> "image/png"
      name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
      name.endsWith(".txt", ignoreCase = true) -> "text/plain"
      name.endsWith(".kt", ignoreCase = true) || name.endsWith(".kts", ignoreCase = true) -> "text/plain"
      uri.path?.contains("/tree/") == true -> "vnd.android.cursor.dir/document"
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
    val path = uri.path ?: return null
    val cleanPath = path.removePrefix("/files/").removePrefix("/tree/").removePrefix("/")

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
    val direct = File(path)
    if (direct.exists() && direct.canRead()) return direct

    // Check app cache
    val ctx = context
    if (ctx != null) {
      val cacheFile = File(ctx.cacheDir, cleanPath)
      if (cacheFile.exists()) return cacheFile
    }

    // Fallback if exists even if canRead is not true yet (e.g. ContentProvider runs as system/provider)
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
