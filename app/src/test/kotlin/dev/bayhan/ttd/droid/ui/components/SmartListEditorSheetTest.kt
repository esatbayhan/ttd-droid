package dev.bayhan.ttd.droid.ui.components

import dev.bayhan.ttd.droid.smartlist.SmartListParser
import dev.bayhan.ttd.droid.smartlist.SmartListSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartListEditorSheetTest {
    @Test
    fun `form serialization preserves raw templates over resolved initial list`() {
        val raw = """
            ---
            name: New Bug
            ---
            project includes {{dir}}
            prefill project {{dir}}
        """.trimIndent()
        val resolved = SmartListParser.parse(raw, "ttd")!!

        val formState = initialSmartListFormState(resolved, raw)
        val serialized = SmartListSerializer.serialize(formState.toSmartList())

        assertEquals(
            "---\nname: New Bug\n---\nproject includes {{dir}}\n\nprefill project {{dir}}\n",
            serialized
        )
    }
}
