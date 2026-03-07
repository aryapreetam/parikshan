package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ParikshanE2ETest {
  @Test
  fun testTaskList() =
    e2eTest {
      waitFor("nav_task_list")
      click("nav_task_list")
      waitFor("task_list_screen")
      assertVisible("task_item_1")
      screenshot("build/parikshan/screenshots/task-list.png")
    }

  @Test
  fun testInputForm() =
    e2eTest {
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
    e2eTest {
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
      assertTrue(nodes.isVisibleWithin("scroll_demo_screen", "scroll_target_button"))
    }
}

private suspend fun E2ETestScope.scrollUntilVisible(
  containerTag: String,
  targetTag: String,
  maxScrolls: Int = 40
) {
  repeat(maxScrolls + 1) { attempt ->
    val targetVisible = getTree().isVisibleWithin(containerTag, targetTag)
    if (targetVisible) {
      return
    }
    if (attempt == maxScrolls) {
      throw AssertionError("Could not make '$targetTag' visible after $maxScrolls scroll actions")
    }
    scroll(containerTag, ScrollDirection.Down)
  }
}

private fun List<NodeSnapshot>.isVisibleWithin(
  containerTag: String,
  targetTag: String,
  edgePadding: Double = 24.0
): Boolean {
  val containerBounds = firstOrNull { it.tag == containerTag }?.bounds ?: return false
  val target = firstOrNull { it.tag == targetTag } ?: return false
  if (!target.visible) return false
  return containerBounds.canSafelyInteractWith(target.bounds, edgePadding)
}

private fun Bounds.canSafelyInteractWith(
  other: Bounds,
  edgePadding: Double
): Boolean {
  if (!hasArea() || !other.hasArea()) return false
  val overlapLeft = maxOf(left, other.left)
  val overlapTop = maxOf(top, other.top)
  val overlapRight = minOf(right, other.right)
  val overlapBottom = minOf(bottom, other.bottom)
  if (overlapRight <= overlapLeft || overlapBottom <= overlapTop) return false

  val centerX = (other.left + other.right) / 2.0
  val centerY = (other.top + other.bottom) / 2.0
  val horizontalPadding = edgePadding
  val topPadding = maxOf(edgePadding, other.height() * 0.2)
  val bottomPadding = maxOf(edgePadding, other.height() * 0.35)
  val safeLeft = left + horizontalPadding
  val safeTop = top + topPadding
  val safeRight = right - horizontalPadding
  val safeBottom = bottom - bottomPadding
  if (safeRight <= safeLeft || safeBottom <= safeTop) return false

  return centerX in safeLeft..safeRight && centerY in safeTop..safeBottom
}

private fun Bounds.hasArea(): Boolean = right > left && bottom > top

private fun Bounds.height(): Double = bottom - top
