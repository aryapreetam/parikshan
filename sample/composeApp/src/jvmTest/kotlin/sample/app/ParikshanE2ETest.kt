package sample.app

import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ParikshanE2ETest {
  @Test
  fun testTaskList() =
    e2eTest {
      click("nav_task_list")
      waitFor("task_list_screen")
      assertVisible("task_item_1")
      screenshot("build/parikshan/screenshots/task-list.png")
    }

  @Test
  fun testInputForm() =
    e2eTest {
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
    e2eTest {
      click("nav_scroll_demo")
      waitFor("scroll_demo_screen")
      repeat(8) {
        scroll("scroll_demo_screen", ScrollDirection.Down)
      }
      click("scroll_target_button")
      waitFor("scrolled_action_done")
      val nodes = getTree()
      assertTrue(nodes.any { it.tag == "scroll_target_button" })
    }
}
