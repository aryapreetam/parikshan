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

class HomePageTest : E2ETestLifecycle {

  override suspend fun E2ETestScope.beforeEach() {
    openAppNavigation(); click("nav_task_list")
  }

  override suspend fun E2ETestScope.afterEach() {
    navigateToSection("nav_task_list")
  }
 
  @Test
  fun testTaskList() = e2eTest {
    assertVisible("task_item_1")
    screenshot(screenshotPath("task-list"))
  }
}