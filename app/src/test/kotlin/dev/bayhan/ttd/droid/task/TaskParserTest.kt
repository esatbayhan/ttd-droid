package dev.bayhan.ttd.droid.task

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TaskParserTest {

    private val examplesDir = File("../spec/examples")

    @Test
    fun `parse task with priority only`() {
        val task = TaskParser.parse("(A) Submit report")
        assertFalse(task.done)
        assertEquals('A', task.priority)
        assertEquals("Submit report", task.description)
        assertNull(task.creationDate)
        assertTrue(task.projects.isEmpty())
        assertTrue(task.contexts.isEmpty())
        assertTrue(task.tags.isEmpty())
    }

    @Test
    fun `parse task with priority and creation date`() {
        val task = TaskParser.parse("(B) 2024-01-15 Submit tax return")
        assertEquals('B', task.priority)
        assertEquals("2024-01-15", task.creationDate)
        assertEquals("Submit tax return", task.description)
    }

    @Test
    fun `parse task with creation date no priority`() {
        val task = TaskParser.parse("2024-01-15 Submit tax return")
        assertNull(task.priority)
        assertEquals("2024-01-15", task.creationDate)
        assertEquals("Submit tax return", task.description)
    }

    @Test
    fun `parse done task`() {
        val task = TaskParser.parse("x 2024-03-01 2024-02-15 Review PR +ProjectX @github")
        assertTrue(task.done)
        assertEquals("2024-03-01", task.completionDate)
        assertEquals("2024-02-15", task.creationDate)
        assertEquals("Review PR +ProjectX @github", task.description)
        assertEquals(listOf("ProjectX"), task.projects)
        assertEquals(listOf("github"), task.contexts)
    }

    @Test
    fun `parse task with projects and contexts`() {
        val task = TaskParser.parse("(A) Call mom +Personal @phone @home")
        assertEquals(listOf("Personal"), task.projects)
        assertEquals(listOf("phone", "home"), task.contexts)
    }

    @Test
    fun `parse task with tags`() {
        val task = TaskParser.parse("Submit report due:2024-12-31 scheduled:2024-06-01")
        assertEquals("2024-12-31", task.tags["due"])
        assertEquals("2024-06-01", task.tags["scheduled"])
    }

    @Test
    fun `parse task with multiple tags`() {
        val task = TaskParser.parse("(C) Plan trip due:2024-06-01 scheduled:2024-05-15 +Travel @desk")
        assertEquals("2024-06-01", task.tags["due"])
        assertEquals("2024-05-15", task.tags["scheduled"])
        assertEquals(listOf("Travel"), task.projects)
    }

    @Test
    fun `parse plain task no metadata`() {
        val task = TaskParser.parse("Buy milk")
        assertFalse(task.done)
        assertNull(task.priority)
        assertNull(task.creationDate)
        assertEquals("Buy milk", task.description)
    }

    @Test
    fun `lenient on lowercase priority`() {
        val task = TaskParser.parse("(a) Not a priority")
        assertNull(task.priority)
        assertEquals("(a) Not a priority", task.description)
    }

    @Test
    fun `lenient on uppercase completion marker`() {
        val task = TaskParser.parse("X 2024-03-01 Not done")
        assertFalse(task.done)
        assertEquals("X 2024-03-01 Not done", task.description)
    }

    @Test
    fun `lenient on email containing at-sign`() {
        val task = TaskParser.parse("Email user@example.com about project")
        assertTrue(task.contexts.isEmpty())
        assertEquals("Email user@example.com about project", task.description)
    }

    @Test
    fun `lenient on plus sign in math`() {
        val task = TaskParser.parse("Calculate 2+2 result")
        assertTrue(task.projects.isEmpty())
    }

    @Test
    fun `parse priority Z`() {
        val task = TaskParser.parse("(Z) Lowest priority item")
        assertEquals('Z', task.priority)
    }

    @Test
    fun `time with two colons not parsed as tag`() {
        val task = TaskParser.parse("Meeting time:12:30 today")
         assertTrue("time:12:30 contains colon in value, should not be a tag", task.tags.isEmpty())
    }

    @Test
    fun `duplicate tag key - first wins`() {
        val task = TaskParser.parse("Test due:2024-12-31 due:2025-01-01")
        assertEquals("2024-12-31", task.tags["due"])
    }

    @Test
    fun `raw field preserves original line`() {
        val task = TaskParser.parse("(A) 2024-01-01 Original text")
        assertEquals("(A) 2024-01-01 Original text", task.raw)
    }

    @Test
    fun `parser never throws`() {
        val task = TaskParser.parse("")
        assertFalse(task.done)
        assertEquals("", task.description)
    }

    @Test
    fun `all spec valid fixtures parse per expected fields`() {
        val validDir = File(examplesDir, "valid")
        val fixtures = mapOf(
            "priority-only.txt" to { t: Task -> assertEquals('A', t.priority); assertEquals("Call Mom", t.description) },
            "creation-date-no-priority.txt" to { t: Task -> assertNull(t.priority); assertEquals("2024-01-15", t.creationDate) },
            "priority-and-creation-date.txt" to { t: Task -> assertEquals('A', t.priority); assertEquals("2024-01-15", t.creationDate) },
            "single-project.txt" to { t: Task -> assertEquals(listOf("GarageSale"), t.projects) },
            "single-context.txt" to { t: Task -> assertEquals(listOf("phone"), t.contexts) },
            "tag-due.txt" to { t: Task -> assertEquals("2024-04-15", t.tags["due"]) },
            "tag-scheduled.txt" to { t: Task -> assertEquals("2024-03-20", t.tags["scheduled"]) },
        )
        fixtures.forEach { (name, check) ->
            val file = File(validDir, name)
            if (file.exists()) {
                val task = TaskParser.parse(file.readText().trim())
                check(task)
            }
        }
    }

    @Test
    fun `invalid fixtures trigger lenient parsing`() {
        val invalidDir = File(examplesDir, "invalid")
        invalidDir.listFiles()?.filter { it.extension == "txt" }?.forEach { file ->
            val line = file.readText().trim()
            val task = TaskParser.parse(line)
            assertNotNull("Failed to parse invalid fixture: ${file.name}", task)
            assertFalse("Invalid fixture should not be done: ${file.name}", task.done)
        }

        val dueInvalid = TaskParser.parse(File(invalidDir, "due-key-non-date-value.txt").readText().trim())
        assertTrue("Non-date due value should not be parsed as a tag",
            dueInvalid.tags.isEmpty() || dueInvalid.tags["due"] == null)

        val completionMissing = TaskParser.parse(File(invalidDir, "completion-date-missing.txt").readText().trim())
        assertFalse("x without valid date should not mark as done", completionMissing.done)
    }

    @Test
    fun `all spec edge-case fixtures parse without exception`() {
        val edgeDir = File(examplesDir, "edge-cases")
        edgeDir.listFiles()?.filter { it.extension == "txt" }?.forEach { file ->
            val line = file.readText().trim()
            val task = TaskParser.parse(line)
            assertNotNull("Failed to parse edge-case fixture: ${file.name}", task)
        }

        val timeColonTask = TaskParser.parse(
            File(examplesDir, "edge-cases/time-with-two-colons-not-a-tag.txt").readText().trim()
        )
        assertTrue("time:12:30 should not create tags", timeColonTask.tags.isEmpty())
    }

    @Test
    fun `format task to string roundtrip`() {
        val original = "(A) 2024-01-15 Submit tax return due:2024-04-15 +Finance @desk"
        val task = TaskParser.parse(original)
        val formatted = TaskParser.format(task)
        assertEquals(original, formatted)
    }

    @Test
    fun `format done task roundtrip`() {
        val original = "x 2024-03-01 2024-02-15 Review pull request +ProjectX @github"
        val task = TaskParser.parse(original)
        val formatted = TaskParser.format(task)
        assertEquals(original, formatted)
    }
}
