package dev.bayhan.ttd.droid.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bayhan.ttd.droid.smartlist.Directive
import dev.bayhan.ttd.droid.task.Task
import dev.bayhan.ttd.droid.ui.theme.TtdDroidTheme
import org.junit.Rule
import org.junit.Test

class TaskGroupCollapseTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun groupHeaderTogglesTasksAndViewChangeExpandsIt() {
        val viewKey = mutableStateOf("first-view")
        val tasks = mutableStateOf(listOf(
            task("A one", 'A'),
            task("A two", 'A'),
            task("B one", 'B')
        ))

        composeRule.setContent {
            TtdDroidTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                TaskListContent(
                    tasks = tasks.value,
                    onMarkDone = {},
                    onEditTask = {},
                    onDeleteTask = {},
                    onUndoDelete = {},
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxSize(),
                    viewKey = viewKey.value,
                    groupDirectives = listOf(Directive("priority"))
                )
            }
        }

        composeRule.onNodeWithText("A one").assertIsDisplayed()
        composeRule.onNodeWithText("A two").assertIsDisplayed()
        composeRule.onNodeWithText("B one").assertIsDisplayed()

        composeRule.onNodeWithText("(A)").performClick()

        composeRule.onNodeWithText("A one").assertDoesNotExist()
        composeRule.onNodeWithText("A two").assertDoesNotExist()
        composeRule.onNodeWithText("B one").assertIsDisplayed()
        composeRule.onNodeWithText("(A)").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Expand group").assertIsDisplayed()

        composeRule.runOnIdle { tasks.value += task("A three", 'A') }
        composeRule.onNodeWithText("A one").assertDoesNotExist()
        composeRule.onNodeWithText("A two").assertDoesNotExist()
        composeRule.onNodeWithText("A three").assertDoesNotExist()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Expand group").assertIsDisplayed()

        composeRule.onNodeWithText("(A)").performClick()
        composeRule.onNodeWithText("A one").assertIsDisplayed()
        composeRule.onNodeWithText("A three").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Collapse group").assertCountEquals(2)

        composeRule.onNodeWithText("(A)").performClick()
        composeRule.runOnIdle { viewKey.value = "second-view" }
        composeRule.onNodeWithText("A one").assertIsDisplayed()
        composeRule.onNodeWithText("A two").assertIsDisplayed()
    }

    private fun task(description: String, priority: Char) = Task(
        done = false,
        completionDate = null,
        priority = priority,
        creationDate = null,
        description = description,
        projects = emptyList(),
        contexts = emptyList(),
        tags = emptyMap(),
        raw = "($priority) $description",
        filename = "$description.txt"
    )
}
