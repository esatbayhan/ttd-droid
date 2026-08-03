package dev.bayhan.ttd.droid.notify

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderDateTimeTest {
    private val fallback = LocalTime.of(9, 0)

    @Test
    fun `task time overrides configured default`() {
        assertEquals(
            LocalDateTime.of(2026, 7, 20, 14, 30),
            resolveReminderDateTime("2026-07-20T14:30", fallback)
        )
    }

    @Test
    fun `date only uses configured default`() {
        assertEquals(
            LocalDateTime.of(2026, 7, 20, 9, 0),
            resolveReminderDateTime("2026-07-20", fallback)
        )
    }

    @Test
    fun `invalid date time is ignored`() {
        assertNull(resolveReminderDateTime("2026-07-20T14:30Z", fallback))
    }

    @Test
    fun `alarm request code is keyed by filename and date field`() {
        assertEquals("task.txt|due".hashCode(), reminderRequestCode("task.txt", "due"))
        assertEquals("task.txt|scheduled".hashCode(), reminderRequestCode("task.txt", "scheduled"))
    }

    @Test
    fun `persisted entry includes effective time and parses pipe filename`() {
        val entry = reminderEntry(
            "work|today.txt",
            "due",
            "2026-07-20",
            LocalDateTime.of(2026, 7, 20, 9, 0)
        )

        assertEquals("work|today.txt|due|2026-07-20|2026-07-20T09:00", entry)
        assertEquals(
            PersistedReminderEntry(
                "work|today.txt",
                "due",
                "2026-07-20",
                LocalDateTime.of(2026, 7, 20, 9, 0)
            ),
            parseReminderEntry(entry)
        )
    }

    @Test
    fun `legacy entries parse for active migration and deleted identity recovery`() {
        val cases = listOf(
            "task.txt|due|2026-07-20" to
                PersistedReminderEntry("task.txt", "due", "2026-07-20", null),
            "task.txt|due|2026-07-20T14:00" to
                PersistedReminderEntry("task.txt", "due", "2026-07-20T14:00", null),
            "work|today.txt|scheduled|2026-07-20" to
                PersistedReminderEntry("work|today.txt", "scheduled", "2026-07-20", null),
            "work|today.txt|scheduled|2026-07-20T14:00" to
                PersistedReminderEntry("work|today.txt", "scheduled", "2026-07-20T14:00", null)
        )

        for ((raw, expected) in cases) {
            assertEquals(raw, expected, parseReminderEntry(raw))
        }
        assertNull(parseReminderEntry("work|today.txt|unknown|2026-07-20"))
    }

    @Test
    fun `effective time changes date-only identity but task time overrides config`() {
        val morning = resolveReminderDateTime("2026-07-20", LocalTime.of(9, 0))!!
        val afternoon = resolveReminderDateTime("2026-07-20", LocalTime.of(15, 0))!!
        assertNotEquals(
            reminderEntry("task.txt", "due", "2026-07-20", morning),
            reminderEntry("task.txt", "due", "2026-07-20", afternoon)
        )

        val explicitMorning = resolveReminderDateTime("2026-07-20T10:00", LocalTime.of(9, 0))!!
        val explicitAfternoon = resolveReminderDateTime("2026-07-20T10:00", LocalTime.of(15, 0))!!
        assertEquals(
            reminderEntry("task.txt", "due", "2026-07-20T10:00", explicitMorning),
            reminderEntry("task.txt", "due", "2026-07-20T10:00", explicitAfternoon)
        )
    }

    @Test
    fun `periodic reminder decision covers prior effective time transitions`() {
        val today = LocalDate.of(2026, 7, 20)
        val now = LocalDateTime.of(2026, 7, 20, 12, 0)
        val future = LocalDateTime.of(2026, 7, 20, 14, 0)
        val past = LocalDateTime.of(2026, 7, 20, 10, 0)
        val tomorrow = LocalDateTime.of(2026, 7, 21, 10, 0)
        fun recorded(effective: LocalDateTime?) = PersistedReminderEntry(
            "task.txt", "due", "2026-07-20", effective
        )
        data class Case(
            val name: String,
            val target: LocalDateTime?,
            val prior: PersistedReminderEntry? = null,
            val live: Boolean = false,
            val exact: Boolean = true,
            val completed: Boolean = false,
            val expected: ReminderScheduleDecision
        )
        val cases = listOf(
            Case("unchanged live future", future, recorded(future), live = true, expected = ReminderScheduleDecision.KEEP),
            Case("unchanged reboot future", future, recorded(future), expected = ReminderScheduleDecision.SCHEDULE),
            Case("config change after reboot before old trigger", future, recorded(LocalDateTime.of(2026, 7, 20, 13, 0)), expected = ReminderScheduleDecision.SCHEDULE),
            Case("config change after reboot after old trigger", future, recorded(past), expected = ReminderScheduleDecision.SKIP),
            Case("live config change", future, recorded(past), live = true, expected = ReminderScheduleDecision.SCHEDULE),
            Case("legacy future migration", future, recorded(null), expected = ReminderScheduleDecision.SKIP),
            Case("new target past before old trigger", past, recorded(future), expected = ReminderScheduleDecision.NOTIFY),
            Case("new target past after old trigger", past, recorded(past), expected = ReminderScheduleDecision.SKIP),
            Case("legacy target past", past, recorded(null), expected = ReminderScheduleDecision.SKIP),
            Case("new past reminder", past, expected = ReminderScheduleDecision.NOTIFY),
            Case("future day stays deferred", tomorrow, recorded(future), live = true, expected = ReminderScheduleDecision.DEFER),
            Case("new future without exact permission", future, exact = false, expected = ReminderScheduleDecision.NOTIFY),
            Case("current future without exact permission", future, recorded(future), exact = false, expected = ReminderScheduleDecision.SKIP),
            Case("missing value", null, expected = ReminderScheduleDecision.CANCEL),
            Case("completed task", future, recorded(future), live = true, completed = true, expected = ReminderScheduleDecision.CANCEL)
        )

        for (case in cases) {
            assertEquals(
                case.name,
                case.expected,
                reminderScheduleDecision(
                    case.completed, case.target, case.prior, case.live, case.exact, today, now
                )
            )
        }
    }
}
