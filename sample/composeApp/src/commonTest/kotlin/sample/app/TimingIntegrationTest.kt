package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class TimingIntegrationTest {

  @Test
  fun testAsynchronousLoadingWithPolling() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_timing_playground")
    assertVisible("timing_playground_screen")

    // Assert results not showing initially
    assertNotVisible("async_result_text")

    // Click trigger to start loading (which has a 2-second delay)
    click("trigger_async_load_button")

    // Assert spinner is shown immediately
    assertVisible("timing_loading_spinner")

    // Assert result is eventually visible (automatically handles wait polling up to default timeout)
    assertText("async_result_text", "Asynchronous Data Loaded Successfully!")

    // Assert spinner is now gone
    assertNotVisible("timing_loading_spinner")
  }

  @Test
  fun testDynamicLayoutResizingAndCoordinateShifts() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_timing_playground")
    assertVisible("timing_playground_screen")

    // Initially details should not exist
    assertNotVisible("expandable_details_text")

    // Toggle expand card
    click("expandable_toggle_button")
    
    // Assert details appear and are visible
    assertVisible("expandable_details_text")

    // Toggle collapse card
    click("expandable_toggle_button")
    
    // Assert details disappear
    assertNotVisible("expandable_details_text")
  }
}