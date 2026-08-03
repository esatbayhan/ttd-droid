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
    fun `prefill keeps first valid scalar and ignores malformed declarations`() {
        val list = SmartListParser.parse("""
            ---
            name: Prefill
            ---
            prefill unknown value
            prefill priority a
            prefill priority B
            prefill priority C
            prefill due todayT2:00
            prefill due todayT10:00
            prefill due todayT11:00
        """.trimIndent())

        assertEquals(
            listOf(Prefill("priority", "B"), Prefill("due", "todayT10:00")),
            list.prefills
        )
    }

    @Test
    fun `prefill accepts only supported fields and accumulates valid list values`() {
        val list = SmartListParser.parse("""
            ---
            name: Prefill
            ---
            prefill project work
            prefill project client+invalid
            prefill project client work
            prefill context office
            prefill context home@invalid
            prefill context home desk
            prefill scheduled next-week
            prefill scheduled today + 7 T09:00
            prefill scheduled today + 8
            prefill starting 2026-06-01-3
            prefill starting 2026-07-01
            prefill updated today
            prefill creation_date today
        """.trimIndent())

        assertEquals(
            listOf(
                Prefill("project", "work"),
                Prefill("project", "client work"),
                Prefill("context", "office"),
                Prefill("context", "home desk"),
                Prefill("scheduled", "today + 7 T09:00"),
                Prefill("starting", "2026-06-01-3")
            ),
            list.prefills
        )
    }

    @Test
    fun `project prefill rejects malformed whitespace and keeps a later valid value`() {
        val list = SmartListParser.parse(
            "---\nname: Prefill\n---\n" +
                "prefill project  work\n" +
                "prefill project work\tclient\n" +
                "prefill project \n" +
                "prefill project trailing \n" +
                "prefill project client  work\n"
        )

        assertEquals(listOf(Prefill("project", "client  work")), list.prefills)
    }

    @Test
    fun `context prefill rejects tab and empty values and keeps a later valid value`() {
        val list = SmartListParser.parse(
            "---\nname: Prefill\n---\n" +
                "prefill context \toffice\n" +
                "prefill context home\tdesk\n" +
                "prefill context\n" +
                "prefill context home office\t\n" +
                "prefill context home office\n"
        )

        assertEquals(listOf(Prefill("context", "home office")), list.prefills)
    }

    @Test
    fun `text prefill rejects a bare carriage return and keeps a later valid value`() {
        val list = SmartListParser.parse(
            "---\nname: Prefill\n---\n" +
                "prefill project work\rclient\n" +
                "prefill project valid\n"
        )

        assertEquals(listOf(Prefill("project", "valid")), list.prefills)
    }

    @Test
    fun `prefills remain global across OR blocks`() {
        val list = SmartListParser.parse("""
            ---
            name: Global Prefill
            ---
            prefill project work
            prefill context office
            prefill priority A
            prefill due today+1
            not done
            OR
            prefill project urgent
            prefill context phone
            prefill priority B
            prefill due today+7
            done
        """.trimIndent())

        assertEquals(
            listOf(
                Prefill("project", "work"),
                Prefill("context", "office"),
                Prefill("priority", "A"),
                Prefill("due", "today+1"),
                Prefill("project", "urgent"),
                Prefill("context", "phone")
            ),
            list.prefills
        )
    }

    @Test
    fun `parse with group path resolves immediate parent templates`() {
        val list = SmartListParser.parse("""
            ---
            name: New Bug
            ---
            project includes {{dir}}
            context excludes {{dir:0}}
            prefill project {{dir}}
            prefill context {{dir:0}}
        """.trimIndent(), "ttd")

        assertNotNull(list)
        assertEquals(
            listOf(
                TextCondition(TextField.PROJECT, TextOp.INCLUDES, "ttd"),
                TextCondition(TextField.CONTEXT, TextOp.EXCLUDES, "ttd")
            ),
            list!!.conditions.single().conditions
        )
        assertEquals(
            listOf(Prefill("project", "ttd"), Prefill("context", "ttd")),
            list.prefills
        )
    }

    @Test
    fun `parse with nested group path resolves requested ancestors`() {
        val list = SmartListParser.parse("""
            ---
            name: Nested
            ---
            project includes {{dir:2}}
            context includes {{dir:1}}
            description includes {{dir:0}}
        """.trimIndent(), "work/ttd/v2")

        assertNotNull(list)
        assertEquals(
            listOf("work", "ttd", "v2"),
            list!!.conditions.single().conditions.map { (it as TextCondition).value }
        )
    }

    @Test
    fun `parse with group path rejects a template beyond lists boundary`() {
        val list = SmartListParser.parse("""
            ---
            name: Invalid
            ---
            project includes {{dir:1}}
        """.trimIndent(), "ttd")

        assertNull(list)
    }

    @Test
    fun `parse with group path rejects an overflowing ancestor depth`() {
        val list = SmartListParser.parse("""
            ---
            name: Invalid
            ---
            project includes {{dir:999999999999999999999}}
        """.trimIndent(), "ttd")

        assertNull(list)
    }

    @Test
    fun `parse with group path resolves templates only in allowed positions`() {
        val list = SmartListParser.parse("""
            ---
            name: {{dir:9}}
            ---
            sort by {{dir:9}}
            prefill priority {{dir:9}}
            prefill due {{dir:9}}
            not done
        """.trimIndent(), "ttd")

        assertNotNull(list)
        assertEquals("{{dir:9}}", list!!.name)
        assertEquals("{{dir:9}}", list.sorts.single().field)
        assertTrue(list.prefills.isEmpty())
        assertEquals(listOf(DoneCondition(false)), list.conditions.single().conditions)
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

    @Test
    fun `parse time aware date anchors with optional offsets`() {
        val list = SmartListParser.parse("""
            ---
            name: Timed
            ---
            due >= todayT14:00
            scheduled < today + 3 T10:00
            starting = 2026-12-31T09:30
        """.trimIndent())

        val conditions = list.conditions.single().conditions.map { it as DateCondition }
        assertEquals("14:00", conditions[0].value.anchorTime)
        assertEquals(3, conditions[1].value.offset)
        assertEquals("10:00", conditions[1].value.anchorTime)
        assertEquals("2026-12-31", conditions[2].value.anchorDate)
        assertEquals("09:30", conditions[2].value.anchorTime)
    }

    @Test
    fun `parse time existence only for date fields`() {
        val list = SmartListParser.parse("""
            ---
            name: Time
            ---
            has time due
            no time creation_date
            has time project
        """.trimIndent())

        assertEquals(
            listOf(
                TimeExistsCondition(true, DateField.DUE),
                TimeExistsCondition(false, DateField.CREATION_DATE)
            ),
            list.conditions.single().conditions
        )
    }

    @Test
    fun `malformed smart list times are ignored`() {
        val list = SmartListParser.parse("""
            ---
            name: Invalid Times
            ---
            due = todayT2:30
            due = todayT14:30:00
            due = todayT14:30Z
        """.trimIndent())
        assertTrue(list.conditions.isEmpty())
    }

    @Test
    fun `overflowing date offsets are ignored`() {
        val list = SmartListParser.parse("""
            ---
            name: Overflow
            ---
            due = today + 999999999999999999999
        """.trimIndent())
        assertTrue(list.conditions.isEmpty())
    }

    @Test
    fun `resolve timed date prefill to an absolute raw value`() {
        assertEquals(
            "2026-07-23T10:00",
            SmartListParser.resolveDateValue("today + 3 T10:00", java.time.LocalDate.of(2026, 7, 20))
        )
        assertEquals(
            "2026-12-28T09:30",
            SmartListParser.resolveDateValue("2026-12-31-3T09:30", java.time.LocalDate.of(2026, 7, 20))
        )
    }
}
