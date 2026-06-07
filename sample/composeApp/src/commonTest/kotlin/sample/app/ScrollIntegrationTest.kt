package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test


class ScrollIntegrationTest {

  @Test
  fun testLazyColumnScrollingWithStickyHeaders() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_scroll_playground")
    assertVisible("scroll_playground_screen")

    // Default tab is LazyList
    assertVisible("lazy_column_list")

    // Scroll to section 6 header (index 5)
    scrollUntilVisible(
      containerSelector = Selector.Tag("lazy_column_list"),
      targetSelector = Selector.Tag("sticky_header_5")
    )
    assertVisible("sticky_header_5")

    // Scroll down further to item 75
    scrollUntilVisible(
      containerSelector = Selector.Tag("lazy_column_list"),
      targetSelector = Selector.Tag("lazy_item_75")
    )
    assertVisible("lazy_item_75")
  }

  @Test
  fun testNestedHorizontalScrolling() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_scroll_playground")
    assertVisible("scroll_playground_screen")

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
    relaunchApp()
    openAppNavigation(); click("nav_scroll_playground")
    assertVisible("scroll_playground_screen")

    // Select Grid Tab
    click("tab_grid_layout")
    assertVisible("lazy_grid_container")

    // Scroll grid to cell 45
    scrollUntilVisible(
      containerSelector = Selector.Tag("lazy_grid_container"),
      targetSelector = Selector.Tag("grid_cell_45")
    )
    assertVisible("grid_cell_45")
  }

  @Test
  fun testCanvasPanning() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_scroll_playground")
    assertVisible("scroll_playground_screen")

    // Select Panning Tab
    click("tab_panning")
    assertVisible("panning_drag_surface")

    // Verify initial state
    assertVisible("panning_target_node")
    assertText("panning_coords_text", "X: 0\nY: 0")

    // Perform physical drag/pan on the surface
    val surface = resolveNode("panning_drag_surface")
    drag(
      fromX = surface.bounds.centerX,
      fromY = surface.bounds.centerY,
      toX = surface.bounds.centerX + 120.0,
      toY = surface.bounds.centerY + 180.0,
      durationMs = 600
    )

    // Verify coordinates updated
    assertText("panning_coords_text", "X: 120\nY: 180")
  }
}
