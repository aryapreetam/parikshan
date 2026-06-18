package sample.app

/**
 * A very simple UTC date formatter to avoid external dependencies in the sample app.
 * Only intended for basic display in the OverlayPlayground.
 */
fun formatUtcDate(millis: Long): String {
    // Days since Jan 1, 1970
    var days = millis / (1000 * 60 * 60 * 24)
    
    // Very basic year calculation (approximate but enough for 2026)
    var year = 1970
    while (true) {
        val daysInYear = if (isLeapYear(year)) 366 else 365
        if (days < daysInYear) break
        days -= daysInYear
        year++
    }
    
    val isLeap = isLeapYear(year)
    val monthDays = intArrayOf(31, if (isLeap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    
    var month = 0
    while (days >= monthDays[month]) {
        days -= monthDays[month]
        month++
    }
    
    val day = days + 1
    val monthNum = month + 1
    
    return "${day.toString().padStart(2, '0')}/${monthNum.toString().padStart(2, '0')}/$year"
}

fun getMonthName(month: Int): String {
    return when (month) {
        1 -> "January"; 2 -> "February"; 3 -> "March"; 4 -> "April"
        5 -> "May"; 6 -> "June"; 7 -> "July"; 8 -> "August"
        9 -> "September"; 10 -> "October"; 11 -> "November"; 12 -> "December"
        else -> ""
    }
}

/**
 * Basic parsing of "June 2026" or "June 2026" strings.
 * Returns Pair(month, year)
 */
fun parseMonthYear(text: String): Pair<Int, Int>? {
    val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val parts = text.split(" ", ",")
    val month = months.indexOfFirst { m -> parts.any { it.equals(m, ignoreCase = true) } } + 1
    val year = parts.find { it.length == 4 && it.all { c -> c.isDigit() } }?.toIntOrNull()
    
    if (month > 0 && year != null) return month to year
    return null
}

private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
