package dev.bayhan.ttd.droid.smartlist

import org.junit.Assert.*
import org.junit.Test

class SmartListParserTest {

    @Test
    fun `parse minimal smart list`() {
        val input = """
            ---
            name: Today
            ---
            due <= today
        """.trimIndent()
        val list = SmartListParser.parse(input)
        assertEquals("Today", list.name)
        assertEquals(1, list.conditions.size)
    }

    @Test
    fun `parse smart list with OR blocks`() {
        val input = """
            ---
            name: Urgent
            ---
            priority above C
            OR
            due <= today + 3
        """.trimIndent()
        val list = SmartListParser.parse(input)
        assertEquals("Urgent", list.name)
        assertEquals(2, list.conditions.size)
    }

    @Test
    fun `parse smart list with sort directive`() {
        val input = """
            ---
            name: Sorted
            ---
            sort by priority asc
        """.trimIndent()
        val list = SmartListParser.parse(input)
        assertEquals(1, list.sorts.size)
        assertEquals("priority", list.sorts[0].field)
        assertTrue(list.sorts[0].ascending)
    }

    @Test
    fun `parse smart list with group directive`() {
        val input = """
            ---
            name: Grouped
            ---
            group by project asc
        """.trimIndent()
        val list = SmartListParser.parse(input)
        assertEquals(1, list.groups.size)
        assertEquals("project", list.groups[0].field)
    }

    @Test
    fun `parse smart list with prefill`() {
        val input = """
            ---
            name: Work
            ---
            prefill project Work
            prefill context desk
            project includes Work
        """.trimIndent()
        val list = SmartListParser.parse(input)
        assertEquals(2, list.prefills.size)
        assertEquals("project", list.prefills[0].field)
        assertEquals("Work", list.prefills[0].value)
    }

    @Test
    fun `all spec list fixtures parse correctly`() {
        val listsDir = java.io.File("../spec/examples/lists.d")
        listsDir.walk()
            .filter { it.extension == "list" && !it.path.contains("invalid") }
            .forEach { file ->
                val list = SmartListParser.parse(file.readText())
                assertNotNull("Failed to parse: ${file.name}", list.name)
                assertTrue("Name should not be empty for ${file.name}", list.name.isNotEmpty())
                when (file.name) {
                    "1 Today.list" -> {
                        assertTrue("Today should have conditions", list.conditions.isNotEmpty())
                    }
                    "2 Inbox.list" -> {
                        assertEquals("Inbox should have 1 block", 1, list.conditions.size)
                    }
                    "7 Work Inbox.list" -> {
                        assertEquals("Work Inbox should have 1 block", 1, list.conditions.size)
                        assertEquals("Work Inbox should have 2 prefills", 2, list.prefills.size)
                    }
                    "8 This Week.list" -> {
                        assertEquals("This Week should have 1 block", 1, list.conditions.size)
                        assertEquals("This Week should have 3 prefills", 3, list.prefills.size)
                    }
                    "9 Year End.list" -> {
                        assertEquals("Year End should have 1 block", 1, list.conditions.size)
                        assertEquals("Year End should have 1 prefill", 1, list.prefills.size)
                    }
                }
            }
    }

    @Test
    fun `parse done filter`() {
        val input = """
            ---
            name: Done Items
            ---
            done
        """.trimIndent()
        val list = SmartListParser.parse(input)
        assertTrue(list.conditions[0].conditions[0] is DoneCondition)
    }

    @Test
    fun `parse not done filter`() {
        val input = """
            ---
            name: Open Items
            ---
            not done
        """.trimIndent()
        val list = SmartListParser.parse(input)
        val cond = list.conditions[0].conditions[0] as DoneCondition
        assertFalse(cond.done)
    }

    @Test
    fun `parse has filter`() {
        val input = """
            ---
            name: Has Due
            ---
            has due
        """.trimIndent()
        val list = SmartListParser.parse(input)
        assertTrue(list.conditions[0].conditions[0] is ExistsCondition)
    }

    @Test
    fun `parse no filter`() {
        val input = """
            ---
            name: No Priority
            ---
            no priority
        """.trimIndent()
        val list = SmartListParser.parse(input)
        val cond = list.conditions[0].conditions[0] as ExistsCondition
        assertFalse(cond.has)
    }
}
