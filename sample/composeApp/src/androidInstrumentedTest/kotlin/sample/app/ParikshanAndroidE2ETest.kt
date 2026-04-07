package sample.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParikshanAndroidE2ETest {
  @get:Rule
  val composeRule = createAndroidComposeRule<AppActivity>()

  @Test
  fun testTaskList() =
    e2eTest(composeRule) {
      val screenshotPath =
        "${composeRule.activity.cacheDir.absolutePath}/parikshan/task-list-android.png"
      runTaskListScenario(screenshotPath = screenshotPath)
    }

  @Test
  fun testInputForm() =
    e2eTest(composeRule) {
      runInputFormTagRegressionScenario()
    }

  @Test
  fun testScrollAndTree() =
    e2eTest(composeRule) {
      runOffScreenVisibilityScenario()
    }

  @Test
  fun testUniqueTextSelector() =
    e2eTest(composeRule) {
      runUniqueTextScenario()
    }

  @Test
  fun testTagPrecedenceOverText() =
    e2eTest(composeRule) {
      runTagPrecedenceScenario()
    }

  @Test
  fun testAmbiguousTextSelectorFailsClearly() =
    e2eTest(composeRule) {
      runAmbiguousTextScenario()
    }
}
