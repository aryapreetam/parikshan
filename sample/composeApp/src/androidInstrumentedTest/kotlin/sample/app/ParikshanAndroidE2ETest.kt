package sample.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParikshanAndroidE2ETest {
  @get:Rule
  val composeRule = createAndroidComposeRule<AppActivity>()

  @Test
  fun testTaskList() =
    e2eTest(composeRule) {
      waitFor("nav_task_list")
      click("nav_task_list")
      waitFor("task_list_screen")
      assertVisible("task_item_1")
      val screenshotPath =
        "${composeRule.activity.cacheDir.absolutePath}/parikshan/task-list-android.png"
      screenshot(screenshotPath)
    }

  @Test
  fun testInputForm() =
    e2eTest(composeRule) {
      waitFor("nav_input_form")
      click("nav_input_form")
      waitFor("input_form_screen")
      input("input_name_field", "New Task")
      assertText("input_name_preview", "New Task")
      click("form_submit_button")
      waitFor("form_success_message")
      assertVisible("form_success_message")
    }

  @Test
  fun testScrollAndTree() =
    e2eTest(composeRule) {
      waitFor("nav_scroll_demo")
      click("nav_scroll_demo")
      waitFor("scroll_demo_screen")
      scrollUntilVisible(
        containerTag = "scroll_demo_screen",
        targetTag = "scroll_target_button"
      )
      click("scroll_target_button")
      waitFor("scrolled_action_done")
      val nodes = getTree()
      assertTrue(nodes.any { it.tag == "scroll_target_button" })
    }
}

private suspend fun E2ETestScope.scrollUntilVisible(
  containerTag: String,
  targetTag: String,
  maxScrolls: Int = 40
) {
  repeat(maxScrolls + 1) { attempt ->
    val targetVisible = getTree().any { it.tag == targetTag && it.visible }
    if (targetVisible) {
      return
    }
    if (attempt == maxScrolls) {
      throw AssertionError("Could not make '$targetTag' visible after $maxScrolls scroll actions")
    }
    scroll(containerTag, ScrollDirection.Down)
  }
}
