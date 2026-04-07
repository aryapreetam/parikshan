package sample.app

import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class ParikshanE2ETest {
  @Test
  fun testTaskList() =
    e2eTest {
      runTaskListScenario(screenshotPath = "build/parikshan/screenshots/task-list.png")
    }

  @Test
  fun testInputForm() =
    e2eTest {
      runInputFormTagRegressionScenario()
    }

  @Test
  fun testScrollAndTree() =
    e2eTest {
      runOffScreenVisibilityScenario()
    }

  @Test
  fun testUniqueTextSelector() =
    e2eTest {
      runUniqueTextScenario()
    }

  @Test
  fun testTagPrecedenceOverText() =
    e2eTest {
      runTagPrecedenceScenario()
    }

  @Test
  fun testAmbiguousTextSelectorFailsClearly() =
    e2eTest {
      runAmbiguousTextScenario()
    }
}
