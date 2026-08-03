package dev.bayhan.ttd.droid.smartlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartListSerializerTest {

    @Test
    fun `serialize minimal list with name only`() {
        val list = SmartList(name = "Inbox")
        val expected = "---\nname: Inbox\n---\n"
        assertEquals(expected, SmartListSerializer.serialize(list))
    }

    @Test
    fun `serialize full frontmatter`() {
        val list = SmartList(name = "Today", icon = "📅", description = "Due or scheduled today")
        val expected = "---\nname: Today\nicon: 📅\ndescription: Due or scheduled today\n---\n"
        assertEquals(expected, SmartListSerializer.serialize(list))
    }

    @Test
    fun `serialize done and not done`() {
        val list = SmartList(name = "Done", conditions = listOf(
            FilterBlock(listOf(DoneCondition(true)))
        ))
        assertTrue(SmartListSerializer.serialize(list).contains("done"))
    }

    @Test
    fun `serialize priority conditions`() {
        val list = SmartList(name = "High", conditions = listOf(
            FilterBlock(listOf(
                PriorityCondition(PriorityOp.ABOVE, 'C'),
                PriorityCondition(PriorityOp.EQ, 'A')
            ))
        ))
        val output = SmartListSerializer.serialize(list)
        assertTrue(output.contains("priority above C"))
        assertTrue(output.contains("priority = A"))
    }

    @Test
    fun `serialize text conditions`() {
        val list = SmartList(name = "Work", conditions = listOf(
            FilterBlock(listOf(
                TextCondition(TextField.PROJECT, TextOp.INCLUDES, "work"),
                TextCondition(TextField.CONTEXT, TextOp.EXCLUDES, "home")
            ))
        ))
        val output = SmartListSerializer.serialize(list)
        assertTrue(output.contains("project includes work"))
        assertTrue(output.contains("context excludes home"))
    }

    @Test
    fun `serialize date conditions`() {
        val list = SmartList(name = "Today", conditions = listOf(
            FilterBlock(listOf(
                DateCondition(DateField.DUE, CompareOp.LTE, DateValue(DateAnchor.TODAY, 0)),
                DateCondition(DateField.SCHEDULED, CompareOp.LTE, DateValue(DateAnchor.TODAY, 7))
            ))
        ))
        val output = SmartListSerializer.serialize(list)
        assertTrue(output.contains("due <= today"))
        assertTrue(output.contains("scheduled <= today+7"))
    }

    @Test
    fun `serialize existence conditions`() {
        val list = SmartList(name = "Dated", conditions = listOf(
            FilterBlock(listOf(
                ExistsCondition(true, Field.DUE),
                ExistsCondition(false, Field.PRIORITY)
            ))
        ))
        val output = SmartListSerializer.serialize(list)
        assertTrue(output.contains("has due"))
        assertTrue(output.contains("no priority"))
    }

    @Test
    fun `serialize OR blocks`() {
        val list = SmartList(name = "Active", conditions = listOf(
            FilterBlock(listOf(DoneCondition(false), ExistsCondition(true, Field.DUE))),
            FilterBlock(listOf(DateCondition(DateField.SCHEDULED, CompareOp.LTE, DateValue(DateAnchor.TODAY, 0))))
        ))
        val output = SmartListSerializer.serialize(list)
        val lines = output.lines()
        assertTrue(lines.contains("OR"))
        val orIndex = lines.indexOf("OR")
        assertTrue(orIndex > 0)
    }

    @Test
    fun `serialize directives`() {
        val list = SmartList(
            name = "Sorted",
            sorts = listOf(Directive("priority", false), Directive("due", true)),
            groups = listOf(Directive("project", true))
        )
        val output = SmartListSerializer.serialize(list)
        assertTrue(output.contains("sort by priority desc"))
        assertTrue(output.contains("sort by due asc"))
        assertTrue(output.contains("group by project asc"))
    }

    @Test
    fun `serialize prefills`() {
        val list = SmartList(
            name = "Prefilled",
            prefills = listOf(
                Prefill("project", "work"),
                Prefill("priority", "A"),
                Prefill("due", "today+3")
            )
        )
        val output = SmartListSerializer.serialize(list)
        assertTrue(output.contains("prefill project work"))
        assertTrue(output.contains("prefill priority A"))
        assertTrue(output.contains("prefill due today+3"))
    }

    @Test
    fun `serialize absolute date`() {
        val list = SmartList(name = "Fixed", conditions = listOf(
            FilterBlock(listOf(
                DateCondition(DateField.DUE, CompareOp.EQ, DateValue(DateAnchor.ABSOLUTE, 0, "2026-12-31"))
            ))
        ))
        assertTrue(SmartListSerializer.serialize(list).contains("due = 2026-12-31"))
    }

    @Test
    fun `serialize negative date offset`() {
        val list = SmartList(name = "Past", conditions = listOf(
            FilterBlock(listOf(
                DateCondition(DateField.CREATION_DATE, CompareOp.GTE, DateValue(DateAnchor.TODAY, -14))
            ))
        ))
        assertTrue(SmartListSerializer.serialize(list).contains("creation_date >= today-14"))
    }

    @Test
    fun `round-trip parse then serialize`() {
        val raw = """
            |---
            |name: AllTheThings
            |icon: 🧪
            |description: Full round-trip test
            |order: 7
            |---
            |done
            |not done
            |priority above C
            |priority below D
            |priority = A
            |project includes work
            |context excludes home
            |description equals meeting
            |has due
            |no priority
            |due <= today
            |due < today
            |due > today+3
            |due >= today-1
            |due = 2026-12-31
            |due != 2026-12-31+3
            |OR
            |scheduled <= today
            |
            |sort by priority desc
            |sort by due asc
            |group by project asc
            |group by context desc
            |prefill project test
            |prefill due today
        """.trimMargin()
        val parsed = SmartListParser.parse(raw)
        val serialized = SmartListSerializer.serialize(parsed)
        val reparsed = SmartListParser.parse(serialized)

        assertEquals(parsed.name, reparsed.name)
        assertEquals(parsed.icon, reparsed.icon)
        assertEquals(parsed.description, reparsed.description)
        assertEquals(parsed.order, reparsed.order)
        assertEquals(parsed.conditions.size, reparsed.conditions.size)
        assertEquals(parsed.sorts.size, reparsed.sorts.size)
        assertEquals(parsed.groups.size, reparsed.groups.size)
        assertEquals(parsed.prefills.size, reparsed.prefills.size)

        for (i in parsed.conditions.indices) {
            val orig = parsed.conditions[i].conditions
            val back = reparsed.conditions[i].conditions
            assertEquals(orig.size, back.size)
            for (j in orig.indices) {
                assertEquals(orig[j], back[j])
            }
        }

        for (i in parsed.sorts.indices) {
            assertEquals(parsed.sorts[i].field, reparsed.sorts[i].field)
            assertEquals(parsed.sorts[i].ascending, reparsed.sorts[i].ascending)
        }

        for (i in parsed.groups.indices) {
            assertEquals(parsed.groups[i].field, reparsed.groups[i].field)
            assertEquals(parsed.groups[i].ascending, reparsed.groups[i].ascending)
        }

        for (i in parsed.prefills.indices) {
            assertEquals(parsed.prefills[i].field, reparsed.prefills[i].field)
            assertEquals(parsed.prefills[i].value, reparsed.prefills[i].value)
        }
    }

    @Test
    fun `serialize priority below`() {
        val list = SmartList(name = "Low", conditions = listOf(
            FilterBlock(listOf(PriorityCondition(PriorityOp.BELOW, 'D')))
        ))
        val output = SmartListSerializer.serialize(list)
        assertTrue(output.contains("priority below D"))
    }

    @Test
    fun `serialize text equals`() {
        val list = SmartList(name = "Exact", conditions = listOf(
            FilterBlock(listOf(TextCondition(TextField.DESCRIPTION, TextOp.EQUALS, "meeting")))
        ))
        val output = SmartListSerializer.serialize(list)
        assertTrue(output.contains("description equals meeting"))
    }

    @Test
    fun `serialize date compare ops`() {
        val list = SmartList(name = "Compare", conditions = listOf(
            FilterBlock(listOf(
                DateCondition(DateField.DUE, CompareOp.LT, DateValue(DateAnchor.TODAY, 0)),
                DateCondition(DateField.DUE, CompareOp.GT, DateValue(DateAnchor.TODAY, 0)),
                DateCondition(DateField.DUE, CompareOp.GTE, DateValue(DateAnchor.TODAY, 0)),
                DateCondition(DateField.DUE, CompareOp.NEQ, DateValue(DateAnchor.TODAY, 0)),
            ))
        ))
        val output = SmartListSerializer.serialize(list)
        assertTrue(output.contains("due < today"))
        assertTrue(output.contains("due > today"))
        assertTrue(output.contains("due >= today"))
        assertTrue(output.contains("due != today"))
    }

    @Test
    fun `serialize absolute date with offset`() {
        val list = SmartList(name = "AbsOffset", conditions = listOf(
            FilterBlock(listOf(
                DateCondition(DateField.DUE, CompareOp.EQ, DateValue(DateAnchor.ABSOLUTE, 3, "2026-12-31"))
            ))
        ))
        val output = SmartListSerializer.serialize(list)
        assertTrue(output.contains("due = 2026-12-31+3"))
    }

    @Test
    fun `serialize empty description`() {
        val list = SmartList(name = "EmptyDesc", description = "")
        val output = SmartListSerializer.serialize(list)
        assertTrue(output.contains("description:"))
        val reparsed = SmartListParser.parse(output)
        assertEquals("", reparsed.description)
    }

    @Test
    fun `serialize order field`() {
        val list = SmartList(name = "Ordered", order = 3)
        val output = SmartListSerializer.serialize(list)
        assertTrue(output.contains("order: 3"))
    }

    @Test
    fun `serialize with order zero omits field`() {
        val list = SmartList(name = "ZeroOrder", order = 0)
        val output = SmartListSerializer.serialize(list)
        assertTrue(!output.contains("order:"))
    }

    @Test
    fun `serialize time aware conditions and time existence`() {
        val list = SmartList(
            name = "Timed",
            conditions = listOf(FilterBlock(listOf(
                DateCondition(DateField.DUE, CompareOp.GTE,
                    DateValue(DateAnchor.TODAY, 3, anchorTime = "10:00")),
                DateCondition(DateField.STARTING, CompareOp.EQ,
                    DateValue(DateAnchor.ABSOLUTE, anchorDate = "2026-12-31", anchorTime = "09:30")),
                TimeExistsCondition(true, DateField.DUE),
                TimeExistsCondition(false, DateField.UPDATED)
            )))
        )
        val raw = SmartListSerializer.serialize(list)
        assertTrue(raw.contains("due >= today+3T10:00"))
        assertTrue(raw.contains("starting = 2026-12-31T09:30"))
        assertTrue(raw.contains("has time due"))
        assertTrue(raw.contains("no time updated"))
        assertEquals(list.conditions, SmartListParser.parse(raw).conditions)
    }
}
