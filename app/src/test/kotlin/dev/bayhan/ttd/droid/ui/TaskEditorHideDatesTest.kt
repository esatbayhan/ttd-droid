package dev.bayhan.ttd.droid.ui

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
    fun `stripDateTokens removes multiple instances of same key`() {
        val input = "due:2025-01-01 Test due:2025-12-31"
        assertEquals("Test", stripDateTokens(input))
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
}
