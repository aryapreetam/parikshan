package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.e2eTest
import kotlinx.coroutines.delay
import io.github.aryapreetam.parikshan.E2ETestLifecycle
import kotlin.test.Test


class ScrollIntegrationTest : E2ETestLifecycle {

  override suspend fun E2ETestScope.beforeEach() {
    navigateToSection("nav_scroll_playground")
    assertVisible("scroll_playground_screen")
  }

  override suspend fun E2ETestScope.afterEach() {
    navigateToSection("nav_home_screen")
  }

  @Test
  fun testLazyColumnScrollingWithStickyHeaders() = e2eTest {
    // Default tab is LazyList
    assertVisible("lazy_column_list")

    // Scroll to section 3 header (index 2)
    scrollUntilVisible(
      containerSelector = Selector.Tag("lazy_column_list"),
      targetSelector = Selector.Tag("sticky_header_2")
    )
    assertVisible("sticky_header_2")

    // Scroll down further to item 25
    scrollUntilVisible(
      containerSelector = Selector.Tag("lazy_column_list"),
      targetSelector = Selector.Tag("lazy_item_25")
    )
    assertVisible("lazy_item_25")
  }

  @Test
  fun testNestedHorizontalScrolling() = e2eTest {
    // Select Nested Scroll Tab
    click("tab_nested_scroll")
    assertVisible("nested_scroll_column")

    // Scroll down to reveal row 7 (index 6)
    scrollUntilVisible(
      containerSelector = Selector.Tag("nested_scroll_column"),
      targetSelector = Selector.Tag("carousel_row_6")
    )

    // Scroll horizontally in row 7 to reveal col 10 (index 9)
    scrollUntilVisible(
      containerSelector = Selector.Tag("carousel_row_6"),
      targetSelector = Selector.Tag("carousel_cell_6_9"),
      direction = ScrollDirection.Right
    )
    assertVisible("carousel_cell_6_9")
  }

  @Test
  fun testGridLayoutScrolling() = e2eTest {
    // Select Grid Tab
    scrollUntilVisible(                                                                                                                                                                                                   
        containerSelector = Selector.Tag("scroll_tab_row"),                                                                                                                                                               
        targetSelector = Selector.Tag("tab_grid_layout"),                                                                                                                                                                 
        direction = ScrollDirection.Right                                                                                                                                                                                 
    )                                                                                                                                                                                                                     
    click("tab_grid_layout")
    assertVisible("lazy_grid_container")

    // Scroll grid to cell 21
    scrollUntilVisible(
      containerSelector = Selector.Tag("lazy_grid_container"),
      targetSelector = Selector.Tag("grid_cell_21")
    )
    assertVisible("grid_cell_21")
  }

  @Test
  fun testCanvasPanning() = e2eTest {
    delay(500)

    // Select Panning Tab
    scrollUntilVisible(
      containerSelector = Selector.Tag("scroll_tab_row"),
      targetSelector = Selector.Tag("tab_panning"),
      direction = ScrollDirection.Right
    )
    click("tab_panning")
    assertVisible("panning_drag_surface")

    // Verify initial state
    assertVisible("panning_target_node")
    val initialBounds = resolveNode("panning_target_node").bounds

    // Perform physical drag/pan on the surface
    val surface = resolveNode("panning_drag_surface")
    drag(
      fromX = surface.bounds.centerX,
      fromY = surface.bounds.centerY,
      toX = surface.bounds.centerX + 100.0,
      toY = surface.bounds.centerY + 100.0,
      durationMs = 600
    )

    // Verify the node has actually moved
    val finalBounds = resolveNode("panning_target_node").bounds
    if (initialBounds == finalBounds) {
        throw AssertionError("Panning failed: Node position did not change. Initial=$initialBounds, Final=$finalBounds")
    }
  }

  @Test
  fun testScrollAndTree() = e2eTest {
    // Select Pull to Refresh tab
    click("tab_pull_to_refresh")
    assertVisible("pull_to_refresh_list")

    // Scroll down to Load More button
    scrollUntilVisible(
      containerSelector = Selector.Tag("pull_to_refresh_list"),
      targetSelector = Selector.Tag("load_more_button")
    )
    click("load_more_button")

    scrollUntilVisible(
      containerSelector = Selector.Tag("pull_to_refresh_list"),
      targetSelector = Selector.Tag("load_more_status")
    )
    assertVisible("load_more_status")
    assertText("load_more_status", "Loading items...")
  }
}
