package dev.bayhan.ttd.droid.task

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

data class ParsedTaskDateTime(
    val date: LocalDate,
    val time: LocalTime?
)

object TaskDateTime {
    private val pattern = Regex("^(\\d{4}-\\d{2}-\\d{2})(?:T(\\d{2}:\\d{2}))?$")

    fun parse(raw: String): ParsedTaskDateTime? {
        val match = pattern.matchEntire(raw) ?: return null
        return try {
            ParsedTaskDateTime(
                date = LocalDate.parse(match.groupValues[1]),
                time = match.groupValues[2].takeIf { it.isNotEmpty() }?.let(LocalTime::parse)
            )
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun formatForDisplay(raw: String): String {
        val parsed = parse(raw) ?: return raw
        return if (parsed.time == null) raw else "${parsed.date} ${parsed.time}"
    }

    fun datePart(raw: String): String? = parse(raw)?.date?.toString()
}
