package dev.bayhan.ttd.droid.ui.components

import androidx.compose.ui.graphics.Color
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
}
