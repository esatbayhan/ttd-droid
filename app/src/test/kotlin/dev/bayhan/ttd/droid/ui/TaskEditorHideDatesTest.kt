package dev.bayhan.ttd.droid.ui

import dev.bayhan.ttd.droid.task.TaskParser
import org.junit.Assert.*
import org.junit.Test

class TaskEditorHideDatesTest {

    @Test
    fun `stripDateTokens removes due scheduled and starting`() {
        val input = "Buy milk due:2025-06-15 scheduled:2025-06-20 starting:2025-06-10"
        val expected = "Buy milk"
        assertEquals(expected, stripDateTokens(input))
    }

    @Test
    fun `stripDateTokens handles trailing whitespace`() {
        val input = "Buy milk   due:2025-06-15   "
        assertEquals("Buy milk", stripDateTokens(input))
    }

    @Test
    fun `stripDateTokens removes only first instance of same key`() {
        val input = "due:2025-01-01 Test due:2025-12-31"
        val stripped = stripDateTokens(input)
        assertEquals("Test due:2025-12-31", stripped)
        assertEquals(stripped, TaskParser.parse(stripped).raw)
    }

    @Test
    fun `stripDateTokens preserves projects and contexts`() {
        val input = "Task +project @context due:2025-06-15"
        assertEquals("Task +project @context", stripDateTokens(input))
    }

    @Test
    fun `stripDateTokens no dates returns original`() {
        assertEquals("Simple task +proj @ctx", stripDateTokens("Simple task +proj @ctx"))
    }

    @Test
    fun `stripDateTokens empty string returns empty`() {
        assertEquals("", stripDateTokens(""))
    }

    @Test
    fun `reconstructTaskText appends date tokens`() {
        val result = reconstructTaskText("Buy milk", mapOf("due" to "2025-06-15", "scheduled" to "2025-06-20"))
        assertEquals("Buy milk due:2025-06-15 scheduled:2025-06-20", result)
    }

    @Test
    fun `reconstructTaskText handles single date`() {
        val result = reconstructTaskText("Task", mapOf("due" to "2025-12-31"))
        assertEquals("Task due:2025-12-31", result)
    }

    @Test
    fun `reconstructTaskText skips null values`() {
        val result = reconstructTaskText("Task", mapOf("due" to null, "scheduled" to "2025-06-20"))
        assertEquals("Task scheduled:2025-06-20", result)
    }

    @Test
    fun `reconstructTaskText empty description with dates`() {
        val result = reconstructTaskText("", mapOf("due" to "2025-06-15"))
        assertEquals("due:2025-06-15", result)
    }

    @Test
    fun `reconstructTaskText empty description with no dates`() {
        val result = reconstructTaskText("   ", mapOf("due" to null, "scheduled" to null))
        assertEquals("", result)
    }

    @Test
    fun `strip then reconstruct returns original`() {
        val original = "Buy milk +project @store due:2025-06-15 scheduled:2025-06-20"
        val stripped = stripDateTokens(original)
        val dates = mapOf("due" to "2025-06-15", "scheduled" to "2025-06-20")
        assertEquals(original, reconstructTaskText(stripped, dates))
    }

    @Test
    fun `strip and reconstruct preserve optional times`() {
        val original = "Meet due:2026-07-20T09:30 scheduled:2026-07-20"
        assertEquals("Meet", stripDateTokens(original))
        assertEquals(
            original,
            reconstructTaskText(
                "Meet",
                mapOf(
                    "due" to "2026-07-20T09:30",
                    "scheduled" to "2026-07-20"
                )
            )
        )
    }

    @Test
    fun `time can be set and cleared without changing date`() {
        assertEquals("2026-07-20T09:30", setTimeOnDate("2026-07-20", "09:30"))
        assertEquals("2026-07-20T10:45", setTimeOnDate("2026-07-20T09:30", "10:45"))
        assertEquals("2026-07-20", clearTimeFromDate("2026-07-20T09:30"))
    }

    @Test
    fun `display formatting replaces T only in valid date tokens`() {
        assertEquals(
            "Meet due:2026-07-20 09:30 time:12:30",
            formatDateTokensForDisplay("Meet due:2026-07-20T09:30 time:12:30")
        )
    }

    @Test
    fun `strip updated token accepts optional time`() {
        assertEquals("Task", stripUpdatedToken("Task updated:2026-07-20T09:30"))
    }

