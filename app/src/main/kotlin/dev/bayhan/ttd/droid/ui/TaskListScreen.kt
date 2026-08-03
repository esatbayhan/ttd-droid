package dev.bayhan.ttd.droid.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.bayhan.ttd.droid.smartlist.Directive
import dev.bayhan.ttd.droid.smartlist.SmartListEval
import dev.bayhan.ttd.droid.task.Task
import dev.bayhan.ttd.droid.task.TaskQuery
import dev.bayhan.ttd.droid.ui.components.ChipBar
import dev.bayhan.ttd.droid.R
import dev.bayhan.ttd.droid.ui.components.TaskRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListContent(
    tasks: List<Task>,
    onMarkDone: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onUndoDelete: (Task) -> Unit,
    snackbarHostState: SnackbarHostState,
    onRemoveFromList: (Task) -> Unit = {},
    onUpdateTime: (Task) -> Unit = {},
    modifier: Modifier = Modifier,
    showFullTaskText: Boolean = false,
    hideDateValues: Boolean = false,
    hideUpdatedDate: Boolean = true,
    highlightTaskKey: String? = null,
    viewKey: String = "",
    groupDirectives: List<Directive> = emptyList(),
    sortDirectives: List<Directive> = emptyList(),
    sortField: String = "default",
    sortAsc: Boolean = true
) {
    val sorted = remember(tasks, sortDirectives) {
        val base = TaskQuery.defaultSort(tasks)
        if (sortDirectives.isNotEmpty()) SmartListEval.sort(base, sortDirectives) else base
    }
    var selectedProjects by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedContexts by remember { mutableStateOf<Set<String>>(emptySet()) }
    val dismissedKeys = remember { mutableStateSetOf<String>() }
    val toggledDoneKeys = remember { mutableStateSetOf<String>() }
    val disappearingKeys = remember { mutableStateSetOf<String>() }
    val toggleJobs = remember { mutableMapOf<String, Job>() }
    val collapsedGroupKeys = remember { mutableStateSetOf<String>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewKey) {
        collapsedGroupKeys.clear()
    }

    val availableProjects = remember(sorted) {
        sorted.flatMap { it.projects }.distinct().sorted()
    }
    val availableContexts = remember(sorted) {
        sorted.flatMap { it.contexts }.distinct().sorted()
    }

    LaunchedEffect(sorted) {
        selectedProjects = selectedProjects.filter { it in availableProjects }.toSet()
        selectedContexts = selectedContexts.filter { it in availableContexts }.toSet()
    }

    LaunchedEffect(sorted) {
        dismissedKeys.clear()
        toggledDoneKeys.clear()
        disappearingKeys.clear()
        toggleJobs.values.forEach { it.cancel() }
        toggleJobs.clear()
    }

    val filtered = remember(sorted, selectedProjects, selectedContexts, sortField, sortAsc, sortDirectives) {
        var result = sorted.filter { task ->
            (selectedProjects.isEmpty() || selectedProjects.all { it in task.projects }) &&
            (selectedContexts.isEmpty() || selectedContexts.all { it in task.contexts })
        }
        if (sortDirectives.isEmpty()) {
            result = when (sortField) {
                "priority" -> result.sortedBy { it.priority ?: 'Z' }
                "date" -> result.sortedBy { it.creationDate ?: "" }
                "description" -> result.sortedBy { it.description }
                else -> result
            }
            if (!sortAsc) result = result.reversed()
        }
        result
    }

    fun taskKey(task: Task): String = "${task.filename}\t${task.raw}"

    @Composable
    fun TaskItem(task: Task) {
        val key = taskKey(task)
        val deletedMsg = stringResource(R.string.task_deleted)
        val undoLabel = stringResource(R.string.task_undo)
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        dismissedKeys.add(key)
                        onDeleteTask(task)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = deletedMsg,
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onUndoDelete(task)
                            }
                        }
                        true
                    }
                    SwipeToDismissBoxValue.StartToEnd -> true
                    else -> false
                }
            }
        )
        LaunchedEffect(dismissState.currentValue) {
            if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
                onUpdateTime(task)
                dismissState.reset()
            }
        }
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                val color by animateColorAsState(
                    when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                        else -> Color.Transparent
                    }, label = "swipe-color"
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.StartToEnd -> Arrangement.Start
                        else -> Arrangement.End
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.EndToStart ->
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.task_swipe_delete), tint = MaterialTheme.colorScheme.onError)
                        SwipeToDismissBoxValue.StartToEnd ->
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.task_swipe_update), tint = MaterialTheme.colorScheme.onPrimary)
                        else -> {}
                    }
                }
            },
            content = {
                Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    TaskRow(
                        task = task,
                        isDone = task.done || key in toggledDoneKeys,
                        onToggleDone = {
                            if (key in toggledDoneKeys) {
                                toggledDoneKeys.remove(key)
                                toggleJobs[key]?.cancel()
                                toggleJobs.remove(key)
                            } else {
                                toggledDoneKeys.add(key)
                                onMarkDone(task)
                                toggleJobs[key] = scope.launch {
                                    delay(2000L)
                                    if (key !in toggledDoneKeys) return@launch
                                    disappearingKeys.add(key)
                                    delay(300L)
                                    onRemoveFromList(task)
                                }
                            }
                        },
                        onClick = { onEditTask(task) },
                        maxLines = if (showFullTaskText) Int.MAX_VALUE else 2,
                        hideDateValues = hideDateValues,
                        hideUpdatedDate = hideUpdatedDate,
                        highlighted = highlightTaskKey != null && taskKey(task) == highlightTaskKey
                    )
                }
            }
        )
    }

    @Composable
    fun AnimatedTaskItem(task: Task, modifier: Modifier = Modifier) {
        val key = taskKey(task)
        AnimatedVisibility(
            visible = key !in disappearingKeys,
            exit = fadeOut() + shrinkVertically(),
            modifier = modifier
        ) {
            TaskItem(task = task)
        }
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            val hasChips = availableProjects.isNotEmpty() || availableContexts.isNotEmpty()
            if (hasChips) {
                ChipBar(
                    projects = availableProjects, contexts = availableContexts,
                    selectedProjects = selectedProjects, selectedContexts = selectedContexts,
                    onToggleProject = { p ->
                        selectedProjects = if (p in selectedProjects) selectedProjects - p else selectedProjects + p
                    },
                    onToggleContext = { c ->
                        selectedContexts = if (c in selectedContexts) selectedContexts - c else selectedContexts + c
                    },
                    onReset = { selectedProjects = emptySet(); selectedContexts = emptySet() }
                )
            }
            val groupField = groupDirectives.firstOrNull()?.field
            if (groupField != null) {
                Text(
                    stringResource(R.string.grouped_by, stringResource(groupFieldLabelRes(groupField))),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            val displayTasks = filtered.filter { taskKey(it) !in dismissedKeys }
            if (displayTasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(stringResource(R.string.task_list_empty), modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                val grouped = if (groupDirectives.isNotEmpty()) {
                    SmartListEval.group(filtered.filter { taskKey(it) !in dismissedKeys }, groupDirectives)
                } else {
                    null
                }
                if (grouped != null && grouped.isNotEmpty()) {
                    val currentGroupKeys = grouped.keys.toSet()
                    LaunchedEffect(currentGroupKeys) {
                        collapsedGroupKeys.retainAll(currentGroupKeys)
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        grouped.forEach { (groupName, groupTasks) ->
                            val displayName = formatGroupKey(groupName, groupField ?: "")
                            val collapsed = groupName in collapsedGroupKeys
                            stickyHeader {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Button) {
                                            if (collapsed) {
                                                collapsedGroupKeys.remove(groupName)
                                            } else {
                                                collapsedGroupKeys.add(groupName)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "${groupTasks.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                imageVector = if (collapsed) {
                                                    Icons.Default.KeyboardArrowDown
                                                } else {
                                                    Icons.Default.KeyboardArrowUp
                                                },
                                                contentDescription = stringResource(
                                                    if (collapsed) R.string.group_expand else R.string.group_collapse
                                                ),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            if (!collapsed) {
                                items(groupTasks, key = { taskKey(it) }) { task ->
                                    AnimatedTaskItem(task = task, modifier = Modifier.animateItem())
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(displayTasks, key = { taskKey(it) }) { task ->
                            AnimatedTaskItem(task = task, modifier = Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}

private fun formatGroupKey(key: String, field: String): String {
    return when (field) {
        "due", "scheduled", "starting", "updated", "creation_date" -> {
            try {
                val date = LocalDate.parse(key)
                val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                date.format(formatter)
            } catch (_: Exception) {
                key
            }
        }
        "priority" -> if (key != "No Priority") "($key)" else key
        "project" -> if (key != "No Project") "+$key" else key
        "context" -> if (key != "No Context") "@$key" else key
        else -> key
    }
}

private fun groupFieldLabelRes(field: String): Int {
    return when (field) {
        "due" -> R.string.group_field_due
        "scheduled" -> R.string.group_field_scheduled
        "starting" -> R.string.group_field_starting
        "updated" -> R.string.group_field_updated
        "creation_date" -> R.string.group_field_creation_date
        "priority" -> R.string.group_field_priority
        "project" -> R.string.group_field_project
        "context" -> R.string.group_field_context
        "description" -> R.string.group_field_description
        "done" -> R.string.group_field_done
        else -> R.string.group_field_description
    }
}
