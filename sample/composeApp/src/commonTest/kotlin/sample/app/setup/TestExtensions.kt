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
suspend fun E2ETestScope.selectTimeFromDial(hour: String, minute: String, is24Hour: Boolean = true) {
    // Select AM/PM if needed
    if (!is24Hour) {
        val h = hour.toInt()
        val amPmSelector = if (h < 12) Selector.Text("AM") else Selector.Text("PM")
        click(amPmSelector)
    }

    // Click hour
    click(Selector.Auto("Select hour"))
    // In M3 TimePicker, the numbers on dial have specific text properties
    val hInt = hour.toInt()
    val displayHour = if (!is24Hour) {
        if (hInt == 0) "12" else if (hInt > 12) (hInt - 12).toString() else hInt.toString()
    } else {
        hour
    }

    val hourSelector = if (displayHour.toInt() < 10) {
        Selector.Text("$displayHour o'clock").atIndex(0)
    } else {
        Selector.Text(displayHour).atIndex(0)
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
    // 5px move ensures Wasm registers the pointer sequence
    drag(fromX = x, fromY = y, toX = x + 5.0, toY = y, durationMs = 100L)
    delay(800L) 
}

suspend fun E2ETestScope.clickAtStill(x: Double, y: Double) {
    // 0px move but 150ms duration ensures high precision for minute targets like '30'
    drag(fromX = x, fromY = y, toX = x, toY = y, durationMs = 150L)
    delay(1000L)
}

suspend fun E2ETestScope.selectTimeFromDialGeometrically(hour: Int, minute: Int, is24Hour: Boolean = true) {
    println("Calibration: Pure Math + Targeted Taps (is24Hour=$is24Hour)...")
    delay(2000)
    
    val dialNode = resolveNode(Selector.Tag("time_picker_dial"))
    val initialTree = getTree()
    val hourBtn = initialTree.firstOrNull { it.text?.contains("hour", ignoreCase = true) == true }
        ?: throw AssertionError("Could not find hour button")
        
    val width = dialNode.bounds.right - dialNode.bounds.left
    val height = dialNode.bounds.bottom - dialNode.bounds.top
    val isLandscape = width > height
    val dialSize = if (isLandscape) height else width
    
    val padding = 12.0
    val offset = 8.0
    val centerX = if (isLandscape) dialNode.bounds.right - (dialSize / 2.0) - offset else dialNode.bounds.centerX
    val centerY = if (isLandscape) hourBtn.bounds.centerY else dialNode.bounds.bottom - (dialSize / 2.0) - offset
    
    val maxRadius = (dialSize / 2.0) - padding
    
    println("Math Calibrated: Center=($centerX, $centerY), MaxRadius=$maxRadius")
    
    // 0. Handle AM/PM
    if (!is24Hour) {
        val amPmNode = getTree().firstOrNull { it.text?.contains("a.m.", ignoreCase = true) == true || it.text?.contains("p.m.", ignoreCase = true) == true }
        if (amPmNode != null) {
            val isPm = hour >= 12
            val targetX = if (isPm) amPmNode.bounds.right - 20.0 else amPmNode.bounds.left + 20.0
            clickAtFast(targetX, amPmNode.bounds.centerY)
            println("  Selected ${if (isPm) "PM" else "AM"} mode")
            delay(1000)
        }
    }

    // 1. PINPOINT HOUR
    val displayHour = if (!is24Hour) {
        if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    } else {
        hour
    }

    val hourAngle = (displayHour - 3) * (PI / 6.0)
    // CORRECT RING LOGIC: 1-12 is OUTER (~0.85), 0 and 13-23 is INNER (~0.55)
    val preferredRScale = if (is24Hour && (hour == 0 || hour >= 13)) 0.55 else 0.85
    val rScales = if (is24Hour) listOf(preferredRScale, if (preferredRScale == 0.85) 0.55 else 0.85) else listOf(0.85)
    
    var hourFound = false
    println("Pinpointing Hour $hour (display=$displayHour, rScales=$rScales)...")
    
    // Focus Hour box once
    clickAtFast(hourBtn.bounds.centerX, hourBtn.bounds.centerY)
    var lastHourText = ""

    for (rScale in rScales) {
        for (aNudge in listOf(0.0, -0.05, 0.05, -0.1, 0.1)) {
            val tx = centerX + (maxRadius * rScale) * cos(hourAngle + aNudge)
            val ty = centerY + (maxRadius * rScale) * sin(hourAngle + aNudge)
            clickAtFast(tx, ty)
            
            for (w in 1..4) {
                val currentText = getTree().find { it.text?.contains("Select hour", ignoreCase = true) == true }?.text ?: ""
                val digits = currentText.filter { it.isDigit() }
                if (digits == displayHour.toString() || currentText.contains("$displayHour hours") || currentText.contains("$displayHour o'clock")) {
                    println("Successfully locked Hour $hour (Text: $currentText)")
                    hourFound = true; break
                }
                delay(500)
            }
            if (hourFound) break
        }
        if (hourFound) break
    }

    if (!hourFound) throw AssertionError("Failed to select hour $hour")

    // 2. SWITCH TO MINUTE MODE
    println("Switching to Minute mode...")
    val safeMinBtnX = hourBtn.bounds.right + 60.0
    val safeMinBtnY = centerY
    clickAtFast(safeMinBtnX, safeMinBtnY)
    delay(1500)

    // 3. SELECT MINUTE (Direct Math - No Feedback due to Wasm focus bug)
    val minuteAngle = (minute - 15) * (PI / 30.0)
    println("Pinpointing Minute $minute at angle=$minuteAngle...")
    
    // Target minute with high precision (still tap)
    val tx = centerX + (maxRadius * 0.85) * cos(minuteAngle)
    val ty = centerY + (maxRadius * 0.85) * sin(minuteAngle)
    clickAtStill(tx, ty)
    println("Successfully clicked Minute $minute at X=$tx, Y=$ty")
    
    click(Selector.Tag("time_picker_ok_button").atIndex(0))
}
