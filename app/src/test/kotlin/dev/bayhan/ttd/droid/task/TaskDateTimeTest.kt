package dev.bayhan.ttd.droid.task

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class TaskDateTimeTest {
    @Test fun `parse date only`() {
        assertEquals(
            ParsedTaskDateTime(LocalDate.of(2026, 7, 20), null),
            TaskDateTime.parse("2026-07-20")
        )
    }

    @Test fun `parse date with local time`() {
        assertEquals(
            ParsedTaskDateTime(LocalDate.of(2026, 7, 20), LocalTime.of(9, 5)),
            TaskDateTime.parse("2026-07-20T09:05")
        )
    }

    @Test fun `reject malformed and impossible values`() {
        listOf(
            "2026-02-30", "2026-07-20T9:05", "2026-07-20T09:05:00",
            "2026-07-20T09:05Z", "2026-07-20T24:00"
        ).forEach { assertNull(it, TaskDateTime.parse(it)) }
    }

    @Test fun `format raw value for display`() {
        assertEquals("2026-07-20", TaskDateTime.formatForDisplay("2026-07-20"))
        assertEquals("2026-07-20 09:05", TaskDateTime.formatForDisplay("2026-07-20T09:05"))
        assertEquals("not-a-date", TaskDateTime.formatForDisplay("not-a-date"))
    }

    @Test fun `datePart returns date only for valid values`() {
        assertEquals("2026-07-20", TaskDateTime.datePart("2026-07-20"))
        assertEquals("2026-07-20", TaskDateTime.datePart("2026-07-20T09:05"))
        assertNull(TaskDateTime.datePart("2026-02-30"))
        assertNull(TaskDateTime.datePart("2026-07-20T24:00"))
    }
}
