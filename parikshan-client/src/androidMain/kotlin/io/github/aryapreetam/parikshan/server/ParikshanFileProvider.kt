package io.github.aryapreetam.parikshan.server

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Embedded ContentProvider that serves test fixture files and directories to the
 * application under test via `content://` URIs without requiring any changes to user code.
 *
 * Implements the standard Storage Access Framework (`DocumentsContract`) query, open,
 * and `call()` mutation contract (create, delete, rename, child check) over an app-owned
 * writable sandbox directory so both read and write picker workflows work identically with
 * FileKit, AndroidX DocumentFile, or direct `DocumentsContract` / `ContentResolver` usage.
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
    val isSafTreeOrDocUri = uri.path?.contains("/tree/") == true || uri.path?.contains("/document/") == true

    if (isChildrenQuery) {
      if (targetFile != null && targetFile.isDirectory) {
        val children = targetFile.listFiles()?.sortedBy { it.name } ?: emptyList()
        for (child in children) {
          addFileRow(cursor, cols, child)
        }
      }
      return cursor
    }

    if (targetFile != null) {
      addFileRow(cursor, cols, targetFile)
    } else if (!isSafTreeOrDocUri) {
      // Legacy fallback row for non-SAF /files/<name> URIs when fixture is not on disk
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

  override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
    if (extras == null) return super.call(method, arg, extras)
    return when (method) {
      METHOD_CREATE_DOCUMENT -> handleCreateDocument(extras)
      METHOD_DELETE_DOCUMENT, METHOD_REMOVE_DOCUMENT -> handleDeleteDocument(extras)
      METHOD_RENAME_DOCUMENT -> handleRenameDocument(extras)
      METHOD_IS_CHILD_DOCUMENT -> handleIsChildDocument(extras)
      else -> super.call(method, arg, extras)
    }
  }

  private fun handleCreateDocument(extras: Bundle): Bundle? {
    val parentUri = extras.getParcelableUri(EXTRA_URI) ?: return null
    val mimeType = extras.getString(DocumentsContract.Document.COLUMN_MIME_TYPE)
    val displayName = extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    val parentDir = getFileForUri(parentUri) ?: return null

    val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    val createdFile = ParikshanDocumentStore.createDocument(parentDir, displayName, isDirectory) ?: return null
    val createdDocId = getDocumentIdForFile(createdFile)
    val createdUri = buildDocumentUriForParent(parentUri, createdDocId) ?: return null

    return Bundle().apply {
      putParcelable(EXTRA_URI, createdUri)
    }
  }

  private fun handleDeleteDocument(extras: Bundle): Bundle? {
    val targetUri = extras.getParcelableUri(EXTRA_URI) ?: return null
    val targetFile = getFileForUri(targetUri) ?: return null
    if (!ParikshanDocumentStore.deleteDocument(targetFile)) {
      return null
    }
    return Bundle()
  }

  private fun handleRenameDocument(extras: Bundle): Bundle? {
    val sourceUri = extras.getParcelableUri(EXTRA_URI) ?: return null
    val newDisplayName = extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME) ?: return null
    val sourceFile = getFileForUri(sourceUri) ?: return null

    val renamedFile = ParikshanDocumentStore.renameDocument(sourceFile, newDisplayName) ?: return null
    val renamedDocId = getDocumentIdForFile(renamedFile)
    val renamedUri = buildDocumentUriForParent(sourceUri, renamedDocId) ?: return null

    return Bundle().apply {
      putParcelable(EXTRA_URI, renamedUri)
    }
  }

  private fun handleIsChildDocument(extras: Bundle): Bundle {
    val parentUri = extras.getParcelableUri(EXTRA_URI)
    val childUri = extras.getParcelableUri(EXTRA_TARGET_URI)
    val parentFile = parentUri?.let { getFileForUri(it) }
    val childFile = childUri?.let { getFileForUri(it) }
    val isChild = parentFile != null && childFile != null &&
      ParikshanDocumentStore.isChildDocument(parentFile, childFile)
    return Bundle().apply {
      putBoolean(EXTRA_RESULT, isChild)
    }
  }

  private fun buildDocumentUriForParent(referenceUri: Uri, documentId: String): Uri? {
    val authority = referenceUri.authority ?: context?.let { "${it.packageName}.parikshan.fileprovider" } ?: return null
    return if (referenceUri.path?.startsWith("/tree/") == true) {
      DocumentsContract.buildDocumentUriUsingTree(referenceUri, documentId)
    } else {
      DocumentsContract.buildDocumentUri(authority, documentId)
    }
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
            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
              DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
              DocumentsContract.Document.FLAG_SUPPORTS_RENAME
          } else {
            DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
              DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
              DocumentsContract.Document.FLAG_SUPPORTS_RENAME
          }
          row.add(flags)
        }
        else -> row.add(null)
      }
    }
  }

  private fun getDocumentIdForFile(file: File): String {
    val roots = buildList {
      context?.let { ctx ->
        add(File(ctx.cacheDir, ParikshanDocumentStore.SANDBOX_DIR_NAME))
      }
      add(File(TMP_FIXTURES_ROOT))
      add(File(DOWNLOAD_FIXTURES_ROOT))
      add(File(DOWNLOAD_ROOT))
    }
    return ParikshanDocumentStore.toDocumentId(file, roots)
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

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    val pfdMode = ParcelFileDescriptor.parseMode(mode)
    val wantsWrite = (pfdMode and (ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_READ_WRITE)) != 0
    val existingFile = getFileForUri(uri)

    val targetFile = if (existingFile != null) {
      existingFile
    } else if (wantsWrite && (pfdMode and ParcelFileDescriptor.MODE_CREATE) != 0) {
      resolveCreatableSandboxFile(uri)
        ?: throw FileNotFoundException("Cannot resolve writable path for $uri")
    } else {
      throw FileNotFoundException("Cannot resolve $uri to local file")
    }

    if (wantsWrite && targetFile.isDirectory) {
      throw FileNotFoundException("Cannot open directory $uri as a file")
    }
    return ParcelFileDescriptor.open(targetFile, pfdMode)
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

  private fun extractCleanRelativePath(uri: Uri): String? {
    val rawPath = uri.path ?: return null
    var path = rawPath
    if (path.startsWith("/")) path = path.substring(1)
    if (path.endsWith("/children")) {
      path = path.removeSuffix("/children")
    }

    return when {
      path.contains("document/") -> Uri.decode(path.substringAfter("document/"))
      path.startsWith("tree/") -> Uri.decode(path.removePrefix("tree/"))
      path.startsWith("files/") -> Uri.decode(path.removePrefix("files/"))
      else -> Uri.decode(path)
    }
  }

  private fun resolveCreatableSandboxFile(uri: Uri): File? {
    val ctx = context ?: return null
    val cleanPath = extractCleanRelativePath(uri) ?: return null
    val sandboxRoot = File(ctx.cacheDir, ParikshanDocumentStore.SANDBOX_DIR_NAME).apply { mkdirs() }
    val resolved = ParikshanDocumentStore.resolveRelativeInside(sandboxRoot, cleanPath) ?: return null
    val parent = resolved.parentFile ?: return null
    if (!parent.exists()) return null
    return resolved
  }

  private fun getFileForUri(uri: Uri): File? {
    val rawPath = uri.path ?: return null
    val cleanPath = extractCleanRelativePath(uri) ?: return null

    // 1. Check app-owned writable sandbox mirror first (zero SELinux restrictions on API 24-37+)
    val ctx = context
    if (ctx != null) {
      val sandboxRoot = File(ctx.cacheDir, ParikshanDocumentStore.SANDBOX_DIR_NAME)
      val sandboxFile = ParikshanDocumentStore.resolveRelativeInside(sandboxRoot, cleanPath)
      if (sandboxFile != null && sandboxFile.exists()) {
        return sandboxFile
      }
    }

    // 2. Check /data/local/tmp/parikshan_fixtures directory
    val tmpFixture = File(TMP_FIXTURES_ROOT, cleanPath)
    if (tmpFixture.exists() && tmpFixture.canRead()) return tmpFixture

    // 3. Check fixtures directory in Download
    val fixture = File(DOWNLOAD_FIXTURES_ROOT, cleanPath)
    if (fixture.exists() && fixture.canRead()) return fixture

    // 4. Check download root
    val downloadFile = File(DOWNLOAD_ROOT, cleanPath)
    if (downloadFile.exists() && downloadFile.canRead()) return downloadFile

    // 5. Check direct path
    val direct = File(cleanPath)
    if (direct.exists() && direct.canRead()) return direct

    val directWithSlash = File(rawPath)
    if (directWithSlash.exists() && directWithSlash.canRead()) return directWithSlash

    // 6. Check app cache root
    if (ctx != null) {
      val cacheFile = File(ctx.cacheDir, cleanPath)
      if (cacheFile.exists()) return cacheFile
    }

    // Fallback if exists even if canRead is not true yet
    if (tmpFixture.exists()) return tmpFixture
    if (fixture.exists()) return fixture

    return null
  }

  @Suppress("DEPRECATION")
  private fun Bundle.getParcelableUri(key: String): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      getParcelable(key, Uri::class.java)
    } else {
      getParcelable(key)
    }

  companion object {
    private const val TMP_FIXTURES_ROOT = "/data/local/tmp/parikshan_fixtures"
    private const val DOWNLOAD_FIXTURES_ROOT = "/sdcard/Download/parikshan_fixtures"
    private const val DOWNLOAD_ROOT = "/sdcard/Download"

    private const val METHOD_CREATE_DOCUMENT = "android:createDocument"
    private const val METHOD_DELETE_DOCUMENT = "android:deleteDocument"
    private const val METHOD_REMOVE_DOCUMENT = "android:removeDocument"
    private const val METHOD_RENAME_DOCUMENT = "android:renameDocument"
    private const val METHOD_IS_CHILD_DOCUMENT = "android:isChildDocument"
    private const val EXTRA_URI = "uri"
    private const val EXTRA_TARGET_URI = "android.content.extra.TARGET_URI"
    private const val EXTRA_RESULT = "result"

    /**
     * Copies a staged fixture into the target application's own writable cache sandbox so
     * SAF read/create/write/truncate/rename/delete operations run under the app UID without
     * SELinux restrictions and with clean per-selection state.
     */
    internal fun stageToAppSandbox(
      context: Context,
      pathOrName: String,
      isDirectory: Boolean
    ): File? {
      val cleanName = File(pathOrName).name
      if (!ParikshanDocumentStore.isValidDisplayName(cleanName)) return null

      val candidates = listOf(
        File(TMP_FIXTURES_ROOT, cleanName),
        File(DOWNLOAD_FIXTURES_ROOT, cleanName),
        File(DOWNLOAD_ROOT, cleanName),
        File(pathOrName)
      )
      val source = candidates.firstOrNull { candidate ->
        candidate.exists() && if (isDirectory) candidate.isDirectory else candidate.isFile
      }
      val sandboxRoot = File(context.cacheDir, ParikshanDocumentStore.SANDBOX_DIR_NAME)
      return ParikshanDocumentStore.mirrorToSandbox(
        sandboxRoot = sandboxRoot,
        fixtureName = cleanName,
        source = source,
        isDirectory = isDirectory
      )
    }

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
