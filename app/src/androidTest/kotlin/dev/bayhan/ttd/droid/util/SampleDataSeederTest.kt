package dev.bayhan.ttd.droid.util

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SampleDataSeederTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `seed creates directory structure`() {
        val rootDir = File(context.filesDir, "test-sample")
        rootDir.deleteRecursively()
        rootDir.mkdirs()

        val targetDoc = DocumentFile.fromFile(rootDir)
        val success = SampleDataSeeder.seed(context, targetDoc)
        assertTrue("seeding should succeed", success)

        val entries = rootDir.list()?.toSet() ?: emptySet()
        assertTrue("should have todo.txt", entries.contains("todo.txt"))
        assertTrue("should have done.txt", entries.contains("done.txt"))
        assertTrue("should have projects/", entries.contains("projects"))
        assertTrue("should have lists.d/", entries.contains("lists.d"))

        val projectsDir = File(rootDir, "projects")
        assertTrue("projects/ should exist", projectsDir.isDirectory)
        assertTrue("should have Work.txt", projectsDir.list()?.contains("Work.txt") == true)

        val listsDir = File(rootDir, "lists.d")
        assertTrue("lists.d/ should exist", listsDir.isDirectory)
        val listFiles = listsDir.list()?.toSet() ?: emptySet()
        assertTrue(listFiles.contains("Today.list"))
        assertTrue(listFiles.contains("Review.list"))
        assertTrue(listFiles.contains("Stale.list"))
        assertTrue(listFiles.contains("ttd"))

        val ttdDir = File(listsDir, "ttd")
        val ttdFiles = ttdDir.list()?.toSet() ?: emptySet()
        assertTrue(ttdFiles.contains("bugs.list"))
        assertTrue(ttdFiles.contains("features.list"))

        rootDir.deleteRecursively()
    }

    @Test
    fun `seed resolves dates in txt files`() {
        val rootDir = File(context.filesDir, "test-sample-dates")
        rootDir.deleteRecursively()
        rootDir.mkdirs()

        val targetDoc = DocumentFile.fromFile(rootDir)
        SampleDataSeeder.seed(context, targetDoc)

        val todoFile = File(rootDir, "todo.txt")
        val content = todoFile.readText()

        assertFalse("should not contain unresolved template", content.contains("{{today}}"))
        assertFalse("should not contain unresolved +3d", content.contains("{{+3d}}"))
        assertTrue("should contain resolved date YYYY-MM-DD", content.contains(Regex("\\d{4}-\\d{2}-\\d{2}")))

        rootDir.deleteRecursively()
    }
}
