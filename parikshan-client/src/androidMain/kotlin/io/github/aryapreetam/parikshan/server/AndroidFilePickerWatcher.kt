package io.github.aryapreetam.parikshan.server

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aryapreetam.parikshan.protocol.PickerAction
import io.github.aryapreetam.parikshan.protocol.Response
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal object AndroidFilePickerWatcher {

  private val lock = Any()
  private var activeMonitor: PickerActivityMonitor? = null

  fun register(commandId: String, action: PickerAction, timeoutMs: Long): Response {
    synchronized(lock) {
      // Disarm any prior monitor cleanly
      activeMonitor?.let { monitor ->
        try {
          InstrumentationRegistry.getInstrumentation().removeMonitor(monitor)
        } catch (_: Throwable) {}
      }

      val instrumentation = InstrumentationRegistry.getInstrumentation()
      val context = instrumentation.targetContext
      val authority = "${context.packageName}.parikshan.fileprovider"

      val monitor = PickerActivityMonitor(
        action = action,
        authority = authority
      )
      activeMonitor = monitor
      instrumentation.addMonitor(monitor)
      return Response.Ok(commandId)
    }
  }

  fun unregister(commandId: String, awaitCompletion: Boolean, timeoutMs: Long): Response {
    val monitor: PickerActivityMonitor?
    synchronized(lock) {
      monitor = activeMonitor
      activeMonitor = null
    }

    if (monitor == null) {
      return Response.FilePickerHandled(
        id = commandId,
        wasHandled = false,
        selectedPaths = emptyList(),
        wasCancelled = false,
        elapsedMs = 0L
      )
    }

    try {
      if (awaitCompletion && !monitor.wasHandled.get()) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!monitor.wasHandled.get() && System.currentTimeMillis() < deadline) {
          try {
            Thread.sleep(10L)
          } catch (_: InterruptedException) {
            break
          }
        }
      }

      val elapsed = System.currentTimeMillis() - monitor.startTime
      return Response.FilePickerHandled(
        id = commandId,
        wasHandled = monitor.wasHandled.get(),
        selectedPaths = monitor.selectedPaths,
        wasCancelled = monitor.wasCancelled.get(),
        elapsedMs = elapsed
      )
    } finally {
      try {
        InstrumentationRegistry.getInstrumentation().removeMonitor(monitor)
      } catch (_: Throwable) {}
    }
  }

  private class PickerActivityMonitor(
    val action: PickerAction,
    val authority: String
  ) : Instrumentation.ActivityMonitor() {

    val wasHandled = AtomicBoolean(false)
    val wasCancelled = AtomicBoolean(false)
    val startTime = System.currentTimeMillis()
    @Volatile
    var selectedPaths: List<String> = emptyList()

    override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
      if (!isPickerIntent(intent)) return null

      if (!wasHandled.compareAndSet(false, true)) {
        return null
      }

      val isDirectoryIntent = intent.action == Intent.ACTION_OPEN_DOCUMENT_TREE

      return when (action) {
        is PickerAction.Cancel -> {
          wasCancelled.set(true)
          Instrumentation.ActivityResult(Activity.RESULT_CANCELED, Intent())
        }

        is PickerAction.Select -> {
          selectedPaths = action.paths
          val resultIntent = Intent()
          val uris = action.paths.map { path ->
            val isDir = isDirectoryIntent || File(path).isDirectory
            ParikshanFileProvider.buildUri(authority, path, isDirectory = isDir)
          }

          if (uris.isNotEmpty()) {
            val firstUri = uris.first()
            resultIntent.data = firstUri
            resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (isDirectoryIntent) {
              resultIntent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val clipData = ClipData.newUri(context.contentResolver, "file", firstUri)
            for (i in 1 until uris.size) {
              clipData.addItem(ClipData.Item(uris[i]))
            }
            resultIntent.clipData = clipData
          }

          Instrumentation.ActivityResult(Activity.RESULT_OK, resultIntent)
        }
      }
    }

    private fun isPickerIntent(intent: Intent): Boolean {
      val act = intent.action ?: return false
      return act in PICKER_ACTIONS
    }

    companion object {
      private val PICKER_ACTIONS = setOf(
        Intent.ACTION_GET_CONTENT,
        Intent.ACTION_OPEN_DOCUMENT,
        Intent.ACTION_OPEN_DOCUMENT_TREE,
        Intent.ACTION_CREATE_DOCUMENT,
        "android.provider.action.PICK_IMAGES",
        "androidx.activity.result.contract.action.PICK_IMAGES"
      )
    }
  }
}
