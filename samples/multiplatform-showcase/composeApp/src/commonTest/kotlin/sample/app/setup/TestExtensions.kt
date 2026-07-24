package sample.app.setup

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.atIndex
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import sample.app.scrollUntilVisible
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.abs

import io.github.aryapreetam.parikshan.isWasm


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
 * Helper to click the dropdown chevron area on Wasm generically.
 */
suspend fun E2ETestScope.clickDropdown(selector: Selector) {
    if (isWasm()) {
        val node = resolveNode(selector)
        val chevronX = node.bounds.right - 16.0
        val centerY = node.bounds.centerY
        drag(fromX = chevronX, fromY = centerY, toX = chevronX, toY = centerY, durationMs = 100L)
    } else {
        click(selector)
    }
}

suspend fun E2ETestScope.clickDropdown(tag: String) {
    clickDropdown(Selector.Auto(tag))
}

fun resolveDateTextForLocale(day: Int, month: Int, year: Int, tree: List<io.github.aryapreetam.parikshan.protocol.NodeSnapshot>): String {
    val placeholderNode = tree.find { it.text?.contains("YYYY", ignoreCase = true) == true }
    val placeholder = placeholderNode?.text ?: ""
    val clean = placeholder.uppercase()
    val dayStr = day.toString().padStart(2, '0')
    val monthStr = month.toString().padStart(2, '0')
    val yearStr = year.toString()
    
    return when {
        clean.contains("MM") && clean.contains("DD") -> {
            val separator = if (clean.contains("/")) "/" else if (clean.contains(".")) "." else "-"
            if (clean.indexOf("MM") < clean.indexOf("DD")) {
                "$monthStr$separator$dayStr$separator$yearStr"
            } else {
                "$dayStr$separator$monthStr$separator$yearStr"
            }
        }
        else -> "$dayStr/$monthStr/$yearStr"
    }
}

fun resolveDateTextForLocale(dateText: String, tree: List<io.github.aryapreetam.parikshan.protocol.NodeSnapshot>): String {
    val placeholderNode = tree.find { it.text?.contains("YYYY", ignoreCase = true) == true }
    val placeholder = placeholderNode?.text ?: ""
    val parts = dateText.split('/', '.', '-')
    if (parts.size != 3) return dateText
    
    val p0 = parts[0]
    val p1 = parts[1]
    val p2 = parts[2]
    
    val v0 = p0.toIntOrNull() ?: return dateText
    val v1 = p1.toIntOrNull() ?: return dateText
    
    val (day, month, year) = if (p0.length == 4) {
        Triple(p2, p1, p0)
    } else if (v0 > 12) {
        Triple(p0, p1, p2)
    } else if (v1 > 12) {
        Triple(p1, p0, p2)
    } else {
        Triple(p0, p1, p2)
    }
    
    val clean = placeholder.uppercase()
    return when {
        clean.contains("MM") && clean.contains("DD") -> {
            val separator = if (clean.contains("/")) "/" else if (clean.contains(".")) "." else "-"
            if (clean.indexOf("MM") < clean.indexOf("DD")) {
                "$month$separator$day$separator$year"
            } else {
                "$day$separator$month$separator$year"
            }
        }
        else -> dateText
    }
}

/**
 * Interacts with a Material 3 DatePickerDialog via text input mode.
 */
suspend fun E2ETestScope.selectDateViaInput(day: Int, month: Int, year: Int) {
    if (isWasm()) {
        selectDateViaInputWasm(day, month, year)
    } else {
        selectDateViaInputNative(day, month, year)
    }
}

