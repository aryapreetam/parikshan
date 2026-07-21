package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.resolveNode
import io.github.aryapreetam.parikshan.E2ETestLifecycle
import kotlin.test.Test

class ScrollSceenTest : E2ETestLifecycle {

  override suspend fun E2ETestScope.beforeEach() {
    openAppNavigation(); click("nav_scroll_demo")
    assertVisible("scroll_demo_screen")
  }

  override suspend fun E2ETestScope.afterEach() {
    navigateToSection("nav_home_screen")
  }

  @Test
  fun testScrollAndTree() = e2eTest {
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
}