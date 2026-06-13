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
    
    // Material 3 date picker input mode test
    selectDateViaInput("10/24/2026")
    
    // We just verify it successfully dismissed and output updated
    delay(500)
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
    delay(800)
    try {
        // Select 22:30 (10:30 PM) from the dial
        selectTimeFromDial("22", "30", is24Hour = true)
    } catch (e: AssertionError) {
        println("Dial semantics missing, falling back to geometric tap")
        selectTimeFromDialGeometrically(22, 30, is24Hour = true)
    }
    
    assertVisible("Time Selected: 22:30")
  }

  @Test
  fun testTimePickerDialSelection12h() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("time_picker_12h_trigger_button")
    assertVisible("time_picker_dialog")
    delay(800)
    try {
        // Select 22:30 (10:30 PM) from the dial
        selectTimeFromDial("22", "30", is24Hour = false)
    } catch (e: AssertionError) {
        println("Dial semantics missing, falling back to geometric tap")
        selectTimeFromDialGeometrically(22, 30, is24Hour = false)
    }
    
    assertVisible("Time Selected: 22:30")
  }

  @Test
  fun testDumpTree() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    click("time_picker_24h_trigger_button")
    assertVisible("time_picker_dialog")    
    selectTimeFromDialGeometrically(10, 30, is24Hour = true)
    assertVisible("Time Selected: 10:30")
  }

  @Test
  fun testDumpTree12h() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    click("time_picker_12h_trigger_button")
    assertVisible("time_picker_dialog")    
    selectTimeFromDialGeometrically(22, 30, is24Hour = false)
    assertVisible("Time Selected: 22:30")
  }
}