suspend fun E2ETestScope.selectDateViaInput(dateText: String) {
    val parts = dateText.split('/', '.', '-')
    if (parts.size != 3) {
        if (isWasm()) selectDateViaInputWasm(dateText)
        else selectDateViaInputNative(dateText)
        return
    }
    val p0 = parts[0].toIntOrNull()
    val p1 = parts[1].toIntOrNull()
    val p2 = parts[2].toIntOrNull()
    if (p0 == null || p1 == null || p2 == null) {
        if (isWasm()) selectDateViaInputWasm(dateText)
        else selectDateViaInputNative(dateText)
        return
    }
    
    val (day, month, year) = if (parts[0].length == 4) {
        Triple(p2, p1, p0)
    } else if (p0 > 12) {
        Triple(p0, p1, p2)
    } else if (p1 > 12) {
        Triple(p1, p0, p2)
    } else {
        Triple(p0, p1, p2)
    }
    
    selectDateViaInput(day = day, month = month, year = year)
}

private suspend fun E2ETestScope.selectDateViaInputNative(day: Int, month: Int, year: Int) {
    click(Selector.Auto("Switch to text input mode"))
    delay(500)
    
    val tree = getTree()
    val inputSelector = when {
        tree.any { it.text?.contains("Date", ignoreCase = true) == true } -> Selector.Auto("Date")
        tree.any { it.text?.contains("Enter date", ignoreCase = true) == true } -> Selector.Auto("Enter date")
        else -> Selector.Auto("Date")
    }
    
    val resolvedDate = resolveDateTextForLocale(day, month, year, tree)
    val digitsOnly = resolvedDate.filter { it.isDigit() }
    input(inputSelector, digitsOnly)
    delay(300)
    
    val okSelector = when {
        getTree().any { it.tag == "date_picker_ok_button" } -> Selector.Tag("date_picker_ok_button")
        else -> Selector.Text("OK")
    }
    click(okSelector.atIndex(0))
}

private suspend fun E2ETestScope.selectDateViaInputWasm(day: Int, month: Int, year: Int) {
    click(Selector.Auto("Switch to text input mode"))
    delay(1000)

    val tree = getTree()
    val inputSelector = when {
        tree.any { it.text?.contains("YYYY", ignoreCase = true) == true } -> {
            val text = tree.first { it.text?.contains("YYYY", ignoreCase = true) == true }.text!!
            Selector.Auto(text)
        }
        tree.any { it.text?.contains("Date", ignoreCase = true) == true } -> {
            val text = tree.first { it.text?.contains("Date", ignoreCase = true) == true }.text!!
            Selector.Auto(text)
        }
        else -> Selector.Auto("YYYY")
    }
    
    waitFor(inputSelector)
    click(inputSelector)
    val resolvedDate = resolveDateTextForLocale(day, month, year, tree)
    val digitsOnly = resolvedDate.filter { it.isDigit() }
    input(inputSelector, digitsOnly)
    delay(1000)

    val treeAfter = getTree()
    val okSelector = when {
        treeAfter.any { it.tag == "date_picker_ok_button" } -> Selector.Tag("date_picker_ok_button")
        else -> Selector.Text("OK")
    }
    click(okSelector)
}

private suspend fun E2ETestScope.selectDateViaInputNative(dateText: String) {
    click(Selector.Auto("Switch to text input mode"))
    delay(500)
    
    val tree = getTree()
    val inputSelector = when {
        tree.any { it.text?.contains("Date", ignoreCase = true) == true } -> Selector.Auto("Date")
        tree.any { it.text?.contains("Enter date", ignoreCase = true) == true } -> Selector.Auto("Enter date")
        else -> Selector.Auto("Date")
    }
    
    val resolvedDate = resolveDateTextForLocale(dateText, tree)
    val digitsOnly = resolvedDate.filter { it.isDigit() }
    input(inputSelector, digitsOnly)
    delay(300)
    
    val okSelector = when {
        getTree().any { it.tag == "date_picker_ok_button" } -> Selector.Tag("date_picker_ok_button")
        else -> Selector.Text("OK")
    }
    click(okSelector.atIndex(0))
}

