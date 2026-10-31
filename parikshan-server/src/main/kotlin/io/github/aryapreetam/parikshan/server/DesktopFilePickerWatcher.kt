package io.github.aryapreetam.parikshan.server

import io.github.aryapreetam.parikshan.protocol.PickerAction
import io.github.aryapreetam.parikshan.protocol.Response
import java.awt.AWTEvent
import java.awt.Container
import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFileChooser

internal class DesktopFilePickerWatcher {
  private val isArmed = AtomicBoolean(false)
  private val isHandled = AtomicBoolean(false)

  @Volatile private var activeAction: PickerAction? = null
  @Volatile private var selectedPaths: List<String> = emptyList()
  @Volatile private var wasCancelled: Boolean = false
  @Volatile private var startTimestamp: Long = 0L
  @Volatile private var elapsedMs: Long = 0L

  private var completionLatch = CountDownLatch(1)
  private var pollingThread: Thread? = null
  private var awtEventListener: AWTEventListener? = null

  fun register(action: PickerAction, timeoutMs: Long) {
    disarm()

    activeAction = action
    selectedPaths = emptyList()
    wasCancelled = false
    startTimestamp = System.currentTimeMillis()
    elapsedMs = 0L
    isHandled.set(false)
    completionLatch = CountDownLatch(1)
    isArmed.set(true)

    // Arm the JVM agent for FileDialog
    System.setProperty(ParikshanAgent.SYSTEM_PROPERTY_ACTIVE, "true")
    when (action) {
      is PickerAction.Select -> {
        System.setProperty(ParikshanAgent.SYSTEM_PROPERTY_PATHS, action.paths.joinToString(File.pathSeparator))
        System.clearProperty(ParikshanAgent.SYSTEM_PROPERTY_CANCEL)
      }
      is PickerAction.Cancel -> {
        System.setProperty(ParikshanAgent.SYSTEM_PROPERTY_CANCEL, "true")
        System.clearProperty(ParikshanAgent.SYSTEM_PROPERTY_PATHS)
      }
    }
    System.clearProperty(ParikshanAgent.SYSTEM_PROPERTY_HANDLED)

    // 1. AWTEventListener for window opened / activated events (e.g. Swing JFileChooser)
    val listener = AWTEventListener { event ->
      if (!isArmed.get() || isHandled.get()) return@AWTEventListener
      if (event is WindowEvent && (event.id == WindowEvent.WINDOW_OPENED || event.id == WindowEvent.WINDOW_ACTIVATED)) {
        val window = event.window
        if (window is Dialog) {
          handleDialogCandidate(window)
        }
      }
    }
    awtEventListener = listener
    runCatching {
      Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)
    }

