package dev.bayhan.ttd.droid.store

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import dev.bayhan.ttd.droid.util.SampleDataSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class TaskStoreTest {

    private lateinit var context: Context
    private lateinit var store: TaskStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = TaskStore(context)
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
    fun `loads tasks from seeded sample data`() {
        val rootDir = File(context.filesDir, "test-taskstore-seed")
        rootDir.deleteRecursively()
        rootDir.mkdirs()

        val targetDoc = DocumentFile.fromFile(rootDir)
        val seeded = SampleDataSeeder.seed(context, targetDoc)
        assertTrue("seeding should succeed", seeded)

        store.setRoot(targetDoc)
        assertTrue("store should be ready", store.isReady())

        val tasks = store.loadTasks()
        assertTrue("should have tasks", tasks.isNotEmpty())
        assertTrue("should have 50+ tasks", tasks.size >= 50)

        rootDir.deleteRecursively()
    }

    @Test
    fun `seeded tasks have projects extracted`() {
        val rootDir = File(context.filesDir, "test-taskstore-projects")
        rootDir.deleteRecursively()
        rootDir.mkdirs()

        val targetDoc = DocumentFile.fromFile(rootDir)
        SampleDataSeeder.seed(context, targetDoc)
        store.setRoot(targetDoc)

        val tasks = store.loadTasks()
        val allProjects = tasks.flatMap { it.projects }.toSet()
        assertTrue("should have Work project", allProjects.contains("Work"))
        assertTrue("should have Personal project", allProjects.contains("Personal"))
        assertTrue("should have Health project", allProjects.contains("Health"))
        assertTrue("should have GarageSale project", allProjects.contains("GarageSale"))
        assertTrue("should have OpenSource project", allProjects.contains("OpenSource"))

        rootDir.deleteRecursively()
    }

    @Test
    fun `seeded tasks have contexts extracted`() {
        val rootDir = File(context.filesDir, "test-taskstore-contexts")
        rootDir.deleteRecursively()
        rootDir.mkdirs()

        val targetDoc = DocumentFile.fromFile(rootDir)
        SampleDataSeeder.seed(context, targetDoc)
        store.setRoot(targetDoc)

        val tasks = store.loadTasks()
        val allContexts = tasks.flatMap { it.contexts }.toSet()
        assertTrue("should have phone context", allContexts.contains("phone"))
        assertTrue("should have email context", allContexts.contains("email"))
        assertTrue("should have home context", allContexts.contains("home"))
        assertTrue("should have office context", allContexts.contains("office"))
        assertTrue("should have computer context", allContexts.contains("computer"))

        rootDir.deleteRecursively()
    }

    @Test
    fun `seeded tasks include done tasks`() {
        val rootDir = File(context.filesDir, "test-taskstore-done")
        rootDir.deleteRecursively()
        rootDir.mkdirs()

        val targetDoc = DocumentFile.fromFile(rootDir)
        SampleDataSeeder.seed(context, targetDoc)
        store.setRoot(targetDoc)

        val allTasks = store.loadTasks(includeDone = true)
        val doneTasks = allTasks.filter { it.done }
        assertTrue("should have completed tasks", doneTasks.isNotEmpty())
        assertTrue("should have completed tasks from done.txt", doneTasks.size >= 5)

        rootDir.deleteRecursively()
    }

    @Test
    fun `seeded tasks have date keys`() {
        val rootDir = File(context.filesDir, "test-taskstore-dates")
        rootDir.deleteRecursively()
        rootDir.mkdirs()

        val targetDoc = DocumentFile.fromFile(rootDir)
        SampleDataSeeder.seed(context, targetDoc)
        store.setRoot(targetDoc)

        val tasks = store.loadTasks(includeDone = true)
        val dateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        val dueTasks = tasks.filter { it.tags["due"]?.let { dateRegex.matches(it) } == true }
        val scheduledTasks = tasks.filter { it.tags["scheduled"]?.let { dateRegex.matches(it) } == true }
        val startingTasks = tasks.filter { it.tags["starting"]?.let { dateRegex.matches(it) } == true }
        val updatedTasks = tasks.filter { it.tags["updated"]?.let { dateRegex.matches(it) } == true }

        assertTrue("should have tasks with due dates", dueTasks.isNotEmpty())
        assertTrue("should have tasks with scheduled dates", scheduledTasks.isNotEmpty())
        assertTrue("should have tasks with starting dates", startingTasks.isNotEmpty())
        assertTrue("should have tasks with updated dates", updatedTasks.isNotEmpty())

        rootDir.deleteRecursively()
    }

    @Test
    fun `seeded smart list directory is accessible`() {
        val rootDir = File(context.filesDir, "test-taskstore-smartlists")
        rootDir.deleteRecursively()
        rootDir.mkdirs()

        val targetDoc = DocumentFile.fromFile(rootDir)
        SampleDataSeeder.seed(context, targetDoc)
        store.setRoot(targetDoc)

        val listsDir = store.loadSmartListsDir()
        assertNotNull("lists.d/ should exist", listsDir)

        rootDir.deleteRecursively()
    }
}
