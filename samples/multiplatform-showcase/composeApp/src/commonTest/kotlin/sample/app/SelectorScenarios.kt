package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.resolveNode
import kotlin.test.Test

class SelectorScenarios {
 
  @Test
  fun testTaskListDisplays() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_task_list")
    assertVisible("Task 1")
  }

  @Test
  fun testTaskList() = e2eTest {
    openAppNavigation(); click("nav_task_list")
    assertVisible("task_item_1")
    screenshot(screenshotPath("task-list"))
  }

  @Test
  fun testInputForm() = e2eTest {
    openInputForm()
    input("input_name_field", "New Task")
    assertText("input_name_preview", "New Task")
    click("form_submit_button")
    assertVisible("form_success_message")
  }

  @Test
  fun testUniqueTextSelector() = e2eTest {
    openInputForm()
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Auto("Unique Text Action"))
    click("Unique Text Action")
    assertVisible("Unique text clicked")
  }

  @Test
  fun testTagPrecedenceOverText() = e2eTest {
    openInputForm()
    click("Submit")
    assertText("selector_result_message", "Tag selector won")
  }

  @Test
  fun testAmbiguousTextSelectorFailsClearly() = e2eTest {
    openInputForm()
    // Scroll to the second button to ensure BOTH "Duplicate Action" buttons are physically visible
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Tag("duplicate_action_secondary"))

    assertFailure("multiple visible nodes") {
      click("Duplicate Action")
    }
  }

  @Test
  fun testScrollAndTree() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_scroll_demo")
    assertVisible("scroll_demo_screen")

    assertNotVisible(
      "Trigger Bottom Action",
      "Bottom action should not be interactable before scrolling"
    )

    scrollUntilVisible(
      containerSelector = Selector.Tag("scroll_demo_screen"),
      targetSelector = Selector.Auto("Trigger Bottom Action")
    )
    click("Trigger Bottom Action")

    // Ensure 'done' is physically visible on screen before asserting
    scrollUntilVisible(
      containerSelector = Selector.Tag("scroll_demo_screen"),
      targetSelector = Selector.Auto("done")
    )
    assertVisible("done")
  }

  @Test
  fun testRelaunchAppReturnsToLaunchScreen() = e2eTest {
    openAppNavigation(); click("nav_input_form")
    assertVisible("input_form_screen")

    relaunchApp()

    assertVisible("task_list_screen")
    assertVisible("task_item_1")
  }
}

private suspend fun E2ETestScope.openInputForm() {
  openAppNavigation(); click("nav_input_form")
  assertVisible("input_form_screen")
}