    // 2. Background polling daemon inspecting agent completion and JFileChooser windows
    val thread = Thread({
      val deadline = System.currentTimeMillis() + timeoutMs
      while (isArmed.get() && !isHandled.get() && System.currentTimeMillis() <= deadline) {
        // Check if agent handled the FileDialog
        if (System.getProperty(ParikshanAgent.SYSTEM_PROPERTY_HANDLED) == "true") {
          System.clearProperty(ParikshanAgent.SYSTEM_PROPERTY_HANDLED)
          val elapsed = System.currentTimeMillis() - startTimestamp
          val paths = when (val act = activeAction) {
            is PickerAction.Select -> act.paths
            is PickerAction.Cancel -> emptyList()
            null -> emptyList()
          }
          val cancelled = activeAction is PickerAction.Cancel

          selectedPaths = paths
          wasCancelled = cancelled
          elapsedMs = elapsed

          if (isHandled.compareAndSet(false, true)) {
            disarm()
            completionLatch.countDown()
            break
          }
        }

        checkOpenWindows()
        try {
          Thread.sleep(10)
        } catch (_: InterruptedException) {
          break
        }
      }
    }, "parikshan-desktop-picker-watcher")
    thread.isDaemon = true
    pollingThread = thread
    thread.start()
  }

  private fun checkOpenWindows() {
    val windows = runCatching { Window.getWindows() }.getOrNull() ?: return
    for (window in windows) {
      if (window.isShowing && window is Dialog) {
        if (handleDialogCandidate(window)) {
          break
        }
      }
    }
  }

  private fun handleDialogCandidate(dialog: Dialog): Boolean {
    if (!isArmed.get() || isHandled.get()) return false
    val action = activeAction ?: return false

    // Narrow dialog matching: ONLY handle if dialog is FileDialog OR contains a JFileChooser
    val isFileDialog = dialog is FileDialog
    val fileChooser = if (!isFileDialog) findFileChooser(dialog) else null
    if (!isFileDialog && fileChooser == null) {
      return false
    }

    // Skip Compose-internal in-app dialogs unless containing a file chooser
    if (dialog.javaClass.name.contains("Compose") && fileChooser == null) {
      return false
    }

    val elapsed = System.currentTimeMillis() - startTimestamp
    val paths = when (action) {
      is PickerAction.Select -> action.paths
      is PickerAction.Cancel -> emptyList()
    }
    val cancelled = action is PickerAction.Cancel

    val applied = when (action) {
      is PickerAction.Select -> applySelection(dialog, action.paths, fileChooser)
      is PickerAction.Cancel -> applyCancellation(dialog, fileChooser)
    }
    if (!applied) {
      return false
    }

    selectedPaths = paths
    wasCancelled = cancelled
    elapsedMs = elapsed

    if (isHandled.compareAndSet(false, true)) {
      disarm()
      completionLatch.countDown()
      return true
    }
    return false
  }

  private fun applySelection(dialog: Dialog, paths: List<String>, fileChooser: JFileChooser? = null): Boolean {
    return try {
      if (dialog is FileDialog) {
        val firstPath = paths.firstOrNull() ?: return false
        val file = File(firstPath)
        dialog.directory = file.parent ?: ""
        dialog.file = file.name
        if (paths.size > 1) {
          applyFilesViaUnsafe(dialog, paths)
        }
        dialog.dispose()
        true
      } else {
        val fc = fileChooser ?: findFileChooser(dialog) ?: return false
        val files = paths.map { File(it) }.toTypedArray()
        if (fc.isMultiSelectionEnabled) {
          fc.selectedFiles = files
        } else {
          fc.selectedFile = files.firstOrNull()
        }
        fc.approveSelection()
        dialog.dispose()
        true
      }
    } catch (e: Throwable) {
      System.err.println("Parikshan: Failed to apply selection to dialog: ${e.message}")
      dialog.dispose()
      false
    }
  }

  private fun applyCancellation(dialog: Dialog, fileChooser: JFileChooser? = null): Boolean {
    return try {
      if (dialog is FileDialog) {
        dialog.file = null
        dialog.dispose()
        true
      } else {
        val fc = fileChooser ?: findFileChooser(dialog) ?: return false
        fc.cancelSelection()
        dialog.dispose()
        true
      }
    } catch (e: Throwable) {
      System.err.println("Parikshan: Failed to cancel dialog: ${e.message}")
      dialog.dispose()
      false
    }
  }

  private fun applyFilesViaUnsafe(dialog: FileDialog, paths: List<String>) {
    runCatching {
      val unsafeClass = Class.forName("sun.misc.Unsafe")
      val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
      theUnsafeField.isAccessible = true
      val unsafe = theUnsafeField.get(null)
      val filesField = FileDialog::class.java.getDeclaredField("files")
      val objectFieldOffsetMethod = unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field::class.java)
      val offset = objectFieldOffsetMethod.invoke(unsafe, filesField) as Long
      val putObjectMethod = unsafeClass.getMethod("putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)
      val filesArray = paths.map { File(it) }.toTypedArray()
      putObjectMethod.invoke(unsafe, dialog, offset, filesArray)
    }
  }

  private fun findFileChooser(container: Container): JFileChooser? {
    if (container is JFileChooser) return container
    for (component in container.components) {
      if (component is JFileChooser) return component
      if (component is Container) {
        val found = findFileChooser(component)
        if (found != null) return found
      }
    }
    return null
  }

  fun unregister(awaitCompletion: Boolean, timeoutMs: Long): Response.FilePickerHandled {
    if (awaitCompletion && isArmed.get() && !isHandled.get()) {
      try {
        completionLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
      } catch (_: InterruptedException) {}
    }

    val handled = isHandled.get()
    val paths = selectedPaths
    val cancelled = wasCancelled
    val elapsed = elapsedMs

    disarm()

    return Response.FilePickerHandled(
      id = "picker_result",
      wasHandled = handled,
      selectedPaths = paths,
      wasCancelled = cancelled,
      elapsedMs = elapsed
    )
  }

  fun disarm() {
    isArmed.set(false)
    System.clearProperty(ParikshanAgent.SYSTEM_PROPERTY_ACTIVE)
    System.clearProperty(ParikshanAgent.SYSTEM_PROPERTY_CANCEL)
    System.clearProperty(ParikshanAgent.SYSTEM_PROPERTY_PATHS)
    System.clearProperty(ParikshanAgent.SYSTEM_PROPERTY_HANDLED)

    awtEventListener?.let { listener ->
      runCatching {
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
      }
      awtEventListener = null
    }
    pollingThread?.interrupt()
    pollingThread = null
    completionLatch.countDown()
  }
}
