import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.jetbrains.compose.resources.configureWebResources
import sample.app.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  configureWebResources {
    resourcePathMapping { path -> "./$path" }
  }
  val body = document.body ?: return
  ComposeViewport(body) {
    App()
  }
}