package dev.bayhan.ttd.droid.task

import org.junit.Assert.*
import org.junit.Test

class TaskQueryTest {

    private fun t(
        priority: Char? = null,
        done: Boolean = false,
        creationDate: String? = null,
        desc: String = "a"
    ) = Task(
        done = done,
        completionDate = if (done) "2024-01-01" else null,
        priority = priority,
        creationDate = creationDate,
        description = desc,
        projects = emptyList(),
        contexts = emptyList(),
        tags = emptyMap(),
        raw = ""
    )

    @Test
    fun `done tasks sort to bottom`() {
        val tasks = listOf(
            t(priority = 'A'),
            t(done = true),
            t(priority = 'B'),
            t(done = true)
        )
        val sorted = TaskQuery.defaultSort(tasks)
        assertFalse(sorted[0].done)
        assertFalse(sorted[1].done)
        assertTrue(sorted[2].done)
        assertTrue(sorted[3].done)
    }

    @Test
    fun `priorities sort A before B`() {
        val tasks = listOf(t(priority = 'B'), t(priority = 'A'), t(priority = 'C'))
        val sorted = TaskQuery.defaultSort(tasks)
        assertEquals('A', sorted[0].priority)
        assertEquals('B', sorted[1].priority)
        assertEquals('C', sorted[2].priority)
    }

    @Test
    fun `no priority sorts after priorities`() {
        val tasks = listOf(t(priority = null), t(priority = 'A'), t(priority = null))
        val sorted = TaskQuery.defaultSort(tasks)
        assertEquals('A', sorted[0].priority)
        assertNull(sorted[1].priority)
        assertNull(sorted[2].priority)
    }

    @Test
    fun `empty list returns empty`() {
        assertTrue(TaskQuery.defaultSort(emptyList()).isEmpty())
    }

    @Test
    fun `creation date sorts earlier dates first`() {
        val tasks = listOf(
            t(creationDate = "2024-03-01", desc = "b"),
            t(creationDate = "2024-01-01", desc = "a"),
            t(creationDate = "2024-02-01", desc = "c")
        )
        val sorted = TaskQuery.defaultSort(tasks)
        assertEquals("2024-01-01", sorted[0].creationDate)
        assertEquals("2024-02-01", sorted[1].creationDate)
        assertEquals("2024-03-01", sorted[2].creationDate)
    }

    @Test
    fun `creation date sort places date only before timed values on the same day`() {
        val sorted = TaskQuery.defaultSort(listOf(
            t(creationDate = "2026-07-20T09:00", desc = "late"),
            t(creationDate = "2026-07-20", desc = "start"),
            t(creationDate = "2026-07-20T08:00", desc = "early")
        ))
        assertEquals(
            listOf("2026-07-20", "2026-07-20T08:00", "2026-07-20T09:00"),
            sorted.map { it.creationDate }
        )
    }

    @Test
    fun `description sorts alphabetically as tiebreaker`() {
        val tasks = listOf(t(desc = "c"), t(desc = "a"), t(desc = "b"))
        val sorted = TaskQuery.defaultSort(tasks)
        assertEquals("a", sorted[0].description)
        assertEquals("b", sorted[1].description)
        assertEquals("c", sorted[2].description)
    }

    @Test
    fun `single element list returns same`() {
        val tasks = listOf(t(priority = 'A'))
        val sorted = TaskQuery.defaultSort(tasks)
        assertEquals(1, sorted.size)
        assertEquals('A', sorted[0].priority)
    }

    @Test
    fun `all done list works`() {
        val tasks = listOf(t(done = true, desc = "b"), t(done = true, desc = "a"))
        val sorted = TaskQuery.defaultSort(tasks)
        assertTrue(sorted.all { it.done })
    }
}
