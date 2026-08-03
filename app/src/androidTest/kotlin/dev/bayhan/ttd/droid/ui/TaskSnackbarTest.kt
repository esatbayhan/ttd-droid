package dev.bayhan.ttd.droid.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import dev.bayhan.ttd.droid.task.Task
import dev.bayhan.ttd.droid.ui.theme.TtdDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TaskSnackbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deleteSnackbarPlacesUndoAboveAddButtonAndRestoresTask() {
        val task = Task(
            done = false,
            completionDate = null,
            priority = null,
            creationDate = null,
            description = "Buy milk",
            projects = emptyList(),
            contexts = emptyList(),
            tags = emptyMap(),
            raw = "Buy milk",
            filename = "task.txt"
        )
        var restoredTask: Task? = null

        composeRule.setContent {
            TtdDroidTheme {
                MainScreen(
                    tasks = listOf(task),
                    smartLists = emptyList(),
                    onMarkDone = {},
                    onSaveTask = {},
                    onEditTask = { _, _, _ -> },
                    onDeleteTask = {},
                    onUndoDelete = { restoredTask = it }
                )
            }
        }

        composeRule.onNodeWithText("Buy milk").performTouchInput { swipeLeft() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()
        }

        val undoBounds = composeRule.onNodeWithText("Undo").fetchSemanticsNode().boundsInRoot
        val addButtonBounds = composeRule
            .onNodeWithContentDescription("Add task")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Undo action must be above the add-task button",
            undoBounds.bottom <= addButtonBounds.top
        )

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(task, restoredTask)
    }
}
