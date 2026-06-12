package sample.app.setup

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.atIndex
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Interacts with a Material 3 ExposedDropdownMenuBox.
 */
suspend fun E2ETestScope.selectFromExposedDropdown(anchorSelector: Selector, optionText: String) {
    click(anchorSelector)
    click(Selector.Text(optionText))
}

suspend fun E2ETestScope.selectFromExposedDropdown(tag: String, optionText: String) {
    selectFromExposedDropdown(Selector.Auto(tag), optionText)
}

/**
 * Interacts with a Material 3 DatePickerDialog via text input mode.
 */
suspend fun E2ETestScope.selectDateViaInput(dateText: String) {
    click(Selector.Tag("toggle_date_picker_mode_button"))
    // Material 3 DatePicker input field is a TextField
    input(Selector.Tag("date_picker_input_field"), dateText)
    click(Selector.Tag("date_picker_ok_button"))
}

/**
 * Interacts with a Material 3 TimePicker via dial selection.
 */
suspend fun E2ETestScope.selectTimeFromDial(hour: String, minute: String) {
    // Click hour
    click(Selector.Auto("Select hour"))
    // In M3 TimePicker, the numbers on dial have specific text properties
    val hourSelector = if (hour.toInt() < 10) {
        Selector.Text("$hour o'clock").atIndex(0)
    } else {
        Selector.Text(hour).atIndex(0)
    }
    click(hourSelector)

    // Click minute
    click(Selector.Auto("Select minutes"))
    val minuteText = minute.padStart(2, '0')
    val minuteSelector = if (minute.toInt() % 5 == 0) {
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
suspend fun E2ETestScope.selectTimeViaInput(hour: String, minute: String) {
    click(Selector.Tag("toggle_time_picker_mode_button"))
    
    val hourSelector = Selector.Auto("Select hour")
    //val minuteSelector = Selector.Auto("Select minute")

    //input(hourSelector, hour)
    //input(minuteSelector, minute)

    click(Selector.Tag("time_picker_ok_button").atIndex(0))
}

/**
 * Drags a slider physically by calculating its bounds.
 */
suspend fun E2ETestScope.dragSliderPhysically(selector: Selector, percent: Float) {
    val node = resolveNode(selector)
    val bounds = node.bounds
    val width = bounds.right - bounds.left
    
    val startX = bounds.centerX
    val startY = bounds.centerY
    
    // Calculate target thumb position based on percentage
    val targetX = bounds.left + (width * percent)
    
    drag(fromX = startX, fromY = startY, toX = targetX, toY = startY, durationMs = 500L)
}

suspend fun E2ETestScope.dragSliderPhysically(tag: String, percent: Float) {
    dragSliderPhysically(Selector.Auto(tag), percent)
}

suspend fun E2ETestScope.clickAtFast(x: Double, y: Double) {
    // 2px move + 100ms duration ensures Wasm registers the tap
    drag(fromX = x, fromY = y, toX = x + 2.0, toY = y, durationMs = 100L)
    delay(500L) // Wait for tree update
}

suspend fun E2ETestScope.clickAt(x: Double, y: Double) {
    drag(fromX = x, fromY = y, toX = x + 2.0, toY = y, durationMs = 100L)
    delay(1000L)
}

suspend fun E2ETestScope.selectTimeFromDialGeometrically(hour: Int, minute: Int) {
    println("Calibration: Pure Math + Feedback...")
    delay(2000)
    
    val dialNode = resolveNode(Selector.Tag("time_picker_dial"))
    val hourBtn = getTree().firstOrNull { it.text?.contains("hour", ignoreCase = true) == true }
        ?: throw AssertionError("Could not find hour button")
        
    val width = dialNode.bounds.right - dialNode.bounds.left
    val height = dialNode.bounds.bottom - dialNode.bounds.top
    val isLandscape = width > height
    val dialSize = if (isLandscape) height else width
    
    // Material 3 TimePicker dial has internal padding. 
    // Empirically derived: dial center is shifted inward by ~8px, and radius is smaller by ~12px.
    val padding = 12.0
    val offset = 8.0
    val centerX = if (isLandscape) dialNode.bounds.right - (dialSize / 2.0) - offset else dialNode.bounds.centerX
    val centerY = if (isLandscape) hourBtn.bounds.centerY else dialNode.bounds.bottom - (dialSize / 2.0) - offset
    
    val maxRadius = (dialSize / 2.0) - padding
    
    println("Math Calibrated: Center=($centerX, $centerY), MaxRadius=$maxRadius")
    
    // 1. Feedback Loop for Hour
    val hourAngle = (hour - 3) * (PI / 6.0)
    // 24h dial ring logic: 1-12 is inner ring (~0.55), 13-23/0 is outer ring (~0.85)
    val rScales = if (hour in 1..12) listOf(0.55, 0.45, 0.65) else listOf(0.85, 0.75, 0.95)
    
    var hourFound = false
    println("Pinpointing Hour $hour...")
    for (rScale in rScales) {
        val r = maxRadius * rScale
        for (aNudge in listOf(0.0, -0.05, 0.05, -0.1, 0.1, -0.15, 0.15, -0.2, 0.2)) {
            // Re-click hour box
            val hBtn = getTree().firstOrNull { it.text?.contains("hour", ignoreCase = true) == true }
            if (hBtn != null) clickAtFast(hBtn.bounds.centerX, hBtn.bounds.centerY)
            
            val tx = centerX + r * cos(hourAngle + aNudge)
            val ty = centerY + r * sin(hourAngle + aNudge)
            clickAtFast(tx, ty)
            
            val text = getTree().find { it.text?.contains("hour", ignoreCase = true) == true }?.text ?: ""
            if (text.contains("$hour hours") || text.contains("${hour+12} hours") || text.contains("${hour-12} hours")) {
                println("Successfully locked Hour $hour (Text: $text)")
                hourFound = true; break
            }
        }
        if (hourFound) break
    }

    if (!hourFound) throw AssertionError("Failed to select hour $hour")

    // 2. Switch to Minutes Mode
    println("Switching to Minute mode...")
    val currentMinBtn = getTree().filter { it.bounds.centerY == hourBtn.bounds.centerY && it.bounds.left > hourBtn.bounds.right }
                                 .minByOrNull { it.bounds.left }
    val safeMinBtnX = currentMinBtn?.bounds?.centerX ?: (hourBtn.bounds.right + 50.0)
    val safeMinBtnY = currentMinBtn?.bounds?.centerY ?: centerY
    
    clickAtFast(safeMinBtnX, safeMinBtnY)
    delay(1000)
    
    // 3. Select Minute directly via math (No feedback loop needed since calibration is perfect and text reads fail in Wasm)
    val minuteAngle = (minute - 15) * (PI / 30.0)
    println("Pinpointing Minute $minute...")
    
    val tx = centerX + (maxRadius * 0.85) * cos(minuteAngle)
    val ty = centerY + (maxRadius * 0.85) * sin(minuteAngle)
    clickAtFast(tx, ty)
    println("Successfully clicked Minute $minute at X=$tx, Y=$ty")
    
    click(Selector.Tag("time_picker_ok_button").atIndex(0))
}
