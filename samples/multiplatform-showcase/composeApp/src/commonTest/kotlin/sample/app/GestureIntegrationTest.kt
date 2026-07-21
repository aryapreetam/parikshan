package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.e2eTest
import sample.app.setup.dragSliderPhysically
import io.github.aryapreetam.parikshan.E2ETestLifecycle
import kotlin.test.Test

class GestureIntegrationTest : E2ETestLifecycle {
    override suspend fun E2ETestScope.beforeEach() {
      navigateToSection("nav_gesture_playground")
      assertVisible("gesture_playground_screen")
    }

    override suspend fun E2ETestScope.afterEach() {
      navigateToSection("nav_home_screen")
    }

  @Test
  fun testTapAndMultiTapGestures() = e2eTest {

    // Test Single Click / Tap via Robot/Playwright fallback on the yellow surface
    scrollUntilVisible(Selector.Tag("gesture_playground_screen"), Selector.Tag("multi_tap_surface"))
    click("multi_tap_surface")
    
    // Check if the single tap registered
    scrollUntilVisible(
      containerSelector = Selector.Tag("gesture_playground_screen"),
      targetSelector = Selector.Tag("gesture_result_text"),
      direction = ScrollDirection.Up
    )
    assertText("gesture_result_text", "Single Tapped Yellow Area")
  }

  @Test
  fun testSwipeToDismissUsingPhysicalDrag() = e2eTest {
      // Skipped on Desktop: Mobile-only idiom
  }

  @Test
  fun testDragAndDropUsingCoordinateDrag() = e2eTest {
    // Verify source card and drop boxes are visible
    assertVisible("draggable_red_card")
    assertVisible("drag_source_box_a")
    assertVisible("drag_target_box_b")

    // Physically drag the red card over to box B
    val startNode = resolveNode("draggable_red_card")
    val targetNode = resolveNode("drag_target_box_b")
    
    drag(
      fromX = startNode.bounds.centerX,
      fromY = startNode.bounds.centerY,
      toX = targetNode.bounds.centerX,
      toY = targetNode.bounds.centerY,
      durationMs = 600
    )

    // The red card is now dropped, turning into a green card
    assertVisible("dropped_green_card")
  }

  @Test
  fun testPullToRefresh() = e2eTest {
      // Skipped on Desktop: Mobile-only idiom
  }
}
