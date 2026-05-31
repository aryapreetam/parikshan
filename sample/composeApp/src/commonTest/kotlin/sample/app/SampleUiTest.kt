package sample.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

class SampleUITest {

  @OptIn(ExperimentalTestApi::class, kotlin.js.ExperimentalWasmJsInterop::class)
  @Test
  fun sampleUiTest() = runComposeUiTest {
    setContent { App() }
  }
}