private suspend fun E2ETestScope.selectDateViaInputWasm(dateText: String) {
    click(Selector.Auto("Switch to text input mode"))
    delay(1000)

    val tree = getTree()
    val inputSelector = when {
        tree.any { it.text?.contains("YYYY", ignoreCase = true) == true } -> {
            val text = tree.first { it.text?.contains("YYYY", ignoreCase = true) == true }.text!!
            Selector.Auto(text)
        }
        tree.any { it.text?.contains("Date", ignoreCase = true) == true } -> {
            val text = tree.first { it.text?.contains("Date", ignoreCase = true) == true }.text!!
            Selector.Auto(text)
        }
        else -> Selector.Auto("YYYY")
    }
    
    waitFor(inputSelector)
    click(inputSelector)
    val resolvedDate = resolveDateTextForLocale(dateText, tree)
    val digitsOnly = resolvedDate.filter { it.isDigit() }
    input(inputSelector, digitsOnly)
    delay(1000)

    val treeAfter = getTree()
    val okSelector = when {
        treeAfter.any { it.tag == "date_picker_ok_button" } -> Selector.Tag("date_picker_ok_button")
        else -> Selector.Text("OK")
    }
    click(okSelector)
}

/**
 * Interacts with a Material 3 DatePickerDialog via calendar mode selection.
 */
suspend fun E2ETestScope.selectDateFromCalendar(day: Int, month: Int, year: Int) {
    if (isWasm()) {
        selectDateFromCalendarWasm(day, month, year)
    } else {
        selectDateFromCalendarNative(day, month, year)
    }
}

private suspend fun E2ETestScope.selectDateFromCalendarNative(day: Int, month: Int, year: Int) {
    val initialTree = getTree()
    val dialog = initialTree.find { it.tag == "date_picker_dialog" } ?: throw AssertionError("DatePicker missing")
    
    var curMonth = 6
    var curYear = 2026
    val monthYearNode = initialTree.find { node ->
      val text = node.text ?: ""
      text.contains(Regex("20\\d{2}")) && 
      (1..12).any { m -> text.contains(sample.app.getMonthName(m), ignoreCase = true) }
    }
    if (monthYearNode != null) {
      val text = monthYearNode.text!!
      val yearMatch = Regex("20\\d{2}").find(text)?.value?.toIntOrNull()
      if (yearMatch != null) curYear = yearMatch
      val monthMatch = (1..12).firstOrNull { m -> text.contains(sample.app.getMonthName(m), ignoreCase = true) }
      if (monthMatch != null) curMonth = monthMatch
    }

    if (curYear != year) {
        val selectYearNode = initialTree.find { it.text?.contains("selecting a year", ignoreCase = true) == true }
          ?: throw AssertionError("Could not find year selection toggle button")
        click(Selector.Text(selectYearNode.text!!))
        delay(1500)
        
        val yearTargetText = "Navigate to year $year"
        val target = Selector.Text(yearTargetText)
        
        scrollUntilVisible(Selector.Auto("Navigate to year").atIndex(0), target, if (year > curYear) ScrollDirection.Down else ScrollDirection.Up)
        click(target.atIndex(-1))
        delay(1500)
        
        clickAt(dialog.bounds.left + 20.0, dialog.bounds.top + 20.0)
        delay(500)
    }

    var tries = 0
    while (tries < 24) {
        val currentTree = getTree()
        var cMonth = 6
        var cYear = 2026
        val cMonthYearNode = currentTree.find { node ->
          val text = node.text ?: ""
          text.contains(Regex("20\\d{2}")) && 
          (1..12).any { m -> text.contains(sample.app.getMonthName(m), ignoreCase = true) }
        }
        if (cMonthYearNode != null) {
          val text = cMonthYearNode.text!!
          val yearMatch = Regex("20\\d{2}").find(text)?.value?.toIntOrNull()
          if (yearMatch != null) cYear = yearMatch
          val monthMatch = (1..12).firstOrNull { m -> text.contains(sample.app.getMonthName(m), ignoreCase = true) }
          if (monthMatch != null) cMonth = monthMatch
        }
        
        if (cMonth == month && cYear == year) break
        
        val nextSelector = if ((year * 12 + month) > (cYear * 12 + cMonth)) {
            Selector.Auto("next month")
        } else {
            Selector.Auto("previous month")
        }
        click(nextSelector)
        
        var changed = false
        val prevText = cMonthYearNode?.text ?: ""
        for (i in 0 until 15) {
            delay(400)
            val updatedTree = getTree()
            val updatedNodeText = updatedTree.find { node ->
              val text = node.text ?: ""
              text.contains(Regex("20\\d{2}")) && 
              (1..12).any { m -> text.contains(sample.app.getMonthName(m), ignoreCase = true) }
            }?.text ?: ""
            if (updatedNodeText != prevText && updatedNodeText.isNotEmpty()) {
                changed = true
                break
            }
        }
        if (!changed) break
        tries++
    }

    delay(1000)
    val finalTree = getTree()
    
    val monthName = sample.app.getMonthName(month)
    val dayNode = finalTree.find { 
        val text = it.text ?: ""
        !text.contains("\n") &&
        text.contains(monthName, ignoreCase = true) &&
        text.contains(year.toString()) &&
        text.split(Regex("[\\s,]+")).contains(day.toString())
    }
    
    if (dayNode != null) {
        click(Selector.Text(dayNode.text!!).atIndex(-1))
    } else {
        val monHeader = finalTree.find { it.text == "Monday" } ?: throw AssertionError("Monday header missing for grid calibration")
        val dayList = finalTree.find { it.text?.contains(",") == true && it.bounds.top > monHeader.bounds.top } ?: throw AssertionError("Day grid missing")
        
        val gridTop = monHeader.bounds.bottom
        val startOffset = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            .indexOfFirst { (dayList.text ?: "").split("\n").firstOrNull()?.contains(it, ignoreCase = true) == true }.coerceAtLeast(0)

        val colW = (dayList.bounds.right - dayList.bounds.left) / 7.0
        val absIdx = startOffset + (day - 1)
        
        clickAt(dayList.bounds.left + (absIdx % 7 + 0.5) * colW, gridTop + (absIdx / 7 + 0.5) * 48.0)
    }
    
    delay(500)
    val okButton = finalTree.find { it.tag == "date_picker_ok_button" || it.text == "OK" }
      ?: throw AssertionError("Could not find date picker OK button")
    if (okButton.tag == "date_picker_ok_button") {
        click(Selector.Tag("date_picker_ok_button").atIndex(0))
    } else {
        click(Selector.Text("OK").atIndex(-1))
    }
}

