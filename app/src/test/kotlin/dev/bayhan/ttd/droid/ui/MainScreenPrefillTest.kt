package dev.bayhan.ttd.droid.ui

import dev.bayhan.ttd.droid.smartlist.Prefill
import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenPrefillTest {
    @Test
    fun `smart list prefill puts priority first and resolves timed dates`() {
        val result = buildSmartListPrefill(
            listOf(Prefill("project", "work"), Prefill("priority", "A"), Prefill("due", "2026-07-20T10:00"))
        )

        assertEquals("(A) +work due:2026-07-20T10:00", result)
    }
}
