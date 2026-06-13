package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.e2eTest
import sample.app.setup.selectFromExposedDropdown
import sample.app.setup.selectDateViaInput
import sample.app.setup.selectTimeFromDial
import sample.app.setup.selectTimeFromDialGeometrically
import sample.app.setup.selectTimeViaInput
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.resolveNode
import io.github.aryapreetam.parikshan.protocol.atIndex
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertTrue

class OverlayIntegrationTest {

  @Test
  fun testDropdownMenuSelection() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")

    // Open Dropdown Menu via ExposedDropdownMenuBox
    click("dropdown_anchor")
    click(Selector.Text("Option Blue"))
    
	assertVisible("Selected Blue from Dropdown")
  }

  @Test
  fun testDropdownMenuSelectionAndScroll() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")

    // Open Dropdown Menu via ExposedDropdownMenuBox
    click("dropdown_anchor")
    
    // Scroll down inside the dropdown menu to find 'Purple'
    scrollUntilVisible(
     containerSelector = Selector.Tag("dropdown_menu"),
     targetSelector = Selector.Text("Option Purple")
   )
    click(Selector.Text("Option Purple"))
    
	assertVisible("Selected Purple from Dropdown")
  }

  @Test
  fun testAlertDialogConfirmation() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("dialog_trigger_button")
    
    click(Selector.Text("Confirm"))
    
    assertVisible("Dialog Confirmed")
    assertNotVisible("alert_dialog")
  }

  @Test
  fun testModalBottomSheetInteraction() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("bottom_sheet_trigger_button")
    assertVisible("bottom_sheet_content")
    
    // Click Execute Action B
    click("sheet_action_b_button")
    
    assertVisible("Action B from Sheet clicked")
  }

  @Test
  fun testDatePickerInputSelection() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("date_picker_trigger_button")
    assertVisible("date_picker_dialog")
    
    // Material 3 date picker input mode test. Platform routing handles waits internally.
    selectDateViaInput("10/24/2026")
    
    // Verify it successfully dismissed and output updated
    assertVisible("Date Selected:")
    val resultText = resolveNode("overlay_result_message").text.orEmpty()
    assertTrue(resultText.startsWith("Date Selected:"), "Expected date selected message, got: $resultText")
  }

  @Test
  fun testTimePickerDialSelection24h() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("time_picker_24h_trigger_button")
    assertVisible("time_picker_dialog")
    
    // TestExtensions handles the platform routing internally
    selectTimeFromDial("22", "30", is24Hour = true)
    
    assertVisible("Time Selected: 22:30")
  }

  @Test
  fun testTimePickerDialSelection12h() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("time_picker_12h_trigger_button")
    assertVisible("time_picker_dialog")
    
    // TestExtensions handles the platform routing internally
    selectTimeFromDial("22", "30", is24Hour = false)
    
    waitFor(Selector.Auto("Time Selected:"))
    val resultText = resolveNode("overlay_result_message").text.orEmpty()
    assertTrue(resultText.contains("22:30") || resultText.contains("10:30"), "Expected 22:30 or 10:30, but got: $resultText")
  }
}
