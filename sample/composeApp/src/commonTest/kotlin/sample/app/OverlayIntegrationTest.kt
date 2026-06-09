package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.e2eTest
import sample.app.setup.selectFromExposedDropdown
import sample.app.setup.selectDateViaInput
import sample.app.setup.selectTimeFromDial
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
//    waitFor("dropdown_menu")
    
    // Scroll down inside the dropdown menu to find 'Purple'
    //scrollUntilVisible(
   //   containerSelector = Selector.Tag("dropdown_menu"),
   //   targetSelector = Selector.Text("Option Purple")
   // )
    click(Selector.Text("Option Blue"))
    
    // Assert correct output message
  //  delay(500) // Wait for state update
   // val resultText = resolveNode("overlay_result_message").text.orEmpty()
	assertVisible("Selected Blue from Dropdown")
  }

  @Test
  fun testDropdownMenuSelectionAndScroll() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")

    // Open Dropdown Menu via ExposedDropdownMenuBox
    click("dropdown_anchor")
//    waitFor("dropdown_menu")
    
    // Scroll down inside the dropdown menu to find 'Purple'
    scrollUntilVisible(
     containerSelector = Selector.Tag("dropdown_menu"),
     targetSelector = Selector.Text("Option Purple")
   )
    click(Selector.Text("Option Purple"))
    
    // Assert correct output message
  //  delay(500) // Wait for state update
   // val resultText = resolveNode("overlay_result_message").text.orEmpty()
	assertVisible("Selected Purple from Dropdown")
  }

  @Test
  fun testAlertDialogConfirmation() = e2eTest {
    //relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("dialog_trigger_button")
    //assertVisible("alert_dialog")
    
    click(Selector.Text("Confirm"))
    
    assertVisible("Dialog Confirmed")
    assertNotVisible("alert_dialog")
  }

  @Test
  fun testModalBottomSheetInteraction() = e2eTest {
    //relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("bottom_sheet_trigger_button")
    assertVisible("bottom_sheet_content")
    
    // Click Execute Action B
    click("sheet_action_b_button")
    
    // Assert correct output message and sheet dismissal
    assertText("overlay_result_message", "Action B from Sheet clicked")
    assertNotVisible("bottom_sheet_content")
  }

  @Test
  fun testDatePickerInputSelection() = e2eTest {
    //relaunchApp()
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
    assertNotVisible("date_picker_dialog")
  }

  @Test
  fun testTimePickerInputSelection() = e2eTest {
    //relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("time_picker_trigger_button")
    assertVisible("time_picker_dialog")
    
    // Select 10:30 via direct input
    selectTimeViaInput("10", "30")
    
    //delay(500)
    //val resultText = resolveNode("overlay_result_message").text.orEmpty()
    //assertTrue(resultText.startsWith("Time Selected:"), "Expected time selected message, got: $resultText")
    assertVisible("Time Selected:")
    assertNotVisible("time_picker_dialog")
  }

  @Test
  fun testTimePickerDialSelection() = e2eTest {
    //relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("time_picker_trigger_button")
    assertVisible("time_picker_dialog")
    
    // Select 10:30 from the dial
    selectTimeFromDial("10", "30")
    
    delay(500)
    val resultText = resolveNode("overlay_result_message").text.orEmpty()
    assertTrue(resultText.startsWith("Time Selected:"), "Expected time selected message, got: $resultText")
    assertNotVisible("time_picker_dialog")
  }
}
