package dev.bayhan.ttd.droid.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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
}
