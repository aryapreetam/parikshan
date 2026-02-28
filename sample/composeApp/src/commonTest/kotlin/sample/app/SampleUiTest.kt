package sample.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import fiblib.getFibonacciNumbers
import kotlin.test.Test

class SampleUITest {

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun sampleUiTest() = runComposeUiTest {
    setContent { App() }
    val num = 7
    onNodeWithText("Enter a number(1-9)").performTextInput("$num")
    onNodeWithText("First $num fibonacci numbers=${getFibonacciNumbers(num).joinToString(", ")}").assertExists()
  }
}