package sample.app.setup

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.atIndex
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.abs

/**
 * Checks if the current E2E test is running against the Wasm target.
 */
private fun isWasmTarget(): Boolean {
    val target = System.getProperty("parikshan.target") ?: ""
    return target.equals("wasm", ignoreCase = true)
}

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
    if (isWasmTarget()) {
        selectDateViaInputWasm(dateText)
    } else {
        selectDateViaInputNative(dateText)
    }
}

private suspend fun E2ETestScope.selectDateViaInputNative(dateText: String) {
    // Standard M3 DatePicker Native logic using substrings for resilience
    click(Selector.Auto("text input"))
    input(Selector.Auto("Date"), dateText)
    click(Selector.Auto("OK"))
}

private suspend fun E2ETestScope.selectDateViaInputWasm(dateText: String) {
    try {
        click(Selector.Auto("Switch to text input mode"))
    } catch (e: Throwable) {
        val tree = getTree()
        val toggleBtn = tree.firstOrNull { it.text?.contains("text input mode", ignoreCase = true) == true }
        if (toggleBtn != null) {
            clickAtFast(toggleBtn.bounds.centerX, toggleBtn.bounds.centerY)
        }
    }
    delay(2000)

    val currentTree = getTree()
    val inputNode = currentTree.firstOrNull { it.text?.contains("YYYY", ignoreCase = true) == true || it.text?.contains("Date", ignoreCase = true) == true }
    if (inputNode != null) {
        clickAtFast(inputNode.bounds.centerX, inputNode.bounds.centerY)
        input(Selector.Text(inputNode.text!!).atIndex(0), dateText)
    } else {
        input(Selector.Tag("date_picker_input_field"), dateText)
    }
    delay(1000)

    try {
        click(Selector.Auto("OK"))
    } catch (e: Throwable) {
        clickAtFast(840.0, 500.0)
    }
}

/**
 * Interacts with a Material 3 TimePicker via dial selection.
 */
suspend fun E2ETestScope.selectTimeFromDial(hour: String, minute: String, is24Hour: Boolean = true) {
    if (isWasmTarget()) {
        selectTimeFromDialGeometrically(hour.toInt(), minute.toInt(), is24Hour)
    } else {
        selectTimeFromDialNative(hour, minute, is24Hour)
    }
}

private suspend fun E2ETestScope.selectTimeFromDialNative(hourText: String, minuteText: String, is24Hour: Boolean = true) {
    waitFor("time_picker_dialog")

    if (!is24Hour) {
        val hInt = hourText.toInt()
        val isPm = hInt >= 12
        val tree = getTree()
        
        // Find the node that contains period info (M3 toggle uses localized strings)
        val periodNode = tree.firstOrNull { 
            val t = it.text?.lowercase() ?: ""
            if (isPm) t == "p.m." || t == "pm" else t == "a.m." || t == "am"
        }
        
        if (periodNode != null) {
            click(Selector.Text(periodNode.text!!).atIndex(0))
        } else {
            click(Selector.Auto(if (isPm) "PM" else "AM"))
        }
        delay(300)
    }

    // Ensure Hour mode is active
    click(Selector.Auto("Select hour"))
    delay(300)

    val hInt = hourText.toInt()
    val displayHour = if (!is24Hour) {
        if (hInt == 0) 12 else if (hInt > 12) hInt - 12 else hInt
    } else {
        hInt
    }

    // Dial numbers in M3 are unique enough for Auto (substring) search. 
    // We click atIndex(0) to avoid any duplicated semantics nodes.
    click(Selector.Auto(displayHour.toString()).atIndex(0))
    delay(500)

    // Switch to minutes
    click(Selector.Auto("Select minutes"))
    delay(300)
    
    click(Selector.Auto(minuteText).atIndex(0))
    delay(500)

    click(Selector.Auto("OK"))
}

/**
 * Interacts with a Material 3 DatePickerDialog via text input mode.
 */
suspend fun E2ETestScope.selectTimeViaInput(hour: String, minute: String) {
    click(Selector.Tag("toggle_time_picker_mode_button"))
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
    val targetX = bounds.left + (width * percent)
    drag(fromX = startX, fromY = startY, toX = targetX, toY = startY, durationMs = 500L)
}

suspend fun E2ETestScope.dragSliderPhysically(tag: String, percent: Float) {
    dragSliderPhysically(Selector.Auto(tag), percent)
}

