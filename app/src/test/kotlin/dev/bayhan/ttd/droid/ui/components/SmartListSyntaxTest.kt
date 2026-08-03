package dev.bayhan.ttd.droid.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartListSyntaxTest {

    private val testColors = ListSyntaxColors(
        delim = Color.Gray,
        comment = Color.Gray,
        keyword = Color.Blue,
        operator = Color.Red,
        date = Color.Green,
        directive = Color.Magenta,
        orKeyword = Color.Red,
        template = Color.Green
    )

    @Test
    fun `buildListHighlightedText returns non-null for empty string`() {
        val result = buildListHighlightedText("", testColors)
        assertNotNull(result)
        assertTrue(result.text.isEmpty())
    }

    @Test
    fun `buildListHighlightedText returns non-null for valid input`() {
        val raw = "---\nname: Test\n---\ndue <= today\n"
        val result = buildListHighlightedText(raw, testColors)
        assertNotNull(result)
        assertTrue(result.text.contains("due"))
    }

    @Test
    fun `parseValidity returns true for valid input`() {
        val raw = "---\nname: Test\n---\ndue <= today\n"
        val (valid, error) = parseValidity(raw)
        assertTrue(valid)
    }

    @Test
    fun `parseValidity returns true for empty input`() {
        val (valid, _) = parseValidity("")
        assertTrue(valid)
    }

    @Test
    fun `parseValidity handles invalid syntax gracefully`() {
        val (valid, _) = parseValidity("garbage text")
        assertTrue(valid)
    }

    @Test
    fun `buildListHighlightedText handles multiple OR blocks`() {
        val raw = "due <= today\nOR\nscheduled <= today\n"
        val result = buildListHighlightedText(raw, testColors)
        assertTrue(result.text.contains("OR"))
    }

    @Test
    fun `date style covers optional time and time existence keyword`() {
        val raw = "due >= todayT14:30\nhas time due\nstarting = 2026-07-20T09:00\n"
        val result = buildListHighlightedText(raw, testColors)
        val timedDateStart = raw.indexOf("2026-07-20T09:00")
        assertTrue(result.spanStyles.any { span ->
            span.start == timedDateStart && span.end == timedDateStart + "2026-07-20T09:00".length &&
                span.item.color == testColors.date
        })
        val timeStart = raw.indexOf("time")
        val timeSpan = result.spanStyles.single { span -> span.start == timeStart && span.end == timeStart + 4 }
        assertEquals(testColors.operator, timeSpan.item.color)
    }
}
