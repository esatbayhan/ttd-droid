package dev.bayhan.ttd.droid.smartlist

import dev.bayhan.ttd.droid.task.Task
import org.junit.Assert.*
import org.junit.Test

class SmartListEvalTest {

    private fun task(
        done: Boolean = false, priority: Char? = null,
        projects: List<String> = emptyList(), contexts: List<String> = emptyList(),
        tags: Map<String, String> = emptyMap(), desc: String = "", creationDate: String? = null
    ) = Task(
        done = done, completionDate = null, priority = priority,
        creationDate = creationDate, description = desc,
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
    fun `date only anchor ignores task time`() {
        val list = SmartList("Day", conditions = listOf(FilterBlock(listOf(
            DateCondition(DateField.DUE, CompareOp.EQ,
                DateValue(DateAnchor.ABSOLUTE, anchorDate = "2026-07-20"))
        ))))
        assertTrue(SmartListEval.matches(task(tags = mapOf("due" to "2026-07-20T23:59")), list))
    }

    @Test
    fun `time aware anchor compares full local date time`() {
        val list = SmartList("Before", conditions = listOf(FilterBlock(listOf(
            DateCondition(DateField.DUE, CompareOp.LT,
                DateValue(DateAnchor.ABSOLUTE, anchorDate = "2026-07-20", anchorTime = "12:00"))
        ))))
        assertTrue(SmartListEval.matches(task(tags = mapOf("due" to "2026-07-20")), list))
        assertTrue(SmartListEval.matches(task(tags = mapOf("due" to "2026-07-20T11:59")), list))
        assertFalse(SmartListEval.matches(task(tags = mapOf("due" to "2026-07-20T12:00")), list))
    }

    @Test
    fun `time existence requires an existing valid date field`() {
        fun list(has: Boolean) = SmartList("Time", conditions = listOf(FilterBlock(listOf(
            TimeExistsCondition(has, DateField.DUE)
        ))))
        assertTrue(SmartListEval.matches(task(tags = mapOf("due" to "2026-07-20T09:00")), list(true)))
        assertFalse(SmartListEval.matches(task(tags = mapOf("due" to "2026-07-20")), list(true)))
        assertTrue(SmartListEval.matches(task(tags = mapOf("due" to "2026-07-20")), list(false)))
        assertFalse(SmartListEval.matches(task(), list(false)))
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
    fun `date sort treats date only as start of day`() {
        val values = listOf("2026-07-20T09:00", "2026-07-21", "2026-07-20", "2026-07-20T08:00")
        val sorted = SmartListEval.sort(
            values.map { task(tags = mapOf("due" to it), desc = it) },
            listOf(Directive("due", true))
        )
        assertEquals(
            listOf("2026-07-20", "2026-07-20T08:00", "2026-07-20T09:00", "2026-07-21"),
            sorted.map { it.tags.getValue("due") }
        )
    }

    @Test
    fun `date grouping ignores time`() {
        val grouped = SmartListEval.group(
            listOf(
                task(tags = mapOf("due" to "2026-07-20")),
                task(tags = mapOf("due" to "2026-07-20T09:00")),
                task(tags = mapOf("due" to "2026-07-21T09:00"))
            ),
            listOf(Directive("due", true))
        )
        assertEquals(2, grouped.getValue("2026-07-20").size)
        assertEquals(1, grouped.getValue("2026-07-21").size)
    }

    @Test
    fun `date grouping orders date only before times`() {
        val grouped = SmartListEval.group(
            listOf(
                task(tags = mapOf("due" to "2026-07-20T09:00"), desc = "late"),
                task(tags = mapOf("due" to "2026-07-20"), desc = "date-only"),
                task(tags = mapOf("due" to "2026-07-20T08:00"), desc = "early")
            ),
            listOf(Directive("due", true))
        )
        assertEquals(listOf("date-only", "early", "late"), grouped.getValue("2026-07-20").map { it.description })
    }

    @Test
    fun `date grouping orders all date fields in both directions`() {
        val fields = listOf("due", "scheduled", "starting", "updated", "creation_date")

        for (field in fields) {
            fun dated(value: String, description: String) = if (field == "creation_date") {
                task(creationDate = value, desc = description)
            } else {
                task(tags = mapOf(field to value), desc = description)
            }

            val tasks = listOf(
                dated("2026-07-20T08:00", "early"),
                dated("2026-07-20", "date-only"),
                dated("2026-07-20T09:00", "late")
            )
            assertEquals(
                "$field ascending",
                listOf("date-only", "early", "late"),
                SmartListEval.group(tasks, listOf(Directive(field, true)))
                    .getValue("2026-07-20")
                    .map { it.description }
            )
            assertEquals(
                "$field descending",
                listOf("late", "early", "date-only"),
                SmartListEval.group(tasks, listOf(Directive(field, false)))
                    .getValue("2026-07-20")
                    .map { it.description }
            )
        }
    }

    @Test
    fun `all date fields map to sort and group values`() {
        val fields = listOf("due", "scheduled", "starting", "updated", "creation_date")

        for (field in fields) {
            fun dated(value: String, description: String) = if (field == "creation_date") {
                task(creationDate = value, desc = description)
            } else {
                task(tags = mapOf(field to value), desc = description)
            }

            val tasks = listOf(
                dated("2026-07-21", "later"),
                dated("2026-07-20T09:00", "timed"),
                dated("2026-07-20", "date-only")
            )
            assertEquals(
                field,
                listOf("date-only", "timed", "later"),
                SmartListEval.sort(tasks, listOf(Directive(field, true))).map { it.description }
            )
            assertEquals(
                field,
                setOf("2026-07-20", "2026-07-21"),
                SmartListEval.group(tasks, listOf(Directive(field, true))).keys
            )
        }
    }

    @Test
    fun `no valid filters match no tasks`() {
        val list = SmartList(name = "All")
        assertFalse(SmartListEval.matches(task(), list))
    }
}
