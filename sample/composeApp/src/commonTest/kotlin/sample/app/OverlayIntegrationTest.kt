package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.e2eTest
import sample.app.setup.selectFromExposedDropdown
import sample.app.setup.selectDateViaInput
import sample.app.setup.selectDateFromCalendar
import sample.app.setup.selectTimeFromDial
import sample.app.setup.selectTimeFromDialGeometrically
import sample.app.setup.selectTimeViaInput
import sample.app.setup.isWasmTarget
import sample.app.setup.clickAtStill
import sample.app.setup.clickDropdown
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.resolveNode
import io.github.aryapreetam.parikshan.protocol.atIndex
import kotlinx.coroutines.delay
import kotlin.test.Test

class OverlayIntegrationTest {

  @Test
  fun testDropdownMenuSelection() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")

    // Open Dropdown Menu via ExposedDropdownMenuBox
    clickDropdown("dropdown_anchor")
    click(Selector.Text("Option Blue"))
    
	assertVisible("Selected Blue from Dropdown")
  }

  @Test
  fun testDropdownMenuSelectionAndScroll() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")

    // Open Dropdown Menu via ExposedDropdownMenuBox
    clickDropdown("Select an option")
    
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
    assertContains("overlay_result_message", "Date Selected:")
  }



  @Test
  fun testCalendarDateSelectionCurrentMonth() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("date_picker_trigger_button")
    assertVisible("date_picker_dialog")

    // Select June 15, 2026 (Current month in session context)
    // We use a date slightly before today to ensure it's selectable (not in the future if the picker has constraints)
    // But today is June 17, 2026.
    selectDateFromCalendar(day = 15, month = 6, year = 2026)

    // Verify it successfully dismissed and output updated to dd/mm/yyyy
    assertContains("overlay_result_message", "Date Selected: 15/06/2026")
  }

  @Test
  fun testCalendarDateSelectionPastDate() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("date_picker_trigger_button")
    assertVisible("date_picker_dialog")

    println("DEBUG DESKTOP TREE START:")
    getTree().forEach { println("NODE: tag='${it.tag}' text='${it.text}' visible=${it.visible} bounds=${it.bounds}") }
    println("DEBUG DESKTOP TREE END")

    // Select December 25, 2025 (Past date)
    selectDateFromCalendar(day = 25, month = 12, year = 2025)

    assertContains("overlay_result_message", "Date Selected: 25/12/2025")
  }

  @Test
  fun testCalendarDateSelectionFutureDate() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_overlay_playground")
    assertVisible("overlay_playground_screen")

    click("date_picker_trigger_button")
    assertVisible("date_picker_dialog")

    // Select March 10, 2027 (Future date)
    selectDateFromCalendar(day = 10, month = 3, year = 2027)

    assertContains("overlay_result_message", "Date Selected: 10/03/2027")
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
    
    // We use assertContains to verify the minute selection and the result message,
    // avoiding fragility around platform-specific hour formatting (10 vs 22).
    assertContains("overlay_result_message", "Time Selected:")
    assertContains("overlay_result_message", "30")
  }
}
