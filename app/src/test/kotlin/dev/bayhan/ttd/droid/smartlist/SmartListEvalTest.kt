package dev.bayhan.ttd.droid.smartlist

import dev.bayhan.ttd.droid.task.Task
import org.junit.Assert.*
import org.junit.Test

class SmartListEvalTest {

    private fun task(
        done: Boolean = false, priority: Char? = null,
        projects: List<String> = emptyList(), contexts: List<String> = emptyList(),
        tags: Map<String, String> = emptyMap(), desc: String = ""
    ) = Task(
        done = done, completionDate = null, priority = priority,
        creationDate = null, description = desc,
        projects = projects, contexts = contexts, tags = tags, raw = ""
    )

    @Test
    fun `done filter matches done tasks`() {
        val list = SmartList(name = "Done", conditions = listOf(FilterBlock(listOf(DoneCondition(true)))))
        assertTrue(SmartListEval.matches(task(done = true), list))
        assertFalse(SmartListEval.matches(task(done = false), list))
    }

    @Test
    fun `not done filter excludes done tasks`() {
        val list = SmartList(name = "Open", conditions = listOf(FilterBlock(listOf(DoneCondition(false)))))
        assertTrue(SmartListEval.matches(task(done = false), list))
        assertFalse(SmartListEval.matches(task(done = true), list))
    }

    @Test
    fun `priority above condition`() {
        val list = SmartList(name = "High", conditions = listOf(FilterBlock(listOf(
            PriorityCondition(PriorityOp.ABOVE, 'C')
        ))))
        assertTrue(SmartListEval.matches(task(priority = 'A'), list))
        assertTrue(SmartListEval.matches(task(priority = 'B'), list))
        assertFalse(SmartListEval.matches(task(priority = 'C'), list))
        assertFalse(SmartListEval.matches(task(priority = 'D'), list))
    }

    @Test
    fun `priority equals condition`() {
        val list = SmartList(name = "Exact", conditions = listOf(FilterBlock(listOf(
            PriorityCondition(PriorityOp.EQ, 'B')
        ))))
        assertTrue(SmartListEval.matches(task(priority = 'B'), list))
        assertFalse(SmartListEval.matches(task(priority = 'A'), list))
        assertFalse(SmartListEval.matches(task(priority = 'C'), list))
    }

    @Test
    fun `project includes condition`() {
        val list = SmartList(name = "Work", conditions = listOf(FilterBlock(listOf(
            TextCondition(TextField.PROJECT, TextOp.INCLUDES, "Work")
        ))))
        assertTrue(SmartListEval.matches(task(projects = listOf("Work", "Personal")), list))
        assertFalse(SmartListEval.matches(task(projects = listOf("Personal")), list))
    }

    @Test
    fun `has due condition`() {
        val list = SmartList(name = "HasDue", conditions = listOf(FilterBlock(listOf(
            ExistsCondition(true, Field.DUE)
        ))))
        assertTrue(SmartListEval.matches(task(tags = mapOf("due" to "2024-12-31")), list))
        assertFalse(SmartListEval.matches(task(), list))
    }

    @Test
    fun `OR blocks - task matches either block`() {
        val list = SmartList(name = "WorkOrUrgent", conditions = listOf(
            FilterBlock(listOf(TextCondition(TextField.PROJECT, TextOp.INCLUDES, "Work"))),
            FilterBlock(listOf(PriorityCondition(PriorityOp.ABOVE, 'B')))
        ))
        assertTrue(SmartListEval.matches(task(projects = listOf("Work")), list))
        assertTrue(SmartListEval.matches(task(priority = 'A'), list))
        assertFalse(SmartListEval.matches(task(projects = listOf("Home"), priority = 'D'), list))
    }

    @Test
    fun `sort by priority asc`() {
        val tasks = listOf(task(priority = 'C'), task(priority = 'A'), task(priority = 'B'))
        val directives = listOf(Directive("priority", true))
        val sorted = SmartListEval.sort(tasks, directives)
        assertEquals('A', sorted[0].priority)
        assertEquals('B', sorted[1].priority)
        assertEquals('C', sorted[2].priority)
    }

    @Test
    fun `sort by priority desc`() {
        val tasks = listOf(task(priority = 'A'), task(priority = 'C'), task(priority = 'B'))
        val directives = listOf(Directive("priority", false))
        val sorted = SmartListEval.sort(tasks, directives)
        assertEquals('C', sorted[0].priority)
        assertEquals('B', sorted[1].priority)
        assertEquals('A', sorted[2].priority)
    }

    @Test
    fun `no filters means match all`() {
        val list = SmartList(name = "All")
        assertTrue(SmartListEval.matches(task(), list))
    }
}