/**
 * Wasm-specific implementation using the Core Multi-Root fix and Tree Dump findings.
 */
private suspend fun E2ETestScope.selectDateFromCalendarWasm(day: Int, month: Int, year: Int) {
    // 1. Initial State Calibration
    val initialTree = getTree()
    val dialog = initialTree.find { it.tag == "date_picker_dialog" } ?: throw AssertionError("DatePicker missing")
    
    // Find Header (e.g., "June 2026, Switch to selecting a year")
    val headerNode = initialTree.find { it.text?.contains("20") == true && it.text?.contains("selecting a year") == true }
    val headerText = headerNode?.text ?: ""
    val (curMonth, curYear) = sample.app.parseMonthYear(headerText.split(",").firstOrNull() ?: "") ?: (6 to 2026)

    // 2. Year Navigation (NATIVE SEMANTIC)
    if (curYear != year) {
        click(Selector.Text(headerText))
        delay(1500)
        
        // Year list is VISIBLE thanks to core fix.
        val yearTargetText = "Navigate to year $year"
        val target = Selector.Text(yearTargetText)
        
        // We use the first node starting with "Navigate to year" as the scroll container anchor if list tag is missing
        scrollUntilVisible(Selector.Auto("Navigate to year").atIndex(0), target, if (year > curYear) ScrollDirection.Down else ScrollDirection.Up)
        click(target.atIndex(-1))
        delay(1500)
        
        // Wake up ping
        clickAt(dialog.bounds.left + 20.0, dialog.bounds.top + 20.0)
        delay(500)
    }

    // 3. Month Navigation Loop (Native Semantic + Patient Observer)
    var tries = 0
    while (tries < 24) {
        val currentTree = getTree()
        val currentHeader = currentTree.find { it.text?.contains("20") == true && it.text?.contains("selecting a year") == true }
        if (currentHeader == null) {
             tries++; continue
        }
        
        val currentHeaderText = currentHeader.text!!
        val (m, y) = sample.app.parseMonthYear(currentHeaderText.split(",").firstOrNull() ?: "") ?: break
        if (m == month && y == year) break
        
        val nextSelector = if ((year * 12 + month) > (y * 12 + m)) Selector.Text("Change to next month") else Selector.Text("Change to previous month")
        click(nextSelector)
        
        // Wait for change
        var changed = false
        for (i in 0 until 15) {
            delay(400)
            val updatedText = getTree().find { it.text?.contains("20") == true && it.text?.contains("selecting a year") == true }?.text ?: ""
            if (updatedText != currentHeaderText && updatedText.isNotEmpty()) {
                changed = true
                break
            }
        }
        if (!changed) break
        tries++
    }

    // 4. Final Day Selection
    delay(1000)
    val finalTree = getTree()
    
    // Attempt semantic match first (e.g. "Monday, 15 June 2026")
    val monthName = sample.app.getMonthName(month)
    val dayTarget = "$day $monthName $year" // Material 3 format in dump
    val dayNode = finalTree.find { 
        val text = it.text ?: ""
        text.contains(dayTarget) && !text.contains("\n")
    }
    
    if (dayNode != null) {
        click(Selector.Text(dayNode.text!!).atIndex(-1))
    } else {
        // Precise geometric fallback using Monday anchor
        val monHeader = finalTree.find { it.text == "Monday" } ?: throw AssertionError("Monday header missing for grid calibration")
        val dayList = finalTree.find { it.text?.contains(",") == true && it.bounds.top > monHeader.bounds.top } ?: throw AssertionError("Day grid missing")
        
        val gridTop = monHeader.bounds.bottom
        val startOffset = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            .indexOfFirst { (dayList.text ?: "").split("\n").firstOrNull()?.contains(it, ignoreCase = true) == true }.coerceAtLeast(0)

        val colW = (dayList.bounds.right - dayList.bounds.left) / 7.0
        val absIdx = startOffset + (day - 1)
        
        clickAt(dayList.bounds.left + (absIdx % 7 + 0.5) * colW, gridTop + (absIdx / 7 + 0.5) * 48.0)
    }
    
    delay(500)
    click(Selector.Tag("date_picker_ok_button").atIndex(0))
}

