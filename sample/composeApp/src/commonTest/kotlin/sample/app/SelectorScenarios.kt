package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.Selector
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.resolveNode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectorScenarios {

  @Test
  fun testTaskList() = e2eTest {
    waitFor("nav_task_list")
    click("nav_task_list")
    waitFor("task_list_screen")
    assertVisible("task_item_1")
    screenshot(screenshotPath("task-list"))
  }

  @Test
  fun testInputForm() = e2eTest {
    openInputForm()
    input("input_name_field", "New Task")
    waitForVisibleText("input_name_preview", expected = "New Task")
    assertText("input_name_preview", "New Task")
    click("form_submit_button")
    waitFor("form_success_message")
    assertVisible("form_success_message")
  }

  @Test
  fun testUniqueTextSelector() = e2eTest {
    openInputForm()
    click("Unique Text Action")
    waitFor("Unique text clicked")
    assertVisible("Unique text clicked")
  }

  @Test
  fun testTagPrecedenceOverText() = e2eTest {
    openInputForm()
    click("Submit")
    waitFor("Tag selector won")
    assertText("selector_result_message", "Tag selector won")
  }

  @Test
  fun testAmbiguousTextSelectorFailsClearly() = e2eTest {
    openInputForm()

    val error =
      kotlin.runCatching {
        click("Duplicate Action")
      }.exceptionOrNull() as? AssertionError
        ?: throw AssertionError("Expected click(\"Duplicate Action\") to fail because the text is ambiguous")

    assertContains(error.message.orEmpty(), "multiple visible text nodes")
    assertContains(error.message.orEmpty(), "duplicate_action_primary")
    assertContains(error.message.orEmpty(), "duplicate_action_secondary")
  }

  @Test
  fun testScrollAndTree() = e2eTest {
    waitFor("nav_scroll_demo")
    click("nav_scroll_demo")
    waitFor("scroll_demo_screen")

    val initialNodes = getTree()
    assertFalse(
      initialNodes.isVisibleWithin(
        containerSelector = Selector.Tag("scroll_demo_screen"),
        targetSelector = Selector.Auto("Trigger Bottom Action")
      ),
      "Bottom action should not be interactable before scrolling"
    )

    scrollUntilVisible(
      containerSelector = Selector.Tag("scroll_demo_screen"),
      targetSelector = Selector.Auto("Trigger Bottom Action")
    )
    click("Trigger Bottom Action")
    
    // Wait for it to appear, then assert it is physically visible on screen
    waitFor("done")
    assertVisible("done")
  }
}

private suspend fun E2ETestScope.openInputForm() {
  waitFor("nav_input_form")
  click("nav_input_form")
  waitFor("input_form_screen")
}

private suspend fun E2ETestScope.scrollUntilVisible(
  containerSelector: Selector,
  targetSelector: Selector,
  edgePadding: Double = 24.0,
  maxScrolls: Int = 40
) {
  repeat(maxScrolls + 1) { attempt ->
    val targetVisible = getTree().isVisibleWithin(containerSelector, targetSelector, edgePadding)
    if (targetVisible) {
      return
    }
    if (attempt == maxScrolls) {
      throw AssertionError(
        "Could not make '${targetSelector.raw}' visible after $maxScrolls scroll actions"
      )
    }
    scroll(containerSelector, ScrollDirection.Down)
  }
}

private fun List<NodeSnapshot>.isVisibleWithin(
  containerTag: String,
  targetTag: String,
  edgePadding: Double = 24.0
): Boolean =
  isVisibleWithin(
    containerSelector = Selector.Tag(containerTag),
    targetSelector = Selector.Auto(targetTag),
    edgePadding = edgePadding
  )

private fun List<NodeSnapshot>.isVisibleWithin(
  containerSelector: Selector,
  targetSelector: Selector,
  edgePadding: Double = 24.0
): Boolean {
  val containerBounds =
    try {
      containerSelector.resolveNode(this, requireVisible = false).node.bounds
    } catch (e: io.github.aryapreetam.parikshan.SelectorResolutionException) {
      return false
    }
  val target =
    try {
      targetSelector.resolveNode(this, requireVisible = false).node
    } catch (e: io.github.aryapreetam.parikshan.SelectorResolutionException) {
      return false
    }
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
