package dev.bayhan.ttd.droid.store

import android.content.Context
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import dev.bayhan.ttd.droid.smartlist.Prefill
import dev.bayhan.ttd.droid.smartlist.SmartListLoader
import dev.bayhan.ttd.droid.smartlist.TextCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class TaskStoreTest {

    private lateinit var context: Context
    private lateinit var store: TaskStore
    private lateinit var rootDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = TaskStore(context)
        rootDir = File(context.filesDir, "test-task-store").apply {
            deleteRecursively()
            mkdirs()
        }
        File(context.filesDir, "smartlists.cache").delete()
    }

    @After
    fun cleanup() {
        rootDir.deleteRecursively()
        File(context.filesDir, "smartlists.cache").delete()
    }

    private fun configureFixture() {
        File(rootDir, "active.txt").writeText(
            "(A) 2026-07-20 Ship release +Work @office due:2026-07-21\n" +
                "Review logs +ttd @computer updated:2026-07-19"
        )
        File(rootDir, "ignored.md").writeText("not a task")
        File(rootDir, "done.txt.d").mkdirs()
        File(rootDir, "done.txt.d/completed.txt").writeText(
            "x 2026-07-19 2026-07-01 Submit report +Work @office"
        )
        store.setRoot(DocumentFile.fromFile(rootDir))
    }

    @Test
    fun `store is not ready without root URI`() {
        assertFalse(store.isReady())
    }

    @Test
    fun `loadTasks returns empty list when not configured`() {
        val tasks = store.loadTasks()
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun `loads active txt tasks from configured root`() {
        configureFixture()
        val tasks = store.loadTasks()
        assertEquals(2, tasks.size)
        assertEquals(setOf("active.txt"), tasks.map { it.filename }.toSet())
        assertTrue(tasks.any { it.projects == listOf("Work") && it.contexts == listOf("office") })
        assertTrue(tasks.any { it.tags["updated"] == "2026-07-19" })
    }

    @Test
    fun `includes done directory only when requested`() {
        configureFixture()
        assertEquals(2, store.loadTasks().size)
        val allTasks = store.loadTasks(includeDone = true)
        assertEquals(3, allTasks.size)
        assertTrue(allTasks.any { it.done && it.filename == "completed.txt" })
    }

    @Test
    fun `SAF smart list loading resolves group templates and skips boundary escapes`() {
        val listsDir = File(rootDir, "lists.d").apply { mkdirs() }
        val groupDir = File(listsDir, "ttd").apply { mkdirs() }
        val validRaw = "---\nname: New Bug\n---\nproject includes {{dir}}\nprefill project {{dir}}\n"
        File(groupDir, "new-bug.list").writeText(validRaw)
        File(groupDir, "invalid.list").writeText(
            "---\nname: Invalid\n---\nproject includes {{dir:1}}\n"
        )

        val loaded = SmartListLoader.load(DocumentFile.fromFile(listsDir), context)

        assertEquals(1, loaded.size)
        assertEquals("ttd", loaded.single().group)
        assertEquals("new-bug", loaded.single().fileName)
        assertEquals(validRaw, loaded.single().raw)
        assertEquals("ttd", (loaded.single().list.conditions.single().conditions.single() as TextCondition).value)
        assertEquals(listOf(Prefill("project", "ttd")), loaded.single().list.prefills)
    }

    @Test
    fun `current smart list cache resolves group templates and skips boundary escapes`() {
        val validRaw = "---\nname: New Bug\n---\nproject includes {{dir}}\n"
        val invalidRaw = "---\nname: Invalid\n---\nproject includes {{dir:1}}\n"
        File(context.filesDir, "smartlists.cache").writeText(
            "ttd\tnew-bug\t${encode(validRaw)}\n" +
                "ttd\tinvalid\t${encode(invalidRaw)}"
        )

        val loaded = store.loadCachedSmartLists()

        assertEquals(1, loaded.size)
        assertEquals("new-bug", loaded.single().fileName)
        assertEquals(validRaw, loaded.single().raw)
        assertEquals("ttd", (loaded.single().list.conditions.single().conditions.single() as TextCondition).value)
    }

    @Test
    fun `legacy smart list cache resolves group templates and skips boundary escapes`() {
        val validRaw = "---\nname: Work List\n---\nproject includes {{dir:1}}\n"
        val invalidRaw = "---\nname: Invalid\n---\nproject includes {{dir}}\n"
        File(context.filesDir, "smartlists.cache").writeText(
            "work/ttd\t${encode(validRaw)}\n" +
                "\t${encode(invalidRaw)}"
        )

        val loaded = store.loadCachedSmartLists()

        assertEquals(1, loaded.size)
        assertEquals("Work List", loaded.single().fileName)
        assertEquals(validRaw, loaded.single().raw)
        assertEquals("work", (loaded.single().list.conditions.single().conditions.single() as TextCondition).value)
    }

    private fun encode(raw: String): String =
        Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP)
}
