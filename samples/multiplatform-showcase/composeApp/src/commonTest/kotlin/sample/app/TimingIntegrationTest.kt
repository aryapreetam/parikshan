package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.isWasm
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test
import io.github.aryapreetam.parikshan.E2ETestLifecycle

class TimingIntegrationTest : E2ETestLifecycle {

    override suspend fun E2ETestScope.beforeEach() {
        navigateToSection("nav_timing_playground")
        assertVisible("timing_playground_screen")
    }

    override suspend fun E2ETestScope.afterEach() {
        navigateToSection("nav_home_screen")
    }

  @Test
  fun testAsynchronousLoadingWithPolling() = e2eTest {
    // Assert results not showing initially
    assertNotVisible("async_result_text")

    // Trigger async load (2s delay in app)
    click("trigger_async_load_button")

    // Use built-in polling via waitFor (default 10s)
    waitFor("async_result_text")
    assertVisible("Asynchronous Data Loaded Successfully!")
  }

  @Test
  fun testDynamicLayoutResizingAndCoordinateShifts() = e2eTest {
    // Initially details should not exist
    assertNotVisible("expandable_details_text")

    // Toggle expand card
    clickReliably("expandable_toggle_button")
    
    // Assert details appear and are visible
    waitFor("expandable_details_text")
    assertVisible("expandable_details_text")

    // Toggle collapse card
    clickReliably("expandable_toggle_button")
    
    // Assert details disappear
    assertNotVisible("expandable_details_text")
  }

  private suspend fun E2ETestScope.clickReliably(target: String) {
    if (isWasm()) {
       val node = resolveNode(target)
       // Proof-of-work: 0px drag is the most reliable way to click Canvas items
       drag(fromX = node.bounds.centerX, fromY = node.bounds.centerY, toX = node.bounds.centerX, toY = node.bounds.centerY, durationMs = 400L)
       kotlinx.coroutines.delay(1200)
    } else {
       click(target)
    }
  }
}