suspend fun E2ETestScope.clickAtFast(x: Double, y: Double) {
    drag(fromX = x, fromY = y, toX = x + 15.0, toY = y, durationMs = 300L)
    delay(1200L) 
}

suspend fun E2ETestScope.clickAtStill(x: Double, y: Double) {
    drag(fromX = x, fromY = y, toX = x + 5.0, toY = y + 15.0, durationMs = 400L)
    delay(1500L)
}

suspend fun E2ETestScope.selectTimeFromDialGeometrically(hour: Int, minute: Int, is24Hour: Boolean = true) {
    println("Calibration: Pure Math + Force-Drags (is24Hour=$is24Hour)...")
    delay(2000)
    
    val dialNode = resolveNode(Selector.Tag("time_picker_dial"))
    val initialTree = getTree()
    val hourBtn = initialTree.firstOrNull { it.text?.contains("Select hour", ignoreCase = true) == true }
        ?: throw AssertionError("Could not find hour button.")
        
    val width = dialNode.bounds.right - dialNode.bounds.left
    val height = dialNode.bounds.bottom - dialNode.bounds.top
    val isLandscape = width > height
    val dialSize = if (isLandscape) height else width
    
    val centerX = if (isLandscape) dialNode.bounds.right - (dialSize / 2.0) - 8.0 else dialNode.bounds.centerX
    val centerY = dialNode.bounds.centerY
    
    val maxRadius = (dialSize / 2.0) - 12.0
    
    println("Math Calibrated: Center=($centerX, $centerY), MaxRadius=$maxRadius")
    
    if (!is24Hour) {
        val amPmNode = getTree().firstOrNull { it.text?.contains("a.m.", ignoreCase = true) == true || it.text?.contains("p.m.", ignoreCase = true) == true }
        if (amPmNode != null) {
            val isPm = hour >= 12
            // AM is left side (~54px from center), PM is right side (~160px from center)
            val targetX = if (isPm) amPmNode.bounds.left + 160.0 else amPmNode.bounds.left + 54.0
            clickAtFast(targetX, amPmNode.bounds.centerY)
            println("  Selected ${if (isPm) "PM" else "AM"} mode via geometry")
            delay(1000)
        }
    }

    val displayHour = if (!is24Hour) {
        if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    } else {
        hour
    }

    val hourAngle = (displayHour - 3) * (PI / 6.0)
    val rScales = if (is24Hour && (hour == 0 || hour >= 13)) listOf(0.55, 0.85) else listOf(0.85, 0.55)
    
    var hourFound = false
    println("Pinpointing Hour $hour (display=$displayHour, rScales=$rScales)...")
    
    clickAtFast(hourBtn.bounds.centerX, hourBtn.bounds.centerY)

    for (rScale in rScales) {
        for (aNudge in listOf(0.0, -0.06, 0.06, -0.12, 0.12)) {
            val tx = centerX + (maxRadius * rScale) * cos(hourAngle + aNudge)
            val ty = centerY + (maxRadius * rScale) * sin(hourAngle + aNudge)
            clickAtStill(tx, ty)
            
            for (w in 1..8) {
                val currentText = getTree().find { it.text?.contains("Select hour", ignoreCase = true) == true }?.text ?: ""
                val digits = currentText.filter { it.isDigit() }
                if (digits == displayHour.toString()) {
                    println("Successfully locked Hour $hour (Text: $currentText)")
                    hourFound = true; break
                }
                delay(500)
            }
            if (hourFound) break
            clickAtFast(hourBtn.bounds.centerX, hourBtn.bounds.centerY)
        }
        if (hourFound) break
    }

    if (!hourFound) throw AssertionError("Failed to select hour $hour")

    println("Switching to Minute mode...")
    val safeMinBtnX = hourBtn.bounds.right + 75.0
    val safeMinBtnY = hourBtn.bounds.centerY
    clickAtFast(safeMinBtnX, safeMinBtnY)
    delay(2000)

    val minuteAngle = (minute - 15) * (PI / 30.0)
    val tx = centerX + (maxRadius * 0.85) * cos(minuteAngle)
    val ty = centerY + (maxRadius * 0.85) * sin(minuteAngle)
    clickAtStill(tx, ty)
    
    try {
        click(Selector.Auto("OK"))
    } catch (e: Throwable) {
        clickAtFast(840.0, 500.0) 
    }
}