suspend fun E2ETestScope.dragSlider(selector: Selector, percent: Float) {
    val node = resolveNode(selector)
    val bounds = node.bounds
    drag(fromX = bounds.centerX, fromY = bounds.centerY, toX = bounds.left + ((bounds.right - bounds.left) * percent), toY = bounds.centerY, durationMs = 500L)
}

suspend fun E2ETestScope.dragSlider(tag: String, percent: Float) {
    dragSlider(Selector.Auto(tag), percent)
}

suspend fun E2ETestScope.clickAt(x: Double, y: Double) {
    drag(fromX = x, fromY = y, toX = x + 5.0, toY = y + 5.0, durationMs = 300L)
    delay(1000L)
}

suspend fun E2ETestScope.selectTimeFromDial(hour: String, minute: String, is24Hour: Boolean = true) {
    if (isWasm()) selectTimeFromDialByCoordinates(hour.toInt(), minute.toInt(), is24Hour)
    else selectTimeFromDialNative(hour, minute, is24Hour)
}

private suspend fun E2ETestScope.selectTimeFromDialNative(hourText: String, minuteText: String, is24Hour: Boolean = true) {
    waitFor("time_picker_dialog")
    delay(500)
    if (!is24Hour) {
        val hInt = hourText.toInt()
        click(Selector.Text(if (hInt >= 12) "p.m." else "a.m.").atIndex(-1))
        delay(300)
    }
    click(Selector.Auto("Select hour").atIndex(-1))
    delay(300)
    val hInt = hourText.toInt()
    val displayHour = if (!is24Hour) (if (hInt == 0) 12 else if (hInt > 12) hInt - 12 else hInt) else hInt
    val hourLabel = getTree().find { it.text?.contains("$displayHour", ignoreCase = true) == true }?.text ?: displayHour.toString()
    click(Selector.Text(hourLabel).atIndex(-1))
    delay(500)
    click(Selector.Auto("Select minutes").atIndex(-1))
    delay(300)
    val minuteLabel = getTree().find { it.text?.contains(minuteText, ignoreCase = true) == true }?.text ?: minuteText
    click(Selector.Text(minuteLabel).atIndex(-1))
    delay(500)
    click(Selector.Tag("time_picker_ok_button").atIndex(0))
}

