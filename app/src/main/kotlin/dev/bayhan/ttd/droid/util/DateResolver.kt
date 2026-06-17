package dev.bayhan.ttd.droid.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object DateResolver {
    private val placeholderRegex = Regex("\\{\\{today\\}\\}|\\{\\{([+-]\\d+)([dwmy])\\}\\}")

    fun resolve(text: String, today: LocalDate = LocalDate.now()): String {
        return placeholderRegex.replace(text) { match ->
            val whole = match.value
            if (whole == "{{today}}") {
                today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            } else {
                val amount = match.groupValues[1].toInt()
                val unit = match.groupValues[2]
                val chronoUnit = when (unit) {
                    "d" -> ChronoUnit.DAYS
                    "w" -> ChronoUnit.WEEKS
                    "m" -> ChronoUnit.MONTHS
                    "y" -> ChronoUnit.YEARS
                    else -> ChronoUnit.DAYS
                }
                today.plus(amount.toLong(), chronoUnit).format(DateTimeFormatter.ISO_LOCAL_DATE)
            }
        }
    }
}
