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

class HomeScreenTest : E2ETestLifecycle {

  override suspend fun E2ETestScope.beforeEach() {
    openAppNavigation(); click("nav_home_screen")
  }

  override suspend fun E2ETestScope.afterEach() {
    navigateToSection("nav_home_screen")
  }
 
  @Test
  fun testHomeScreen() = e2eTest {
    assertVisible("home_screen")
    assertVisible("parikshan_logo_image")
    assertText("parikshan_title", "Parikshan")
    assertContains("parikshan_description", "End-to-End Testing")
    screenshot(screenshotPath("home-screen"))
  }
}