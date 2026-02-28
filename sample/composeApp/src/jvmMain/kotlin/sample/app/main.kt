import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.aryapreetam.parikshan.server.ParikshanServer
import sample.app.App
import java.awt.Dimension

fun main(args: Array<String>) = application {
  val testMode = args.contains("--test-mode")

  Window(
    title = "sample",
    state = rememberWindowState(width = 800.dp, height = 600.dp),
    onCloseRequest = ::exitApplication,
  ) {
    if (testMode) {
      DisposableEffect(Unit) {
        val server = ParikshanServer.start(window)
        onDispose {
          server.stop()
        }
      }
    }

    window.minimumSize = Dimension(350, 600)
    App()
  }
}
