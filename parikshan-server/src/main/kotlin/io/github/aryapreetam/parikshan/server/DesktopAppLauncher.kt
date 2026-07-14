package io.github.aryapreetam.parikshan.server

import androidx.compose.ui.awt.ComposeWindow
import java.awt.Window
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import javax.swing.SwingUtilities

object DesktopAppLauncher {
  @JvmStatic
  fun main(args: Array<String>) {
    val appMainClassName =
      System.getProperty(PARIKSHAN_DESKTOP_APP_MAIN_CLASS_PROPERTY)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: error(
          "Missing required system property '$PARIKSHAN_DESKTOP_APP_MAIN_CLASS_PROPERTY' for the desktop app launcher."
        )
    val windowTitleOverride =
      System.getProperty(PARIKSHAN_DESKTOP_WINDOW_TITLE_PROPERTY)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    if (System.getProperty("parikshan.background") == "true") {
      System.err.println("Parikshan: Background mode active. Enforcing focus-prevention listener.")
      java.awt.Toolkit.getDefaultToolkit().addAWTEventListener({ event ->
        if (event is java.awt.event.WindowEvent && event.id == java.awt.event.WindowEvent.WINDOW_OPENED) {
          val window = event.window
          if (window is ComposeWindow) {
            System.err.println("Parikshan: Intercepted window open, applying background settings to '${window.title}'")
            window.setFocusableWindowState(true)
            window.setAutoRequestFocus(false)
            window.setAlwaysOnTop(true)
          }
        }
      }, java.awt.AWTEvent.WINDOW_EVENT_MASK)
    }

    val bootstrap =
      DesktopBootstrapController(
        config = TestServerConfig.fromSystemProperties(),
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
  private val config: TestServerConfig,
  private val requiredWindowTitle: String?,
  private val pollIntervalMs: Long = 100L
) {
  @Volatile
  private var handle: TestServerHandle? = null

  @Volatile
  private var lastStatusMessage: String? = null

  private val activeWindows = java.util.concurrent.ConcurrentHashMap.newKeySet<Window>()

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
    activeWindows.clear()
  }

  private fun waitForComposeWindowAndStartServer() {
    val isBackground = System.getProperty("parikshan.background") == "true"
    val isFocusDisabled = System.getProperty("parikshan.desktop.focus") == "false"
    
    while (!Thread.currentThread().isInterrupted) {
      if (isBackground) {
          applyBackgroundSettings()
      }
      if (isFocusDisabled) {
          applyFocusPrevention()
      }

      val x = System.getProperty("parikshan.desktop.windowX")?.toIntOrNull()
      val y = System.getProperty("parikshan.desktop.windowY")?.toIntOrNull()
      val w = System.getProperty("parikshan.desktop.windowWidth")?.toIntOrNull()
      val h = System.getProperty("parikshan.desktop.windowHeight")?.toIntOrNull()

      if (x != null || y != null || w != null || h != null) {
        onEdt {
          Window.getWindows().filterIsInstance<ComposeWindow>().forEach { window ->
            if (activeWindows.add(window)) {
              val targetX = x ?: window.x
              val targetY = y ?: window.y
              val targetW = w ?: window.width
              val targetH = h ?: window.height
              System.err.println("Parikshan: Registered new window '${window.title}' with target bounds: ($targetX, $targetY, $targetW, $targetH)")
              
              val listener = object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent?) {
                  onEdt {
                    if (window.width != targetW || window.height != targetH) {
                      System.err.println("Parikshan: Restoring window size to target bounds: width=$targetW, height=$targetH (was ${window.width}x${window.height})")
                      window.setSize(targetW, targetH)
                    }
                  }
                }
                override fun componentMoved(e: java.awt.event.ComponentEvent?) {
                  onEdt {
                    if (window.x != targetX || window.y != targetY) {
                      System.err.println("Parikshan: Restoring window position to target bounds: x=$targetX, y=$targetY (was ${window.x},${window.y})")
                      window.setLocation(targetX, targetY)
                    }
                  }
                }
              }
              window.addComponentListener(listener)
              window.setBounds(targetX, targetY, targetW, targetH)
            }
          }
        }
      }

      when (val selection = selectComposeWindow(requiredWindowTitle)) {
        is WindowSelection.Ready -> {
          if (handle == null) {
            val window = selection.window
            System.err.println("Parikshan: Found visible Compose window. Starting server...")
            handle = E2ETestServer.start(window = window, config = config)
            System.err.println("Parikshan: Server started on ${config.host}:${config.port}")
            
            if (isBackground) {
                onEdt { window.isAlwaysOnTop = true }
            }
          }
        }

        is WindowSelection.Waiting -> {
          if (handle == null && selection.message != lastStatusMessage) {
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

  private fun applyBackgroundSettings() {
    onEdt {
      Window.getWindows().filterIsInstance<ComposeWindow>().forEach { window ->
        if (!window.isAlwaysOnTop || window.isAutoRequestFocus) {
          System.err.println("Parikshan: Applying background settings to window '${window.title}'")
          window.setFocusableWindowState(true)
          window.setAutoRequestFocus(false)
          window.setAlwaysOnTop(true)
        }
      }
    }
  }

  private fun applyFocusPrevention() {
    onEdt {
      Window.getWindows().forEach { window ->
        if (window is ComposeWindow) {
          if (window.isAutoRequestFocus) {
            System.err.println("Parikshan: Applying focus prevention to window '${window.title}'")
            window.setFocusableWindowState(true)
            window.setAutoRequestFocus(false)
          }
        }
      }
    }
  }

  private fun applyWindowPosition() {
    val x = System.getProperty("parikshan.desktop.windowX")?.toIntOrNull()
    val y = System.getProperty("parikshan.desktop.windowY")?.toIntOrNull()
    val w = System.getProperty("parikshan.desktop.windowWidth")?.toIntOrNull()
    val h = System.getProperty("parikshan.desktop.windowHeight")?.toIntOrNull()
    System.err.println("Parikshan window bounds loaded: x=$x, y=$y, w=$w, h=$h")

    if (x != null || y != null || w != null || h != null) {
      onEdt {
        Window.getWindows().filterIsInstance<ComposeWindow>().forEach { window ->
          val targetX = x ?: window.x
          val targetY = y ?: window.y
          val targetW = w ?: window.width
          val targetH = h ?: window.height
          if (window.x != targetX || window.y != targetY || window.width != targetW || window.height != targetH) {
            System.err.println("Parikshan: Positioning new window to match bounds: ($targetX, $targetY, $targetW, $targetH)")
            window.setBounds(targetX, targetY, targetW, targetH)
          }
        }
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