    @Test
    fun `strip date tokens preserves malformed time suffixes`() {
        val seconds = "Task due:2026-07-20T09:30:45"
        val timezone = "Task due:2026-07-20T09:30Z"
        assertEquals(seconds, stripDateTokens(seconds))
        assertEquals(timezone, stripDateTokens(timezone))
    }

    @Test
    fun `display formatting preserves malformed time suffixes`() {
        val seconds = "Task due:2026-07-20T09:30:45"
        val timezone = "Task due:2026-07-20T09:30Z"
        assertEquals(seconds, formatDateTokensForDisplay(seconds))
        assertEquals(timezone, formatDateTokensForDisplay(timezone))
    }

    @Test
    fun `strip updated token preserves malformed time suffixes`() {
        val seconds = "Task updated:2026-07-20T09:30:45"
        val timezone = "Task updated:2026-07-20T09:30Z"
        assertEquals(seconds, stripUpdatedToken(seconds))
        assertEquals(timezone, stripUpdatedToken(timezone))
    }

    @Test
    fun `date token operations preserve impossible dates and invalid times`() {
        val impossible = "Task due:2026-02-30"
        val invalidTime = "Task scheduled:2026-07-20T24:00"

        assertNull(getDateValue("due", impossible))
        assertEquals(impossible, stripDateTokens(impossible))
        assertEquals(invalidTime, stripDateTokens(invalidTime))
        assertEquals(impossible, clearDateValue("due", impossible))
        assertEquals(invalidTime, formatDateTokensForDisplay(invalidTime))
    }

    @Test
    fun `malformed first date keeps later valid token unmanaged`() {
        val raw = "Task due:2026-02-30 due:2026-08-01"

        assertNull(getDateValue("due", raw))
        assertEquals(raw, stripDateTokens(raw))
        assertEquals(raw, clearDateValue("due", raw))
        assertEquals(raw, formatDateTokensForDisplay(raw))
        assertNull(TaskParser.parse(formatDateTokensForDisplay(raw)).tags["due"])
    }

    @Test
    fun `stripping preserves malformed raw whitespace byte for byte`() {
        val dateRaw = "  Task due:2026-02-30  "
        val updatedRaw = "  Task updated:2026-07-20T24:00  "

        assertEquals(dateRaw, stripDateTokens(dateRaw))
        assertEquals(updatedRaw, stripUpdatedToken(updatedRaw))
    }

    @Test
    fun `valid first date is the only duplicate occurrence managed`() {
        val raw = "Task due:2026-07-20T09:30 due:2026-08-01T10:00"

        val set = setDateValue("due", "2026-07-21", raw)
        assertEquals("Task due:2026-07-21 due:2026-08-01T10:00", set)
        assertEquals("2026-07-21", TaskParser.parse(set).tags["due"])

        val cleared = clearDateValue("due", raw)
        assertEquals("Task due:2026-08-01T10:00", cleared)
        assertEquals("2026-08-01T10:00", TaskParser.parse(cleared).tags["due"])

        val displayed = formatDateTokensForDisplay(raw)
        assertEquals("Task due:2026-07-20 09:30 due:2026-08-01T10:00", displayed)
        assertEquals(displayed, TaskParser.parse(displayed).raw)
    }

    @Test
    fun `setting a date inserts it before a malformed first occurrence`() {
        val result = setDateValue("due", "2026-08-01", "Task due:2026-02-30 due:2026-09-01")

        assertEquals("Task due:2026-08-01 due:2026-02-30 due:2026-09-01", result)
        assertEquals("2026-08-01", TaskParser.parse(result).tags["due"])
    }

    @Test
    fun `hidden date reconstruction restores managed value before duplicate`() {
        val raw = "Task due:2026-07-20 due:2026-08-01"
        val managed = getDateValue("due", raw)
        val stripped = stripDateTokens(raw)
        val result = reconstructTaskText(stripped, mapOf("due" to managed))

        assertEquals(raw, result)
        assertEquals("2026-07-20", TaskParser.parse(result).tags["due"])
    }

    @Test
    fun `updated replacement follows first occurrence semantics`() {
        assertEquals(
            "Task updated:2026-08-01 updated:2026-02-30 updated:2026-09-01",
            replaceUpdatedDate("Task updated:2026-02-30 updated:2026-09-01", "2026-08-01")
        )
        val replaced = replaceUpdatedDate(
            "Task updated:2026-07-20T09:30 updated:2026-09-01",
            "2026-08-01"
        )
        assertEquals("Task updated:2026-08-01 updated:2026-09-01", replaced)
        assertEquals("2026-08-01", TaskParser.parse(replaced).tags["updated"])
    }
}
