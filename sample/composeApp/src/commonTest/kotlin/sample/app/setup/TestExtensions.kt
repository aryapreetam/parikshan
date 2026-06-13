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
    // Standard M3 DatePicker Native logic.
    click(Selector.Auto("text input"))
    delay(300)
    input(Selector.Auto("Date"), dateText)
    delay(300)
    click(Selector.Tag("date_picker_ok_button").atIndex(0))
}

private suspend fun E2ETestScope.selectDateViaInputWasm(dateText: String) {
    // Wasm Canvas mode toggle.
    try {
        click(Selector.Auto("Switch to text input mode"))
    } catch (e: Throwable) {
        val tree = getTree()
        val toggleBtn = tree.firstOrNull { it.text?.contains("text input mode", ignoreCase = true) == true }
        if (toggleBtn != null) {
            clickAtFast(toggleBtn.bounds.centerX, toggleBtn.bounds.centerY)
        }
    }
    
    waitFor(Selector.Auto("YYYY"))

    val currentTree = getTree()
    val inputNode = currentTree.firstOrNull { it.text?.contains("YYYY", ignoreCase = true) == true || it.text?.contains("Date", ignoreCase = true) == true }
    if (inputNode != null) {
        clickAtFast(inputNode.bounds.centerX, inputNode.bounds.centerY)
        input(Selector.Text(inputNode.text!!).atIndex(0), dateText)
    } else {
        input(Selector.Tag("date_picker_input_field"), dateText)
    }

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
    // Built-in wait for the modal
    waitFor("time_picker_dialog")
    delay(800) // Stability wait for M3 animation

    if (!is24Hour) {
        val hInt = hourText.toInt()
        val isPm = hInt >= 12
        val amPmSearch = if (isPm) "p.m." else "a.m."
        val tree = getTree()
        val amPmNode = tree.firstOrNull { it.text?.contains(amPmSearch, ignoreCase = true) == true }
            ?: tree.firstOrNull { it.text?.contains(if (isPm) "PM" else "AM", ignoreCase = true) == true }
        
        if (amPmNode != null) {
            click(Selector.Text(amPmNode.text!!).atIndex(0))
        } else {
            click(Selector.Auto(if (isPm) "PM" else "AM"))
        }
        delay(300)
    }

    // Ensure Hour mode
    click(Selector.Auto("Select hour"))
    delay(300)

    val hInt = hourText.toInt()
    val displayHour = if (!is24Hour) {
        if (hInt == 0) 12 else if (hInt > 12) hInt - 12 else hInt
    } else {
        hInt
    }

    // Search for the specific label format on the dial (hours vs o'clock)
    val tree = getTree()
    val hourLabel = when {
        tree.any { it.text == "$displayHour hours" } -> "$displayHour hours"
        tree.any { it.text == "$displayHour o'clock" } -> "$displayHour o'clock"
        else -> displayHour.toString()
    }
    click(Selector.Text(hourLabel).atIndex(0))
    delay(500)

    // Switch to minutes
    click(Selector.Auto("Select minutes"))
    delay(300)
    
    val minTree = getTree()
    val minuteLabel = when {
        minTree.any { it.text == "$minuteText minutes" } -> "$minuteText minutes"
        else -> minuteText
    }
    click(Selector.Text(minuteLabel).atIndex(0))
    delay(500)

    click(Selector.Tag("time_picker_ok_button").atIndex(0))
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
    // 20px move ensures Wasm registers interaction unmistakably
    drag(fromX = x, fromY = y, toX = x + 20.0, toY = y, durationMs = 300L)
    delay(1200L) 
}

suspend fun E2ETestScope.clickAtStill(x: Double, y: Double) {
    // Force-drag to ensure Wasm registers selection on dial
    drag(fromX = x, fromY = y, toX = x + 5.0, toY = y + 15.0, durationMs = 400L)
    delay(1500L)
}

suspend fun E2ETestScope.selectTimeFromDialGeometrically(hour: Int, minute: Int, is24Hour: Boolean = true) {
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
    
    if (!is24Hour) {
        val amPmNode = getTree().firstOrNull { it.text?.contains("a.m.", ignoreCase = true) == true || it.text?.contains("p.m.", ignoreCase = true) == true }
        if (amPmNode != null) {
            val isPm = hour >= 12
            val targetX = if (isPm) amPmNode.bounds.right - 25.0 else amPmNode.bounds.left + 25.0
            clickAtFast(targetX, amPmNode.bounds.centerY)
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

    val safeMinBtnX = hourBtn.bounds.right + 75.0
    val safeMinBtnY = hourBtn.bounds.centerY
    clickAtFast(safeMinBtnX, safeMinBtnY)
    delay(2000)

    val minuteAngle = (minute - 15) * (PI / 30.0)
    val tx = centerX + (maxRadius * 0.85) * cos(minuteAngle)
    val ty = centerY + (maxRadius * 0.85) * sin(minuteAngle)
    clickAtStill(tx, ty)
    
    try {
        click(Selector.Tag("time_picker_ok_button"))
    } catch (e: Throwable) {
        clickAtFast(840.0, 500.0) 
    }
}
