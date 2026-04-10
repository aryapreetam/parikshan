package io.github.aryapreetam.parikshan.server

import androidx.compose.ui.awt.ComposeWindow
import java.awt.Window
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import javax.swing.SwingUtilities

object ParikshanDesktopLauncher {
  @JvmStatic
  fun main(args: Array<String>) {
    val appMainClassName =
      System.getProperty(PARIKSHAN_DESKTOP_APP_MAIN_CLASS_PROPERTY)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: error(
          "Missing required system property '$PARIKSHAN_DESKTOP_APP_MAIN_CLASS_PROPERTY' for the Parikshan desktop launcher."
        )
    val windowTitleOverride =
      System.getProperty(PARIKSHAN_DESKTOP_WINDOW_TITLE_PROPERTY)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    val bootstrap =
      DesktopBootstrapController(
        config = ParikshanServerConfig.fromSystemProperties(),
        requiredWindowTitle = windowTitleOverride
      )
    bootstrap.start()

    try {
      invokeAppMain(
        appMainClassName = appMainClassName,
        args = args
      )
    } finally {
      bootstrap.stop()
    }
  }
}

private class DesktopBootstrapController(
  private val config: ParikshanServerConfig,
  private val requiredWindowTitle: String?,
  private val pollIntervalMs: Long = 100L
) {
  @Volatile
  private var handle: ParikshanServerHandle? = null

  @Volatile
  private var lastStatusMessage: String? = null

  private val watcherThread =
    Thread(::waitForComposeWindowAndStartServer, "parikshan-desktop-bootstrap").apply {
      isDaemon = true
    }

  fun start() {
    watcherThread.start()
  }

  fun stop() {
    watcherThread.interrupt()
    handle?.stop()
    handle = null
  }

  private fun waitForComposeWindowAndStartServer() {
    while (!Thread.currentThread().isInterrupted && handle == null) {
      when (val selection = selectComposeWindow(requiredWindowTitle)) {
        is WindowSelection.Ready -> {
          handle = ParikshanServer.start(window = selection.window, config = config)
          return
        }

        is WindowSelection.Waiting -> {
          if (selection.message != lastStatusMessage) {
            System.err.println(selection.message)
            lastStatusMessage = selection.message
          }
        }
      }

      try {
        Thread.sleep(pollIntervalMs)
      } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        return
      }
    }
  }
}

private sealed interface WindowSelection {
  data class Ready(
    val window: ComposeWindow
  ) : WindowSelection

  data class Waiting(
    val message: String
  ) : WindowSelection
}

private fun selectComposeWindow(requiredWindowTitle: String?): WindowSelection =
  onEdt {
    val visibleWindows =
      Window.getWindows()
        .asSequence()
        .filterIsInstance<ComposeWindow>()
        .filter { it.isShowing }
        .toList()

    if (!requiredWindowTitle.isNullOrBlank()) {
      val matches = visibleWindows.filter { it.title == requiredWindowTitle }
      return@onEdt when {
        matches.size == 1 -> WindowSelection.Ready(matches.single())
        matches.isEmpty() ->
          WindowSelection.Waiting(
            "Parikshan waiting for a visible Compose window titled '$requiredWindowTitle'. " +
              "Visible windows: ${visibleWindows.describeForLogs()}"
          )
        else ->
          WindowSelection.Waiting(
            "Parikshan found multiple visible Compose windows titled '$requiredWindowTitle'. " +
              "Visible windows: ${visibleWindows.describeForLogs()}"
          )
      }
    }

    return@onEdt when (visibleWindows.size) {
      1 -> WindowSelection.Ready(visibleWindows.single())
      0 ->
        WindowSelection.Waiting(
          "Parikshan waiting for a visible Compose window."
        )
      else ->
        WindowSelection.Waiting(
          "Parikshan found multiple visible Compose windows. " +
            "Configure -D$PARIKSHAN_DESKTOP_WINDOW_TITLE_PROPERTY to pick one explicitly. " +
            "Visible windows: ${visibleWindows.describeForLogs()}"
        )
    }
  }

private fun List<ComposeWindow>.describeForLogs(): String =
  if (isEmpty()) {
    "<none>"
  } else {
    joinToString(separator = ", ") { window ->
      val title = window.title.takeIf { it.isNotBlank() } ?: "<untitled>"
      "'$title'"
    }
  }

private fun invokeAppMain(
  appMainClassName: String,
  args: Array<String>
) {
  val appMainClass = Class.forName(appMainClassName)
  val mainMethod =
    appMainClass.findStaticMainMethod()
      ?: error(
        "Parikshan could not find a supported static main method on '$appMainClassName'. " +
          "Expected either main() or main(Array<String>)."
      )

  try {
    when (mainMethod.parameterCount) {
      0 -> mainMethod.invoke(null)
      1 -> mainMethod.invoke(null, args as Any)
      else -> error("Unsupported main method on '$appMainClassName'")
    }
  } catch (error: InvocationTargetException) {
    throw (error.targetException ?: error.cause ?: error)
  }
}

private fun Class<*>.findStaticMainMethod(): Method? =
  methods.firstOrNull { method ->
    method.name == "main" &&
      Modifier.isStatic(method.modifiers) &&
      (
        method.parameterCount == 0 ||
          (
            method.parameterCount == 1 &&
              method.parameterTypes[0] == Array<String>::class.java
          )
        )
  }

private fun <T> onEdt(action: () -> T): T {
  if (SwingUtilities.isEventDispatchThread()) {
    return action()
  }

  var result: T? = null
  var error: Throwable? = null
  SwingUtilities.invokeAndWait {
    try {
      result = action()
    } catch (throwable: Throwable) {
      error = throwable
    }
  }
  error?.let { throw it }
  @Suppress("UNCHECKED_CAST")
  return result as T
}

private const val PARIKSHAN_DESKTOP_APP_MAIN_CLASS_PROPERTY = "parikshan.desktop.appMainClass"
private const val PARIKSHAN_DESKTOP_WINDOW_TITLE_PROPERTY = "parikshan.desktop.windowTitle"
