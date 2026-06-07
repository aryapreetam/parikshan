package sample.app.setup

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.atIndex

/**
 * Interacts with a Material 3 ExposedDropdownMenuBox.
 */
suspend fun E2ETestScope.selectFromExposedDropdown(anchorSelector: Selector, optionText: String) {
    click(anchorSelector)
    click(Selector.Text(optionText))
}

suspend fun E2ETestScope.selectFromExposedDropdown(anchorTag: String, optionText: String) {
    selectFromExposedDropdown(Selector.Auto(anchorTag), optionText)
}

/**
 * Interacts with a Material 3 DatePickerDialog via manual grid selection.
 */
suspend fun E2ETestScope.selectDateFromGrid(dateText: String) {
    // Click the specific date in the calendar grid. This relies on the content description or text.
    click(Selector.Text(dateText))
    click(Selector.Text("OK"))
}

/**
 * Interacts with a Material 3 TimePicker via dial selection.
 */
suspend fun E2ETestScope.selectTimeFromDial(hourText: String, minuteText: String) {
    // Click the hour on the dial uniquely
    click(Selector.Text("$hourText o'clock"))
    // Click the minute on the dial
    val minuteSelector = if (hasVisibleNode(Selector.Text("$minuteText minutes"))) {
        Selector.Text("$minuteText minutes")
    } else {
        Selector.Text(minuteText)
    }
    click(minuteSelector)
    click(Selector.Tag("time_picker_ok_button").atIndex(0))
}

/**
 * Interacts with a Material 3 DatePickerDialog via text input mode.
 */
suspend fun E2ETestScope.selectDateViaInput(dateString: String) {
    // Switch to input mode
    click(Selector.Auto("Switch to text input"))
    
    // In text input mode, the TextField usually has a label "Date"
    input(Selector.Auto("Date"), dateString)
    click(Selector.Tag("date_picker_ok_button").atIndex(0))
}

/**
 * Interacts with a Material 3 TimePicker via text input mode.
 */
suspend fun E2ETestScope.selectTimeViaInput(hour: String, minute: String) {
    // Switch to input mode using the tag we added in OverlayPlayground
    // On small screens, we might need to scroll the dialog content
    click(Selector.Tag("toggle_time_picker_mode_button"))
    
    // Dynamically look for the two time text fields
    val hourSelector = if (hasVisibleNode(Selector.Text("Hour"))) Selector.Text("Hour") else Selector.Text("12")
    val minuteSelector = if (hasVisibleNode(Selector.Text("Minute"))) Selector.Text("Minute") else Selector.Text("00")
    
    input(hourSelector, hour)
    input(minuteSelector, minute)
    
    click(Selector.Tag("time_picker_ok_button").atIndex(0))
}

/**
 * Drags a slider physically by calculating its bounds.
 */
suspend fun E2ETestScope.dragSliderPhysically(sliderSelector: Selector, percent: Float) {
    val node = resolveNode(sliderSelector)
    val bounds = node.bounds
    val width = bounds.right - bounds.left
    
    // Calculate current thumb position roughly (center of the bounds)
    val startX = bounds.centerX
    val startY = bounds.centerY
    
    // Calculate target thumb position based on percentage
    val targetX = bounds.left + (width * percent)
    
    drag(fromX = startX, fromY = startY, toX = targetX, toY = startY, durationMs = 500L)
}

suspend fun E2ETestScope.dragSliderPhysically(tag: String, percent: Float) {
    dragSliderPhysically(Selector.Auto(tag), percent)
}