suspend fun E2ETestScope.selectTimeViaInput(hour: String, minute: String) {
    click(Selector.Tag("toggle_time_picker_mode_button"))
    click(Selector.Tag("time_picker_ok_button").atIndex(0))
}

private suspend fun E2ETestScope.selectTimeFromDialByCoordinates(hour: Int, minute: Int, is24Hour: Boolean = true) {
    val dialNode = resolveNode(Selector.Tag("time_picker_dial"))
    val initialTree = getTree()
    val hourBtn = initialTree.firstOrNull { it.text?.contains("Select hour", ignoreCase = true) == true } ?: throw AssertionError("Hour button missing")
    val width = dialNode.bounds.right - dialNode.bounds.left
    val height = dialNode.bounds.bottom - dialNode.bounds.top
    val isLandscape = width > height
    val dialSize = if (isLandscape) height else width
    val centerX = if (isLandscape) dialNode.bounds.right - (dialSize / 2.0) - 8.0 else dialNode.bounds.centerX
    val centerY = dialNode.bounds.centerY
    val maxRadius = (dialSize / 2.0) - 12.0
    if (!is24Hour) {
        val amPmNode = getTree().firstOrNull { it.text?.contains("a.m.", ignoreCase = true) == true || it.text?.contains("p.m.", ignoreCase = true) == true }
        if (amPmNode != null) clickAt(if (hour >= 12) amPmNode.bounds.right - 25.0 else amPmNode.bounds.left + 25.0, amPmNode.bounds.centerY)
        delay(1000)
    }
    val displayHour = if (!is24Hour) (if (hour == 0) 12 else if (hour > 12) hour - 12 else hour) else hour
    val hourAngle = (displayHour - 3) * (PI / 6.0)
    val rScales = if (is24Hour && (hour == 0 || hour >= 13)) listOf(0.55, 0.85) else listOf(0.85, 0.55)
    var hourFound = false
    clickAt(hourBtn.bounds.centerX, hourBtn.bounds.centerY)
    for (rScale in rScales) {
        for (aNudge in listOf(0.0, -0.06, 0.06, -0.12, 0.12)) {
            clickAt(centerX + (maxRadius * rScale) * cos(hourAngle + aNudge), centerY + (maxRadius * rScale) * sin(hourAngle + aNudge))
            repeat(8) { if (getTree().find { it.text?.contains("Select hour", ignoreCase = true) == true }?.text?.filter { it.isDigit() } == displayHour.toString()) { hourFound = true; return@repeat }; delay(500) }
            if (hourFound) break
            clickAt(hourBtn.bounds.centerX, hourBtn.bounds.centerY)
        }
        if (hourFound) break
    }
    if (!hourFound) throw AssertionError("Failed select hour $hour")
    clickAt(hourBtn.bounds.right + 75.0, hourBtn.bounds.centerY)
    delay(1000)
    clickAt(centerX + (maxRadius * 0.85) * cos((minute - 15) * (PI / 30.0)), centerY + (maxRadius * 0.85) * sin((minute - 15) * (PI / 30.0)))
    try { click(Selector.Tag("time_picker_ok_button")) } catch (e: Throwable) { clickAt(840.0, 500.0) }
}
