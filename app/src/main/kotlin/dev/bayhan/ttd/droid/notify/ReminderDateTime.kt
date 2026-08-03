package dev.bayhan.ttd.droid.notify

import dev.bayhan.ttd.droid.task.TaskDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

data class PersistedReminderEntry(
    val filename: String,
    val key: String,
    val rawValue: String,
    val effectiveDateTime: LocalDateTime?
)

enum class ReminderScheduleDecision { KEEP, SCHEDULE, NOTIFY, SKIP, DEFER, CANCEL }

private val reminderKeys = setOf("due", "scheduled", "starting")

fun reminderRequestCode(filename: String, key: String): Int = "$filename|$key".hashCode()

fun reminderEntry(
    filename: String,
    key: String,
    rawValue: String,
    effectiveDateTime: LocalDateTime
): String = "$filename|$key|$rawValue|$effectiveDateTime"

fun parseReminderEntry(raw: String): PersistedReminderEntry? {
    fun splitLast(value: String): Pair<String, String>? {
        val index = value.lastIndexOf('|')
        return if (index <= 0 || index == value.lastIndex) null
        else value.substring(0, index) to value.substring(index + 1)
    }

    val (beforeLast, last) = splitLast(raw) ?: return null
    val effectiveDateTime = try {
        LocalDateTime.parse(last)
    } catch (_: DateTimeParseException) {
        null
    }
    if (effectiveDateTime != null) {
        val valueParts = splitLast(beforeLast)
        val identityParts = valueParts?.let { splitLast(it.first) }
        if (valueParts != null && identityParts != null) {
            val (beforeValue, rawValue) = valueParts
            val (filename, key) = identityParts
            if (key in reminderKeys && TaskDateTime.parse(rawValue) != null) {
                return PersistedReminderEntry(filename, key, rawValue, effectiveDateTime)
            }
        }
    }

    val (filename, key) = splitLast(beforeLast) ?: return null
    return if (key in reminderKeys && TaskDateTime.parse(last) != null) {
        PersistedReminderEntry(filename, key, last, null)
    } else {
        null
    }
}

fun reminderScheduleDecision(
    completed: Boolean,
    dateTime: LocalDateTime?,
    priorEntry: PersistedReminderEntry?,
    hasLiveAlarm: Boolean,
    canScheduleExact: Boolean,
    today: LocalDate,
    now: LocalDateTime
): ReminderScheduleDecision {
    if (completed || dateTime == null) return ReminderScheduleDecision.CANCEL
    if (dateTime.toLocalDate() > today) return ReminderScheduleDecision.DEFER
    val isCurrentEntry = priorEntry?.effectiveDateTime == dateTime
    val wasDelivered = priorEntry != null && (
        priorEntry.effectiveDateTime == null || priorEntry.effectiveDateTime <= now
    )
    if (dateTime.toLocalDate() == today && dateTime > now) {
        return when {
            isCurrentEntry && hasLiveAlarm -> ReminderScheduleDecision.KEEP
            isCurrentEntry && canScheduleExact -> ReminderScheduleDecision.SCHEDULE
            isCurrentEntry -> ReminderScheduleDecision.SKIP
            hasLiveAlarm && canScheduleExact -> ReminderScheduleDecision.SCHEDULE
            wasDelivered -> ReminderScheduleDecision.SKIP
            canScheduleExact -> ReminderScheduleDecision.SCHEDULE
            else -> ReminderScheduleDecision.NOTIFY
        }
    }
    return if (wasDelivered) ReminderScheduleDecision.SKIP else ReminderScheduleDecision.NOTIFY
}

fun resolveReminderDateTime(rawValue: String, defaultTime: LocalTime): LocalDateTime? {
    val parsed = TaskDateTime.parse(rawValue) ?: return null
    return parsed.date.atTime(parsed.time ?: defaultTime)
}
